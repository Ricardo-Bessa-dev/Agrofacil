import { readFileSync } from "node:fs";
import { isDeepStrictEqual } from "node:util";

const snapshotFile = new URL("./openapi.json", import.meta.url);
const apiFile = process.argv[2];
if (!apiFile) throw new Error("Informe o OpenAPI gerado pela API.");

const read = (file) => JSON.parse(readFileSync(file, "utf8"));
const expected = read(snapshotFile);
const actual = read(apiFile);

// Ordem de propriedades e metadados de apresentação podem variar entre execuções do SpringDoc.
const ignored = new Set(["description", "summary", "tags", "operationId", "title", "example"]);
function normalize(value, field) {
  if (Array.isArray(value)) {
    const items = value.map((item) => normalize(item));
    return ["required", "enum", "parameters"].includes(field)
      ? items.sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)))
      : items;
  }
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.keys(value)
      .filter((key) => field === "properties" || !ignored.has(key))
      .sort()
      .map((key) => [key, normalize(value[key], key)]),
  );
}

let differences = 0;
function compare(section, committed, current) {
  for (const name of new Set([...Object.keys(committed), ...Object.keys(current)])) {
    if (!isDeepStrictEqual(normalize(committed[name]), normalize(current[name]))) {
      console.error(`Contrato divergente em ${section} ${name}`);
      differences++;
    }
  }
}

compare("rota", expected.paths, actual.paths);
compare("schema", expected.components.schemas, actual.components.schemas);
if (differences) process.exitCode = 1;
