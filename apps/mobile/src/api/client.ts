// Cliente HTTP tipado com openapi-fetch para a aplicação mobile do agricultor.
// Inclui ponte transparente para /sync/pull e /sync/push, cache offline em localStorage (ADR-02),
// fila de mutações offline e upload de anexos fotográficos com verificação SHA-256.
// Toda chamada à API passa por authFetch, que renova o token vencido (15 min) e repete a chamada uma vez.
// Espelho, fila e fotos são separados por conta: duas pessoas podem usar o mesmo aparelho.
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
import { rejectionMessage, splitPushResults } from "./pushResults";
import { applyPull, emptySyncState, parseSyncState, type SyncState } from "./syncState";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  /** Texto da API sem o resumo dos campos. */
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
  SYNC_STATE: "af_mobile_sync_state",
  /** Formato antigo (pull completo a cada vez); só é apagado. */
  LEGACY_SYNC_SNAPSHOT: "af_mobile_sync_snapshot",
  OFFLINE_QUEUE: "af_mobile_offline_queue",
  /** Lista antiga de recusas; hoje a recusa fica no próprio item da fila. */
  LEGACY_REJECTED_CHANGES: "af_mobile_rejected_changes",
} as const;

/** Chave do usuário salvo; as outras abas observam a mudança dela para acompanhar a troca de conta. */
export const USER_STORAGE_KEY = STORAGE_KEYS.USER;

/** Evento disparado quando a fila, as fotos pendentes ou as recusas mudam (o banner do app escuta). */
export const OFFLINE_QUEUE_EVENT = "af-offline-queue-changed";

/** Página do /sync/pull (doc 1 §3.5.2) e teto de páginas por sincronização, para não girar sem fim. */
const PULL_PAGE_SIZE = 500;
const MAX_PULL_PAGES = 50;
/** Pulls seguidos dentro deste intervalo (ms) reaproveitam o resultado em memória. */
const PULL_MEMORY_CACHE_MS = 2500;
/** Mutações por envio no /sync/push (limite da API). */
const PUSH_BATCH_SIZE = 100;

export interface QueuedMutation {
  mutationId: string;
  entity: "record" | "property" | "attachment" | "notification" | "visit";
  op: "UPSERT" | "DELETE";
  entityId: string;
  clientTimestamp: string;
  data?: Record<string, unknown>;
  /** Motivo da recusa. O item espera a pessoa corrigir ou descartar e não é reenviado sozinho. */
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

/** Sobe a cada troca de conta ou saída. Respostas de uma sessão anterior são ignoradas. */
let sessionGeneration = 0;

function accountId(): string | undefined {
  return getStoredUser()?.id;
}

function accountKey(key: string, owner: string): string {
  return `${key}:${owner}`;
}

export function saveSession(session: SessionResponse): void {
  if (session.user?.id && session.user.id !== accountId()) {
    invalidateStoredSession();
  }
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

/** Descarta o que está em memória da sessão (ex.: outra aba trocou de conta). */
export function invalidateStoredSession(): void {
  sessionGeneration++;
  cachedPull = null;
}

export function clearSession(): void {
  const owner = accountId();
  invalidateStoredSession();
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.USER);
  // LGPD: o espelho tem dados pessoais e não pode ficar para o próximo usuário do aparelho.
  // A fila e as fotos ficam, separadas por conta: são trabalho que ainda não chegou à API.
  if (owner) localStorage.removeItem(accountKey(STORAGE_KEYS.SYNC_STATE, owner));
  localStorage.removeItem(STORAGE_KEYS.SYNC_STATE);
  localStorage.removeItem(STORAGE_KEYS.LEGACY_SYNC_SNAPSHOT);
}

function readList<T>(key: string): T[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(key) ?? "[]");
    return Array.isArray(value) ? (value as T[]) : [];
  } catch {
    return [];
  }
}

function readQueue(owner: string): QueuedMutation[] {
  return readList(accountKey(STORAGE_KEYS.OFFLINE_QUEUE, owner));
}

