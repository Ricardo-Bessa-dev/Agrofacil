// Espelho local dos dados do agricultor, montado aos poucos com o /sync/pull incremental (doc 1 §3.5.2).
import type { PullResponse } from "@agrofacil/api-types";

export type SyncChanges = NonNullable<PullResponse["changes"]>;

/** O que fica guardado no aparelho: o cursor do último pull e todos os dados recebidos até ali. */
export interface SyncState {
  /** Versão do formato salvo; ao mudar o formato, o app descarta o antigo e puxa tudo de novo. */
  version: typeof SYNC_STATE_VERSION;
  /** Texto opaco devolvido pela API. "0" = puxar tudo desde o início. */
  cursor: string;
  changes: SyncChanges;
}

export const SYNC_STATE_VERSION = 3;
export const INITIAL_CURSOR = "0";

/** Listas que chegam no pull; cada item é substituído pela versão mais nova (mesma chave). */
const COLLECTION_KEYS = [
  "people",
  "properties",
  "plots",
  "workers",
  "certifications",
  "records",
  "attachments",
  "correctionRequests",
  "announcements",
  "notifications",
  "crops",
  "farmInputs",
  "units",
  "visits",
  "visitChecklistItems",
] as const satisfies readonly (keyof SyncChanges)[];

type CollectionKey = (typeof COLLECTION_KEYS)[number];

/**
 * Campo que identifica o item, quando não é o id. Unidades de medida e itens do roteiro de visita não
 * têm id no contrato; ambos são únicos pelo código (o roteiro, por associação, e o agricultor só vê a dele).
 */
const ITEM_KEY: Partial<Record<CollectionKey, string>> = {
  units: "code",
  visitChecklistItems: "code",
};

/** Nome da entidade nas lápides (deleted[].entity) → lista local de onde o item sai. */
const TOMBSTONE_COLLECTION: Record<string, CollectionKey> = {
  property: "properties",
  plot: "plots",
  worker: "workers",
  certification: "certifications",
  record: "records",
  attachment: "attachments",
  announcement: "announcements",
  visit: "visits",
};

export function emptySyncState(): SyncState {
  return { version: SYNC_STATE_VERSION, cursor: INITIAL_CURSOR, changes: {} };
}

/**
 * Aplica uma página do /sync/pull sobre o espelho local e devolve o novo estado (não altera o recebido).
 * Com reset=true, a API pede para descartar tudo e recomeçar do cursor "0".
 */
export function applyPull(state: SyncState, response: PullResponse): SyncState {
  if (response.reset) {
    return emptySyncState();
  }
  const incoming = response.changes ?? {};
  const changes: SyncChanges = { ...state.changes };

  if (incoming.me) changes.me = incoming.me;
  if (incoming.association) changes.association = incoming.association;

  for (const key of COLLECTION_KEYS) {
    const updates = incoming[key];
    if (updates && updates.length > 0) {
      const itemKey = ITEM_KEY[key] ?? "id";
      setCollection(
        changes,
        key,
        upsertBy(itemKey, getCollection(changes, key), updates as Item[]),
      );
    }
  }

  for (const tombstone of response.deleted ?? []) {
    const key = tombstone.entity ? TOMBSTONE_COLLECTION[tombstone.entity] : undefined;
    if (key && tombstone.id) {
      setCollection(
        changes,
        key,
        getCollection(changes, key).filter((item) => item.id !== tombstone.id),
      );
    }
  }

  return { ...state, cursor: response.cursor ?? state.cursor, changes };
}

/** Lê um estado salvo; devolve null se não existir, estiver corrompido ou for de um formato antigo. */
export function parseSyncState(raw: string | null): SyncState | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<SyncState>;
    if (parsed.version !== SYNC_STATE_VERSION || typeof parsed.cursor !== "string") return null;
    return { version: SYNC_STATE_VERSION, cursor: parsed.cursor, changes: parsed.changes ?? {} };
  } catch {
    return null;
  }
}

type Item = Record<string, unknown>;

function upsertBy(itemKey: string, current: Item[], updates: Item[]): Item[] {
  const byKey = new Map(current.map((item) => [item[itemKey], item]));
  for (const item of updates) {
    byKey.set(item[itemKey], item);
  }
  return [...byKey.values()];
}

// As listas têm tipos diferentes; o acesso genérico fica isolado nestas duas funções.
function getCollection(changes: SyncChanges, key: CollectionKey): Item[] {
  return (changes[key] as Item[] | undefined) ?? [];
}

function setCollection(changes: SyncChanges, key: CollectionKey, items: Item[]): void {
  (changes as Record<CollectionKey, Item[]>)[key] = items;
}
