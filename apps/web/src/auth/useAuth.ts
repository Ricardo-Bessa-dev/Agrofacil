import { createContext, useContext } from "react";
import type { LoginChallengeResponse, Me } from "@agrofacil/api-types";

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

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth deve ser usado dentro de um AuthProvider.");
  }
  return ctx;
}