/** Fila da conta atual, incluindo os itens recusados que esperam correção. */
export function getOfflineQueue(): QueuedMutation[] {
  const owner = accountId();
  return owner ? readQueue(owner) : [];
}

function setOfflineQueue(owner: string, queue: QueuedMutation[]): void {
  localStorage.setItem(accountKey(STORAGE_KEYS.OFFLINE_QUEUE, owner), JSON.stringify(queue));
  notifyQueueChanged();
}

function notifyQueueChanged(): void {
  window.dispatchEvent(new CustomEvent(OFFLINE_QUEUE_EVENT));
}

function enqueueOfflineMutation(owner: string, mutation: QueuedMutation): void {
  const others = readQueue(owner).filter(
    (m) => !(m.entity === mutation.entity && m.entityId === mutation.entityId),
  );
  setOfflineQueue(owner, [...others, mutation]);
}

/** Tira da fila uma alteração recusada que a pessoa decidiu não corrigir. */
export function discardRejectedMutation(mutationId: string): void {
  const owner = accountId();
  if (!owner) return;
  setOfflineQueue(
    owner,
    readQueue(owner).filter((m) => !(m.rejection && m.mutationId === mutationId)),
  );
}

/**
 * Versões anteriores guardavam uma fila só para o aparelho, sem dono. Recupera apenas os itens cujo
 * destino está no espelho desta conta; os demais ficam para a conta certa.
 */
function recoverLegacyQueue(owner: string, changes: SyncState["changes"]): void {
  const legacyRejected = readList<{
    mutationId: string;
    entity: QueuedMutation["entity"];
    entityId: string;
    message: string;
    rejectedAt: string;
    data?: Record<string, unknown>;
  }>(STORAGE_KEYS.LEGACY_REJECTED_CHANGES).map(
    ({ message, rejectedAt, ...item }): QueuedMutation => ({
      ...item,
      op: "UPSERT",
      clientTimestamp: rejectedAt,
      rejection: message,
    }),
  );
  const legacyQueue = readList<QueuedMutation>(STORAGE_KEYS.OFFLINE_QUEUE);
  if (legacyQueue.length + legacyRejected.length === 0) return;

  const propertyIds = new Set((changes.properties ?? []).map((row) => row.id));
  const recordIds = new Set((changes.records ?? []).map((row) => row.id));
  const isOwn = (item: QueuedMutation) => {
    const target = item.entity === "property" ? item.entityId : item.data?.propertyId;
    return (
      (typeof target === "string" && propertyIds.has(target)) ||
      (item.entity === "record" && recordIds.has(item.entityId))
    );
  };

  const pending = readQueue(owner);
  for (const item of [...legacyQueue, ...legacyRejected].filter(isOwn)) {
    const exists = pending.some((m) => m.entity === item.entity && m.entityId === item.entityId);
    if (!exists) pending.push(item);
  }
  setOfflineQueue(owner, pending);
  localStorage.setItem(
    STORAGE_KEYS.OFFLINE_QUEUE,
    JSON.stringify(legacyQueue.filter((m) => !isOwn(m))),
  );
  localStorage.setItem(
    STORAGE_KEYS.LEGACY_REJECTED_CHANGES,
    JSON.stringify(
      readList<{ mutationId: string }>(STORAGE_KEYS.LEGACY_REJECTED_CHANGES).filter(
        (m) => !legacyRejected.some((item) => item.mutationId === m.mutationId && isOwn(item)),
      ),
    ),
  );
}

function loadSyncState(owner: string): SyncState | null {
  const own = parseSyncState(localStorage.getItem(accountKey(STORAGE_KEYS.SYNC_STATE, owner)));
  if (own) return own;
  // Espelho de versões anteriores, sem dono na chave: só vale se for desta conta.
  const legacy = parseSyncState(localStorage.getItem(STORAGE_KEYS.SYNC_STATE));
  return legacy?.changes.me?.id === owner ? legacy : null;
}

