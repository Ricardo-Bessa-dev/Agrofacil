import { createContext, useCallback, useEffect, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { Me } from "@agrofacil/api-types";
import { onlyDigits } from "@agrofacil/domain";
import {
  api,
  clearSession,
  deviceIdForActivation,
  rememberActivatedDevice,
  flushOfflineQueue,
  invalidateMobileSyncCache,
  invalidateStoredSession,
  getStoredAccessToken,
  getStoredDeviceId,
  getStoredUser,
  saveSession,
  setOnUnauthorized,
} from "../api/client";

export interface AuthContextValue {
  user: Me | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  activate: (cpf: string, code: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<Me | null>(() => getStoredUser());
  const [isLoading, setIsLoading] = useState<boolean>(() => Boolean(getStoredAccessToken()));

  const refreshProfile = useCallback(async () => {
    try {
      const { data, response } = await api.GET("/me");
      if (response.status === 401 || response.status === 403) {
        if (!getStoredAccessToken()) setUser(null);
        return;
      }
      if (!data || !getStoredAccessToken()) return;
      setUser(data);
      saveSession({ user: data });
    } catch {
      // Mantém o perfil salvo em caso de falha de rede.
    }
  }, []);

  useEffect(() => {
    setOnUnauthorized(() => {
      queryClient.clear();
      setUser(null);
    });

    if (getStoredAccessToken()) {
      void refreshProfile().finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
    return () => setOnUnauthorized(null);
  }, [queryClient, refreshProfile]);

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key && event.key !== "af_mobile_user") return;
      const stored = getStoredUser();
      if (stored?.id === user?.id) return;
      invalidateStoredSession();
      queryClient.clear();
      setUser(stored);
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [queryClient, user?.id]);

  useEffect(() => {
    if (!user?.id) return;
    const sync = async () => {
      invalidateMobileSyncCache();
      await flushOfflineQueue();
      await queryClient.invalidateQueries();
    };
    const onOnline = () => void sync();
    window.addEventListener("online", onOnline);
    if (navigator.onLine) onOnline();
    return () => window.removeEventListener("online", onOnline);
  }, [queryClient, user?.id]);

  const activate = useCallback(
    async (cpf: string, code: string) => {
      const cleanCpf = onlyDigits(cpf);
      const cleanCode = code.trim();
      const deviceId = await deviceIdForActivation(cleanCpf);
      const { data, error, response } = await api.POST("/auth/farmer/activate", {
        body: {
          cpf: cleanCpf,
          code: cleanCode,
          device: {
            id: deviceId,
            name:
              typeof navigator !== "undefined"
                ? navigator.userAgent.slice(0, 50)
                : "Celular do Agricultor",
            platform: "WEB",
            appVersion: "1.0.0",
          },
        },
      });

      if (error || !data || !response.ok) {
        const errObj = (typeof error === "object" && error !== null ? error : {}) as Record<
          string,
          unknown
        >;
        const detail =
          typeof errObj.detail === "string"
            ? errObj.detail
            : "Código de acesso ou CPF incorreto. Verifique os dados com a coordenação.";
        throw new Error(detail);
      }

      await rememberActivatedDevice(cleanCpf, deviceId);
      queryClient.clear();
      saveSession(data);
      if (data.user) {
        setUser(data.user);
      } else {
        await refreshProfile();
      }
    },
    [queryClient, refreshProfile],
  );

  const logout = useCallback(async () => {
    try {
      const deviceId = getStoredDeviceId();
      await api.POST("/auth/logout", {
        body: { deviceId },
      });
    } catch {
      // Ignora erro no logout para garantir saída local
    } finally {
      clearSession();
      queryClient.clear();
      setUser(null);
    }
  }, [queryClient]);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: Boolean(user),
        isLoading,
        activate,
        logout,
        refreshProfile,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
