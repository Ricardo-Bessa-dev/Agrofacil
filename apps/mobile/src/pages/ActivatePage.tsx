import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router";
import { formatCpf, isValidCpf, onlyDigits } from "@agrofacil/domain";
import { useAuth } from "../auth/useAuth";
import loginIllustration from "../assets/figma/login-illustration.webp";
import logoMark from "../assets/figma/logo-mark.png";
import logoText from "../assets/figma/logo-text.png";

export function ActivatePage() {
  const navigate = useNavigate();
  const { activate } = useAuth();

  const [cpf, setCpf] = useState("");
  const [code, setCode] = useState("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleCpfChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = onlyDigits(e.target.value).slice(0, 11);
    setCpf(formatCpf(raw));
  };

  const handleCodeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/\D/g, "").slice(0, 6);
    setCode(raw);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    const cleanCpf = onlyDigits(cpf);
    if (!isValidCpf(cleanCpf)) {
      setErrorMsg("Por favor, digite um CPF válido.");
      return;
    }

    if (code.trim().length !== 6) {
      setErrorMsg("O código de acesso deve conter exatamente 6 dígitos.");
      return;
    }

    setIsSubmitting(true);
    try {
      await activate(cleanCpf, code);
      navigate("/", { replace: true });
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Erro ao ativar o aparelho.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="mobile-app-container"
      style={{
        backgroundColor: "#ffffff",
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        padding: "24px 20px calc(24px + var(--safe-bottom)) 20px",
      }}
    >
      <div>
        {/* Top Header / Logo */}
        <div style={{ textAlign: "center", marginTop: "16px", marginBottom: "20px" }}>
          <div
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              justifyContent: "center",
            }}
          >
            <img src={logoMark} alt="Agrofácil" height={36} />
            <img src={logoText} alt="Agrofácil" height={26} />
          </div>
        </div>

        {/* Hero Illustration */}
        <div
          style={{
            display: "flex",
            justifyContent: "center",
            marginBottom: "24px",
          }}
        >
          <img
            src={loginIllustration}
            alt="Agricultor no campo"
            style={{
              maxWidth: "260px",
              width: "100%",
              height: "auto",
              objectFit: "contain",
            }}
          />
        </div>

        {/* Title */}
        <div style={{ marginBottom: "24px" }}>
          <h1
            style={{
              fontSize: "22px",
              fontWeight: 700,
              color: "var(--color-text)",
              marginBottom: "6px",
            }}
          >
            Acesse seu Caderno
          </h1>
          <p
            style={{
              fontSize: "14px",
              color: "var(--color-text-secondary)",
              lineHeight: 1.4,
            }}
          >
            Informe seu CPF e o código de acesso de 6 dígitos fornecido pela coordenação da sua
            associação.
          </p>
        </div>

        {/* Error Alert */}
        {errorMsg && (
          <div
            role="alert"
            style={{
              backgroundColor: "var(--color-error-bg)",
              border: "1px solid var(--color-error)",
              borderRadius: "var(--radius-sm)",
              padding: "12px 14px",
              marginBottom: "16px",
              color: "var(--color-error)",
              fontSize: "13px",
              display: "flex",
              alignItems: "center",
              gap: "8px",
            }}
          >
            <span>⚠️</span>
            <span>{errorMsg}</span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} noValidate>
          <div className="form-group">
            <label htmlFor="cpf-input" className="form-label">
              Seu CPF
            </label>
            <input
              id="cpf-input"
              type="text"
              inputMode="numeric"
              placeholder="000.000.000-00"
              value={cpf}
              onChange={handleCpfChange}
              className="form-input"
              required
              autoComplete="username"
            />
          </div>

          <div className="form-group">
            <label htmlFor="code-input" className="form-label">
              Código de Acesso (6 dígitos)
            </label>
            <input
              id="code-input"
              type="text"
              inputMode="numeric"
              placeholder="000000"
              value={code}
              onChange={handleCodeChange}
              maxLength={6}
              className="form-input"
              style={{
                letterSpacing: "8px",
                textAlign: "center",
                fontSize: "20px",
                fontWeight: 600,
              }}
              required
              autoComplete="one-time-code"
            />
          </div>

          <button
            type="submit"
            className="btn-primary"
            disabled={isSubmitting}
            style={{ marginTop: "8px" }}
          >
            {isSubmitting ? "Verificando..." : "Entrar no Agrofácil"}
          </button>
        </form>
      </div>

      {/* Footer Info */}
      <div
        style={{
          marginTop: "32px",
          padding: "14px",
          backgroundColor: "var(--color-primary-surface)",
          borderRadius: "var(--radius-sm)",
          border: "1px dashed var(--color-primary)",
        }}
      >
        <p
          style={{
            fontSize: "12px",
            color: "var(--color-primary-dark)",
            lineHeight: 1.5,
            textAlign: "center",
          }}
        >
          <strong>Não possui seu código de 6 dígitos?</strong>
          <br />O código é gerado pelo coordenador da sua associação para a sua segurança. Entre em
          contato com a sua coordenação para recebê-lo.
        </p>
      </div>
    </div>
  );
}