function saveSyncState(owner: string, state: SyncState): void {
  localStorage.removeItem(STORAGE_KEYS.LEGACY_SYNC_SNAPSHOT);
  localStorage.removeItem(STORAGE_KEYS.SYNC_STATE);
  try {
    localStorage.setItem(accountKey(STORAGE_KEYS.SYNC_STATE, owner), JSON.stringify(state));
  } catch {
    // Cota cheia: o espelho continua só em memória e o próximo início puxa tudo de novo (cursor "0").
  }
}

let refreshing: { generation: number; promise: Promise<string | null> } | null = null;
let onUnauthorizedCallback: (() => void) | null = null;

export function setOnUnauthorized(cb: (() => void) | null): void {
  onUnauthorizedCallback = cb;
}

function endSession(): void {
  clearSession();
  onUnauthorizedCallback?.();
}

/** Renova o token de acesso. Chamadas simultâneas da mesma sessão compartilham a renovação. */
async function renewAccessToken(
  generation: number,
  failedToken: string | null,
): Promise<string | null> {
  if (generation !== sessionGeneration) return null;
  const current = getStoredAccessToken();
  if (current && current !== failedToken) return current;
  if (refreshing?.generation === generation) return refreshing.promise;

  const refreshToken = getStoredRefreshToken();
  if (!refreshToken) {
    endSession();
    return null;
  }
  const promise = (async () => {
    try {
      const response = await window.fetch("/api/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken, deviceId: getStoredDeviceId() }),
      });
      if (generation !== sessionGeneration) return null;
      if (response.status === 401 || response.status === 403) {
        endSession();
        return null;
      }
      // API fora do ar (5xx): a sessão continua e a renovação é tentada de novo depois.
      if (!response.ok) return null;
      const session = (await response.json()) as SessionResponse;
      if (generation !== sessionGeneration) return null;
      saveSession(session);
      return session.accessToken ?? null;
    } catch {
      // Sem rede: não derruba a sessão; o agricultor continua trabalhando offline.
      return null;
    }
  })();
  refreshing = { generation, promise };
  try {
    return await promise;
  } finally {
    if (refreshing?.promise === promise) refreshing = null;
  }
}

/**
 * fetch para a API com o token do aparelho. Em 401, renova o token e repete a chamada uma vez.
 * O body precisa poder ser reenviado (string, ArrayBuffer ou Blob; não stream).
 * Se a conta mudar durante a chamada, devolve 401 para a resposta não cair na conta errada.
 * @param path caminho relativo a /api/v1, com a query string
 */
async function authFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const isLogin = path.startsWith("/auth/") && !path.startsWith("/auth/logout");
  const generation = sessionGeneration;
  const send = async (token: string | null) => {
    const headers = new Headers(init.headers);
    if (token) headers.set("Authorization", `Bearer ${token}`);
    else headers.delete("Authorization");
    const result = await window.fetch(`/api/v1${path}`, { ...init, headers });
    return isLogin || generation === sessionGeneration
      ? result
      : new Response(null, { status: 401 });
  };
  const token = getStoredAccessToken();
  const response = await send(token);
  if (response.status !== 401 || path.startsWith("/auth/") || generation !== sessionGeneration) {
    return response;
  }
  const renewed = await renewAccessToken(generation, token);
  return renewed ? send(renewed) : response;
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
let inflightPull: {
  generation: number;
  revision: number;
  promise: Promise<PullResponse>;
} | null = null;
/** Sobe quando algo foi enviado: um pull iniciado antes disso não serve como resposta. */
let pullRevision = 0;

export function invalidateMobileSyncCache(): void {
  pullRevision++;
  cachedPull = null;
}

/**
 * Devolve o espelho local atualizado. Puxa só o que mudou desde o último cursor, página a página,
 * até hasMore=false. Sem rede, devolve o último espelho salvo no aparelho.
 */
