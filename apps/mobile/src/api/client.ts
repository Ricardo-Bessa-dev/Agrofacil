// Cliente HTTP tipado com openapi-fetch para a aplicação mobile do agricultor.
// Inclui ponte transparente para /sync/pull e /sync/push, cache offline em localStorage (ADR-02),
// fila de mutações offline e upload de anexos fotográficos com verificação SHA-256.
import createClient from "openapi-fetch";
import type {
  AttachmentView,
  CorrectionRequestView,
  FieldErrorItem,
  Me,
  paths,
  PropertyWrite,
  PullResponse,
  PushResponse,
  RecordListItem,
  RecordView,
  SessionResponse,
} from "@agrofacil/api-types";
import { pendingPhotos, removePhoto, savePhoto, type PendingPhoto } from "./pending-photos";
import { BAHIA_MUNICIPALITIES } from "../../../web/src/api/municipalities";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  readonly problemDetail: string;
  readonly errors: FieldErrorItem[];

  constructor(status: number, body: unknown) {
    const obj = (typeof body === "object" && body !== null ? body : {}) as Record<string, unknown>;
    const errs = Array.isArray(obj.errors) ? (obj.errors as FieldErrorItem[]) : [];
    const baseDetail =
      typeof obj.detail === "string"
        ? obj.detail
        : typeof obj.message === "string"
          ? obj.message
          : "Não foi possível concluir a operação. Verifique sua conexão e tente novamente.";
    const fieldSummary =
      errs.length > 0
        ? ` (${errs.map((e) => `${e.field ?? "campo"}: ${e.message ?? "inválido"}`).join("; ")})`
        : "";
    const detail = `${baseDetail}${fieldSummary}`;
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.code = typeof obj.code === "string" ? obj.code : "UNKNOWN_ERROR";
    this.detail = detail;
    this.problemDetail = baseDetail;
    this.errors = errs;
  }
}

const STORAGE_KEYS = {
  ACCESS_TOKEN: "af_mobile_access_token",
  REFRESH_TOKEN: "af_mobile_refresh_token",
  USER: "af_mobile_user",
  DEVICE_ID: "af_mobile_device_id",
  SYNC_SNAPSHOT: "af_mobile_sync_snapshot",
  OFFLINE_QUEUE: "af_mobile_offline_queue",
} as const;

export interface QueuedMutation {
  mutationId: string;
  entity: "record" | "property" | "attachment" | "notification" | "visit";
  op: "UPSERT" | "DELETE";
  entityId: string;
  clientTimestamp: string;
  data?: Record<string, unknown>;
  rejection?: string;
}

export interface MobileRecordDetail {
  id: string;
  type: string;
  performedOn?: string;
  propertyId?: string;
  plotId?: string;
  notes?: string;
  completeness?: "COMPLETE" | "INCOMPLETE";
  missingFields?: string[];
  openCorrectionRequests?: number;
  raw: Record<string, unknown>;
  attachments: AttachmentView[];
  correctionRequests: CorrectionRequestView[];
}

export function getStoredDeviceId(): string {
  let id = localStorage.getItem(STORAGE_KEYS.DEVICE_ID);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(STORAGE_KEYS.DEVICE_ID, id);
  }
  return id;
}

export function getStoredAccessToken(): string | null {
  return localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
}

export function getStoredRefreshToken(): string | null {
  return localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
}

export function getStoredUser(): Me | null {
  const raw = localStorage.getItem(STORAGE_KEYS.USER);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Me;
  } catch {
    return null;
  }
}

let sessionGeneration = 0;
let onUnauthorizedCallback: (() => void) | null = null;
let refreshing: { generation: number; promise: Promise<string | null> } | undefined;

function accountId(): string | undefined {
  return getStoredUser()?.id;
}

function accountKey(key: string, owner: string): string {
  return `${key}:${owner}`;
}

export function saveSession(session: SessionResponse): void {
  const previous = getStoredUser();
  if (session.user?.id && session.user.id !== previous?.id) {
    sessionGeneration++;
  }
  if (session.accessToken) localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, session.accessToken);
  if (session.refreshToken) localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, session.refreshToken);
  if (session.user) localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(session.user));
}

async function activationDeviceKey(cpf: string): Promise<string> {
  return accountKey(
    STORAGE_KEYS.DEVICE_ID,
    await computeSha256Hex(new TextEncoder().encode(cpf).buffer),
  );
}

export async function deviceIdForActivation(cpf: string): Promise<string> {
  const existing = getStoredUser();
  return (
    localStorage.getItem(await activationDeviceKey(cpf)) ??
    (existing?.cpf?.replace(/\D/g, "") === cpf ? getStoredDeviceId() : crypto.randomUUID())
  );
}

