// Cliente HTTP tipado com openapi-fetch para a aplicação mobile do agricultor.
// Inclui ponte transparente para /sync/pull e /sync/push, cache offline em localStorage (ADR-02),
// fila de mutações offline e upload de anexos fotográficos com verificação SHA-256.
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
import { BAHIA_MUNICIPALITIES } from "../../../web/src/api/municipalities";

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
  SYNC_SNAPSHOT: "af_mobile_sync_snapshot",
  OFFLINE_QUEUE: "af_mobile_offline_queue",
} as const;

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
  window.dispatchEvent(new CustomEvent("af-offline-queue-changed"));
}

function enqueueOfflineMutation(mutation: QueuedMutation): void {
  const current = getOfflineQueue().filter(
    (m) => !(m.entity === mutation.entity && m.entityId === mutation.entityId && m.op === "UPSERT"),
  );
  current.push(mutation);
  setOfflineQueue(current);
}

function loadPersistedSnapshot(): PullResponse | null {
  const raw = localStorage.getItem(STORAGE_KEYS.SYNC_SNAPSHOT);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as PullResponse;
  } catch {
    return null;
  }
}

function savePersistedSnapshot(snapshot: PullResponse): void {
  try {
    localStorage.setItem(STORAGE_KEYS.SYNC_SNAPSHOT, JSON.stringify(snapshot));
  } catch {
    // Ignora se exceder cota de armazenamento local
  }
}

let refreshingPromise: Promise<SessionResponse | null> | null = null;
let onUnauthorizedCallback: (() => void) | null = null;

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

let cachedPull: { at: number; data: PullResponse } | null = null;
let inflightPull: Promise<PullResponse> | null = null;

export function invalidateMobileSyncCache(): void {
  cachedPull = null;
}

async function fetchSyncSnapshot(authHeader: string | null, force = false): Promise<PullResponse> {
  const now = Date.now();
  if (!force && cachedPull && now - cachedPull.at < 2500) {
    return cachedPull.data;
  }
  if (inflightPull) {
    return inflightPull;
  }
  inflightPull = (async () => {
    try {
      const headers: Record<string, string> = { Accept: "application/json" };
      if (authHeader) {
        headers["Authorization"] = authHeader;
      }
      const res = await window.fetch("/api/v1/sync/pull?cursor=0&limit=1000", {
        method: "GET",
        headers,
      });
      if (!res.ok) {
        throw new Error(`Sync pull failed with status ${res.status}`);
      }
      const data = (await res.json()) as PullResponse;
      cachedPull = { at: Date.now(), data };
      savePersistedSnapshot(data);
      return data;
    } catch (err) {
      const fallback = cachedPull?.data ?? loadPersistedSnapshot();
      if (fallback) {
        return fallback;
      }
      throw err;
    } finally {
      inflightPull = null;
    }
  })();
  return inflightPull;
}

/**
 * Sincroniza mutações pendentes na fila offline quando a conexão é restabelecida.
 */
export async function flushOfflineQueue(): Promise<{ synced: number; remaining: number }> {
  const queue = getOfflineQueue();
  if (queue.length === 0) {
    return { synced: 0, remaining: 0 };
  }
  const token = getStoredAccessToken();
  if (!token) {
    return { synced: 0, remaining: queue.length };
  }
  try {
    const res = await window.fetch("/api/v1/sync/push", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        deviceId: getStoredDeviceId(),
        mutations: queue,
      }),
    });
    if (!res.ok) {
      return { synced: 0, remaining: queue.length };
    }
    setOfflineQueue([]);
    cachedPull = null;
    return { synced: queue.length, remaining: 0 };
  } catch {
    return { synced: 0, remaining: queue.length };
  }
}

