// Reexporta os tipos gerados do OpenAPI com nomes curtos para uso nos apps (doc 3 §B.3).
import type { components, paths } from "./schema";

export type { paths };
export type Schemas = components["schemas"];

// Aliases dos DTOs mais usados. Acrescente outros conforme a API ganhar rotas.
export type Me = Schemas["Me"];
export type RecordView = Schemas["RecordView"];
export type RecordWrite = Schemas["RecordWrite"];
export type PropertyView = Schemas["PropertyView"];
export type PushRequest = Schemas["PushRequest"];
export type PushResponse = Schemas["PushResponse"];
export type PullResponse = Schemas["PullResponse"];
