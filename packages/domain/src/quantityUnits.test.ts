// Testes das unidades de quantidade: códigos iguais aos da API e opções do formulário.
import { describe, expect, it } from "vitest";
import seedSql from "../../../api/src/main/resources/db/migration/V3__seed_reference_data.sql?raw";
import {
  DEFAULT_APPLICATION_UNIT,
  DEFAULT_QUANTITY_UNIT,
  QUANTITY_UNIT_LABELS,
  quantityUnitOptions,
} from "./quantityUnits";
import { formatQuantity } from "./units";

/** Lê do SQL as unidades que a API aceita em quantidade, na ordem em que foram criadas. */
function seededQuantityUnits(): [string, { singular: string; plural: string }][] {
  const insert = seedSql.match(/insert into measurement_unit[^;]+;/)?.[0] ?? "";
  return [...insert.matchAll(/\('([A-Z_]+)', '([^']+)', '([^']+)', '([A-Z]+)'/g)]
    .filter(([, , , , kind]) => kind !== "AREA")
    .map(([, code, singular, plural]) => [code!, { singular: singular!, plural: plural! }]);
}

describe("unidades de quantidade", () => {
  it("tem os mesmos códigos e nomes que a API cadastra na migração", () => {
    const seeded = seededQuantityUnits();

    expect(seeded.length).toBeGreaterThan(0);
    expect(Object.entries(QUANTITY_UNIT_LABELS)).toEqual(seeded);
  });

  it("usa como padrão unidades que existem na API", () => {
    const codes = seededQuantityUnits().map(([code]) => code);

    expect(codes).toContain(DEFAULT_QUANTITY_UNIT);
    expect(codes).toContain(DEFAULT_APPLICATION_UNIT);
  });

  it("não oferece unidade de área nem desativada e respeita a ordem do catálogo", () => {
    const catalog = [
      { code: "BOX", namePlural: "caixas", kind: "PACKAGE", sortOrder: 40, active: true },
      { code: "HECTARE", namePlural: "hectares", kind: "AREA", sortOrder: 1, active: true },
      { code: "KILOGRAM", namePlural: "quilos", kind: "MASS", sortOrder: 10, active: true },
      { code: "CART", namePlural: "carroças", kind: "PACKAGE", sortOrder: 44, active: false },
    ];

    expect(quantityUnitOptions(catalog)).toEqual([
      { code: "KILOGRAM", label: "quilos" },
      { code: "BOX", label: "caixas" },
    ]);
  });

  it("oferece a lista padrão enquanto o catálogo não chegou", () => {
    const codes = Object.keys(QUANTITY_UNIT_LABELS);

    expect(quantityUnitOptions(undefined).map((u) => u.code)).toEqual(codes);
    expect(quantityUnitOptions([]).map((u) => u.code)).toEqual(codes);
  });

  it("mantém na lista a unidade já escolhida, mesmo desativada", () => {
    const catalog = [
      { code: "KILOGRAM", namePlural: "quilos", kind: "MASS", sortOrder: 10, active: true },
      { code: "CART", namePlural: "carroças", kind: "PACKAGE", sortOrder: 44, active: false },
    ];

    expect(quantityUnitOptions(catalog, "CART")).toContainEqual({
      code: "CART",
      label: "carroças",
    });
  });

  it("mostra o nome da unidade em português, no singular e no plural", () => {
    expect(formatQuantity({ value: 1, unit: "KILOGRAM" })).toBe("1 quilo");
    expect(formatQuantity({ value: 30, unit: "BUNCH" })).toBe("30 molhos");
  });
});