export async function fetchSyncSnapshot(): Promise<PullResponse> {
  const owner = accountId();
  if (!owner) throw new ApiError(401, null);
  if (cachedPull && Date.now() - cachedPull.at < PULL_MEMORY_CACHE_MS) {
    return cachedPull.data;
  }
  const generation = sessionGeneration;
  const revision = pullRevision;
  if (inflightPull?.generation === generation && inflightPull.revision === revision) {
    return inflightPull.promise;
  }
  const saved = loadSyncState(owner);
  if (saved && !navigator.onLine) return { cursor: saved.cursor, changes: saved.changes };

  const promise = (async () => {
    try {
      const state = await pullChanges(owner, generation, saved ?? emptySyncState());
      const data: PullResponse = { cursor: state.cursor, changes: state.changes };
      if (revision === pullRevision) cachedPull = { at: Date.now(), data };
      recoverLegacyQueue(owner, state.changes);
      return data;
    } catch (err) {
      // 401 com a sessão ainda aberta = a renovação falhou por falta de rede: vale o espelho salvo.
      const refused = err instanceof ApiError && err.status === 403;
      const fallback = generation === sessionGeneration && !refused ? loadSyncState(owner) : null;
      if (fallback) return { cursor: fallback.cursor, changes: fallback.changes };
      throw err;
    }
  })();
  inflightPull = { generation, revision, promise };
  try {
    return await promise;
  } finally {
    if (inflightPull?.promise === promise) inflightPull = null;
  }
}

async function pullChanges(
  owner: string,
  generation: number,
  initial: SyncState,
): Promise<SyncState> {
  let state = initial;
  for (let page = 0; page < MAX_PULL_PAGES; page++) {
    const res = await authFetch(
      `/sync/pull?cursor=${encodeURIComponent(state.cursor)}&limit=${PULL_PAGE_SIZE}`,
      { headers: { Accept: "application/json" } },
    );
    if (!res.ok) throw new ApiError(res.status, null);
    const data = (await res.json()) as PullResponse;
    if (generation !== sessionGeneration) throw new ApiError(401, null);
    state = applyPull(state, data);
    saveSyncState(owner, state);
    // reset: o espelho foi zerado; a próxima volta puxa tudo de novo a partir do cursor "0".
    if (!data.reset && !data.hasMore) break;
  }
  return state;
}

/** 4xx (fora 401/408/429) é definitivo, ex.: CHECKSUM_MISMATCH; 5xx e os demais tentam de novo depois. */
function isRetryableStatus(status: number): boolean {
  return status === 401 || status === 408 || status === 429 || status >= 500;
}

async function pushMutations(
  batch: Omit<QueuedMutation, "rejection">[],
): Promise<PushResponse["results"]> {
  const res = await jsonPost("/sync/push", { deviceId: getStoredDeviceId(), mutations: batch });
  if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => null));
  return ((await res.json()) as PushResponse).results;
}

type FlushResult = { synced: number; remaining: number };
let flushing: { generation: number; promise: Promise<FlushResult> } | null = null;

/** Envia o que está na fila da conta atual e, depois, as fotos. Chamadas simultâneas compartilham o envio. */
export async function flushOfflineQueue(): Promise<FlushResult> {
  const owner = accountId();
  if (!owner) return { synced: 0, remaining: 0 };
  const generation = sessionGeneration;
  if (flushing?.generation === generation) return flushing.promise;
  const promise = (async () => {
    const synced = await flushMutations(owner, generation);
    // Fotos depois dos registros: o anexo só é aceito quando o registro já existe na API.
    if (generation === sessionGeneration) await syncPendingPhotos();
    return { synced, remaining: readQueue(owner).length };
  })();
  flushing = { generation, promise };
  try {
    return await promise;
  } finally {
    if (flushing?.promise === promise) flushing = null;
  }
}

async function flushMutations(owner: string, generation: number): Promise<number> {
  const attempted = new Set<string>();
  let synced = 0;
  while (generation === sessionGeneration) {
    const batch = readQueue(owner)
      .filter((m) => !m.rejection && !attempted.has(m.mutationId))
      .slice(0, PUSH_BATCH_SIZE);
    if (batch.length === 0) break;
    for (const item of batch) attempted.add(item.mutationId);
    try {
      const results = await pushMutations(batch);
      const { remaining, rejected } = splitPushResults(batch, results);
      const keep = new Map([...remaining, ...rejected].map((m) => [m.mutationId, m]));
      // Relê a fila: o agricultor pode ter salvo algo novo enquanto o envio estava em andamento.
      setOfflineQueue(
        owner,
        readQueue(owner).flatMap((m) => {
          if (!batch.some((sent) => sent.mutationId === m.mutationId)) return [m];
          const kept = keep.get(m.mutationId);
          return kept ? [kept] : [];
        }),
      );
      synced += batch.length - keep.size;
      invalidateMobileSyncCache();
    } catch {
      break; // Sem rede ou API fora do ar: a fila fica como está.
    }
  }
  return synced;
}

