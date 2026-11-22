// Testa a separação do resultado do /sync/push: aceitas saem, recusadas ficam com o motivo, sem resposta continuam na fila.
import { describe, expect, it } from "vitest";
import { rejectionMessage, splitPushResults } from "./pushResults";

const mutation = (id: string) => ({
  mutationId: id,
  entity: "record",
  entityId: `rec-${id}`,
  data: { notes: id },
});

describe("splitPushResults", () => {
  it("tira da fila as aceitas, guarda as recusadas e mantém as sem resposta", () => {
    const { remaining, rejected } = splitPushResults(
      [mutation("m1"), mutation("m2"), mutation("m3")],
      [
        { mutationId: "m1", status: "APPLIED" },
        {
          mutationId: "m2",
          status: "REJECTED",
          error: { code: "VALIDATION_ERROR", message: "Dados inválidos." },
        },
      ],
    );
    expect(remaining.map((m) => m.mutationId)).toEqual(["m3"]);
    expect(rejected).toEqual([
      {
        mutationId: "m2",
        entity: "record",
        entityId: "rec-m2",
        data: { notes: "m2" },
        rejection: "Dados inválidos.",
      },
    ]);
  });

  it("mantém tudo na fila quando a resposta não traz resultados", () => {
    const { remaining, rejected } = splitPushResults([mutation("m1")], undefined);
    expect(remaining).toHaveLength(1);
    expect(rejected).toHaveLength(0);
  });
});

describe("rejectionMessage", () => {
  it("junta a mensagem e os campos com problema", () => {
    expect(
      rejectionMessage({
        status: "REJECTED",
        error: {
          message: "Dados inválidos.",
          errors: [{ field: "plotId", message: "Escolha a área." }],
        },
      }),
    ).toBe("Dados inválidos. (Escolha a área.)");
  });

  it("usa um texto padrão quando a API não explica", () => {
    expect(rejectionMessage({ status: "REJECTED" })).toBe(
      "A alteração foi recusada pelo servidor.",
    );
  });
});
