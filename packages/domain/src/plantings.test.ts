// Testes da escolha do plantio: lista, rótulo e produto sugerido na saída (RN42).
import { describe, expect, it } from "vitest";
import {
  groupPlantings,
  plantingLabel,
  productFromPlanting,
  showsFinishedPlantings,
  suggestOutflowProduct,
} from "./plantings";

const active = {
  id: "ativo",
  subject: "Feijão",
  performedOn: "2026-11-20",
  plot: { name: "Horta" },
  planting: { cropId: "feijao", cycleStatus: "ACTIVE" },
};
const finished = {
  id: "encerrado",
  subject: "Alface crespa",
  performedOn: "2026-10-24",
  planting: {
    cropOtherName: "Alface crespa",
    cycleStatus: "FINISHED",
    finishedOn: "2026-11-22",
  },
};
const crops = [{ id: "feijao", active: true }];
const empty = { cropId: "", otherName: "" };

describe("lista de plantios", () => {
  it("separa os plantios em andamento dos encerrados", () => {
    expect(groupPlantings([finished, active], { includeFinished: true })).toEqual({
      active: [active],
      finished: [finished],
      hasHiddenFinished: false,
    });
  });

  it("esconde os encerrados e avisa que existem", () => {
    expect(groupPlantings([active, finished], { includeFinished: false })).toEqual({
      active: [active],
      finished: [],
      hasHiddenFinished: true,
    });
  });

  it("mantém o plantio já escolhido na lista mesmo encerrado", () => {
    const groups = groupPlantings([active, finished], {
      includeFinished: false,
      selectedId: "encerrado",
    });

    expect(groups.finished).toEqual([finished]);
    expect(groups.hasHiddenFinished).toBe(false);
  });

  it("trata como em andamento o plantio sem ciclo, ainda não enviado à API", () => {
    const pending = { id: "novo", subject: "Milho" };

    expect(groupPlantings([pending], { includeFinished: false }).active).toEqual([pending]);
  });

  it("mostra os encerrados de início só na saída", () => {
    expect(showsFinishedPlantings("OUTFLOW")).toBe(true);
    expect(showsFinishedPlantings("HARVEST")).toBe(false);
    expect(showsFinishedPlantings("INPUT_APPLICATION")).toBe(false);
  });
});

describe("rótulo do plantio", () => {
  it("mostra cultura, data e área", () => {
    expect(plantingLabel(active)).toBe("Feijão — 20/11/2026 (Horta)");
  });

  it("mostra quando a colheita foi encerrada", () => {
    expect(plantingLabel(finished)).toBe(
      "Alface crespa — 24/10/2026 · colheita encerrada em 22/11/2026",
    );
  });
});

describe("produto sugerido na saída", () => {
  it("usa a cultura do catálogo ou o nome digitado no plantio", () => {
    expect(productFromPlanting(active, crops)).toEqual({ cropId: "feijao", otherName: "" });
    expect(productFromPlanting(finished, crops)).toEqual({
      cropId: "",
      otherName: "Alface crespa",
    });
  });

  it("usa o nome quando a cultura foi desativada no catálogo", () => {
    expect(productFromPlanting(active, [{ id: "feijao", active: false }])).toEqual({
      cropId: "",
      otherName: "Feijão",
    });
  });

  it("preenche o produto vazio e acompanha a troca de plantio", () => {
    const first = suggestOutflowProduct(empty, undefined, active, crops);
    const second = suggestOutflowProduct(first, active, finished, crops);

    expect(first).toEqual({ cropId: "feijao", otherName: "" });
    expect(second).toEqual({ cropId: "", otherName: "Alface crespa" });
  });

  it("não troca o produto que a pessoa escolheu", () => {
    const typed = { cropId: "", otherName: "Polpa de umbu" };

    expect(suggestOutflowProduct(typed, undefined, active, crops)).toEqual(typed);
    expect(suggestOutflowProduct(typed, active, finished, crops)).toEqual(typed);
  });

  it("mantém o produto ao tirar o vínculo com o plantio", () => {
    const product = { cropId: "feijao", otherName: "" };

    expect(suggestOutflowProduct(product, active, undefined, crops)).toEqual(product);
  });
});
