// Cliente HTTP tipado com openapi-fetch para a aplicação mobile do agricultor.
// Inclui ponte transparente para /sync/pull e /sync/push, cache offline em localStorage (ADR-02),
// fila de mutações offline e upload de anexos fotográficos com verificação SHA-256.
// Toda chamada à API passa por authFetch, que renova o token vencido (15 min) e repete a chamada uma vez.
import createClient, { type Middleware } from "openapi-fetch";
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
import { BAHIA_MUNICIPALITIES } from "@agrofacil/domain";
import { generateUuid } from "./uuid";
import {
  deletePendingPhoto,
  listPendingPhotos,
  savePendingPhoto,
  type PendingPhoto,
} from "./photoQueue";
import { splitPushResults, type RejectedChange } from "./pushResults";
import { applyPull, emptySyncState, parseSyncState, type SyncState } from "./syncState";

export type { RejectedChange } from "./pushResults";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
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
    this.errors = errs;
  }
}

const STORAGE_KEYS = {
  ACCESS_TOKEN: "af_mobile_access_token",
  REFRESH_TOKEN: "af_mobile_refresh_token",
  USER: "af_mobile_user",
  DEVICE_ID: "af_mobile_device_id",
  SYNC_STATE: "af_mobile_sync_state",
  /** Formato antigo (pull completo a cada vez); só é apagado. */
  LEGACY_SYNC_SNAPSHOT: "af_mobile_sync_snapshot",
  OFFLINE_QUEUE: "af_mobile_offline_queue",
  REJECTED_CHANGES: "af_mobile_rejected_changes",
} as const;

/** Evento disparado quando a fila, as fotos pendentes ou as recusas mudam (o banner do app escuta). */
export const OFFLINE_QUEUE_EVENT = "af-offline-queue-changed";

/** Página do /sync/pull (doc 1 §3.5.2) e teto de páginas por sincronização, para não girar sem fim. */
const PULL_PAGE_SIZE = 500;
const MAX_PULL_PAGES = 50;
/** Pulls seguidos dentro deste intervalo (ms) reaproveitam o resultado em memória. */
const PULL_MEMORY_CACHE_MS = 2500;

export interface QueuedMutation {
  mutationId: string;
  entity: "record" | "property" | "attachment" | "notification";
  op: "UPSERT" | "DELETE";
  entityId: string;
  clientTimestamp: string;
  data?: Record<string, unknown>;
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
    id = generateUuid();
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

export function saveSession(session: SessionResponse): void {
  if (session.accessToken) {
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, session.accessToken);
  }
  if (session.refreshToken) {
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, session.refreshToken);
  }
  if (session.user) {
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(session.user));
  }
}

export function clearSession(): void {
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.USER);
  // LGPD: o espelho tem dados pessoais e não pode ficar para o próximo usuário do aparelho.
  // A fila offline e as recusas ficam: são trabalho do agricultor que ainda não chegou à API.
  localStorage.removeItem(STORAGE_KEYS.SYNC_STATE);
  localStorage.removeItem(STORAGE_KEYS.LEGACY_SYNC_SNAPSHOT);
  cachedPull = null;
}

export function getOfflineQueue(): QueuedMutation[] {
  const raw = localStorage.getItem(STORAGE_KEYS.OFFLINE_QUEUE);
  if (!raw) return [];
  try {
    return JSON.parse(raw) as QueuedMutation[];
  } catch {
    return [];
  }
}

function setOfflineQueue(queue: QueuedMutation[]): void {
  localStorage.setItem(STORAGE_KEYS.OFFLINE_QUEUE, JSON.stringify(queue));
  notifyQueueChanged();
}

function notifyQueueChanged(): void {
  window.dispatchEvent(new CustomEvent(OFFLINE_QUEUE_EVENT));
}

/** Alterações que a API recusou e o agricultor ainda não viu. */
export function getRejectedChanges(): RejectedChange[] {
  const raw = localStorage.getItem(STORAGE_KEYS.REJECTED_CHANGES);
  if (!raw) return [];
  try {
    return JSON.parse(raw) as RejectedChange[];
  } catch {
    return [];
  }
}

function addRejectedChanges(items: RejectedChange[]): void {
  if (items.length === 0) return;
  localStorage.setItem(
    STORAGE_KEYS.REJECTED_CHANGES,
    JSON.stringify([...getRejectedChanges(), ...items]),
  );
  notifyQueueChanged();
}

