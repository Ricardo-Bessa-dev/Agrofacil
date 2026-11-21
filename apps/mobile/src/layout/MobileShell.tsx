// Moldura do app do agricultor: banner de sincronização/recusas, aceite dos termos (LGPD) e menu inferior.
import { useCallback, useEffect, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import {
  api,
  dismissRejectedChanges,
  flushOfflineQueue,
  getOfflineQueue,
  getRejectedChanges,
  OFFLINE_QUEUE_EVENT,
  type RejectedChange,
} from "../api/client";
import { countPendingPhotos } from "../api/photoQueue";
import { useAuth } from "../auth/useAuth";
import navHome from "../assets/figma/nav-home.svg";
import navHomeActive from "../assets/figma/nav-home-active.svg";
import navProperties from "../assets/figma/nav-properties.svg";
import navPropertiesActive from "../assets/figma/nav-properties-active.svg";
import navRecords from "../assets/figma/nav-records.svg";
import navRecordsActive from "../assets/figma/nav-records-active.svg";
import navProfile from "../assets/figma/nav-profile.svg";

export function MobileShell() {
  const location = useLocation();
  const queryClient = useQueryClient();
  const { user, refreshProfile } = useAuth();

  const [isOnline, setIsOnline] = useState<boolean>(() =>
    typeof navigator !== "undefined" ? navigator.onLine : true,
  );
  const [pendingCount, setPendingCount] = useState<number>(() => getOfflineQueue().length);
  const [pendingPhotos, setPendingPhotos] = useState(0);
  const [rejected, setRejected] = useState<RejectedChange[]>(() => getRejectedChanges());
  const [showRejected, setShowRejected] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [acceptingTerms, setAcceptingTerms] = useState(false);
  const [termsError, setTermsError] = useState<string | null>(null);

  useEffect(() => {
    const updateStatus = () => {
      setIsOnline(navigator.onLine);
      setPendingCount(getOfflineQueue().length);
      setRejected(getRejectedChanges());
      void countPendingPhotos()
        .then(setPendingPhotos)
        .catch(() => setPendingPhotos(0));
    };

    window.addEventListener("online", updateStatus);
    window.addEventListener("offline", updateStatus);
    window.addEventListener(OFFLINE_QUEUE_EVENT, updateStatus);
    return () => {
      window.removeEventListener("online", updateStatus);
      window.removeEventListener("offline", updateStatus);
      window.removeEventListener(OFFLINE_QUEUE_EVENT, updateStatus);
    };
  }, []);

  const handleManualSync = useCallback(async () => {
    setSyncing(true);
    try {
      await flushOfflineQueue();
      setPendingCount(getOfflineQueue().length);
      await queryClient.invalidateQueries({ queryKey: ["records"] });
      await queryClient.invalidateQueries({ queryKey: ["recent-records"] });
    } finally {
      setSyncing(false);
    }
  }, [queryClient]);

  // Ao abrir o app com internet, envia o que ficou pendente de outra vez (manejos e fotos).
  useEffect(() => {
    if (navigator.onLine) void handleManualSync();
  }, [handleManualSync]);

  const totalPending = pendingCount + pendingPhotos;

  const needsTermsConsent = Boolean(
    user?.currentTermsVersion && user.termsAcceptedVersion !== user.currentTermsVersion,
  );

  const handleAcceptTerms = async () => {
    if (!user?.currentTermsVersion) return;
    setTermsError(null);
    setAcceptingTerms(true);
    try {
      const { error, response } = await api.POST("/me/terms/accept", {
        body: { version: user.currentTermsVersion },
      });
      if (error || !response.ok) {
        throw new Error("Não foi possível registrar o aceite dos termos.");
      }
      await refreshProfile();
    } catch (err) {
      setTermsError(err instanceof Error ? err.message : "Erro ao aceitar os termos.");
    } finally {
      setAcceptingTerms(false);
    }
  };

  const isHomeActive = location.pathname === "/";
  const isPropertiesActive = location.pathname.startsWith("/propriedades");
  const isRecordsActive = location.pathname.startsWith("/registros");
  const isProfileActive = location.pathname.startsWith("/perfil");

  return (
    <div className="mobile-app-container">
      {/* Banner Offline / Sincronização Pendente (Item 5 - ADR-02) */}
      {rejected.length > 0 && (
        <div
          role="alert"
          style={{
            backgroundColor: "#991b1b",
            color: "#ffffff",
            padding: "8px 14px",
            fontSize: "12px",
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              gap: 8,
              alignItems: "center",
            }}
          >
            <span>⚠ {rejected.length} alteração(ões) não foram aceitas pelo sistema.</span>
            <button
              type="button"
              onClick={() => setShowRejected((v) => !v)}
              style={bannerButtonStyle("#991b1b")}
            >
              {showRejected ? "Fechar" : "Ver"}
            </button>
          </div>
          {showRejected && (
            <>
              <ul style={{ margin: "8px 0", paddingLeft: 18 }}>
                {rejected.map((r) => (
                  <li key={r.mutationId}>
                    <strong>{REJECTED_ENTITY_LABELS[r.entity] ?? "Alteração"}:</strong> {r.message}
                  </li>
                ))}
              </ul>
              <p style={{ margin: "0 0 8px" }}>
                Confira os dados e registre de novo. Em caso de dúvida, fale com a coordenação.
              </p>
              <button
                type="button"
                onClick={() => {
                  dismissRejectedChanges();
                  setShowRejected(false);
                }}
                style={bannerButtonStyle("#991b1b")}
              >
                Entendi
              </button>
            </>
          )}
        </div>
      )}

      {(!isOnline || totalPending > 0) && (
        <div
          style={{
            backgroundColor: !isOnline ? "#78350f" : "#1e40af",
            color: "#ffffff",
            padding: "8px 14px",
            fontSize: "12px",
            fontWeight: 500,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "8px",
          }}
        >
          <span>
            {!isOnline
              ? `📡 Modo sem internet (${describePending(pendingCount, pendingPhotos)} no celular)`
              : `🔄 ${describePending(pendingCount, pendingPhotos)} aguardando sincronização`}
          </span>
          {isOnline && totalPending > 0 && (
            <button
              type="button"
              onClick={() => void handleManualSync()}
              disabled={syncing}
              style={{
                background: "#ffffff",
                color: "#1e40af",
                border: "none",
                borderRadius: "6px",
                padding: "4px 8px",
                fontSize: "11px",
                fontWeight: 700,
                cursor: "pointer",
              }}
            >
              {syncing ? "Enviando..." : "Sincronizar"}
            </button>
          )}
        </div>
      )}

      <main className="mobile-shell-content">
        <Outlet />
      </main>

      <nav className="bottom-nav" aria-label="Navegação inferior">
        <NavLink to="/" className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}>
          <span className="bottom-nav-icon">
            <img src={isHomeActive ? navHomeActive : navHome} alt="" width={22} height={22} />
          </span>
          <span>Início</span>
        </NavLink>

        <NavLink
          to="/propriedades"
          className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}
        >
          <span className="bottom-nav-icon">
            <img
              src={isPropertiesActive ? navPropertiesActive : navProperties}
              alt=""
              width={22}
              height={22}
            />
          </span>
          <span>Propriedades</span>
        </NavLink>

        <NavLink
          to="/registros"
          className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}
        >
          <span className="bottom-nav-icon">
            <img
              src={isRecordsActive ? navRecordsActive : navRecords}
              alt=""
              width={22}
              height={22}
            />
          </span>
          <span>Registros</span>
        </NavLink>

        <NavLink
          to="/perfil"
          className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}
        >
          <span className="bottom-nav-icon">
            <img
              src={navProfile}
              alt=""
              width={22}
              height={22}
              style={{
                filter: isProfileActive
                  ? "invert(36%) sepia(87%) saturate(471%) hue-rotate(86deg) brightness(91%) contrast(92%)"
                  : "none",
              }}
            />
          </span>
          <span>Perfil</span>
        </NavLink>
      </nav>

      {/* Modal de Aceite de Termos de Uso / LGPD (Item 4 do Backlog) */}
      {needsTermsConsent && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="lgpd-terms-title"
          style={{
            position: "fixed",
            inset: 0,
            backgroundColor: "rgba(0, 0, 0, 0.6)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "16px",
            zIndex: 300,
          }}
        >
          <div
            style={{
              backgroundColor: "#ffffff",
              borderRadius: "16px",
              maxWidth: "390px",
              width: "100%",
              padding: "22px",
              boxShadow: "0 12px 32px rgba(0, 0, 0, 0.25)",
            }}
          >
            <h2
              id="lgpd-terms-title"
              style={{
                fontSize: "17px",
                fontWeight: 700,
                color: "var(--color-primary-dark)",
                marginBottom: "8px",
              }}
            >
              Termo de Uso e Privacidade (LGPD)
            </h2>
            <p
              style={{
                fontSize: "13px",
                color: "var(--color-text-secondary)",
                lineHeight: 1.5,
                marginBottom: "12px",
              }}
            >
              Para utilizar o Caderno de Campo Digital Agrofácil (versão{" "}
              <strong>{user?.currentTermsVersion}</strong>), seus registros de manejo, áreas e
              documentos de conformidade orgânica são compartilhados com a coordenação e a Comissão
              de Avaliação da sua associação no âmbito do Sistema Participativo de Garantia (SPG).
            </p>
            {termsError && (
              <div
                style={{
                  backgroundColor: "var(--color-error-bg)",
                  color: "var(--color-error)",
                  padding: "8px 10px",
                  borderRadius: "6px",
                  fontSize: "12px",
                  marginBottom: "12px",
                }}
              >
                {termsError}
              </div>
            )}
            <button
              type="button"
              className="btn-primary"
              disabled={acceptingTerms}
              onClick={() => void handleAcceptTerms()}
            >
              {acceptingTerms ? "Confirmando..." : "Li e concordo com os Termos"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

const REJECTED_ENTITY_LABELS: Record<string, string> = {
  record: "Manejo",
  property: "Propriedade",
  attachment: "Foto",
  notification: "Aviso",
};

function bannerButtonStyle(color: string) {
  return {
    background: "#ffffff",
    color,
    border: "none",
    borderRadius: "6px",
    padding: "4px 8px",
    fontSize: "11px",
    fontWeight: 700,
    cursor: "pointer",
    flexShrink: 0,
  } as const;
}

/** Ex.: "2 manejo(s) e 1 foto(s)". */
function describePending(records: number, photos: number): string {
  const parts = [];
  if (records > 0 || photos === 0) parts.push(`${records} manejo(s)`);
  if (photos > 0) parts.push(`${photos} foto(s)`);
  return parts.join(" e ");
}
