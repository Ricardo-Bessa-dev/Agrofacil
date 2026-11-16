import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router";
import {
  formatArea,
  PLOT_STATUS_LABELS,
  WORKER_RELATIONSHIP_LABELS,
} from "@agrofacil/domain";
import { api } from "../api/client";
import propertyImg1 from "../assets/figma/property-placeholder-1.png";
import statusOkIcon from "../assets/figma/status-ok.svg";

export function PropertyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const propertyQuery = useQuery({
    queryKey: ["property-detail", id],
    queryFn: async () => {
      if (!id) throw new Error("ID não informado");
      const { data, error } = await api.GET("/properties/{id}", {
        params: { path: { id } },
      });
      if (error || !data) throw new Error("Falha ao carregar detalhes da propriedade.");
      return data;
    },
    enabled: Boolean(id),
  });

  const plotsQuery = useQuery({
    queryKey: ["property-plots", id],
    queryFn: async () => {
      if (!id) return [];
      const { data } = await api.GET("/properties/{id}/plots", {
        params: { path: { id } },
      });
      return data ?? [];
    },
    enabled: Boolean(id),
  });

  const property = propertyQuery.data;
  const plots = plotsQuery.data ?? property?.plots ?? [];
  const workers = property?.workers ?? [];

  if (propertyQuery.isLoading) {
    return (
      <div>
        <div className="page-top-bar">
          <button type="button" className="page-top-bar-back" onClick={() => navigate(-1)}>
            <span>←</span>
            <span>Voltar</span>
          </button>
          <h1 className="page-top-bar-title">Propriedade</h1>
          <div style={{ width: "48px" }} />
        </div>
        <div className="empty-state">
          <div className="empty-state-title">Carregando detalhes...</div>
        </div>
      </div>
    );
  }

  if (!property) {
    return (
      <div>
        <div className="page-top-bar">
          <button type="button" className="page-top-bar-back" onClick={() => navigate(-1)}>
            <span>←</span>
            <span>Voltar</span>
          </button>
          <h1 className="page-top-bar-title">Não encontrada</h1>
          <div style={{ width: "48px" }} />
        </div>
        <div className="empty-state">
          <div className="empty-state-title">Propriedade não encontrada</div>
        </div>
      </div>
    );
  }

  return (
    <div style={{ paddingBottom: "32px" }}>
      {/* Top Bar */}
      <div className="page-top-bar">
        <button type="button" className="page-top-bar-back" onClick={() => navigate(-1)}>
          <span>←</span>
          <span>Voltar</span>
        </button>
        <h1
          className="page-top-bar-title"
          style={{
            maxWidth: "200px",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {property.name}
        </h1>
        <div style={{ width: "48px" }} />
      </div>

      {/* Capa */}
      <div style={{ height: "150px", width: "100%", position: "relative" }}>
        <img
          src={propertyImg1}
          alt={property.name}
          style={{ width: "100%", height: "100%", objectFit: "cover" }}
        />
        <div
          style={{
            position: "absolute",
            bottom: "10px",
            left: "12px",
            backgroundColor: "rgba(0, 0, 0, 0.7)",
            color: "#fff",
            padding: "4px 10px",
            borderRadius: "var(--radius-full)",
            fontSize: "11px",
            fontWeight: 600,
            display: "flex",
            alignItems: "center",
            gap: "5px",
          }}
        >
          <img src={statusOkIcon} alt="" width={14} height={14} />
          <span>Certificação Ativa</span>
        </div>
      </div>

      <div style={{ padding: "16px" }}>
        {/* Card de Informações Gerais */}
        <div className="mobile-card">
          <h2 style={{ fontSize: "17px", fontWeight: 700, marginBottom: "4px" }}>
            {property.name}
          </h2>
          <div style={{ fontSize: "13px", color: "var(--color-text-secondary)", marginBottom: "12px" }}>
            📍 {property.address?.municipalityName} - {property.address?.uf ?? "BA"}
            {property.address?.locality ? ` (${property.address.locality})` : ""}
          </div>

          {property.accessDirections && (
            <div
              style={{
                fontSize: "12px",
                color: "var(--color-text-secondary)",
                backgroundColor: "var(--color-bg)",
                padding: "8px 10px",
                borderRadius: "6px",
                marginBottom: "12px",
              }}
            >
              <strong>Como chegar:</strong> {property.accessDirections}
            </div>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" }}>
            <div style={{ backgroundColor: "var(--color-primary-surface)", padding: "10px", borderRadius: "8px" }}>
              <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Área Total</div>
              <div style={{ fontSize: "15px", fontWeight: 700, color: "var(--color-primary-dark)" }}>
                {property.totalArea ? formatArea(property.totalArea) : "Não informada"}
              </div>
            </div>

            <div style={{ backgroundColor: "var(--color-bg)", padding: "10px", borderRadius: "8px" }}>
              <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Total de Talhões</div>
              <div style={{ fontSize: "15px", fontWeight: 700 }}>
                {plots.length} talh{plots.length === 1 ? "ão" : "ões"}
              </div>
            </div>
          </div>
        </div>

        {/* Lista de Talhões */}
        <div style={{ marginTop: "16px" }}>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: "10px",
            }}
          >
            <h3 style={{ fontSize: "15px", fontWeight: 700 }}>Talhões Cadastrados</h3>
            <span style={{ fontSize: "12px", color: "var(--color-text-muted)" }}>
              {plots.length} no total
            </span>
          </div>

          {plots.length === 0 ? (
            <div className="mobile-card" style={{ textAlign: "center", padding: "16px" }}>
              <div style={{ fontSize: "13px", color: "var(--color-text-muted)" }}>
                Nenhum talhão cadastrado nesta propriedade.
              </div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              {plots.map((plot) => {
                const statusLabel =
                  PLOT_STATUS_LABELS[plot.status ?? ""] ?? plot.status ?? "Orgânico";
                const isOrganic = plot.status === "ORGANIC";

                return (
                  <div key={plot.id} className="mobile-card" style={{ marginBottom: 0, padding: "12px 14px" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                      <span style={{ fontSize: "14px", fontWeight: 600 }}>{plot.name}</span>
                      <span
                        className={`badge-status ${isOrganic ? "badge-complete" : "badge-incomplete"}`}
                      >
                        {statusLabel}
                      </span>
                    </div>

                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        marginTop: "6px",
                        fontSize: "12px",
                        color: "var(--color-text-secondary)",
                      }}
                    >
                      <span>Área do talhão:</span>
                      <span style={{ fontWeight: 600 }}>
                        {plot.area ? formatArea(plot.area) : "Não informada"}
                      </span>
                    </div>

                    {plot.notes && (
                      <div
                        style={{
                          fontSize: "11px",
                          color: "var(--color-text-muted)",
                          marginTop: "4px",
                          fontStyle: "italic",
                        }}
                      >
                        {plot.notes}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Trabalhadores / Mão de Obra */}
        {workers.length > 0 && (
          <div style={{ marginTop: "20px" }}>
            <h3 style={{ fontSize: "15px", fontWeight: 700, marginBottom: "10px" }}>
              Mão de Obra Familiar & Trabalhadores
            </h3>
            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              {workers.map((w) => (
                <div
                  key={w.id}
                  className="mobile-card"
                  style={{
                    marginBottom: 0,
                    padding: "10px 14px",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                  }}
                >
                  <span style={{ fontSize: "14px", fontWeight: 500 }}>{w.name}</span>
                  <span style={{ fontSize: "12px", color: "var(--color-text-muted)" }}>
                    {WORKER_RELATIONSHIP_LABELS[w.relationship ?? ""] ?? w.relationship}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Botão de Ação Rápida */}
        <div style={{ marginTop: "24px" }}>
          <Link to="/registros/novo" className="btn-primary">
            + Registrar Manejo Nesta Propriedade
          </Link>
        </div>
      </div>
    </div>
  );
}