export function dismissRejectedChanges(): void {
  localStorage.removeItem(STORAGE_KEYS.REJECTED_CHANGES);
  notifyQueueChanged();
}

function enqueueOfflineMutation(mutation: QueuedMutation): void {
  const current = getOfflineQueue().filter(
    (m) => !(m.entity === mutation.entity && m.entityId === mutation.entityId && m.op === "UPSERT"),
  );
  current.push(mutation);
  setOfflineQueue(current);
}

function loadSyncState(): SyncState | null {
  return parseSyncState(localStorage.getItem(STORAGE_KEYS.SYNC_STATE));
}

function saveSyncState(state: SyncState): void {
  localStorage.removeItem(STORAGE_KEYS.LEGACY_SYNC_SNAPSHOT);
  try {
    localStorage.setItem(STORAGE_KEYS.SYNC_STATE, JSON.stringify(state));
  } catch {
    // Cota cheia: o espelho continua só em memória e o próximo início puxa tudo de novo (cursor "0").
  }
}

let refreshingPromise: Promise<SessionResponse | null> | null = null;
let onUnauthorizedCallback: (() => void) | null = null;

export function setOnUnauthorized(cb: (() => void) | null): void {
  onUnauthorizedCallback = cb;
}

/** Renova o token com o refresh token. Chamadas simultâneas compartilham a mesma renovação. */
function refreshSession(): Promise<SessionResponse | null> {
  const refreshToken = getStoredRefreshToken();
  if (!refreshToken) {
    clearSession();
    onUnauthorizedCallback?.();
    return Promise.resolve(null);
  }
  refreshingPromise ??= (async () => {
    try {
      const refreshRes = await window.fetch("/api/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken, deviceId: getStoredDeviceId() }),
      });
      if (!refreshRes.ok) {
        clearSession();
        onUnauthorizedCallback?.();
        return null;
      }
      const session = (await refreshRes.json()) as SessionResponse;
      saveSession(session);
      return session;
    } catch {
      // Sem rede: não derruba a sessão; o agricultor continua trabalhando offline.
      return null;
    } finally {
      refreshingPromise = null;
    }
  })();
  return refreshingPromise;
}

/**
 * fetch para a API com o token do aparelho. Em 401, renova o token e repete a chamada uma vez.
 * O body precisa poder ser reenviado (string, ArrayBuffer ou Blob; não stream).
 * @param path caminho relativo a /api/v1, com a query string
 */
async function authFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const send = (token: string | null) => {
    const headers = new Headers(init.headers);
    if (token) headers.set("Authorization", `Bearer ${token}`);
    else headers.delete("Authorization");
    return window.fetch(`/api/v1${path}`, { ...init, headers });
  };
  const response = await send(getStoredAccessToken());
  if (response.status !== 401 || path.startsWith("/auth/")) {
    return response;
  }
  const session = await refreshSession();
  return session?.accessToken ? send(session.accessToken) : response;
}