export async function rememberActivatedDevice(cpf: string, deviceId: string): Promise<void> {
  localStorage.setItem(await activationDeviceKey(cpf), deviceId);
  localStorage.setItem(STORAGE_KEYS.DEVICE_ID, deviceId);
}

export function invalidateStoredSession(): void {
  sessionGeneration++;
  cachedPull = null;
}

export function clearSession(): void {
  invalidateStoredSession();
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.USER);
  localStorage.removeItem(STORAGE_KEYS.DEVICE_ID);
}

function readStored<T>(key: string, owner: string): T | null {
  try {
    const raw = localStorage.getItem(accountKey(key, owner));
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

function legacySnapshot(owner: string): PullResponse | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.SYNC_SNAPSHOT);
    const value = raw ? (JSON.parse(raw) as PullResponse) : null;
    if (value?.changes?.me?.id === owner) {
      return { changes: value.changes };
    }
  } catch {
    // Um cache ilegível é reconstruído no próximo pull.
  }
  return null;
}

function readQueue(owner: string): QueuedMutation[] {
  const value = readStored<QueuedMutation[]>(STORAGE_KEYS.OFFLINE_QUEUE, owner);
  return Array.isArray(value) ? value : [];
}

export function getOfflineQueue(): QueuedMutation[] {
  const owner = accountId();
  if (!owner) return [];
  return readQueue(owner);
}

// A fila anterior não guardava o dono. Só recupera itens cujo destino consta do espelho autenticado.
function recoverLegacyQueue(owner: string, changes: Changes): void {
  const raw = localStorage.getItem(STORAGE_KEYS.OFFLINE_QUEUE);
  if (!raw) return;
  let legacy: QueuedMutation[];
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return;
    legacy = parsed as QueuedMutation[];
  } catch {
    return;
  }
  const keys = new Set((changes.properties ?? []).map((row) => row.id));
  const records = new Set((changes.records ?? []).map((row) => row.id));
  const pending = readQueue(owner);
  const recovered = new Set<string>();
  for (const item of legacy) {
    const target = item.entity === "property" ? item.entityId : item.data?.propertyId;
    const own = typeof target === "string" && keys.has(target);
    if (own || (item.entity === "record" && records.has(item.entityId))) {
      recovered.add(item.mutationId);
      if (
        !pending.some(
          (current) => current.entity === item.entity && current.entityId === item.entityId,
        )
      ) {
        pending.push(item);
      }
    }
  }
  if (recovered.size) {
    setOfflineQueue(owner, pending);
    const remaining = legacy.filter((item) => !recovered.has(item.mutationId));
    if (remaining.length)
      localStorage.setItem(STORAGE_KEYS.OFFLINE_QUEUE, JSON.stringify(remaining));
    else localStorage.removeItem(STORAGE_KEYS.OFFLINE_QUEUE);
  }
}

function setOfflineQueue(owner: string, queue: QueuedMutation[]): void {
  localStorage.setItem(accountKey(STORAGE_KEYS.OFFLINE_QUEUE, owner), JSON.stringify(queue));
  emitPendingChanges();
}

function enqueueOfflineMutation(owner: string, mutation: QueuedMutation): void {
  const queue = readQueue(owner);
  const index = queue.findIndex(
    (item) => item.entity === mutation.entity && item.entityId === mutation.entityId,
  );
  if (index === -1) queue.push(mutation);
  else queue[index] = mutation;
  setOfflineQueue(owner, queue);
}

async function renewAccessToken(
  generation: number,
  failedToken: string | null,
): Promise<string | null> {
  if (generation !== sessionGeneration) return null;
  const access = getStoredAccessToken();
  if (access && access !== failedToken) return access;
  if (refreshing?.generation === generation) return refreshing.promise;
  const refreshToken = getStoredRefreshToken();
  if (!refreshToken) return null;
  const promise = (async () => {
    const response = await window.fetch("/api/v1/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken, deviceId: getStoredDeviceId() }),
    });
    if (generation !== sessionGeneration) return null;
    if (response.status === 401 || response.status === 403) {
      clearSession();
      onUnauthorizedCallback?.();
      return null;
    }
    if (!response.ok) throw new ApiError(response.status, await response.json().catch(() => null));
    const session = (await response.json()) as SessionResponse;
    if (generation !== sessionGeneration) return null;
    saveSession(session);
    return session.accessToken ?? null;
  })();
  refreshing = { generation, promise };
  try {
    return await promise;
  } finally {
    if (refreshing?.promise === promise) refreshing = undefined;
  }
}