if (typeof window !== "undefined") {
  window.addEventListener("online", () => {
    void flushOfflineQueue();
  });
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
export async function uploadRecordPhoto(recordId: string, file: File): Promise<string> {
  const token = getStoredAccessToken();
  const authHeader = token ? `Bearer ${token}` : null;
  const arrayBuffer = await file.arrayBuffer();
  const sha256 = await computeSha256Hex(arrayBuffer);
  const attachmentId = crypto.randomUUID();
  const contentType = file.type || "image/jpeg";

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (authHeader) {
    headers["Authorization"] = authHeader;
  }

  const pushRes = await window.fetch("/api/v1/sync/push", {
    method: "POST",
    headers,
    body: JSON.stringify({
      deviceId: getStoredDeviceId(),
      mutations: [
        {
          mutationId: crypto.randomUUID(),
          entity: "attachment",
          op: "UPSERT",
          entityId: attachmentId,
          clientTimestamp: new Date().toISOString(),
          data: {
            id: attachmentId,
            ownerType: "RECORD",
            ownerId: recordId,
            kind: "PHOTO",
            contentType,
            sizeBytes: file.size,
            sha256,
          },
        },
      ],
    }),
  });

  if (!pushRes.ok) {
    throw new Error("Não foi possível registrar o anexo da foto.");
  }

  const pushData = (await pushRes.json()) as PushResponse;
  const firstResult = pushData.results?.[0];
  if (firstResult?.status === "REJECTED") {
    throw new Error(firstResult.error?.message ?? "Foto recusada pela validação da API.");
  }

  const uploadHeaders: Record<string, string> = {
    "Content-Type": contentType,
  };
  if (authHeader) {
    uploadHeaders["Authorization"] = authHeader;
  }

  const binRes = await window.fetch(`/api/v1/files/${attachmentId}/content`, {
    method: "PUT",
    headers: uploadHeaders,
    body: arrayBuffer,
  });

  if (!binRes.ok) {
    throw new Error("Não foi possível enviar o arquivo de imagem.");
  }

  cachedPull = null;
  return attachmentId;
}

/**
 * Busca os detalhes completos de um registro de manejo (incluindo anexos e pedidos de correção)
 * a partir do espelho de sincronização do agricultor.
 */
export async function fetchMobileRecordDetail(recordId: string): Promise<MobileRecordDetail | null> {
  const token = getStoredAccessToken();
  const pull = await fetchSyncSnapshot(token ? `Bearer ${token}` : null);
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
  const token = getStoredAccessToken();
  const authHeader = token ? `Bearer ${token}` : null;
  const nowIso = new Date().toISOString();
  const dataPayload: Record<string, unknown> = {
    ...payload,
    id: propertyId,
  };

  const mutation: QueuedMutation = {
    mutationId: crypto.randomUUID(),
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

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (authHeader) {
    headers["Authorization"] = authHeader;
  }

  const pushRes = await window.fetch("/api/v1/sync/push", {
    method: "POST",
    headers,
    body: JSON.stringify({
      deviceId: getStoredDeviceId(),
      mutations: [mutation],
    }),
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
  authHeader: string | null,
): Promise<Response> {
  const nowIso = new Date().toISOString();
  const recordPayload: Record<string, unknown> = {
    ...bodyData,
    id: entityId,
    clientCreatedAt: (bodyData.clientCreatedAt as string | undefined) ?? nowIso,
  };

  if (recordPayload.type === "HARVEST" && !recordPayload.plantingId) {
    const pull = await fetchSyncSnapshot(authHeader);
    const plantings = (pull.changes?.records ?? []).filter(
      (r) => r.type === "PLANTING" && r.propertyId === recordPayload.propertyId,
    );
    if (plantings[0]?.id) {
      recordPayload.plantingId = plantings[0].id;
    }
  }

  const mutation: QueuedMutation = {
    mutationId: crypto.randomUUID(),
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

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (authHeader) {
    headers["Authorization"] = authHeader;
  }

  try {
    const pushRes = await window.fetch("/api/v1/sync/push", {
      method: "POST",
      headers,
      body: JSON.stringify({
        deviceId: getStoredDeviceId(),
        mutations: [mutation],
      }),
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
 * /sync/pull e /sync/push para o perfil FARMER.
 */
async function mobileSyncFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const req = input instanceof Request ? input : new Request(input, init);
  const url = new URL(req.url, window.location.origin);
  const path = url.pathname.replace(/^\/api\/v1/, "");
  const method = req.method.toUpperCase();
  const authHeader =
    req.headers.get("Authorization") ??
    (getStoredAccessToken() ? `Bearer ${getStoredAccessToken()}` : null);

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
      path === "/catalog/farm-inputs"
    ) {
      try {
        const pull = await fetchSyncSnapshot(authHeader);
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
                      | Record<string, unknown>
                      | undefined)
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
      } catch {
        // Se falhar, cai para a chamada normal
      }
    }
  }

  // Criação de manejo via /sync/push
  if (method === "POST" && path === "/records") {
    const bodyData = (await req.clone().json()) as Record<string, unknown>;
    const entityId = (typeof bodyData.id === "string" && bodyData.id) || crypto.randomUUID();
    return submitRecordMutation(entityId, bodyData, authHeader);
  }

  // Edição de manejo existente via /sync/push (atende pedidos de correção e completa campos)
  const recordPutMatch = path.match(/^\/records\/([^/]+)$/);
  if (method === "PUT" && recordPutMatch) {
    const entityId = recordPutMatch[1]!;
    const bodyData = (await req.clone().json()) as Record<string, unknown>;
    return submitRecordMutation(entityId, bodyData, authHeader);
  }

  // Marcar notificações como lidas via /sync/push
  if (method === "POST" && path === "/notifications/read-all") {
    const pull = await fetchSyncSnapshot(authHeader);
    const unread = (pull.changes?.notifications ?? []).filter((n) => !n.readAt && n.id);
    if (unread.length > 0) {
      const nowIso = new Date().toISOString();
      const headers: Record<string, string> = { "Content-Type": "application/json" };
      if (authHeader) {
        headers["Authorization"] = authHeader;
      }
      await window.fetch("/api/v1/sync/push", {
        method: "POST",
        headers,
        body: JSON.stringify({
          deviceId: getStoredDeviceId(),
          mutations: unread.map((n) => ({
            mutationId: crypto.randomUUID(),
            entity: "notification",
            op: "UPSERT",
            entityId: n.id,
            clientTimestamp: nowIso,
            data: { readAt: nowIso },
          })),
        }),
      });
      cachedPull = null;
    }
    return new Response(null, { status: 204 });
  }

  return window.fetch(req);
}

const authMiddleware: Middleware = {
  async onRequest({ request }) {
    const token = getStoredAccessToken();
    if (token) {
      request.headers.set("Authorization", `Bearer ${token}`);
    }
    return request;
  },

  async onResponse({ response, request }) {
    if (response.status !== 401) {
      return response;
    }

    const url = new URL(request.url);
    if (url.pathname.includes("/auth/")) {
      return response;
    }

    const refreshToken = getStoredRefreshToken();
    if (!refreshToken) {
      clearSession();
      onUnauthorizedCallback?.();
      return response;
    }

    if (!refreshingPromise) {
      refreshingPromise = (async () => {
        try {
          const deviceId = getStoredDeviceId();
          const refreshRes = await window.fetch("/api/v1/auth/refresh", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken, deviceId }),
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
          clearSession();
          onUnauthorizedCallback?.();
          return null;
        } finally {
          refreshingPromise = null;
        }
      })();
    }

    const session = await refreshingPromise;
    if (!session?.accessToken) {
      return response;
    }

    const newHeaders = new Headers(request.headers);
    newHeaders.set("Authorization", `Bearer ${session.accessToken}`);
    return mobileSyncFetch(
      new Request(request.url, {
        method: request.method,
        headers: newHeaders,
        body: request.body,
      }),
    );
  },
};

export const api = createClient<paths>({
  baseUrl: "/api/v1",
  querySerializer: serializeSpringQuery,
  fetch: mobileSyncFetch,
});

api.use(authMiddleware);
