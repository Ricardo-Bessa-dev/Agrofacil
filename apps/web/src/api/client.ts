// Cliente HTTP tipado com openapi-fetch, token em memória e refresh automático via cookie HttpOnly (doc 3 §D.3).
import createClient, { type Middleware } from "openapi-fetch";
import type { FieldErrorItem, Me, paths, SessionResponse } from "@agrofacil/api-types";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  readonly errors: FieldErrorItem[];
  readonly fieldErrors: FieldErrorItem[];

  constructor(status: number, body: unknown) {
    const obj = (typeof body === "object" && body !== null ? body : {}) as Record<string, unknown>;
    const errs = Array.isArray(obj.errors) ? (obj.errors as FieldErrorItem[]) : [];
    const baseDetail =
      typeof obj.detail === "string"
        ? obj.detail
        : typeof obj.message === "string"
          ? obj.message
          : "Não foi possível concluir a operação. Tente novamente.";
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
    this.fieldErrors = errs;
  }
}

export { ApiError as ApiProblemError };

/**
 * Normaliza telefone digitado pelo usuário (ex.: "(74) 99999-0000" ou "74999990000")
 * para o formato E.164 exigido pela API (\+\d{10,15}, ex.: "+5574999990000").
 */
export function normalizePhoneE164(raw?: string | null): string | undefined {
  if (!raw) return undefined;
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  const digits = trimmed.replace(/\D/g, "");
  if (!digits) return undefined;
  if (trimmed.startsWith("+")) {
    return `+${digits}`;
  }
  if (digits.length === 10 || digits.length === 11) {
    return `+55${digits}`;
  }
  return `+${digits}`;
}

let accessToken: string | null = null;
let refreshingPromise: Promise<SessionResponse | null> | null = null;
let onUnauthorizedCallback: (() => void) | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function setOnUnauthorized(cb: (() => void) | null): void {
  onUnauthorizedCallback = cb;
}

/**
 * Achata parâmetros de consulta aninhados (como filter e pageable gerados pelo springdoc)
 * para o formato plano esperado pelo Spring Boot (?page=0&size=20&search=...).
 */
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

  for (const [key, value] of Object.entries(params)) {
    appendValue(key, value);
  }
  return searchParams.toString();
}

/** Rotas de autenticação: um 401 nelas é resposta final, nunca motivo para renovar a sessão. */
const AUTH_PATH = "/api/v1/auth/";

/**
 * fetch do cliente tipado: em 401, renova a sessão (o token de acesso dura 15 min) e repete a
 * chamada uma vez com o token novo. Sem isso, a primeira ação depois de expirar falharia.
 */
async function fetchWithRefresh(request: Request): Promise<Response> {
  const retry = request.clone(); // o corpo só pode ser lido uma vez
  const response = await fetch(request);
  if (response.status !== 401 || new URL(request.url).pathname.startsWith(AUTH_PATH)) {
    return response;
  }
  const session = await refreshSession();
  if (!session?.accessToken) return response;
  retry.headers.set("Authorization", `Bearer ${session.accessToken}`);
  return fetch(retry);
}

/** Mesma origem da página (o proxy do Vite e o nginx repassam /api); localhost só nos testes em Node. */
const API_BASE_URL = new URL("/api/v1", globalThis.location?.origin ?? "http://localhost").href;

export const api = createClient<paths>({
  baseUrl: API_BASE_URL,
  credentials: "include",
  querySerializer: serializeSpringQuery,
  fetch: fetchWithRefresh,
});

async function doRefreshSession(): Promise<SessionResponse | null> {
  try {
    const response = await fetch("/api/v1/auth/refresh", {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "X-Requested-With": "agrofacil-web",
      },
      body: JSON.stringify({}),
    });
    if (!response.ok) {
      accessToken = null;
      return null;
    }
    const data = (await response.json()) as SessionResponse;
    accessToken = data.accessToken ?? null;
    return data;
  } catch {
    accessToken = null;
    return null;
  }
}

/** Renova a sessão usando o cookie HttpOnly af_refresh; chamadas concorrentes compartilham a mesma promessa. */
export function refreshSession(): Promise<SessionResponse | null> {
  refreshingPromise ??= doRefreshSession().finally(() => {
    refreshingPromise = null;
  });
  return refreshingPromise;
}

const authMiddleware: Middleware = {
  async onRequest({ request }) {
    if (accessToken && !request.headers.has("Authorization")) {
      request.headers.set("Authorization", `Bearer ${accessToken}`);
    }
    return request;
  },
};

api.use(authMiddleware);

/** Desempacota uma chamada do openapi-fetch, lançando ApiError em caso de erro HTTP. */
export async function unwrap<T>(
  call: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  const { data, error, response } = await call;
  if (error !== undefined || !response.ok) {
    // O fetchWithRefresh já tentou renovar: um 401 aqui significa sessão encerrada.
    if (response.status === 401) {
      onUnauthorizedCallback?.();
    }
    throw new ApiError(response.status, error);
  }
  return data as T;
}

/**
 * GET autenticado de um binário. Necessário porque <img src> e <a href> não enviam o token Bearer.
 * @param path caminho relativo a /api/v1, ex.: "/files/{id}/content"
 */
async function fetchBinary(path: string): Promise<Response> {
  const get = () =>
    fetch(`/api/v1${path}`, {
      method: "GET",
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      credentials: "include",
    });
  let response = await get();
  if (response.status === 401 && (await refreshSession())) {
    response = await get(); // refreshSession já gravou o token novo
  }
  if (!response.ok) {
    let body: unknown = null;
    try {
      body = await response.json();
    } catch {
      // ignora se não for JSON
    }
    throw new ApiError(response.status, body);
  }
  return response;
}

/** Baixa um binário autenticado (foto, áudio) para exibir na página com URL.createObjectURL. */
export async function fetchFileBlob(path: string): Promise<Blob> {
  return (await fetchBinary(path)).blob();
}

/** Faz download autenticado de binários (PDF, CSV, fotos, áudios) e aciona o download no navegador. */
export async function downloadFile(path: string, fallbackFilename: string): Promise<void> {
  const response = await fetchBinary(path);
  const disposition = response.headers.get("Content-Disposition");
  const filenameMatch = disposition?.match(/filename="?([^";]+)"?/i);
  const filename = filenameMatch?.[1] ?? fallbackFilename;
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

/** Busca o perfil atual (/me) usando o token em memória. */
export async function fetchMe(): Promise<Me> {
  return unwrap(api.GET("/me"));
}
