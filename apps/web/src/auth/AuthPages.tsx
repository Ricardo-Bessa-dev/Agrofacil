// Telas de autenticação do coordenador fiéis ao protótipo do Figma (54:1667, 3:127 e 3:169).
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from "react-router";
import { api, ApiError, unwrap } from "../api/client";
import { useAuth } from "./useAuth";
import { Logo } from "../components/Logo";
import loginIllustration from "../assets/figma/login-illustration.webp";
import cornerLeaves from "../assets/figma/corner-leaves.png";
import emailBadgedSvg from "../assets/figma/email-badged.svg";

/** Tela 1 de Login do Coordenador (/entrar), fiel ao nó 54:1667 do Figma. */
export function LoginPage() {
  const { user, loginStep1 } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnTo = searchParams.get("voltar") ?? "/";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to={returnTo} replace />;
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!email.trim() || !password) {
      setError("Preencha seu e-mail e sua senha.");
      return;
    }
    setSubmitting(true);
    try {
      const challenge = await loginStep1(email, password);
      navigate("/entrar/codigo", {
        state: {
          challengeId: challenge.challengeId,
          emailHint: challenge.emailHint ?? email,
          returnTo,
        },
      });
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.detail
          : "Não deu certo entrar. Confira seu e-mail e sua senha.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="af-auth-page" data-node-id="54:1667">
      <div className="af-auth-card">
        <div className="af-auth-body">
          <div className="af-auth-logo-wrap" data-node-id="140:6">
            <Logo subtitle="Coordenação" size="lg" />
          </div>

          {error && <div className="af-alert-banner">{error}</div>}

          <form onSubmit={handleSubmit} noValidate>
            <div className="af-field" data-node-id="54:1675">
              <label className="af-label" htmlFor="login-email">
                Login
              </label>
              <input
                id="login-email"
                type="email"
                className="af-input"
                placeholder="Insira seu e-mail institucional"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>

            <div className="af-field" data-node-id="54:1681">
              <label className="af-label" htmlFor="login-password">
                Senha
              </label>
              <input
                id="login-password"
                type="password"
                className="af-input"
                placeholder="Insira sua senha"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            <div style={{ marginBottom: 28 }}>
              <Link to="/esqueci-senha" className="af-link-italic" data-node-id="54:1674">
                Esqueceu a senha?
              </Link>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <button
                type="submit"
                className="af-btn-pill-primary"
                disabled={submitting}
                data-node-id="54:1668"
              >
                {submitting ? "Verificando..." : "Entrar"}
              </button>

              <Link to="/convite" className="af-btn-pill-secondary" data-node-id="54:1671">
                Aceitar convite
              </Link>
            </div>
          </form>
        </div>

        <img
          src={loginIllustration}
          alt="Ilustração rural AgroFácil"
          className="af-auth-illustration"
          data-node-id="54:1687"
        />
      </div>
    </div>
  );
}