async function sessionFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const request =
    input instanceof Request ? input : new Request(new URL(input, window.location.origin), init);
  const path = new URL(request.url).pathname;
  if (path.startsWith("/api/v1/auth/") && !path.endsWith("/logout")) return window.fetch(request);
  const generation = sessionGeneration;
  const token = getStoredAccessToken();
  const send = (access: string | null) => {
    const copy = request.clone();
    if (access) copy.headers.set("Authorization", `Bearer ${access}`);
    return window.fetch(copy);
  };
  const result = await send(token);
  if (generation !== sessionGeneration) return new Response(null, { status: 401 });
  if (result.status !== 401) return result;
  const access = await renewAccessToken(generation, token);
  if (!access) return result;
  const retry = await send(access);
  return generation === sessionGeneration ? retry : new Response(null, { status: 401 });
}

export function setOnUnauthorized(cb: (() => void) | null): void {
  onUnauthorizedCallback = cb;
}

function serializeSpringQuery(params: Record<string, unknown>): string {
  const searchParams = new URLSearchParams();

  const appendValue = (key: string, val: unknown) => {
    if (val === undefined || val === null || val === "") return;
    if (Array.isArray(val)) {
      for (const item of val) {
        if (item !== undefined && item !== null && item !== "") {
          searchParams.append(key, String(item));
        }
      }
    } else if (typeof val === "object") {
      for (const [nestedKey, nestedVal] of Object.entries(val as Record<string, unknown>)) {
        appendValue(nestedKey, nestedVal);
      }
    } else {
      searchParams.append(key, String(val));
    }
  };

  for (const [key, val] of Object.entries(params)) {
    appendValue(key, val);
  }

  return searchParams.toString();
}

const municipalityNameById = new Map<number, string>(
  BAHIA_MUNICIPALITIES.map((m) => [m.id ?? 0, m.name ?? ""]),
);

type Changes = NonNullable<PullResponse["changes"]>;
type Tombstones = NonNullable<PullResponse["deleted"]>;

function mergeRows<T>(
  existing: T[] = [],
  changes: T[] = [],
  key: (row: T) => string | undefined,
  deleted: Tombstones = [],
  entity?: string,
): T[] {
  const rows = new Map(existing.map((row) => [key(row), row]));
  for (const row of changes) rows.set(key(row), row);
  for (const item of deleted) {
    if (item.entity === entity) rows.delete(item.id);
  }
  return [...rows.values()];
}

function mergeChanges(previous: Changes, current: Changes, deleted: Tombstones): Changes {
  const byId = (row: { id?: string }) => row.id;
  return {
    me: current.me ?? previous.me,
    association: current.association ?? previous.association,
    people: mergeRows(previous.people, current.people, byId),
    properties: mergeRows(previous.properties, current.properties, byId, deleted, "property"),
    plots: mergeRows(previous.plots, current.plots, byId, deleted, "plot"),
    workers: mergeRows(previous.workers, current.workers, byId, deleted, "worker"),
    certifications: mergeRows(
      previous.certifications,
      current.certifications,
      byId,
      deleted,
      "certification",
    ),
    records: mergeRows(previous.records, current.records, byId, deleted, "record"),
    attachments: mergeRows(previous.attachments, current.attachments, byId, deleted, "attachment"),
    correctionRequests: mergeRows(previous.correctionRequests, current.correctionRequests, byId),
    announcements: mergeRows(
      previous.announcements,
      current.announcements,
      byId,
      deleted,
      "announcement",
    ),
    notifications: mergeRows(previous.notifications, current.notifications, byId),
    crops: mergeRows(previous.crops, current.crops, byId),
    farmInputs: mergeRows(previous.farmInputs, current.farmInputs, byId),
    units: mergeRows(previous.units, current.units, (row) => row.code),
    visits: mergeRows(previous.visits, current.visits, byId, deleted, "visit"),
    visitChecklistItems: mergeRows(
      previous.visitChecklistItems,
      current.visitChecklistItems,
      (row) => row.code,
    ),
  };
}

let cachedPull: { owner: string; at: number; data: PullResponse } | null = null;
let inflightPull:
  { generation: number; revision: number; promise: Promise<PullResponse> } | undefined;
let pullRevision = 0;

export function invalidateMobileSyncCache(): void {
  pullRevision++;
  if (cachedPull) cachedPull.at = 0;
}

