// Tela Inicial / Painel do Coordenador (RF19, doc 3 §D.6) com cartões fiéis ao Figma (3:188).
import { Link } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { formatArea, formatDatePtBr, RECORD_TYPE_LABELS } from "@agrofacil/domain";
import { api, unwrap } from "../../api/client";
import { CertificationStatusBadge, RecordTypeAvatar } from "../../components/StatusBadge";
import propPlaceholder1 from "../../assets/figma/property-placeholder-1.png";
import propPlaceholder2 from "../../assets/figma/property-placeholder-2.png";
import iconMapPinSvg from "../../assets/figma/icon-map-pin.svg";
import iconFarmSvg from "../../assets/figma/icon-farm.svg";
import iconArrowRightSvg from "../../assets/figma/icon-arrow-right.svg";

export function DashboardPage() {
  const summaryQuery = useQuery({
    queryKey: ["dashboard-summary"],
    queryFn: () => unwrap(api.GET("/dashboard/summary")),
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
  });

  const propertiesQuery = useQuery({
    queryKey: ["properties", "dashboard-preview"],
    queryFn: () =>
      unwrap(
        api.GET("/properties", {
          params: {
            query: {
              filter: {},
              pageable: { page: 0, size: 4 },
            },
          },
        }),
      ),
  });

  const recordsQuery = useQuery({
    queryKey: ["records", "dashboard-preview"],
    queryFn: () =>
      unwrap(
        api.GET("/records", {
          params: {
            query: {
              filter: {},
              pageable: { page: 0, size: 6 },
            },
          },
        }),
      ),
  });

  const summary = summaryQuery.data;
  const properties = propertiesQuery.data?.content ?? [];
  const recentRecords = recordsQuery.data?.content ?? [];

  const certValid = summary?.certifications?.["VALID"] ?? 0;
  const certExpiring = summary?.certifications?.["EXPIRING"] ?? 0;
  const certExpired =
    (summary?.certifications?.["EXPIRED"] ?? 0) + (summary?.certifications?.["SUSPENDED"] ?? 0);

  return (
    <div>
      {/* Indicadores Principais Clicáveis (RF19) */}
      <div className="af-kpi-grid">
        <Link to="/agricultores?status=ACTIVE" className="af-kpi-card">
          <span className="af-kpi-value">{summary?.farmers?.active ?? 0}</span>
          <span className="af-kpi-label">Agricultores ativos</span>
        </Link>

        <Link to="/certificacoes?status=VALID" className="af-kpi-card">
          <span className="af-kpi-value">{certValid}</span>
          <span className="af-kpi-label">Certificações em dia</span>
        </Link>

        <Link to="/certificacoes?status=EXPIRING" className="af-kpi-card warning">
          <span className="af-kpi-value">{certExpiring}</span>
          <span className="af-kpi-label">Certificações vencendo</span>
        </Link>

        <Link to="/certificacoes?status=EXPIRED" className="af-kpi-card danger">
          <span className="af-kpi-value">{certExpired}</span>
          <span className="af-kpi-label">Vencidas ou suspensas</span>
        </Link>

        <Link to="/registros" className="af-kpi-card">
          <span className="af-kpi-value">{summary?.records?.last30Days ?? 0}</span>
          <span className="af-kpi-label">Registros (últimos 30 dias)</span>
        </Link>

        <Link to="/registros?completeness=INCOMPLETE" className="af-kpi-card warning">
          <span className="af-kpi-value">{summary?.records?.incomplete ?? 0}</span>
          <span className="af-kpi-label">Registros incompletos</span>
        </Link>

        <Link to="/registros?hasOpenCorrection=true" className="af-kpi-card warning">
          <span className="af-kpi-value">{summary?.records?.openCorrectionRequests ?? 0}</span>
          <span className="af-kpi-label">Pedidos de correção abertos</span>
        </Link>

        <Link to="/visitas?state=URGENT" className="af-kpi-card danger">
          <span className="af-kpi-value">
            {(summary?.visits?.urgent ?? 0) + (summary?.visits?.resultMissing ?? 0)}
          </span>
          <span className="af-kpi-label">Visitas urgentes / sem resultado</span>
        </Link>
      </div>

      {/* Seção Propriedades (Fiel ao nó 75:2834 do Figma) */}
      <section style={{ marginBottom: 32 }} data-node-id="45:790">
        <div className="af-section-header">
          <h2 className="af-section-title">Propriedades da associação</h2>
          <Link
            to="/propriedades"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 6,
              fontSize: 13,
              fontWeight: 600,
              color: "var(--af-green-primary)",
            }}
          >
            <span>Ver todas</span>
            <img src={iconArrowRightSvg} alt="" style={{ width: 17, height: 17 }} />
          </Link>
        </div>

        {properties.length === 0 ? (
          <div className="af-card" style={{ color: "var(--af-text-muted)", fontSize: 14 }}>
            Nenhuma propriedade cadastrada ainda.{" "}
            <Link to="/agricultores" style={{ fontWeight: 600 }}>
              Cadastre um agricultor e sua propriedade →
            </Link>
          </div>
        ) : (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(340px, 1fr))",
              gap: 16,
            }}
          >
            {properties.map((prop, index) => (
              <Link
                key={prop.id}
                to={`/propriedades/${prop.id}`}
                className="af-property-card"
                data-node-id="75:2742"
              >
                <img
                  src={index % 2 === 0 ? propPlaceholder1 : propPlaceholder2}
                  alt=""
                  className="af-property-thumb"
                />
                <div className="af-property-info">
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "flex-start",
                      gap: 8,
                    }}
                  >
                    <h3 className="af-property-name">{prop.name}</h3>
                    <img src={iconArrowRightSvg} alt="" style={{ width: 20, height: 20 }} />
                  </div>

                  <div className="af-property-meta">
                    <img src={iconMapPinSvg} alt="" style={{ width: 14, height: 14 }} />
                    <span>
                      {prop.address?.municipalityName
                        ? `${prop.address.municipalityName} - ${prop.address.uf ?? "BA"}`
                        : "Bahia"}
                    </span>
                  </div>

                  <div className="af-property-meta">
                    <img src={iconFarmSvg} alt="" style={{ width: 15, height: 15 }} />
                    <span>{formatArea(prop.totalArea)}</span>
                  </div>

                  <div
                    style={{
                      marginTop: 8,
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: 8,
                    }}
                  >
                    <span style={{ fontSize: 11, color: "var(--af-text-muted)", fontWeight: 600 }}>
                      {prop.farmerName}
                    </span>
                    <CertificationStatusBadge
                      status={prop.currentCertification?.status}
                      expiresOn={prop.currentCertification?.expiresOn}
                      daysToExpire={prop.currentCertification?.daysToExpire}
                      variant="figma"
                    />
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>

      {/* Seção Últimos Registros + Alertas de Atenção */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))",
          gap: 24,
        }}
      >
        {/* Últimos Registros (Fiel ao nó 45:794 do Figma) */}
        <section data-node-id="45:794">
          <div className="af-section-header">
            <h2 className="af-section-title">Últimos registros</h2>
            <Link
              to="/registros"
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 6,
                fontSize: 13,
                fontWeight: 600,
                color: "var(--af-green-primary)",
              }}
            >
              <span>Ver todos</span>
              <img src={iconArrowRightSvg} alt="" style={{ width: 17, height: 17 }} />
            </Link>
          </div>

          {recentRecords.length === 0 ? (
            <div className="af-card" style={{ color: "var(--af-text-muted)", fontSize: 14 }}>
              Nenhum registro de manejo cadastrado.{" "}
              <Link to="/registros/novo" style={{ fontWeight: 600 }}>
                Registrar manejo agora →
              </Link>
            </div>
          ) : (
            <div className="af-record-list">
              {recentRecords.map((rec) => (
                <Link key={rec.id} to={`/registros/${rec.id}`} className="af-record-row">
                  <div style={{ display: "flex", alignItems: "center", gap: 12, minWidth: 0 }}>
                    <RecordTypeAvatar type={rec.type} />
                    <div style={{ minWidth: 0 }}>
                      <div
                        style={{
                          fontWeight: 600,
                          fontSize: 14,
                          color: "var(--af-green-primary)",
                        }}
                      >
                        {RECORD_TYPE_LABELS[rec.type ?? ""] ?? rec.type}
                        {rec.subject ? ` · ${rec.subject}` : ""}
                      </div>
                      <div
                        style={{
                          fontWeight: 600,
                          fontSize: 12,
                          color: "var(--af-green-muted)",
                        }}
                      >
                        {rec.property?.name ?? "Propriedade"} · {rec.farmer?.name ?? "Agricultor"}
                      </div>
                    </div>
                  </div>

                  <div style={{ display: "flex", alignItems: "center", gap: 12, flexShrink: 0 }}>
                    {rec.completeness === "INCOMPLETE" && (
                      <span className="af-cert-badge warning">Falta completar</span>
                    )}
                    <span
                      style={{
                        fontWeight: 600,
                        fontSize: 12,
                        color: "var(--af-green-primary)",
                      }}
                    >
                      {formatDatePtBr(rec.performedOn)}
                    </span>
                    <span style={{ color: "var(--af-green-primary)", fontWeight: 700 }}>›</span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </section>

        {/* Precisam de Atenção & Próximos Vencimentos (doc 3 §D.6) */}
        <section>
          <div className="af-section-header">
            <h2 className="af-section-title">Precisam de atenção</h2>
          </div>

          <div className="af-card" style={{ marginBottom: 18 }}>
            <div style={{ display: "flex", flexDirection: "column", gap: 10, fontSize: 14 }}>
              <div
                style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}
              >
                <span>Agricultores sem registrar há 30 dias</span>
                <Link to="/agricultores" className="af-btn-soft">
                  {summary?.farmers?.withoutRecordsLast30Days ?? 0} ver
                </Link>
              </div>
              <div
                style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}
              >
                <span>Agricultores sem aparelho ativo</span>
                <Link to="/agricultores" className="af-btn-soft">
                  {summary?.farmers?.withoutActiveDevice ?? 0} ver
                </Link>
              </div>
              <div
                style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}
              >
                <span>Propriedades sem localização GPS</span>
                <Link to="/propriedades" className="af-btn-soft">
                  {summary?.properties?.withoutLocation ?? 0} ver
                </Link>
              </div>
              <div
                style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}
              >
                <span>Itens digitados como &ldquo;Outro&rdquo; sem catálogo</span>
                <Link to="/catalogo?aba=sem-catalogo" className="af-btn-soft">
                  {summary?.unmappedCatalogItems ?? 0} associar
                </Link>
              </div>
            </div>
          </div>

          <div className="af-section-header">
            <h2 className="af-section-title" style={{ fontSize: 17 }}>
              Certificações vencendo em breve
            </h2>
          </div>

          {(summary?.upcomingExpirations ?? []).length === 0 ? (
            <div className="af-card" style={{ color: "var(--af-text-muted)", fontSize: 14 }}>
              Nenhuma certificação próxima do vencimento nos próximos dias de alerta.
            </div>
          ) : (
            <div className="af-record-list">
              {(summary?.upcomingExpirations ?? []).map((item) => (
                <div key={item.certificationId} className="af-record-row">
                  <div>
                    <div
                      style={{ fontWeight: 600, color: "var(--af-green-primary)", fontSize: 14 }}
                    >
                      {item.propertyName}
                    </div>
                    <div style={{ fontSize: 12, color: "var(--af-text-muted)" }}>
                      {item.farmerName} · Vence em {formatDatePtBr(item.expiresOn)} (
                      {item.daysToExpire} dias)
                    </div>
                  </div>
                  <Link
                    to={`/certificacoes?propertyId=${item.propertyId}&renewFrom=${item.certificationId}`}
                    className="af-btn-soft"
                  >
                    Renovar
                  </Link>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
