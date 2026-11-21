// Avisos rápidos (toasts) no canto da tela, usados para mostrar o erro de ações que falharam no painel.
import { useSyncExternalStore } from "react";
import { dismissToast, getToasts, subscribe } from "./toastStore";

export function Toaster() {
  const items = useSyncExternalStore(subscribe, getToasts);
  if (items.length === 0) return null;
  return (
    <div
      aria-live="assertive"
      style={{
        position: "fixed",
        right: 16,
        bottom: 16,
        left: 16,
        zIndex: 2000,
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-end",
        gap: 8,
        pointerEvents: "none",
      }}
    >
      {items.map((t) => (
        <div
          key={t.id}
          role={t.tone === "danger" ? "alert" : "status"}
          className={`af-alert af-alert--${t.tone}`}
          style={{
            maxWidth: 420,
            display: "flex",
            gap: 10,
            alignItems: "flex-start",
            boxShadow: "0 8px 24px rgba(0, 0, 0, 0.15)",
            pointerEvents: "auto",
          }}
        >
          <span style={{ flex: 1 }}>{t.message}</span>
          <button
            type="button"
            aria-label="Fechar aviso"
            onClick={() => dismissToast(t.id)}
            style={{ border: 0, background: "none", cursor: "pointer", color: "inherit" }}
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  );
}