export async function fetchSyncSnapshot(): Promise<PullResponse> {
  const owner = accountId();
  if (!owner) throw new ApiError(401, null);
  const generation = sessionGeneration;
  const revision = pullRevision;
  if (cachedPull?.owner === owner && Date.now() - cachedPull.at < 2500) return cachedPull.data;
  if (inflightPull?.generation === generation && inflightPull.revision === revision)
    return inflightPull.promise;
  const fallback =
    cachedPull?.owner === owner
      ? cachedPull.data
      : (readStored<PullResponse>(STORAGE_KEYS.SYNC_SNAPSHOT, owner) ?? legacySnapshot(owner));
  if (fallback && typeof navigator !== "undefined" && !navigator.onLine) return fallback;
  const promise = (async () => {
    let cursor = fallback?.cursor ?? "0";
    let changes = fallback?.cursor ? (fallback.changes ?? {}) : {};
    let didReset = false;
    try {
      while (true) {
        const response = await sessionFetch(
          `/api/v1/sync/pull?cursor=${encodeURIComponent(cursor)}&limit=1000`,
        );
        if (!response.ok) throw new ApiError(response.status, null);
        const page = (await response.json()) as PullResponse;
        if (sessionGeneration !== generation) throw new ApiError(401, null);
        if (revision !== pullRevision) return fetchSyncSnapshot();
        if (page.reset) {
          if (didReset)
            throw new Error("O servidor solicitou a reinicialização do espelho novamente.");
          didReset = true;
          cursor = "0";
          changes = {};
          localStorage.removeItem(accountKey(STORAGE_KEYS.SYNC_SNAPSHOT, owner));
          if (cachedPull?.owner === owner) cachedPull = null;
          continue;
        }
        changes = mergeChanges(changes, page.changes ?? {}, page.deleted ?? []);
        if (!page.hasMore) {
          const snapshot = { ...page, changes, deleted: [] };
          if (revision === pullRevision) {
            cachedPull = { owner, at: Date.now(), data: snapshot };
            try {
              localStorage.setItem(
                accountKey(STORAGE_KEYS.SYNC_SNAPSHOT, owner),
                JSON.stringify(snapshot),
              );
              recoverLegacyQueue(owner, changes);
              if (legacySnapshot(owner)) localStorage.removeItem(STORAGE_KEYS.SYNC_SNAPSHOT);
            } catch {
              // O espelho permanece disponível em memória quando a cota é excedida.
            }
          }
          return snapshot;
        }
        if (!page.cursor || page.cursor === cursor) throw new Error("A sincronização não avançou.");
        cursor = page.cursor;
      }
    } catch (error) {
      if (
        generation === sessionGeneration &&
        !didReset &&
        fallback &&
        !(error instanceof ApiError && (error.status === 401 || error.status === 403))
      )
        return fallback;
      throw error;
    }
  })();
  inflightPull = { generation, revision, promise };
  try {
    return await promise;
  } finally {
    if (inflightPull?.promise === promise) inflightPull = undefined;
  }
}

async function pushMutations(batch: QueuedMutation[]): Promise<PushResponse> {
  const response = await sessionFetch("/api/v1/sync/push", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      deviceId: getStoredDeviceId(),
      mutations: batch.map(({ rejection: _rejection, ...mutation }) => mutation),
    }),
  });
  if (!response.ok) throw new ApiError(response.status, await response.json().catch(() => null));
  return (await response.json()) as PushResponse;
}

let flushing:
  { generation: number; promise: Promise<{ synced: number; remaining: number }> } | undefined;

export async function flushOfflineQueue(): Promise<{ synced: number; remaining: number }> {
  const owner = accountId();
  const generation = sessionGeneration;
  if (!owner) return { synced: 0, remaining: 0 };
  if (flushing?.generation === generation) return flushing.promise;
  const promise = (async () => {
    const attempted = new Set<string>();
    let synced = 0;
    while (generation === sessionGeneration) {
      const batch = readQueue(owner)
        .filter((item) => !item.rejection && !attempted.has(item.mutationId))
        .slice(0, 100);
      if (!batch.length) break;
      for (const item of batch) attempted.add(item.mutationId);
      try {
        const response = await pushMutations(batch);
        const results = new Map(response.results?.map((result) => [result.mutationId, result]));
        synced += batch.filter((item) => results.get(item.mutationId)?.status === "APPLIED").length;
        const remaining = readQueue(owner).flatMap((item) => {
          const result = results.get(item.mutationId);
          if (result?.status === "APPLIED") return [];
          if (result?.status === "REJECTED")
            return [
              {
                ...item,
                rejection: result.error?.message ?? "A API recusou este envio.",
              },
            ];
          return [item];
        });
        setOfflineQueue(owner, remaining);
        if (synced) invalidateMobileSyncCache();
      } catch {
        break;
      }
    }
    if (generation === sessionGeneration) await flushPhotos(owner, generation);
    return { synced, remaining: readQueue(owner).length };
  })();
  flushing = { generation, promise };
  try {
    return await promise;
  } finally {
    if (flushing?.promise === promise) flushing = undefined;
  }
}