/** Tela 2 de Login (Código 2FA de 6 dígitos + Reenvio com timer, fiel ao nó 3:169 do Figma). */
export function Verify2faPage() {
  const { user, verifyStep2, resendCode } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const state = (location.state ?? {}) as {
    challengeId?: string;
    emailHint?: string;
    returnTo?: string;
  };

  const [digits, setDigits] = useState<string[]>(["", "", "", "", "", ""]);
  const [cooldown, setCooldown] = useState(30);
  const [error, setError] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const inputsRef = useRef<Array<HTMLInputElement | null>>([]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  if (user) {
    return <Navigate to={state.returnTo ?? "/"} replace />;
  }

  if (!state.challengeId) {
    return <Navigate to="/entrar" replace />;
  }

  const submitCode = async (fullCode: string) => {
    if (fullCode.length !== 6) return;
    setError(null);
    setSubmitting(true);
    try {
      await verifyStep2(state.challengeId!, fullCode);
      navigate(state.returnTo ?? "/", { replace: true });
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.detail
          : "O código não confere ou expirou. Confira os números.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleDigitChange = (index: number, raw: string) => {
    const clean = raw.replace(/\D/g, "");
    if (!clean) {
      const next = [...digits];
      next[index] = "";
      setDigits(next);
      return;
    }
    const next = [...digits];
    for (let i = 0; i < clean.length && index + i < 6; i++) {
      next[index + i] = clean[i] ?? "";
    }
    setDigits(next);
    const nextFocusIndex = Math.min(5, index + clean.length);
    inputsRef.current[nextFocusIndex]?.focus();

    const joined = next.join("");
    if (joined.length === 6 && !next.includes("")) {
      void submitCode(joined);
    }
  };

  const handleResend = async () => {
    if (cooldown > 0 || !state.challengeId) return;
    setError(null);
    setInfoMessage(null);
    try {
      await resendCode(state.challengeId);
      setCooldown(60);
      setInfoMessage("Um novo código de 6 dígitos foi enviado para seu e-mail.");
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : "Não foi possível reenviar o código.");
    }
  };

  return (
    <div className="af-auth-page" data-node-id="3:169">
      <div className="af-auth-card">
        <div className="af-auth-body">
          <div className="af-auth-logo-wrap">
            <Logo subtitle="Coordenação" size="lg" />
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
            <img src={emailBadgedSvg} alt="E-mail enviado" style={{ width: 42, height: 42 }} />
            <div>
              <h1 className="af-auth-title" style={{ margin: 0 }}>
                Código enviado!
              </h1>
              <span style={{ fontSize: 13, color: "var(--af-text-muted)", fontWeight: 600 }}>
                Verificação em duas etapas (RF03)
              </span>
            </div>
          </div>

          <p className="af-auth-subtitle" style={{ textAlign: "justify" }}>
            Enviamos um código de 6 números para{" "}
            <strong style={{ color: "rgba(0,0,0,0.75)" }}>{state.emailHint}</strong>. Digite-o
            abaixo para entrar no painel:
          </p>

          {error && <div className="af-alert-banner">{error}</div>}
          {infoMessage && <div className="af-success-banner">{infoMessage}</div>}

          <div className="af-otp-grid">
            {digits.map((digit, idx) => (
              <input
                key={idx}
                ref={(el) => {
                  inputsRef.current[idx] = el;
                }}
                type="text"
                inputMode="numeric"
                maxLength={6}
                className="af-otp-input"
                value={digit}
                aria-label={`Dígito ${idx + 1} do código`}
                onChange={(e) => handleDigitChange(idx, e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Backspace" && !digits[idx] && idx > 0) {
                    inputsRef.current[idx - 1]?.focus();
                  }
                }}
              />
            ))}
          </div>

          <button
            type="button"
            className="af-btn-pill-primary"
            disabled={submitting || digits.join("").length !== 6}
            onClick={() => void submitCode(digits.join(""))}
          >
            {submitting ? "Validando..." : "Confirmar código"}
          </button>

          <div
            style={{
              marginTop: 28,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: 6,
            }}
          >
            <button
              type="button"
              className="af-link-italic"
              disabled={cooldown > 0}
              onClick={() => void handleResend()}
              style={{
                opacity: cooldown > 0 ? 0.5 : 1,
                cursor: cooldown > 0 ? "not-allowed" : "pointer",
              }}
            >
              Reenviar e-mail
            </button>
            {cooldown > 0 && (
              <span style={{ fontSize: 12, fontWeight: 600, color: "var(--af-text-muted)" }}>
                Aguarde {cooldown}s para reenviar novamente
              </span>
            )}
            <Link
              to="/entrar"
              style={{
                marginTop: 12,
                fontSize: 13,
                fontWeight: 600,
                color: "var(--af-earth-brown)",
              }}
            >
              ← Voltar para o login
            </Link>
          </div>
        </div>

        <img src={cornerLeaves} alt="" className="af-auth-corner-leaves" />
      </div>
    </div>
  );
}

