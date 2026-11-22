const assert = require("node:assert/strict");
const { test } = require("node:test");
const { setTimeout: delay } = require("node:timers/promises");
const { chromium } = require("playwright");

const panelUrl = process.env.E2E_PANEL_URL ?? "http://localhost:5173";
const mobileUrl = process.env.E2E_MOBILE_URL ?? "http://localhost:5174";
const mailUrl = process.env.E2E_MAIL_URL ?? "http://localhost:8025";
const coordinatorEmail = process.env.BOOTSTRAP_COORDINATOR_EMAIL ?? "coordenacao@example.test";
const password = "Caderno-de-campo-2026!";
const cpf = "52998224725";

async function mail(subject) {
  for (let attempt = 0; attempt < 30; attempt++) {
    const response = await fetch(`${mailUrl}/api/v1/messages`);
    assert.equal(response.status, 200);
    const { messages } = await response.json();
    const match = messages?.find(
      (item) =>
        item.Subject?.includes(subject) &&
        item.To?.some(({ Address }) => Address === coordinatorEmail),
    );
    if (match) return match;
    await delay(1000);
  }
  throw new Error(`E-mail de teste não recebido: ${subject}`);
}

async function chooseFirst(select) {
  await select.locator("option:not([value=''])").first().waitFor({ state: "attached" });
  await select.selectOption({ index: 1 });
}

async function createFarmer(panel, farmer) {
  await panel.goto(`${panelUrl}/agricultores/novo`);
  await panel.locator("#f-name").fill(farmer.name);
  await panel.locator("#f-cpf").fill(farmer.cpf);
  await panel.locator("#f-phone").fill("74999990000");
  await panel.locator("#f-group").selectOption({ label: "Grupo de teste SPG" });
  await panel.getByRole("checkbox", { name: /Gerar código de acesso/ }).check();
  const saved = panel.waitForResponse(
    (response) =>
      response.url().endsWith("/access-codes") && response.request().method() === "POST",
  );
  await panel.getByRole("button", { name: "Salvar agricultor" }).click();
  const response = await saved;
  assert.equal(response.ok(), true);
  return (await response.json()).code;
}

async function activate(page, number, code) {
  await page.goto(`${mobileUrl}/login`);
  await page.locator("#cpf-input").fill(number);
  await page.locator("#code-input").fill(code);
  await page.getByRole("button", { name: "Entrar no Agrofácil" }).click();
  await page.getByRole("button", { name: /Li e concordo/ }).click();
  await page.getByRole("link", { name: /Registrar Manejo/ }).waitFor();
}

