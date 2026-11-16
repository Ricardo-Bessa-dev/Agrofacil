// Cliente HTTP tipado com openapi-fetch para a aplicação mobile do agricultor.
// Os tokens são persistidos em localStorage para manter a sessão ativa por longa duração no celular.
import createClient, { type Middleware } from "openapi-fetch";
import type { FieldErrorItem, Me, paths, SessionResponse } from "@agrofacil/api-types";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  readonly errors: FieldErrorItem[];

  constructor(status: number, body: unknown) {
    const obj = (typeof body === "object" && body !== null ? body : {}) as Record<string, unknown>;
    const detail =
      typeof obj.detail === "string"
        ? obj.detail
        : typeof obj.message === "string"
          ? obj.message
          : "Não foi possível concluir a operação. Verifique sua conexão e tente novamente.";
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.code = typeof obj.code === "string" ? obj.code : "UNKNOWN_ERROR";
    this.detail = detail;
    this.errors = Array.isArray(obj.errors) ? (obj.errors as FieldErrorItem[]) : [];
  }
}

const STORAGE_KEYS = {
  ACCESS_TOKEN: "af_mobile_access_token",
  REFRESH_TOKEN: "af_mobile_refresh_token",
  USER: "af_mobile_user",
  DEVICE_ID: "af_mobile_device_id",
} as const;

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

  const qs = searchParams.toString();
  return qs ? `?${qs}` : "";
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

    // Se já é uma requisição de auth, não tenta renovar
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
          const refreshRes = await fetch("/api/v1/auth/refresh", {
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

    // Repete a requisição original com o novo token
    const newHeaders = new Headers(request.headers);
    newHeaders.set("Authorization", `Bearer ${session.accessToken}`);
    return fetch(request.url, {
      method: request.method,
      headers: newHeaders,
      body: request.body,
    });
  },
};

export const api = createClient<paths>({
  baseUrl: "/api/v1",
  querySerializer: serializeSpringQuery,
});

api.use(authMiddleware);