/** Tela de Esqueci a Senha (/esqueci-senha), fiel aos nós 3:127 e 3:169 do Figma. */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => setCooldown((c) => Math.max(0, c - 1)), 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  const sendLink = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!email.trim()) {
      setError("Informe seu e-mail cadastrado.");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await unwrap(
        api.POST("/auth/password/forgot", {
          body: { email: email.trim() },
        }),
      );
      setSent(true);
      setCooldown(30);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : "Não foi possível enviar o e-mail.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="af-auth-page" data-node-id="3:127">
      <div className="af-auth-card">
        <div className="af-auth-body">
          <div className="af-auth-logo-wrap">
            <Logo subtitle="Coordenação" size="lg" />
          </div>

          {!sent ? (
            <form onSubmit={sendLink} noValidate>
              <h1 className="af-auth-title">Recuperação de senha</h1>
              <p className="af-auth-subtitle">
                Informe seu e-mail de coordenador para receber o link de redefinição de senha.
              </p>

              {error && <div className="af-alert-banner">{error}</div>}

              <div className="af-field">
                <label className="af-label" htmlFor="forgot-email">
                  E-mail
                </label>
                <input
                  id="forgot-email"
                  type="email"
                  className="af-input"
                  placeholder="coordenador@associacao.org.br"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>

              <button type="submit" className="af-btn-pill-primary" disabled={submitting}>
                {submitting ? "Enviando..." : "Enviar link de recuperação"}
              </button>

              <div style={{ marginTop: 20, textAlign: "center" }}>
                <Link to="/entrar" className="af-link-italic">
                  Voltar ao login
                </Link>
              </div>
            </form>
          ) : (
            <div data-node-id="45:831">
              <img
                src={emailBadgedSvg}
                alt="E-mail enviado"
                style={{ width: 46, height: 46, marginBottom: 12 }}
              />
              <h1 className="af-auth-title">E-mail enviado!</h1>
              <p className="af-auth-subtitle" style={{ textAlign: "justify" }}>
                Enviamos um link de recuperação da sua conta AgroFácil para:{" "}
                <strong style={{ color: "rgba(0,0,0,0.75)" }}>{email}</strong>
                <br />
                <br />
                Por favor, confira sua caixa de entrada e a pasta de spam.
              </p>

              <div
                style={{
                  marginTop: 32,
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  gap: 6,
                }}
              >
                <button
                  type="button"
                  className="af-link-italic"
                  disabled={cooldown > 0 || submitting}
                  onClick={() => void sendLink()}
                  style={{ opacity: cooldown > 0 ? 0.5 : 1 }}
                >
                  Reenviar e-mail
                </button>
                {cooldown > 0 && (
                  <span style={{ fontSize: 12, fontWeight: 600, color: "var(--af-text-muted)" }}>
                    Aguarde {cooldown}s para reenviar novamente
                  </span>
                )}
                <Link to="/entrar" style={{ marginTop: 18, fontWeight: 600 }}>
                  ← Voltar ao login
                </Link>
              </div>
            </div>
          )}
        </div>

        <img src={cornerLeaves} alt="" className="af-auth-corner-leaves" />
      </div>
    </div>
  );
}