/** Fotos da conta atual. As de versões anteriores não têm dono: só valem se o registro for desta conta. */
async function ownPendingPhotos(): Promise<PendingPhoto[]> {
  const owner = accountId();
  if (!owner) return [];
  const photos = await listPendingPhotos();
  const ownRecords = new Set([
    ...(loadSyncState(owner)?.changes.records ?? []).map((r) => r.id),
    ...readQueue(owner).map((m) => m.entityId),
  ]);
  return photos.filter((p) => (p.owner ? p.owner === owner : ownRecords.has(p.recordId)));
}

/** Fotos da conta atual que ainda não chegaram à API (vazio se o IndexedDB não estiver disponível). */
export async function getPendingPhotos(): Promise<PendingPhoto[]> {
  return ownPendingPhotos().catch(() => []);
}

/**
 * Envia as fotos guardadas no aparelho: primeiro registra o anexo (mutação "attachment"),
 * depois manda o arquivo. Cada etapa concluída é gravada, então uma falha no meio não repete trabalho.
 */
export async function syncPendingPhotos(): Promise<void> {
  const generation = sessionGeneration;
  const photos = await getPendingPhotos();
  const recordsNotSentYet = new Set(
    getOfflineQueue()
      .filter((m) => m.entity === "record")
      .map((m) => m.entityId),
  );
  for (const photo of photos) {
    if (generation !== sessionGeneration) return;
    if (photo.error || recordsNotSentYet.has(photo.recordId)) continue;
    try {
      const done = await sendPendingPhoto(photo);
      if (done) await deletePendingPhoto(photo.attachmentId);
    } catch {
      break; // sem rede: tenta tudo de novo no próximo envio
    }
  }
  if (photos.length > 0) notifyQueueChanged();
}

