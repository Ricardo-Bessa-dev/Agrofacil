// Sessão do agricultor no aparelho: ativação, perfil (/me) e saída. Offline, mantém o perfil salvo.
import { createContext, useCallback, useEffect, useState, type ReactNode } from "react";
import type { Me } from "@agrofacil/api-types";
import { onlyDigits } from "@agrofacil/domain";
import {
  api,
  clearSession,
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
  const [user, setUser] = useState<Me | null>(() => getStoredUser());
  const [isLoading, setIsLoading] = useState<boolean>(() => Boolean(getStoredAccessToken()));

  const refreshProfile = useCallback(async () => {
    try {
      const { data, response } = await api.GET("/me");
      if (data) {
        setUser(data);
        saveSession({ user: data });
      } else if (response.status === 401 || response.status === 403) {
        // Sessão recusada mesmo após tentar renovar o token: volta para a ativação.
        clearSession();
        setUser(null);
      }
      // Outros erros (API fora do ar, 5xx): mantém o perfil salvo no aparelho.
    } catch {
      // Sem internet: o app é offline-first, então o agricultor continua com o perfil salvo.
    }
  }, []);

  useEffect(() => {
    setOnUnauthorized(() => {
      setUser(null);
    });

    const token = getStoredAccessToken();
    if (!token) {
      setIsLoading(false);
      return;
    }

    refreshProfile().finally(() => {
      setIsLoading(false);
    });
  }, [refreshProfile]);

  const activate = useCallback(
    async (cpf: string, code: string) => {
      const cleanCpf = onlyDigits(cpf);
      const cleanCode = code.trim();
      const deviceId = getStoredDeviceId();
      const isIos =
        typeof navigator !== "undefined" && /iPhone|iPad|iPod/i.test(navigator.userAgent);

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
            platform: isIos ? "IOS" : "ANDROID",
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

      saveSession(data);
      if (data.user) {
        setUser(data.user);
      } else {
        await refreshProfile();
      }
    },
    [refreshProfile],
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
      setUser(null);
    }
  }, []);

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