/** Tela de Redefinição de Senha (/redefinir-senha?token=...). */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [token, setToken] = useState(searchParams.get("token") ?? "");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (newPassword.length < 10) {
      setError("A nova senha precisa ter pelo menos 10 caracteres.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("As senhas digitadas não conferem.");
      return;
    }
    setSubmitting(true);
    try {
      await unwrap(
        api.POST("/auth/password/reset", {
          body: { token: token.trim(), newPassword },
        }),
      );
      navigate("/entrar", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : "Não foi possível redefinir a senha.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="af-auth-page">
      <div className="af-auth-card">
        <div className="af-auth-body">
          <div className="af-auth-logo-wrap">
            <Logo subtitle="Coordenação" size="lg" />
          </div>

          <h1 className="af-auth-title">Criar nova senha</h1>
          <p className="af-auth-subtitle">Escolha uma senha forte com no mínimo 10 caracteres.</p>

          {error && <div className="af-alert-banner">{error}</div>}

          <form onSubmit={handleSubmit} noValidate>
            {!searchParams.get("token") && (
              <div className="af-field">
                <label className="af-label" htmlFor="reset-token">
                  Token de recuperação
                </label>
                <input
                  id="reset-token"
                  type="text"
                  className="af-input"
                  placeholder="Cole o token recebido por e-mail"
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                  required
                />
              </div>
            )}

            <div className="af-field">
              <label className="af-label" htmlFor="reset-password">
                Nova senha
              </label>
              <input
                id="reset-password"
                type="password"
                className="af-input"
                placeholder="Mínimo de 10 caracteres"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
              />
            </div>

            <div className="af-field">
              <label className="af-label" htmlFor="reset-confirm">
                Confirmar nova senha
              </label>
              <input
                id="reset-confirm"
                type="password"
                className="af-input"
                placeholder="Repita a nova senha"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
              />
            </div>

            <button type="submit" className="af-btn-pill-primary" disabled={submitting}>
              {submitting ? "Salvando..." : "Redefinir senha"}
            </button>
          </form>
        </div>

        <img src={cornerLeaves} alt="" className="af-auth-corner-leaves" />
      </div>
    </div>
  );
}

/** Tela de Aceite de Convite de Coordenador (/convite?token=...). */
export function AcceptInvitePage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [token, setToken] = useState(searchParams.get("token") ?? "");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!token.trim() || !name.trim() || password.length < 10) {
      setError("Preencha o token, seu nome e uma senha com pelo menos 10 caracteres.");
      return;
    }
    setSubmitting(true);
    try {
      await unwrap(
        api.POST("/auth/invite/accept", {
          body: { token: token.trim(), name: name.trim(), password },
        }),
      );
      navigate("/entrar", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : "Não foi possível aceitar o convite.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="af-auth-page" data-node-id="54:1634">
      <div className="af-auth-card">
        <div className="af-auth-body">
          <div className="af-auth-logo-wrap">
            <Logo subtitle="Coordenação" size="lg" />
          </div>

          <h1 className="af-auth-title">Aceitar convite</h1>
          <p className="af-auth-subtitle">
            Ative sua conta de coordenador informando seu nome e criando uma senha (10+ caracteres).
          </p>

          {error && <div className="af-alert-banner">{error}</div>}

          <form onSubmit={handleSubmit} noValidate>
            {!searchParams.get("token") && (
              <div className="af-field">
                <label className="af-label" htmlFor="invite-token">
                  Código / Token do convite
                </label>
                <input
                  id="invite-token"
                  type="text"
                  className="af-input"
                  placeholder="Cole o token do e-mail de convite"
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                  required
                />
              </div>
            )}

            <div className="af-field">
              <label className="af-label" htmlFor="invite-name">
                Seu nome completo
              </label>
              <input
                id="invite-name"
                type="text"
                className="af-input"
                placeholder="Ex.: Maria Oliveira"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>

            <div className="af-field">
              <label className="af-label" htmlFor="invite-password">
                Defina sua senha
              </label>
              <input
                id="invite-password"
                type="password"
                className="af-input"
                placeholder="Mínimo de 10 caracteres"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            <button type="submit" className="af-btn-pill-primary" disabled={submitting}>
              {submitting ? "Ativando..." : "Concluir cadastro"}
            </button>

            <div style={{ marginTop: 18, textAlign: "center" }}>
              <Link to="/entrar" className="af-link-italic">
                Já tenho conta · Entrar
              </Link>
            </div>
          </form>
        </div>

        <img src={loginIllustration} alt="" className="af-auth-illustration" />
      </div>
    </div>
  );
}