/** Envia uma foto. Devolve true quando a API a recebeu; recusada, fica com o motivo para a pessoa ver. */
async function sendPendingPhoto(photo: PendingPhoto): Promise<boolean> {
  if (!photo.registered) {
    const mutation = {
      mutationId: photo.mutationId,
      entity: "attachment" as const,
      op: "UPSERT" as const,
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
    const { remaining, rejected } = splitPushResults([mutation], await pushMutations([mutation]));
    if (rejected[0]) {
      await savePendingPhoto({ ...photo, error: rejected[0].rejection });
      return false;
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
    invalidateMobileSyncCache();
    return true;
  }
  if (!isRetryableStatus(put.status)) {
    const problem = await put.json().catch(() => null);
    await savePendingPhoto({
      ...photo,
      registered: true,
      error: problem
        ? new ApiError(put.status, problem).detail
        : "A foto foi recusada pelo servidor.",
    });
  }
  return false;
}

/** Desiste de enviar uma foto que está no aparelho. */
export async function discardPendingPhoto(attachmentId: string): Promise<void> {
  const isOwn = (await getPendingPhotos()).some((p) => p.attachmentId === attachmentId);
  if (!isOwn) return;
  await deletePendingPhoto(attachmentId);
  notifyQueueChanged();
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
  const owner = accountId();
  if (!owner) throw new ApiError(401, null);
  const sha256 = await computeSha256Hex(await file.arrayBuffer());
  const attachmentId = generateUuid();
  await savePendingPhoto({
    owner,
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
  notifyQueueChanged();
  await flushOfflineQueue();
  const stillPending = (await getPendingPhotos()).some((p) => p.attachmentId === attachmentId);
  return { attachmentId, queued: stillPending };
}

/** Fotos deste registro que ainda estão só no aparelho. */
export async function listPendingPhotosForRecord(recordId: string): Promise<PendingPhoto[]> {
  return (await getPendingPhotos()).filter((p) => p.recordId === recordId);
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
  // O que está na fila (esperando envio ou correção) vale mais que o espelho.
  const pending = getOfflineQueue().find((m) => m.entity === "record" && m.entityId === recordId);
  const serverRecord = rawRecords.find((r) => r.id === recordId);
  const found = (pending?.data ? { ...serverRecord, ...pending.data } : serverRecord) as
    RecordView | undefined;
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
export function upsertFarmerProperty(
  propertyId: string,
  payload: PropertyWrite,
): Promise<"applied" | "queued"> {
  return submitMutation("property", propertyId, { ...payload, id: propertyId });
}

/**
 * Envia uma alteração. Sem rede, com a API fora do ar ou com itens na frente, ela entra na fila.
 * @throws ApiError se a API recusar
 */
export async function submitMutation(
  entity: QueuedMutation["entity"],
  entityId: string,
  data: Record<string, unknown>,
): Promise<"applied" | "queued"> {
  const owner = accountId();
  if (!owner) throw new ApiError(401, null);
  const generation = sessionGeneration;
  const mutation: QueuedMutation = {
    mutationId: generateUuid(),
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
  if (readQueue(owner).some((m) => !m.rejection)) {
    // Mantém a ordem: um registro não pode chegar antes do plantio que está na fila.
    const status = queue();
    void flushOfflineQueue();
    return status;
  }

  let results: PushResponse["results"];
  try {
    results = await pushMutations([mutation]);
  } catch (error) {
    if (error instanceof ApiError && !isRetryableStatus(error.status)) throw error;
    return queue();
  }
  if (generation !== sessionGeneration) throw new ApiError(401, null);
  const result = results?.find((r) => r.mutationId === mutation.mutationId);
  if (result?.status === "REJECTED") {
    throw new ApiError(422, {
      code: result.error?.code ?? "VALIDATION_ERROR",
      detail: result.error?.message ?? rejectionMessage(result),
      errors: result.error?.errors ?? [],
    });
  }
  if (result?.status !== "APPLIED") return queue();
  // A versão recusada antes, se havia, foi substituída por esta.
  setOfflineQueue(
    owner,
    readQueue(owner).filter(
      (m) => !(m.rejection && m.entity === entity && m.entityId === entityId),
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
  const payload: Record<string, unknown> = {
    ...bodyData,
    id: entityId,
    clientCreatedAt: bodyData.clientCreatedAt ?? new Date().toISOString(),
  };
  try {
    const status = await submitMutation("record", entityId, payload);
    // 201 = guardado no aparelho; 200 = recebido pela API.
    return jsonResponse(
      { ...payload, offlineQueued: status === "queued" },
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

/** Marca avisos como lidos num envio só; sem rede, guarda na fila. */
async function markNotificationsRead(ids: string[]): Promise<void> {
  const owner = accountId();
  if (!owner || ids.length === 0) return;
  const nowIso = new Date().toISOString();
  const mutations = ids.map(
    (id): QueuedMutation => ({
      mutationId: generateUuid(),
      entity: "notification",
      op: "UPSERT",
      entityId: id,
      clientTimestamp: nowIso,
      data: { readAt: nowIso },
    }),
  );
  for (const mutation of mutations) enqueueOfflineMutation(owner, mutation);
  if (navigator.onLine) await flushOfflineQueue();
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
        const pendingProperties = new Map(
          getOfflineQueue()
            .filter((m) => m.entity === "property")
            .map((m) => [m.entityId, m.data]),
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

        if (path === "/catalog/farm-inputs") {
          return jsonResponse(inputs);
        }

        if (path === "/catalog/units") {
          return jsonResponse(
            [...(changes?.units ?? [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)),
          );
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
      } catch (error) {
        // Essas rotas são só do coordenador; repassar para a API daria 403. Sem espelho, não há o que mostrar.
        return jsonResponse(
          { detail: "Sem conexão e ainda sem dados guardados no aparelho. Tente com internet." },
          error instanceof ApiError ? error.status : 503,
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
    await markNotificationsRead(unread.map((n) => n.id!));
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
