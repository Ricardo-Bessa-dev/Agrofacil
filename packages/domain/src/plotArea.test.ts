// Testa o cálculo da área livre da propriedade e se uma nova área cabe nela (RN41).
import { describe, expect, it } from "vitest";
import { freePropertyAreaM2, fromSquareMeters, plotFitsInProperty } from "./plotArea";

const TAREFA = 4356;
const total = { value: 4, unit: "TAREFA", m2: 4 * TAREFA };
const plots = [
  { id: "principal", area: { value: 3, unit: "TAREFA", m2: 3 * TAREFA } },
  { id: "horta", area: null },
];

describe("freePropertyAreaM2", () => {
  it("desconta as áreas com tamanho e ignora as sem tamanho", () => {
    expect(freePropertyAreaM2(total, plots)).toBe(TAREFA);
  });

  it("não conta a área que está sendo editada", () => {
    expect(freePropertyAreaM2(total, plots, "principal")).toBe(4 * TAREFA);
  });

  it("devolve null quando a propriedade não tem tamanho", () => {
    expect(freePropertyAreaM2(undefined, plots)).toBeNull();
  });

  it("nunca fica negativa (dados antigos podem já ter passado do limite)", () => {
    expect(freePropertyAreaM2({ m2: 100 }, [{ area: { m2: 500 } }])).toBe(0);
  });
});

describe("plotFitsInProperty", () => {
  it("aceita até o espaço livre, com a folga de arredondamento", () => {
    expect(plotFitsInProperty(TAREFA, 1, "TAREFA", TAREFA)).toBe(true);
    expect(plotFitsInProperty(1000, 1000.5, "SQUARE_METER", TAREFA)).toBe(true);
  });

  it("recusa o que passa do espaço livre", () => {
    expect(plotFitsInProperty(TAREFA, 0.5, "HECTARE", TAREFA)).toBe(false);
  });
});

describe("fromSquareMeters", () => {
  it("converte para hectare, tarefa e m²", () => {
    expect(fromSquareMeters(25000, "HECTARE", TAREFA)).toBe(2.5);
    expect(fromSquareMeters(2 * TAREFA, "TAREFA", TAREFA)).toBe(2);
    expect(fromSquareMeters(300, "SQUARE_METER", TAREFA)).toBe(300);
  });
});
