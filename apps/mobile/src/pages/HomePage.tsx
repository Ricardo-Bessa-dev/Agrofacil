import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { formatArea, formatDatePtBr, RECORD_TYPE_FARMER_LABELS } from "@agrofacil/domain";
import { api } from "../api/client";
import { useAuth } from "../auth/useAuth";
import { RecordTypeIcon } from "../components/RecordTypeIcon";
import bellIcon from "../assets/figma/bell.svg";
import plusIcon from "../assets/figma/plus-circle.svg";
import sunIcon from "../assets/figma/sun.svg";

export function HomePage() {
  const { user } = useAuth();
  const firstName = user?.name ? user.name.split(" ")[0] : "Agricultor";

  // Busca notificações para o badge do sino
  const notifQuery = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: async () => {
      const { data } = await api.GET("/notifications", {
        params: { query: { pageable: { size: 10 } } },
      });
      const unread = (data?.content ?? []).filter((n) => !n.readAt).length;
      return unread;
    },
    staleTime: 60_000,
  });

  // Busca propriedades do agricultor
  const propertiesQuery = useQuery({
    queryKey: ["farmer-properties"],
    queryFn: async () => {
      const { data } = await api.GET("/properties", {
        params: { query: { filter: {}, pageable: { size: 10 } } },
      });
      return data?.content ?? [];
    },
    staleTime: 60_000,
  });

  // Busca últimos registros de manejo
  const recentRecordsQuery = useQuery({
    queryKey: ["recent-records"],
    queryFn: async () => {
      const { data } = await api.GET("/records", {
        params: {
          query: {
            filter: {},
            pageable: { size: 5, sort: ["performedOn,desc"] },
          },
        },
      });
      return data?.content ?? [];
    },
  });

  // Busca avisos recentes da coordenação
  const announcementsQuery = useQuery({
    queryKey: ["recent-announcements"],
    queryFn: async () => {
      const { data } = await api.GET("/announcements", {
        params: {
          query: {
            pageable: { size: 2, sort: ["createdAt,desc"] },
          },
        },
      });
      return data?.content ?? [];
    },
    staleTime: 60_000,
  });

  const properties = propertiesQuery.data ?? [];
  const primaryProperty = properties[0];
  const recentRecords = recentRecordsQuery.data ?? [];
  const announcements = announcementsQuery.data ?? [];
  const unreadNotifs = notifQuery.data ?? 0;

  return (
    <div>
      {/* Top Header com Ilustração dos Morros (Figma 3:188) */}
      <header className="mobile-header">
        <div className="mobile-header-top">
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
              <h1 className="mobile-header-greeting">Olá, {firstName}!</h1>
              <img src={sunIcon} alt="" width={18} height={18} />
            </div>
            <p className="mobile-header-sub">
              {user?.association?.name || "Caderno de Campo Digital"}
            </p>
          </div>

          <Link to="/notificacoes" className="mobile-header-action-btn" aria-label="Notificações">
            <img src={bellIcon} alt="" width={20} height={20} />
            {unreadNotifs > 0 && <span className="mobile-header-badge-count">{unreadNotifs}</span>}
          </Link>
        </div>

        {user?.farmer?.group?.name && (
          <div className="mobile-header-badge">
            <span>🌱</span>
            <span>Grupo SPG: {user.farmer.group.name}</span>
          </div>
        )}
      </header>

      <div style={{ padding: "16px" }}>
        {/* Banner de Ação Principal: Novo Manejo */}
        <Link
          to="/registros/novo"
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            backgroundColor: "var(--color-primary)",
            color: "#ffffff",
            padding: "16px 20px",
            borderRadius: "var(--radius-md)",
            textDecoration: "none",
            marginBottom: "16px",
            boxShadow: "var(--shadow-md)",
            transition: "transform 0.1s ease",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
            <img src={plusIcon} alt="" width={28} height={28} />
            <div>
              <div style={{ fontSize: "16px", fontWeight: 700 }}>Registrar Manejo</div>
              <div style={{ fontSize: "12px", opacity: 0.9 }}>
                Anote plantio, insumo, colheita ou venda
              </div>
            </div>
          </div>
          <span style={{ fontSize: "20px", fontWeight: 700 }}>→</span>
        </Link>

        <p>
          <Link to="/visitas">Consultar visitas do SPG</Link>
        </p>

        {/* Card Resumo da Propriedade */}
        <div className="mobile-card">
          <div className="mobile-card-header">
            <span className="mobile-card-title">Sua Propriedade</span>
            <Link
              to="/propriedades"
              style={{
                fontSize: "12px",
                color: "var(--color-primary)",
                textDecoration: "none",
                fontWeight: 600,
              }}
            >
              Ver detalhes →
            </Link>
          </div>

          {primaryProperty ? (
            <div>
              <div
                style={{
                  fontSize: "16px",
                  fontWeight: 600,
                  color: "var(--color-text)",
                  marginBottom: "4px",
                }}
              >
                {primaryProperty.name}
              </div>
              <div
                style={{
                  fontSize: "13px",
                  color: "var(--color-text-secondary)",
                  marginBottom: "12px",
                }}
              >
                📍{" "}
                {primaryProperty.address?.municipalityName
                  ? `${primaryProperty.address.municipalityName} - ${primaryProperty.address.uf ?? "BA"}`
                  : "Endereço cadastrado"}
              </div>

              <div
                style={{
                  display: "flex",
                  gap: "10px",
                  marginTop: "8px",
                  flexWrap: "wrap",
                }}
              >
                <div
                  style={{
                    backgroundColor: "var(--color-primary-surface)",
                    padding: "8px 12px",
                    borderRadius: "var(--radius-sm)",
                    flex: 1,
                    minWidth: "120px",
                  }}
                >
                  <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                    Área Total
                  </div>
                  <div
                    style={{
                      fontSize: "14px",
                      fontWeight: 700,
                      color: "var(--color-primary-dark)",
                    }}
                  >
                    {primaryProperty.totalArea
                      ? formatArea(primaryProperty.totalArea)
                      : "Área não informada"}
                  </div>
                </div>

                <div
                  style={{
                    backgroundColor: "#eef7ee",
                    padding: "8px 12px",
                    borderRadius: "var(--radius-sm)",
                    flex: 1,
                    minWidth: "120px",
                  }}
                >
                  <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                    Certificação
                  </div>
                  <div
                    style={{
                      fontSize: "13px",
                      fontWeight: 700,
                      color: "var(--color-primary)",
                      display: "flex",
                      alignItems: "center",
                      gap: "4px",
                    }}
                  >
                    <span>✓</span>
                    <span>Orgânico SPG</span>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div style={{ fontSize: "13px", color: "var(--color-text-secondary)" }}>
              {propertiesQuery.isLoading
                ? "Carregando dados da propriedade..."
                : "Nenhuma propriedade cadastrada no momento."}
            </div>
          )}
        </div>

        {/* Últimos Manejos Registrados (Figma 49:852) */}
        <div style={{ marginTop: "20px" }}>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: "10px",
            }}
          >
            <h2 style={{ fontSize: "16px", fontWeight: 700 }}>Últimos Manejos</h2>
            <Link
              to="/registros"
              style={{
                fontSize: "13px",
                color: "var(--color-primary)",
                textDecoration: "none",
                fontWeight: 600,
              }}
            >
              Ver todos →
            </Link>
          </div>

          {recentRecordsQuery.isLoading ? (
            <div className="mobile-card" style={{ textAlign: "center", padding: "20px" }}>
              Carregando registros...
            </div>
          ) : recentRecords.length === 0 ? (
            <div className="mobile-card" style={{ textAlign: "center", padding: "24px 16px" }}>
              <div style={{ fontSize: "32px", marginBottom: "8px" }}>📝</div>
              <div style={{ fontWeight: 600, marginBottom: "4px" }}>Nenhum manejo recente</div>
              <p
                style={{
                  fontSize: "13px",
                  color: "var(--color-text-muted)",
                  marginBottom: "12px",
                }}
              >
                Comece anotando suas atividades no campo para manter seu caderno em dia.
              </p>
              <Link to="/registros/novo" className="btn-secondary">
                + Novo Manejo
              </Link>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              {recentRecords.map((rec) => {
                const typeLabel = RECORD_TYPE_FARMER_LABELS[rec.type ?? ""] ?? rec.type ?? "Manejo";
                return (
                  <div
                    key={rec.id}
                    className="mobile-card"
                    style={{
                      marginBottom: "0",
                      padding: "12px 14px",
                      display: "flex",
                      alignItems: "center",
                      gap: "12px",
                    }}
                  >
                    <RecordTypeIcon type={rec.type ?? ""} size={42} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div
                        style={{
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "baseline",
                        }}
                      >
                        <span
                          style={{
                            fontSize: "14px",
                            fontWeight: 600,
                            color: "var(--color-text)",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                          }}
                        >
                          {typeLabel}
                        </span>
                        <span
                          style={{
                            fontSize: "11px",
                            color: "var(--color-text-muted)",
                            flexShrink: 0,
                          }}
                        >
                          {rec.performedOn ? formatDatePtBr(rec.performedOn) : ""}
                        </span>
                      </div>
                      <div
                        style={{
                          fontSize: "12px",
                          color: "var(--color-text-secondary)",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                          marginTop: "2px",
                        }}
                      >
                        {rec.subject ||
                          (rec.plot?.name ? `Talhão: ${rec.plot.name}` : "Manejo realizado")}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Avisos da Coordenação */}
        {announcements.length > 0 && (
          <div style={{ marginTop: "24px" }}>
            <h2
              style={{
                fontSize: "16px",
                fontWeight: 700,
                marginBottom: "10px",
              }}
            >
              Avisos da Coordenação
            </h2>

            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              {announcements.map((a) => (
                <div
                  key={a.id}
                  className="mobile-card"
                  style={{
                    marginBottom: 0,
                    borderLeft: "4px solid var(--color-primary)",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      marginBottom: "4px",
                    }}
                  >
                    <span
                      style={{
                        fontSize: "11px",
                        fontWeight: 700,
                        color: "var(--color-primary)",
                        textTransform: "uppercase",
                      }}
                    >
                      {a.type === "TIP" ? "💡 Dica Técnica" : "📢 Aviso"}
                    </span>
                    <span style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                      {a.publishedAt ? formatDatePtBr(a.publishedAt) : ""}
                    </span>
                  </div>
                  <div style={{ fontSize: "14px", fontWeight: 600, marginBottom: "4px" }}>
                    {a.title}
                  </div>
                  {a.body && (
                    <div
                      style={{
                        fontSize: "13px",
                        color: "var(--color-text-secondary)",
                        lineHeight: 1.4,
                      }}
                    >
                      {a.body}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
