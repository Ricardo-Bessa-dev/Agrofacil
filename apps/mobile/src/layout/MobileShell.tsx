// Moldura do app do agricultor: banner de sincronização/recusas, aceite dos termos (LGPD) e menu inferior.
import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import {
  api,
  discardPendingPhoto,
  discardRejectedMutation,
  flushOfflineQueue,
  getOfflineQueue,
  getPendingPhotos,
  OFFLINE_QUEUE_EVENT,
  type QueuedMutation,
} from "../api/client";
import type { PendingPhoto } from "../api/photoQueue";
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
  const [queue, setQueue] = useState<QueuedMutation[]>(getOfflineQueue);
  const [photos, setPhotos] = useState<PendingPhoto[]>([]);
  const [syncFeedback, setSyncFeedback] = useState<string | null>(null);
  const [showRejected, setShowRejected] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [acceptingTerms, setAcceptingTerms] = useState(false);
  const [termsError, setTermsError] = useState<string | null>(null);

  useEffect(() => {
    const updateStatus = () => {
      setIsOnline(navigator.onLine);
      setQueue(getOfflineQueue());
      void getPendingPhotos().then(setPhotos);
    };

    updateStatus();
    window.addEventListener("online", updateStatus);
    window.addEventListener("offline", updateStatus);
    window.addEventListener(OFFLINE_QUEUE_EVENT, updateStatus);
    return () => {
      window.removeEventListener("online", updateStatus);
      window.removeEventListener("offline", updateStatus);
      window.removeEventListener(OFFLINE_QUEUE_EVENT, updateStatus);
    };
  }, []);

  const handleManualSync = async () => {
    setSyncing(true);
    setSyncFeedback(null);
    try {
      const { remaining } = await flushOfflineQueue();
      const remainingPhotos = await getPendingPhotos();
      await queryClient.invalidateQueries();
      setSyncFeedback(
        remaining > 0 || remainingPhotos.length > 0
          ? "Ainda há itens pendentes. Confira os avisos e tente de novo mais tarde."
          : "Sincronização concluída.",
      );
    } finally {
      setSyncing(false);
    }
  };

  // Recusados esperam a pessoa corrigir ou descartar; os outros só esperam internet.
  const rejected = queue.filter((m) => m.rejection);
  const rejectedPhotos = photos.filter((p) => p.error);
  const rejectedCount = rejected.length + rejectedPhotos.length;
  const pendingCount = queue.length - rejected.length;
  const pendingPhotos = photos.length - rejectedPhotos.length;
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
      {rejectedCount > 0 && (
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
            <span>⚠ {rejectedCount} alteração(ões) não foram aceitas pelo sistema.</span>
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
                {rejected.map((item) => {
                  const correctionPath = CORRECTION_PATHS[item.entity]?.(item.entityId);
                  return (
                    <li key={item.mutationId} style={{ marginBottom: 8 }}>
                      <strong>{ENTITY_LABELS[item.entity]}:</strong> {item.rejection}{" "}
                      {correctionPath && (
                        <Link to={correctionPath} style={{ color: "#ffffff", fontWeight: 700 }}>
                          Corrigir
                        </Link>
                      )}{" "}
                      <button
                        type="button"
                        onClick={() => discardRejectedMutation(item.mutationId)}
                        style={bannerButtonStyle("#991b1b")}
                      >
                        Descartar
                      </button>
                    </li>
                  );
                })}
                {rejectedPhotos.map((photo) => (
                  <li key={photo.attachmentId} style={{ marginBottom: 8 }}>
                    <strong>Foto:</strong> {photo.error}{" "}
                    <button
                      type="button"
                      onClick={() => void discardPendingPhoto(photo.attachmentId)}
                      style={bannerButtonStyle("#991b1b")}
                    >
                      Descartar
                    </button>
                  </li>
                ))}
              </ul>
              <p style={{ margin: 0 }}>Em caso de dúvida, fale com a coordenação.</p>
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

      {syncFeedback && (
        <p role="status" className="mobile-card">
          {syncFeedback}
        </p>
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

const ENTITY_LABELS: Record<QueuedMutation["entity"], string> = {
  record: "Manejo",
  property: "Propriedade",
  attachment: "Foto",
  notification: "Aviso",
  visit: "Visita",
};

/** Tela onde a pessoa corrige uma alteração recusada. */
const CORRECTION_PATHS: Partial<Record<QueuedMutation["entity"], (id: string) => string>> = {
  record: (id) => `/registros/${id}/editar`,
  property: (id) => `/propriedades/${id}`,
  visit: (id) => `/visitas/${id}`,
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

/** Ex.: "2 item(ns) e 1 foto(s)". */
function describePending(items: number, photos: number): string {
  const parts = [];
  if (items > 0 || photos === 0) parts.push(`${items} item(ns)`);
  if (photos > 0) parts.push(`${photos} foto(s)`);
  return parts.join(" e ");
}
