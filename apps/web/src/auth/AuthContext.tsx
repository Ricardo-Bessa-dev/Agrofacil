// Contexto de autenticação do coordenador (login + 2FA + sessão por cookie HttpOnly, doc 3 §D.3).
import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { LoginChallengeResponse, Me } from "@agrofacil/api-types";
import {
  api,
  fetchMe,
  refreshSession,
  setAccessToken,
  setOnUnauthorized,
  unwrap,
} from "../api/client";

export type AuthContextValue = {
  user: Me | null;
  isLoading: boolean;
  loginStep1: (email: string, password: string) => Promise<LoginChallengeResponse>;
  verifyStep2: (challengeId: string, code: string) => Promise<Me>;
  resendCode: (challengeId: string) => Promise<void>;
  logout: () => Promise<void>;
  reloadMe: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const reloadMe = useCallback(async () => {
    const me = await fetchMe();
    setUser(me);
  }, []);

  useEffect(() => {
    setOnUnauthorized(() => {
      setAccessToken(null);
      setUser(null);
    });

    let mounted = true;
    (async () => {
      try {
        const session = await refreshSession();
        if (session?.user && mounted) {
          setUser(session.user);
        } else if (session?.accessToken && mounted) {
          const me = await fetchMe();
          if (mounted) setUser(me);
        }
      } catch {
        if (mounted) setUser(null);
      } finally {
        if (mounted) setIsLoading(false);
      }
    })();

    return () => {
      mounted = false;
      setOnUnauthorized(null);
    };
  }, []);

  const loginStep1 = useCallback(
    async (email: string, password: string): Promise<LoginChallengeResponse> => {
      return unwrap(
        api.POST("/auth/coordinator/login", {
          body: { email: email.trim(), password },
        }),
      );
    },
    [],
  );

  const verifyStep2 = useCallback(async (challengeId: string, code: string): Promise<Me> => {
    const session = await unwrap(
      api.POST("/auth/coordinator/verify", {
        body: { challengeId, code: code.trim() },
      }),
    );
    setAccessToken(session.accessToken ?? null);
    const me = session.user ?? (await fetchMe());
    setUser(me);
    return me;
  }, []);

  const resendCode = useCallback(async (challengeId: string): Promise<void> => {
    await unwrap(
      api.POST("/auth/coordinator/resend", {
        body: { challengeId },
      }),
    );
  }, []);

  const logout = useCallback(async () => {
    try {
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          "X-Requested-With": "agrofacil-web",
        },
        body: JSON.stringify({}),
      });
    } finally {
      setAccessToken(null);
      setUser(null);
    }
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      loginStep1,
      verifyStep2,
      resendCode,
      logout,
      reloadMe,
    }),
    [user, isLoading, loginStep1, verifyStep2, resendCode, logout, reloadMe],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}


