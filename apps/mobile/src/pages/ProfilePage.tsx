import { useState } from "react";
import { useNavigate } from "react-router";
import { maskCpf, onlyDigits } from "@agrofacil/domain";
import { useAuth } from "../auth/useAuth";

export function ProfilePage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const handleLogout = async () => {
    setIsLoggingOut(true);
    try {
      await logout();
      navigate("/login", { replace: true });
    } finally {
      setIsLoggingOut(false);
    }
  };

  const associationWhatsapp = user?.association?.whatsapp
    ? onlyDigits(user.association.whatsapp)
    : "";

  const initials = user?.name
    ? user.name
        .split(" ")
        .map((p) => p[0])
        .slice(0, 2)
        .join("")
        .toUpperCase()
    : "AG";

  return (
    <div>
      {/* Top Bar */}
      <div className="page-top-bar">
        <h1 className="page-top-bar-title">Meu Perfil</h1>
      </div>

      <div style={{ padding: "20px 16px 32px 16px" }}>
        {/* Header do Perfil */}
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            marginBottom: "24px",
          }}
        >
          <div
            style={{
              width: "72px",
              height: "72px",
              borderRadius: "50%",
              backgroundColor: "var(--color-primary)",
              color: "#ffffff",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: "24px",
              fontWeight: 700,
              boxShadow: "var(--shadow-md)",
              marginBottom: "12px",
            }}
          >
            {initials}
          </div>

          <h2 style={{ fontSize: "18px", fontWeight: 700, color: "var(--color-text)", textAlign: "center" }}>
            {user?.name || "Agricultor Familiar"}
          </h2>
          <span
            style={{
              fontSize: "12px",
              color: "var(--color-primary-dark)",
              backgroundColor: "var(--color-primary-light)",
              padding: "3px 10px",
              borderRadius: "var(--radius-full)",
              fontWeight: 600,
              marginTop: "4px",
            }}
          >
            Agricultor Familiar · SPG
          </span>
        </div>

        {/* Informações Cadastrais */}
        <div className="mobile-card">
          <h3
            style={{
              fontSize: "14px",
              fontWeight: 600,
              color: "var(--color-text)",
              marginBottom: "12px",
            }}
          >
            Dados do Agricultor
          </h3>

          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "13px" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>CPF</span>
              <span style={{ fontWeight: 600 }}>{user?.cpf ? maskCpf(user.cpf) : "Não informado"}</span>
            </div>

            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "13px" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>CAF</span>
              <span style={{ fontWeight: 600 }}>
                {user?.farmer?.cafNumber || "Não cadastrado"}
              </span>
            </div>

            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "13px" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>Grupo SPG</span>
              <span style={{ fontWeight: 600 }}>
                {user?.farmer?.group?.name || "Grupo não vinculado"}
              </span>
            </div>

            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "13px" }}>
              <span style={{ color: "var(--color-text-secondary)" }}>Associação</span>
              <span style={{ fontWeight: 600 }}>
                {user?.association?.name || "Associação Local"}
              </span>
            </div>
          </div>
        </div>

        {/* Suporte da Coordenação / WhatsApp */}
        <div className="mobile-card">
          <h3 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "8px" }}>
            Suporte da Associação
          </h3>
          <p
            style={{
              fontSize: "13px",
              color: "var(--color-text-secondary)",
              lineHeight: 1.4,
              marginBottom: "14px",
            }}
          >
            Dúvidas sobre o preenchimento do caderno, visitas de pares ou certificação orgânica? Fale
            diretamente com a coordenação.
          </p>

          {associationWhatsapp ? (
            <a
              href={`https://wa.me/55${associationWhatsapp}`}
              target="_blank"
              rel="noreferrer"
              className="btn-primary"
              style={{
                textDecoration: "none",
                backgroundColor: "#25D366",
                boxShadow: "0 2px 6px rgba(37, 211, 102, 0.3)",
              }}
            >
              <span>💬</span>
              <span>Falar no WhatsApp com o Coordenador</span>
            </a>
          ) : (
            <div
              style={{
                fontSize: "12px",
                color: "var(--color-text-muted)",
                backgroundColor: "var(--color-bg)",
                padding: "10px",
                borderRadius: "6px",
                textAlign: "center",
              }}
            >
              Telefone da coordenação disponível presencialmente na sede.
            </div>
          )}
        </div>

        {/* Informações do App & Desconectar */}
        <div style={{ marginTop: "24px" }}>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => setShowLogoutConfirm(true)}
            style={{
              color: "var(--color-error)",
              borderColor: "var(--color-error)",
            }}
          >
            Desconectar deste Celular
          </button>

          <p
            style={{
              textAlign: "center",
              fontSize: "11px",
              color: "var(--color-text-muted)",
              marginTop: "16px",
            }}
          >
            Agrofácil Mobile Web v1.0.0 · Sistema Participativo de Garantia
          </p>
        </div>
      </div>

      {/* Confirmação de Logout */}
      {showLogoutConfirm && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="logout-title"
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: "rgba(0, 0, 0, 0.5)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "20px",
            zIndex: 200,
          }}
        >
          <div
            style={{
              backgroundColor: "var(--color-surface)",
              borderRadius: "var(--radius-md)",
              padding: "20px",
              maxWidth: "340px",
              width: "100%",
              boxShadow: "var(--shadow-lg)",
            }}
          >
            <h3 id="logout-title" style={{ fontSize: "16px", fontWeight: 700, marginBottom: "8px" }}>
              Desconectar da conta?
            </h3>
            <p
              style={{
                fontSize: "13px",
                color: "var(--color-text-secondary)",
                lineHeight: 1.4,
                marginBottom: "20px",
              }}
            >
              Para entrar novamente neste aparelho, você precisará informar seu CPF e o código de acesso.
            </p>

            <div style={{ display: "flex", gap: "10px" }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setShowLogoutConfirm(false)}
                style={{ flex: 1 }}
                disabled={isLoggingOut}
              >
                Cancelar
              </button>
              <button
                type="button"
                className="btn-primary"
                onClick={handleLogout}
                disabled={isLoggingOut}
                style={{
                  flex: 1,
                  backgroundColor: "var(--color-error)",
                }}
              >
                {isLoggingOut ? "Saindo..." : "Sim, Sair"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
