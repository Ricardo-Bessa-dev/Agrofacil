import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import { formatDateTimePtBr } from "@agrofacil/domain";
import { api } from "../api/client";

export function NotificationsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const notifsQuery = useQuery({
    queryKey: ["notifications-list"],
    queryFn: async () => {
      const { data } = await api.GET("/notifications", {
        params: {
          query: {
            pageable: { size: 30, sort: ["createdAt,desc"] },
          },
        },
      });
      return data?.content ?? [];
    },
  });

  const markAllMutation = useMutation({
    mutationFn: async () => {
      await api.POST("/notifications/read-all");
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications-list"] });
      queryClient.invalidateQueries({ queryKey: ["notifications", "unread-count"] });
    },
  });

  const notifs = notifsQuery.data ?? [];
  const hasUnread = notifs.some((n) => !n.readAt);

  return (
    <div>
      {/* Top Bar */}
      <div className="page-top-bar">
        <button type="button" className="page-top-bar-back" onClick={() => navigate(-1)}>
          <span>←</span>
          <span>Voltar</span>
        </button>
        <h1 className="page-top-bar-title">Notificações</h1>
        {hasUnread ? (
          <button
            type="button"
            className="btn-subtle"
            style={{ fontSize: "12px", color: "var(--color-primary)", padding: "4px" }}
            onClick={() => markAllMutation.mutate()}
            disabled={markAllMutation.isPending}
          >
            Marcar lidas
          </button>
        ) : (
          <div style={{ width: "48px" }} />
        )}
      </div>

      <div style={{ padding: "16px 16px 24px 16px" }}>
        {notifsQuery.isLoading ? (
          <div className="empty-state">
            <div className="empty-state-title">Carregando notificações...</div>
          </div>
        ) : notifs.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">🔔</div>
            <div className="empty-state-title">Nenhuma notificação</div>
            <p className="empty-state-desc">
              Você receberá avisos da coordenação, confirmação de visitas de pares e atualizações do
              seu caderno aqui.
            </p>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            {notifs.map((n) => {
              const isUnread = !n.readAt;

              return (
                <div
                  key={n.id}
                  className="mobile-card"
                  style={{
                    marginBottom: 0,
                    padding: "14px",
                    backgroundColor: isUnread ? "var(--color-primary-surface)" : "var(--color-surface)",
                    borderColor: isUnread ? "var(--color-primary)" : "var(--color-border)",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "baseline",
                      marginBottom: "4px",
                    }}
                  >
                    <span style={{ fontSize: "14px", fontWeight: 700, color: "var(--color-text)" }}>
                      {n.title}
                    </span>
                    <span style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                      {n.createdAt ? formatDateTimePtBr(n.createdAt) : ""}
                    </span>
                  </div>

                  <p
                    style={{
                      fontSize: "13px",
                      color: "var(--color-text-secondary)",
                      lineHeight: 1.4,
                    }}
                  >
                    {n.body}
                  </p>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