test(
  "coordenação cadastra grupo, agricultor e propriedade e o app sincroniza manejos",
  { timeout: 180_000 },
  async (t) => {
    const browser = await chromium.launch();
    t.after(() => browser.close());
    const panel = await browser.newPage();
    const invite = await mail("Convite para o painel");
    const inviteResponse = await fetch(`${mailUrl}/api/v1/message/${invite.ID}`);
    const inviteText = (await inviteResponse.json()).Text;
    const token = new URL(inviteText.match(/https?:\/\/\S+/)[0]).searchParams.get("token");
    await panel.goto(`${panelUrl}/convite?token=${encodeURIComponent(token)}`);
    await panel.locator("#invite-name").fill("Coordenação de teste");
    await panel.locator("#invite-password").fill(password);
    await panel.getByRole("button", { name: "Concluir cadastro" }).click();

    await panel.locator("#login-email").fill(coordinatorEmail);
    await panel.locator("#login-password").fill(password);
    await panel.getByRole("button", { name: "Entrar", exact: true }).click();
    const login = await mail("Seu código de acesso");
    await panel.getByLabel("Dígito 1 do código").fill(login.Subject.match(/\d{6}/)[0]);
    await panel.waitForURL(`${panelUrl}/`);

    await panel.goto(`${panelUrl}/grupos`);
    await panel.getByPlaceholder(/Grupo Agroecológico Serra Azul/).fill("Grupo de teste SPG");
    await panel.getByRole("button", { name: /Salvar grupo/ }).click();
    await panel.getByText("Grupo de teste SPG", { exact: true }).first().waitFor();

    const code = await createFarmer(panel, { name: "Pessoa agricultora de teste", cpf });
    await panel.goto(`${panelUrl}/propriedades`);
    await panel.getByRole("button", { name: /Nova propriedade/i }).click();
    const farmerOption = panel
      .locator("#property-farmer option")
      .filter({ hasText: "Pessoa agricultora de teste" });
    await farmerOption.waitFor({ state: "attached" });
    await panel.locator("#property-farmer").selectOption(await farmerOption.getAttribute("value"));
    await panel.locator("#property-name").fill("Sítio E2E");
    await panel.getByRole("button", { name: "Criar propriedade" }).click();
    await panel.getByText("Sítio E2E", { exact: true }).waitFor();

    const otherCpf = "12345678909";
    const otherCode = await createFarmer(panel, { name: "Segunda pessoa de teste", cpf: otherCpf });

    const app = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const page = await app.newPage();
    await activate(page, cpf, code);
    await page.getByText("Sítio E2E", { exact: true }).waitFor();
    await page.evaluate(async () => {
      await navigator.serviceWorker.ready;
      if (!navigator.serviceWorker.controller) {
        await new Promise((resolve) =>
          navigator.serviceWorker.addEventListener("controllerchange", resolve, { once: true }),
        );
      }
    });

    await page.getByRole("link", { name: /Registrar Manejo/ }).click();
    await page.locator("#crop-other").fill("Hortaliça do teste");
    await page.getByRole("button", { name: "Salvar Manejo" }).click();
    await page.getByRole("status").filter({ hasText: "Manejo salvo." }).waitFor();

    for (const [type, name, unit, inputSelector] of [
      ["INPUT_APPLICATION", /Aplicação de Insumo/, "LITER", "#input-other"],
      ["HARVEST", /^Colheita/, "BOX", null],
      ["INPUT_PURCHASE", /Compra de Insumo/, "UNIT", "#purchase-name"],
    ]) {
      await page.getByRole("link", { name: "+ Anotar", exact: true }).click();
      await page.getByRole("button", { name }).click();
      if (type !== "INPUT_PURCHASE") await chooseFirst(page.locator("#planting-select"));
      if (inputSelector) await page.locator(inputSelector).fill("Insumo do teste");
      await page.getByRole("spinbutton").fill("2");
      await page.getByLabel("Unidade da quantidade").selectOption(unit);
      const submitted = page.waitForResponse(
        (response) =>
          response.url().endsWith("/sync/push") &&
          response
            .request()
            .postDataJSON()
            .mutations.some((item) => item.data?.type === type),
      );
      await page.getByRole("button", { name: "Salvar Manejo" }).click();
      const { results } = await (await submitted).json();
      assert.equal(results[0].status, "APPLIED");
      await page.getByRole("status").filter({ hasText: "Manejo salvo." }).waitFor();
    }

    await app.setOffline(true);
    await page.reload();
    await page.getByRole("heading", { name: "Caderno de Campo" }).waitFor();
    await page.getByText("Hortaliça do teste", { exact: true }).first().waitFor();
    await page.getByRole("link", { name: "+ Anotar", exact: true }).click();
    await page.getByRole("button", { name: /Saída \/ Venda/ }).click();
    await chooseFirst(page.locator("#planting-select"));
    await page.getByText("Cultura do plantio: Hortaliça do teste", { exact: true }).waitFor();
    assert.equal(await page.locator("#outflow-crop-select").count(), 0);
    await page.getByRole("spinbutton").fill("2.5");
    await page.getByLabel("Unidade da quantidade").selectOption("KILOGRAM");
    await page.locator("#notes").fill("Venda registrada sem internet");
    await page.locator("#record-photo-input").setInputFiles({
      name: "registro.png",
      mimeType: "image/png",
      buffer: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+kvk0AAAAASUVORK5CYII=",
        "base64",
      ),
    });
    await page.getByRole("button", { name: "Salvar Manejo" }).click();
    await page.getByRole("status").filter({ hasText: "Envio pendente" }).waitFor();

    const sent = page.waitForResponse(
      (response) =>
        response.url().endsWith("/sync/push") &&
        response
          .request()
          .postDataJSON()
          .mutations.some((item) => item.data?.type === "OUTFLOW"),
    );
    const photo = page.waitForResponse(
      (response) =>
        /\/files\/[^/]+\/content$/.test(response.url()) && response.request().method() === "PUT",
    );
    await app.setOffline(false);
    const result = await (await sent).json();
    assert.equal(result.results[0].status, "APPLIED");
    assert.equal((await photo).ok(), true);

    await app.setOffline(true);
    await page.getByRole("link", { name: "+ Anotar", exact: true }).click();
    await page.locator("#crop-other").fill("Rascunho da primeira conta");
    await page.getByRole("button", { name: "Salvar Manejo" }).click();
    await page.getByText("Rascunho da primeira conta", { exact: true }).waitFor();
    await page.getByRole("link", { name: "Perfil" }).click();
    await page.getByRole("button", { name: "Desconectar deste Celular" }).click();
    await page.getByRole("button", { name: "Sim, Sair" }).click();
    await page.waitForURL(`${mobileUrl}/login`);
    await app.setOffline(false);
    await activate(page, otherCpf, otherCode);
    await page.getByRole("link", { name: "Registros", exact: true }).click();
    await page.getByText("Nenhum manejo encontrado", { exact: true }).waitFor();
    assert.equal(await page.getByText("Rascunho da primeira conta").count(), 0);

    await panel.goto(`${panelUrl}/registros`);
    await panel.getByRole("link", { name: /Saída \/ Venda.*Hortaliça do teste/i }).waitFor();
  },
);