function jsonPost(path: string, body: unknown): Promise<Response> {
  return authFetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
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

let cachedPull: { at: number; data: PullResponse } | null = null;
let inflightPull: Promise<PullResponse> | null = null;

export function invalidateMobileSyncCache(): void {
  cachedPull = null;
}

/**
 * Devolve o espelho local atualizado. Puxa só o que mudou desde o último cursor, página a página,
 * até hasMore=false. Sem rede, devolve o último espelho salvo no aparelho.
 */
async function fetchSyncSnapshot(force = false): Promise<PullResponse> {
  if (!force && cachedPull && Date.now() - cachedPull.at < PULL_MEMORY_CACHE_MS) {
    return cachedPull.data;
  }
  inflightPull ??= (async () => {
    try {
      const state = await pullChanges(loadSyncState() ?? emptySyncState());
      const data: PullResponse = { cursor: state.cursor, changes: state.changes };
      cachedPull = { at: Date.now(), data };
      return data;
    } catch (err) {
      const saved = loadSyncState();
      if (saved) {
        return { cursor: saved.cursor, changes: saved.changes };
      }
      throw err;
    } finally {
      inflightPull = null;
    }
  })();
  return inflightPull;
}

async function pullChanges(initial: SyncState): Promise<SyncState> {
  let state = initial;
  for (let page = 0; page < MAX_PULL_PAGES; page++) {
    const res = await authFetch(
      `/sync/pull?cursor=${encodeURIComponent(state.cursor)}&limit=${PULL_PAGE_SIZE}`,
      { headers: { Accept: "application/json" } },
    );
    if (!res.ok) {
      throw new Error(`Sync pull failed with status ${res.status}`);
    }
    const data = (await res.json()) as PullResponse;
    state = applyPull(state, data);
    saveSyncState(state);
    // reset: o espelho foi zerado; a próxima volta puxa tudo de novo a partir do cursor "0".
    if (!data.reset && !data.hasMore) break;
  }
  return state;
}

/**
 * Sincroniza mutações pendentes na fila offline quando a conexão é restabelecida.
 */
export async function flushOfflineQueue(): Promise<{ synced: number; remaining: number }> {
  const queue = getOfflineQueue();
  let synced = 0;
  if (queue.length > 0 && getStoredAccessToken()) {
    try {
      const res = await jsonPost("/sync/push", { deviceId: getStoredDeviceId(), mutations: queue });
      if (res.ok) {
        const pushData = (await res.json()) as PushResponse;
        const { remaining, rejected } = splitPushResults(
          queue,
          pushData.results,
          new Date().toISOString(),
        );
        // Relê a fila: o agricultor pode ter salvo algo novo enquanto o envio estava em andamento.
        const sentIds = new Set(queue.map((m) => m.mutationId));
        const addedMeanwhile = getOfflineQueue().filter((m) => !sentIds.has(m.mutationId));
        setOfflineQueue([...remaining, ...addedMeanwhile]);
        addRejectedChanges(rejected);
        synced = queue.length - remaining.length - rejected.length;
        cachedPull = null;
      }
    } catch {
      // Sem rede: a fila fica como está.
    }
  }
  // Fotos depois dos registros: o anexo só é aceito quando o registro já existe na API.
  await syncPendingPhotos();
  return { synced, remaining: getOfflineQueue().length };
}

if (typeof window !== "undefined") {
  window.addEventListener("online", () => {
    void flushOfflineQueue();
  });
}

/**
 * Envia as fotos guardadas no aparelho: primeiro registra o anexo (mutação "attachment"),
 * depois manda o arquivo. Cada etapa concluída é gravada, então uma falha no meio não repete trabalho.
 */
export async function syncPendingPhotos(): Promise<void> {
  let photos: PendingPhoto[];
  try {
    photos = await listPendingPhotos();
  } catch {
    return; // IndexedDB indisponível (ex.: navegação anônima antiga)
  }
  const recordsNotSentYet = new Set(
    getOfflineQueue()
      .filter((m) => m.entity === "record")
      .map((m) => m.entityId),
  );
  for (const photo of photos) {
    if (recordsNotSentYet.has(photo.recordId)) continue;
    try {
      const done = await sendPendingPhoto(photo);
      if (done) await deletePendingPhoto(photo.attachmentId);
    } catch {
      break; // sem rede: tenta tudo de novo no próximo envio
    }
  }
  notifyQueueChanged();
}

/** Envia uma foto. Devolve true quando ela pode sair da fila (enviada ou recusada de vez). */
async function sendPendingPhoto(photo: PendingPhoto): Promise<boolean> {
  if (!photo.registered) {
    const mutation = {
      mutationId: photo.mutationId,
      entity: "attachment",
      op: "UPSERT",
      entityId: photo.attachmentId,
      clientTimestamp: photo.createdAt,
      data: {
        id: photo.attachmentId,
        ownerType: "RECORD",
        ownerId: photo.recordId,
        kind: "PHOTO",
        contentType: photo.contentType,
        sizeBytes: photo.sizeBytes,
        sha256: photo.sha256,
      },
    };
    const res = await jsonPost("/sync/push", {
      deviceId: getStoredDeviceId(),
      mutations: [mutation],
    });
    if (!res.ok) return false;
    const pushData = (await res.json()) as PushResponse;
    const { remaining, rejected } = splitPushResults(
      [mutation],
      pushData.results,
      new Date().toISOString(),
    );
    if (rejected.length > 0) {
      addRejectedChanges(rejected);
      return true;
    }
    if (remaining.length > 0) return false;
    await savePendingPhoto({ ...photo, registered: true });
  }

  const put = await authFetch(`/files/${photo.attachmentId}/content`, {
    method: "PUT",
    headers: { "Content-Type": photo.contentType },
    body: photo.blob,
  });
  if (put.ok) {
    cachedPull = null;
    return true;
  }
  // 4xx (fora 401/408/429) é definitivo, ex.: CHECKSUM_MISMATCH; 5xx e os demais tentam de novo depois.
  const retryable =
    put.status === 401 || put.status === 408 || put.status === 429 || put.status >= 500;
  if (retryable) return false;
  addRejectedChanges([
    {
      mutationId: photo.mutationId,
      entity: "attachment",
      entityId: photo.attachmentId,
      message: "A foto foi recusada pelo servidor.",
      rejectedAt: new Date().toISOString(),
    },
  ]);
  return true;
}

async function computeSha256Hex(buffer: ArrayBuffer): Promise<string> {
  // Atenção: o navegador só expõe crypto.subtle em contexto seguro (HTTPS ou localhost).
  if (!crypto.subtle) {
    throw new Error("Envio de fotos indisponível: abra o app por HTTPS ou localhost.");
  }
  const hashBuffer = await crypto.subtle.digest("SHA-256", buffer);
  const bytes = new Uint8Array(hashBuffer);
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/**
 * Guarda a foto de um registro de manejo no aparelho e tenta enviá-la logo (doc 1 §3.6).
 * A foto nunca se perde por falta de rede: fica no IndexedDB até a API confirmar o recebimento.
 * @returns queued=true se a foto ficou esperando internet (ou o registro ainda não foi enviado)
 */
export async function uploadRecordPhoto(
  recordId: string,
  file: File,
): Promise<{ attachmentId: string; queued: boolean }> {
  const sha256 = await computeSha256Hex(await file.arrayBuffer());
  const attachmentId = generateUuid();
  await savePendingPhoto({
    attachmentId,
    mutationId: generateUuid(),
    recordId,
    blob: file,
    contentType: file.type || "image/jpeg",
    sizeBytes: file.size,
    sha256,
    createdAt: new Date().toISOString(),
    registered: false,
  });
  await syncPendingPhotos();
  const stillPending = (await listPendingPhotos()).some((p) => p.attachmentId === attachmentId);
  return { attachmentId, queued: stillPending };
}

/** Fotos deste registro que ainda estão só no aparelho (vazio se o IndexedDB não estiver disponível). */
export async function listPendingPhotosForRecord(recordId: string): Promise<PendingPhoto[]> {
  try {
    return (await listPendingPhotos()).filter((p) => p.recordId === recordId);
  } catch {
    return [];
  }
}

/**
 * Baixa o arquivo de um anexo. Precisa de fetch porque <img src> não envia o token Bearer.
 * @throws Error se a API recusar ou não houver internet
 */
export async function fetchAttachmentContent(attachmentId: string): Promise<Blob> {
  const response = await authFetch(`/files/${attachmentId}/content`);
  if (!response.ok) {
    throw new Error(`Não foi possível baixar o anexo (HTTP ${response.status}).`);
  }
  return response.blob();
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
  const found = rawRecords.find((r) => r.id === recordId) as RecordView | undefined;
  if (!found) return null;

  const attachments = (pull.changes?.attachments ?? []).filter(
    (a) => a.ownerType === "RECORD" && a.ownerId === recordId,
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
export async function upsertFarmerProperty(
  propertyId: string,
  payload: PropertyWrite,
): Promise<void> {
  const nowIso = new Date().toISOString();
  const dataPayload: Record<string, unknown> = {
    ...payload,
    id: propertyId,
  };

  const mutation: QueuedMutation = {
    mutationId: generateUuid(),
    entity: "property",
    op: "UPSERT",
    entityId: propertyId,
    clientTimestamp: nowIso,
    data: dataPayload,
  };

  if (typeof navigator !== "undefined" && !navigator.onLine) {
    enqueueOfflineMutation(mutation);
    return;
  }

  const pushRes = await jsonPost("/sync/push", {
    deviceId: getStoredDeviceId(),
    mutations: [mutation],
  });

  if (!pushRes.ok) {
    throw new Error("Não foi possível atualizar os dados da propriedade.");
  }

  const pushData = (await pushRes.json()) as PushResponse;
  const firstResult = pushData.results?.[0];
  if (firstResult?.status === "REJECTED") {
    throw new Error(firstResult.error?.message ?? "Dados da propriedade inválidos.");
  }

  cachedPull = null;
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
  const nowIso = new Date().toISOString();
  const recordPayload: Record<string, unknown> = {
    ...bodyData,
    id: entityId,
    clientCreatedAt: (bodyData.clientCreatedAt as string | undefined) ?? nowIso,
  };

  const mutation: QueuedMutation = {
    mutationId: generateUuid(),
    entity: "record",
    op: "UPSERT",
    entityId,
    clientTimestamp: nowIso,
    data: recordPayload,
  };

  if (typeof navigator !== "undefined" && !navigator.onLine) {
    enqueueOfflineMutation(mutation);
    return jsonResponse({ id: entityId, ...recordPayload, offlineQueued: true }, 201);
  }

  try {
    const pushRes = await jsonPost("/sync/push", {
      deviceId: getStoredDeviceId(),
      mutations: [mutation],
    });

    if (!pushRes.ok) {
      return pushRes;
    }

    const pushData = (await pushRes.json()) as PushResponse;
    const firstResult = pushData.results?.[0];
    if (firstResult?.status === "REJECTED") {
      return jsonResponse(
        {
          code: firstResult.error?.code ?? "VALIDATION_ERROR",
          detail: firstResult.error?.message ?? "Não foi possível salvar o registro.",
          errors: firstResult.error?.errors ?? [],
        },
        422,
      );
    }

    cachedPull = null;
    return jsonResponse({ id: entityId, ...recordPayload }, 200);
  } catch {
    enqueueOfflineMutation(mutation);
    return jsonResponse({ id: entityId, ...recordPayload, offlineQueued: true }, 201);
  }
}

/**
 * Intercepta as rotas que o SecurityConfig restringe a COORDINATOR (/properties, /records,
 * /announcements, /notifications, /catalog/crops, /catalog/farm-inputs) e as atende via
 * /sync/pull e /sync/push para o perfil FARMER. /catalog/units é aberta ao agricultor, mas também sai do
 * espelho local para o formulário ter as unidades sem internet.
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
        const props = (changes?.properties ?? []).map((p) => ({
          ...p,
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

        if (path === "/catalog/farm-inputs") {
          return jsonResponse(inputs);
        }

        if (path === "/catalog/units") {
          return jsonResponse(changes?.units ?? []);
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
                planting:
                  r.type === "PLANTING"
                    ? {
                        cropId: recAny.cropId as string | undefined,
                        cropOtherName: recAny.cropOtherName as string | undefined,
                        cycleStatus: recAny.cycleStatus as NonNullable<RecordListItem["planting"]>["cycleStatus"],
                        finishedOn: recAny.finishedOn as string | undefined,
                      }
                    : undefined,
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
      } catch {
        // Essas rotas são só do coordenador; repassar para a API daria 403. Sem espelho, não há o que mostrar.
        return jsonResponse(
          { detail: "Sem conexão e ainda sem dados guardados no aparelho. Tente com internet." },
          503,
        );
      }
    }
  }

  // Criação de manejo via /sync/push
  if (method === "POST" && path === "/records") {
    const bodyData = (await req.clone().json()) as Record<string, unknown>;
    const entityId = (typeof bodyData.id === "string" && bodyData.id) || generateUuid();
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
    if (unread.length > 0) {
      const nowIso = new Date().toISOString();
      await jsonPost("/sync/push", {
        deviceId: getStoredDeviceId(),
        mutations: unread.map((n) => ({
          mutationId: generateUuid(),
          entity: "notification",
          op: "UPSERT",
          entityId: n.id,
          clientTimestamp: nowIso,
          data: { readAt: nowIso },
        })),
      });
      cachedPull = null;
    }
    return new Response(null, { status: 204 });
  }

  // Demais rotas: repassa à API. O body vira ArrayBuffer para poder ser reenviado após renovar o token.
  const body = method === "GET" || method === "HEAD" ? undefined : await req.arrayBuffer();
  return authFetch(`${path}${url.search}`, { method, headers: req.headers, body });
}

// O token e a renovação em 401 ficam em authFetch (dentro de mobileSyncFetch). O middleware só põe o
// token no Request: refazer a chamada aqui exigiria reenviar um body em stream, que o fetch não permite.
const authMiddleware: Middleware = {
  async onRequest({ request }) {
    const token = getStoredAccessToken();
    if (token) {
      request.headers.set("Authorization", `Bearer ${token}`);
    }
    return request;
  },
};

export const api = createClient<paths>({
  baseUrl: "/api/v1",
  querySerializer: serializeSpringQuery,
  fetch: mobileSyncFetch,
});

api.use(authMiddleware);