async function computeSha256Hex(buffer: ArrayBuffer): Promise<string> {
  const hashBuffer = await crypto.subtle.digest("SHA-256", buffer);
  const bytes = new Uint8Array(hashBuffer);
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/**
 * Faz o upload de uma foto (nota fiscal, insumo, desenvolvimento da lavoura) vinculada a um registro de manejo.
 * Segue o protocolo em duas etapas da API (doc 1 §3.6):
 * 1) Registra o metadado + SHA-256 via POST /api/v1/sync/push (entidade "attachment")
 * 2) Envia o binário via PUT /api/v1/files/{attachmentId}/content
 */
function emitPendingChanges(): void {
  window.dispatchEvent(new CustomEvent("af-offline-queue-changed"));
}

export async function getPendingPhotos(): Promise<PendingPhoto[]> {
  const owner = accountId();
  return owner ? pendingPhotos(owner).catch(() => []) : [];
}

export async function queueRecordPhoto(recordId: string, file: File): Promise<void> {
  const owner = accountId();
  if (!owner) throw new ApiError(401, null);
  await savePhoto({
    owner,
    file,
    recordId,
    id: crypto.randomUUID(),
    mutationId: crypto.randomUUID(),
    registered: false,
  });
  emitPendingChanges();
  if (navigator.onLine) void flushOfflineQueue();
}

async function sendPhoto(photo: PendingPhoto, generation: number): Promise<void> {
  const contentType = photo.file.type || "image/jpeg";
  if (!photo.registered) {
    const sha256 = await computeSha256Hex(await photo.file.arrayBuffer());
    if (generation !== sessionGeneration) return;
    const result = await pushMutations([
      {
        mutationId: photo.mutationId,
        entity: "attachment",
        op: "UPSERT",
        entityId: photo.id,
        clientTimestamp: new Date().toISOString(),
        data: {
          id: photo.id,
          ownerType: "RECORD",
          ownerId: photo.recordId,
          kind: "PHOTO",
          contentType,
          sizeBytes: photo.file.size,
          sha256,
        },
      },
    ]);
    if (result.results?.[0]?.status !== "APPLIED") {
      const message = result.results?.[0]?.error?.message;
      if (message) await savePhoto({ ...photo, error: message });
      return;
    }
    await savePhoto({ ...photo, registered: true, error: undefined });
  }
  if (generation !== sessionGeneration) return;
  const response = await sessionFetch(`/api/v1/files/${photo.id}/content`, {
    method: "PUT",
    headers: { "Content-Type": contentType },
    body: photo.file,
  });
  if (response.ok) {
    await removePhoto(photo);
    invalidateMobileSyncCache();
  } else if (response.status >= 400 && response.status < 500 && response.status !== 401) {
    const problem = await response.json().catch(() => null);
    await savePhoto({
      ...photo,
      registered: true,
      error: new ApiError(response.status, problem).detail,
    });
  }
}

async function flushPhotos(owner: string, generation: number): Promise<void> {
  const photos = await pendingPhotos(owner).catch(() => []);
  const recordsWaiting = new Set(
    readQueue(owner)
      .filter((item) => item.entity === "record")
      .map((item) => item.entityId),
  );
  for (const photo of photos) {
    if (generation !== sessionGeneration) return;
    if (!recordsWaiting.has(photo.recordId)) {
      try {
        await sendPhoto(photo, generation);
      } catch {
        // O binário continua disponível na próxima tentativa.
      }
    }
  }
  if (photos.length) emitPendingChanges();
}

export async function discardPendingPhoto(id: string): Promise<void> {
  const photo = (await getPendingPhotos()).find((item) => item.id === id);
  if (photo) {
    await removePhoto(photo);
    emitPendingChanges();
  }
}

/**
 * Busca os detalhes completos de um registro de manejo (incluindo anexos e pedidos de correção)
 * a partir do espelho de sincronização do agricultor.
 */
export async function fetchMobileRecordDetail(
  recordId: string,
): Promise<MobileRecordDetail | null> {
  const pull = await fetchSyncSnapshot();
  const rawRecords = pull.changes?.records ?? [];
  const pending = getOfflineQueue().find(
    (item) => item.entity === "record" && item.entityId === recordId,
  );
  const serverRecord = rawRecords.find((r) => r.id === recordId);
  const found = (pending?.data ? { ...serverRecord, ...pending.data } : serverRecord) as
    RecordView | undefined;
  if (!found) return null;

  const attachments = (pull.changes?.attachments ?? []).filter(
    (a) => a.ownerType === "RECORD" && a.ownerId === recordId && a.uploaded,
  );
  const correctionRequests = (pull.changes?.correctionRequests ?? []).filter(
    (c) => c.recordId === recordId,
  );

  return {
    id: found.id ?? recordId,
    type: found.type ?? "PLANTING",
    performedOn: found.performedOn,
    propertyId: found.propertyId,
    plotId: found.plotId,
    notes: found.notes,
    completeness: found.completeness,
    missingFields: found.missingFields,
    openCorrectionRequests: found.openCorrectionRequests,
    raw: found as unknown as Record<string, unknown>,
    attachments,
    correctionRequests,
  };
}

/**
 * Atualiza ou cadastra uma propriedade do agricultor via /sync/push (entidade "property").
 */
export async function upsertFarmerProperty(propertyId: string, payload: PropertyWrite) {
  return submitMutation("property", propertyId, { ...payload, id: propertyId });
}

export async function submitMutation(
  entity: QueuedMutation["entity"],
  entityId: string,
  data: Record<string, unknown>,
): Promise<"applied" | "queued"> {
  const owner = accountId();
  const generation = sessionGeneration;
  if (!owner) throw new ApiError(401, null);
  const mutation: QueuedMutation = {
    mutationId: crypto.randomUUID(),
    entity,
    op: "UPSERT",
    entityId,
    clientTimestamp: new Date().toISOString(),
    data,
  };
  const queue = () => {
    if (generation !== sessionGeneration) throw new ApiError(401, null);
    enqueueOfflineMutation(owner, mutation);
    return "queued" as const;
  };
  if (!navigator.onLine) return queue();
  if (getOfflineQueue().some((item) => !item.rejection)) {
    const status = queue();
    void flushOfflineQueue();
    return status;
  }
  let response: PushResponse;
  try {
    response = await pushMutations([mutation]);
  } catch (error) {
    if (error instanceof ApiError && error.status < 500) throw error;
    return queue();
  }
  if (generation !== sessionGeneration) throw new ApiError(401, null);
  const result = response.results?.find((item) => item.mutationId === mutation.mutationId);
  if (result?.status === "REJECTED") {
    throw new ApiError(422, {
      code: result.error?.code,
      detail: result.error?.message,
      errors: result.error?.errors,
    });
  }
  if (result?.status !== "APPLIED") return queue();
  const currentQueue = readQueue(owner);
  // Remove a versão recusada que o usuário acabou de corrigir.
  setOfflineQueue(
    owner,
    currentQueue.filter(
      (item) => !(item.rejection && item.entity === entity && item.entityId === entityId),
    ),
  );
  invalidateMobileSyncCache();
  return "applied";
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

async function submitRecordMutation(
  entityId: string,
  bodyData: Record<string, unknown>,
): Promise<Response> {
  const recordPayload = {
    ...bodyData,
    id: entityId,
    clientCreatedAt: bodyData.clientCreatedAt ?? new Date().toISOString(),
  };
  try {
    const status = await submitMutation("record", entityId, recordPayload);
    return jsonResponse(
      { ...recordPayload, offlineQueued: status === "queued" },
      status === "queued" ? 201 : 200,
    );
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    return jsonResponse(
      { code: error.code, detail: error.problemDetail, errors: error.errors },
      error.status,
    );
  }
}

/**
 * Intercepta as rotas que o SecurityConfig restringe a COORDINATOR (/properties, /records,
 * /announcements, /notifications, /catalog/crops, /catalog/farm-inputs) e as atende via
 * /sync/pull e /sync/push para o perfil FARMER.
 */
async function mobileSyncFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const req = input instanceof Request ? input : new Request(input, init);
  const url = new URL(req.url, window.location.origin);
  const path = url.pathname.replace(/^\/api\/v1/, "");
  const method = req.method.toUpperCase();

  // Leitura via /sync/pull
  if (method === "GET") {
    if (
      path === "/properties" ||
      /^\/properties\/[^/]+$/.test(path) ||
      /^\/properties\/[^/]+\/plots$/.test(path) ||
      path === "/records" ||
      /^\/records\/[^/]+$/.test(path) ||
      path === "/announcements" ||
      path === "/notifications" ||
      path === "/catalog/crops" ||
      path === "/catalog/farm-inputs" ||
      path === "/catalog/units"
    ) {
      try {
        const pull = await fetchSyncSnapshot();
        const changes = pull.changes;
        const pendingProperties = new Map(
          getOfflineQueue()
            .filter((item) => item.entity === "property")
            .map((item) => [item.entityId, item.data]),
        );
        const props = (changes?.properties ?? []).map((p) => ({
          ...p,
          ...(p.id ? pendingProperties.get(p.id) : undefined),
          address: p.address
            ? {
                ...p.address,
                municipalityName:
                  p.address.municipalityName ??
                  (p.address.municipalityId
                    ? municipalityNameById.get(p.address.municipalityId)
                    : undefined),
              }
            : undefined,
        }));
        const plots = changes?.plots ?? [];
        const workers = changes?.workers ?? [];
        const certs = changes?.certifications ?? [];
        const crops = changes?.crops ?? [];
        const inputs = changes?.farmInputs ?? [];

        if (path === "/properties") {
          return jsonResponse({
            content: props,
            page: {
              number: 0,
              size: props.length || 20,
              totalElements: props.length,
              totalPages: 1,
            },
          });
        }

        const propDetailMatch = path.match(/^\/properties\/([^/]+)$/);
        if (propDetailMatch) {
          const propId = propDetailMatch[1];
          const found = props.find((p) => p.id === propId);
          if (!found) {
            return jsonResponse({ detail: "Propriedade não encontrada." }, 404);
          }
          return jsonResponse({
            ...found,
            plots: plots.filter((pl) => pl.propertyId === propId),
            workers: workers.filter((w) => w.propertyId === propId),
            certifications: certs.filter((c) => c.propertyId === propId),
          });
        }

        const propPlotsMatch = path.match(/^\/properties\/([^/]+)\/plots$/);
        if (propPlotsMatch) {
          const propId = propPlotsMatch[1];
          return jsonResponse(plots.filter((pl) => pl.propertyId === propId));
        }

        if (path === "/catalog/crops") {
          return jsonResponse(crops);
        }

        if (path === "/catalog/units") {
          return jsonResponse(
            [...(changes?.units ?? [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)),
          );
        }

        if (path === "/catalog/farm-inputs") {
          return jsonResponse(inputs);
        }

        if (path === "/announcements") {
          const list = [...(changes?.announcements ?? [])].sort((a, b) =>
            (b.publishedAt ?? "").localeCompare(a.publishedAt ?? ""),
          );
          return jsonResponse({
            content: list,
            page: {
              number: 0,
              size: list.length || 10,
              totalElements: list.length,
              totalPages: 1,
            },
          });
        }

        if (path === "/notifications") {
          const list = [...(changes?.notifications ?? [])].sort((a, b) =>
            (b.createdAt ?? "").localeCompare(a.createdAt ?? ""),
          );
          return jsonResponse({
            content: list,
            page: {
              number: 0,
              size: list.length || 30,
              totalElements: list.length,
              totalPages: 1,
            },
          });
        }

        const recDetailMatch = path.match(/^\/records\/([^/]+)$/);
        if (recDetailMatch) {
          const recId = recDetailMatch[1]!;
          const rawRec = (changes?.records ?? []).find((r) => r.id === recId);
          if (!rawRec) {
            return jsonResponse({ detail: "Registro não encontrado." }, 404);
          }
          const atts = (changes?.attachments ?? []).filter(
            (a) => a.ownerType === "RECORD" && a.ownerId === recId,
          );
          const corrs = (changes?.correctionRequests ?? []).filter((c) => c.recordId === recId);
          return jsonResponse({
            record: rawRec,
            attachments: atts,
            correctionRequests: corrs,
          });
        }

        if (path === "/records") {
          const filterType = url.searchParams.get("type");
          const filterPropertyId = url.searchParams.get("propertyId");
          const filterCycleStatus = url.searchParams.get("cycleStatus");
          const cropById = new Map(crops.map((c) => [c.id, c.name]));
          const inputById = new Map(inputs.map((i) => [i.id, i.name]));
          const propById = new Map(props.map((p) => [p.id, p]));
          const plotById = new Map(plots.map((pl) => [pl.id, pl]));
          const rawRecords = changes?.records ?? [];
          const recordById = new Map(rawRecords.map((r) => [r.id, r]));

          // Inclui também registros pendentes na fila offline ainda não sincronizados
          const offlineRecords = getOfflineQueue()
            .filter((m) => m.entity === "record" && m.op === "UPSERT" && m.data)
            .map((m) => ({
              ...(m.data as unknown as RecordView),
              id: m.entityId,
              completeness: "INCOMPLETE" as const,
            }));

          const mergedRecords = [
            ...rawRecords.filter((r) => !offlineRecords.some((o) => o.id === r.id)),
            ...offlineRecords,
          ];

          const mapped: RecordListItem[] = mergedRecords
            .filter((r) => !filterType || r.type === filterType)
            .filter((r) => !filterPropertyId || r.propertyId === filterPropertyId)
            .filter((r) => {
              if (!filterCycleStatus) return true;
              const recAny = r as Record<string, unknown>;
              return !recAny.cycleStatus || recAny.cycleStatus === filterCycleStatus;
            })
            .map((r) => {
              const recAny = r as Record<string, unknown>;
              const prop = r.propertyId ? propById.get(r.propertyId) : undefined;
              const plot = r.plotId ? plotById.get(r.plotId) : undefined;
              let subject = "";
              if (r.type === "PLANTING") {
                subject =
                  (recAny.cropId ? cropById.get(recAny.cropId as string) : undefined) ??
                  (recAny.cropOtherName as string | undefined) ??
                  "Plantio";
              } else if (r.type === "INPUT_APPLICATION" || r.type === "INPUT_PURCHASE") {
                subject =
                  (recAny.inputId ? inputById.get(recAny.inputId as string) : undefined) ??
                  (recAny.inputOtherName as string | undefined) ??
                  "Insumo";
              } else if (r.type === "HARVEST") {
                const planting = recAny.plantingId
                  ? (recordById.get(recAny.plantingId as string) as
                      Record<string, unknown> | undefined)
                  : undefined;
                subject =
                  (planting?.cropId ? cropById.get(planting.cropId as string) : undefined) ??
                  (planting?.cropOtherName as string | undefined) ??
                  "Colheita";
              } else if (r.type === "OUTFLOW") {
                subject =
                  (recAny.cropId ? cropById.get(recAny.cropId as string) : undefined) ??
                  (recAny.productOtherName as string | undefined) ??
                  "Saída / Venda";
              }

              return {
                id: r.id,
                number: r.number,
                type: r.type,
                performedOn: r.performedOn,
                property: prop ? { id: prop.id, name: prop.name } : undefined,
                plot: plot ? { id: plot.id, name: plot.name } : undefined,
                subject,
                quantity: (recAny.quantity ??
                  recAny.propagationQuantity) as RecordListItem["quantity"],
                area: (recAny.plantedArea ??
                  recAny.appliedArea ??
                  recAny.harvestedArea) as RecordListItem["area"],
                completeness: r.completeness,
                openCorrectionRequests: r.openCorrectionRequests,
                source: r.source,
                updatedAt: r.updatedAt,
              };
            })
            .sort((a, b) => (b.performedOn ?? "").localeCompare(a.performedOn ?? ""));

          return jsonResponse({
            content: mapped,
            page: {
              number: 0,
              size: mapped.length || 50,
              totalElements: mapped.length,
              totalPages: 1,
            },
          });
        }
      } catch (error) {
        const status = error instanceof ApiError ? error.status : 503;
        return jsonResponse(
          { detail: "Não foi possível consultar os dados sincronizados." },
          status,
        );
      }
    }
  }

  // Criação de manejo via /sync/push
  if (method === "POST" && path === "/records") {
    const bodyData = (await req.clone().json()) as Record<string, unknown>;
    const entityId = (typeof bodyData.id === "string" && bodyData.id) || crypto.randomUUID();
    return submitRecordMutation(entityId, bodyData);
  }

  // Edição de manejo existente via /sync/push (atende pedidos de correção e completa campos)
  const recordPutMatch = path.match(/^\/records\/([^/]+)$/);
  if (method === "PUT" && recordPutMatch) {
    const entityId = recordPutMatch[1]!;
    const bodyData = (await req.clone().json()) as Record<string, unknown>;
    return submitRecordMutation(entityId, bodyData);
  }

  // Marcar notificações como lidas via /sync/push
  if (method === "POST" && path === "/notifications/read-all") {
    const pull = await fetchSyncSnapshot();
    const unread = (pull.changes?.notifications ?? []).filter((n) => !n.readAt && n.id);
    for (const item of unread) {
      if (item.id)
        await submitMutation("notification", item.id, { readAt: new Date().toISOString() });
    }
    return new Response(null, { status: 204 });
  }

  return sessionFetch(req);
}

export const api = createClient<paths>({
  baseUrl: "/api/v1",
  querySerializer: serializeSpringQuery,
  fetch: mobileSyncFetch,
});
