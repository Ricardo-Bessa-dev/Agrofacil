// Testa o formulário de agendar visita: marcados, participantes enviados e explicação da lista vazia.
import { describe, expect, it } from "vitest";
import { buildParticipants, noVisitorsHelp, preselectedIds } from "./visitScheduling";

const farmer = { person: { kind: "FARMER" as const, id: "ana", name: "Ana" }, preselected: true };
const collaborator = {
  person: { kind: "COLLABORATOR" as const, id: "tec", name: "Técnica" },
  preselected: false,
};

describe("preselectedIds", () => {
  it("marca só quem a sugestão indicou", () => {
    expect(preselectedIds([farmer, collaborator])).toEqual(["ana"]);
    expect(preselectedIds([])).toEqual([]);
  });
});

describe("buildParticipants", () => {
  it("envia o colaborador da Comissão como colaborador", () => {
    const { participants } = buildParticipants(["tec"], [farmer, collaborator]);

    expect(participants).toEqual([
      { person: { kind: "COLLABORATOR", id: "tec" }, role: "VISITOR" },
    ]);
  });

  it("escolhe um agricultor para registrar, mesmo com um colaborador na frente", () => {
    const { recorderId } = buildParticipants(["tec", "ana"], [collaborator, farmer]);

    expect(recorderId).toBe("ana");
  });

  it("deixa a visita sem responsável no app quando só há colaboradores", () => {
    expect(buildParticipants(["tec"], [collaborator]).recorderId).toBeUndefined();
  });

  it("ignora quem não está mais entre os elegíveis", () => {
    expect(buildParticipants(["outra-pessoa"], [farmer]).participants).toEqual([]);
  });
});

describe("noVisitorsHelp", () => {
  const joao = { id: "joao", name: "João", groupId: "g1" };

  it("diz que o agricultor está sem grupo e leva à ficha dele", () => {
    const help = noVisitorsHelp({
      type: "PEER",
      farmer: { ...joao, groupId: undefined },
      excludedCount: 0,
    });

    expect(help.message).toContain("João não está em nenhum grupo");
    expect(help.link?.to).toBe("/agricultores/joao");
  });

  it("diz que o grupo não tem outro agricultor", () => {
    const help = noVisitorsHelp({ type: "PEER", farmer: joao, excludedCount: 0 });

    expect(help.message).toContain("não tem outro agricultor");
    expect(help.link?.to).toBe("/grupos");
  });

  it("diz que a Comissão está sem membros", () => {
    const help = noVisitorsHelp({ type: "VERIFICATION", farmer: joao, excludedCount: 0 });

    expect(help.message).toContain("Comissão de Avaliação");
    expect(help.link?.to).toBe("/spg");
  });

  it("aponta para os motivos quando há pessoas, mas todas impedidas", () => {
    for (const type of ["PEER", "VERIFICATION"] as const) {
      expect(noVisitorsHelp({ type, farmer: joao, excludedCount: 2 }).message).toContain(
        "Veja os motivos abaixo",
      );
    }
  });

  it("não afirma que falta grupo enquanto os dados do agricultor não chegaram", () => {
    const help = noVisitorsHelp({ type: "PEER", farmer: undefined, excludedCount: 0 });

    expect(help.message).not.toContain("nenhum grupo");
  });
});
