import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router";
import {
  formatArea,
  PLOT_STATUS_LABELS,
  WORKER_RELATIONSHIP_LABELS,
  type AreaUnitCode,
} from "@agrofacil/domain";
import { api, upsertFarmerProperty } from "../api/client";
import propertyImg1 from "../assets/figma/property-placeholder-1.png";
import statusOkIcon from "../assets/figma/status-ok.svg";

export function PropertyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState(false);
  const [prodDesc, setProdDesc] = useState("");
  const [accessDir, setAccessDir] = useState("");
  const [areaVal, setAreaVal] = useState("");
  const [areaUnit, setAreaUnit] = useState<AreaUnitCode>("TAREFA");
  const [saving, setSaving] = useState(false);
  const [saveFeedback, setSaveFeedback] = useState<string | null>(null);

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

  const openEditForm = () => {
    if (!property) return;
    setProdDesc(property.productionDescription ?? "");
    setAccessDir(property.accessDirections ?? "");
    setAreaVal(property.totalArea?.value !== undefined ? String(property.totalArea.value) : "");
    setAreaUnit((property.totalArea?.unit as AreaUnitCode) ?? "TAREFA");
    setSaveFeedback(null);
    setEditing(true);
  };

  const handleSaveProperty = async (e: FormEvent) => {
    e.preventDefault();
    if (!property?.id || !property.name) return;
    setSaving(true);
    setSaveFeedback(null);
    try {
      await upsertFarmerProperty(property.id, {
        id: property.id,
        name: property.name,
        totalArea: areaVal ? { value: Number(areaVal), unit: areaUnit } : property.totalArea,
        productionDescription: prodDesc.trim() || undefined,
        accessDirections: accessDir.trim() || undefined,
        address: property.address,
        location: property.location,
      });
      await queryClient.invalidateQueries({ queryKey: ["property-detail", id] });
      await queryClient.invalidateQueries({ queryKey: ["farmer-properties"] });
      await queryClient.invalidateQueries({ queryKey: ["farmer-properties-list"] });
      setEditing(false);
      setSaveFeedback("Dados da propriedade atualizados com sucesso!");
    } catch (err) {
      setSaveFeedback(err instanceof Error ? err.message : "Não foi possível atualizar os dados.");
    } finally {
      setSaving(false);
    }
  };

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
        {saveFeedback && (
          <div
            style={{
              backgroundColor: "var(--color-primary-surface)",
              color: "var(--color-primary-dark)",
              border: "1px solid var(--color-primary)",
              borderRadius: "8px",
              padding: "10px 12px",
              fontSize: "13px",
              fontWeight: 600,
              marginBottom: "14px",
            }}
          >
            {saveFeedback}
          </div>
        )}

        {/* Card de Informações Gerais */}
        <div className="mobile-card">
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "flex-start",
              gap: "8px",
            }}
          >
            <div>
              <h2 style={{ fontSize: "17px", fontWeight: 700, marginBottom: "4px" }}>
                {property.name}
              </h2>
              <div
                style={{
                  fontSize: "13px",
                  color: "var(--color-text-secondary)",
                  marginBottom: "12px",
                }}
              >
                📍 {property.address?.municipalityName} - {property.address?.uf ?? "BA"}
                {property.address?.locality ? ` (${property.address.locality})` : ""}
              </div>
            </div>

            {!editing && (
              <button
                type="button"
                onClick={openEditForm}
                style={{
                  background: "var(--color-primary-surface)",
                  color: "var(--color-primary-dark)",
                  border: "none",
                  borderRadius: "8px",
                  padding: "6px 10px",
                  fontSize: "12px",
                  fontWeight: 600,
                  cursor: "pointer",
                  flexShrink: 0,
                }}
              >
                ✏️ Editar
              </button>
            )}
          </div>

          {property.productionDescription && !editing && (
            <div
              style={{
                fontSize: "12px",
                color: "var(--color-text-secondary)",
                marginBottom: "10px",
              }}
            >
              <strong>Produção / Observações:</strong> {property.productionDescription}
            </div>
          )}

          {property.accessDirections && !editing && (
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

          {editing ? (
            <form onSubmit={handleSaveProperty} style={{ marginTop: "8px" }}>
              <div className="form-group">
                <label className="form-label">Área Total da Propriedade</label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    type="number"
                    step="any"
                    value={areaVal}
                    onChange={(e) => setAreaVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <select
                    value={areaUnit}
                    onChange={(e) => setAreaUnit(e.target.value as AreaUnitCode)}
                    className="form-select"
                    style={{ flex: 1 }}
                  >
                    <option value="TAREFA">tarefas</option>
                    <option value="HECTARE">ha</option>
                    <option value="SQUARE_METER">m²</option>
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Descrição da Produção e Talhões</label>
                <input
                  type="text"
                  value={prodDesc}
                  onChange={(e) => setProdDesc(e.target.value)}
                  placeholder="Ex: Hortaliças orgânicas, mandioca e fruticultura"
                  className="form-input"
                />
              </div>

              <div className="form-group">
                <label className="form-label">Roteiro de Acesso (para visitas do SPG)</label>
                <textarea
                  rows={2}
                  value={accessDir}
                  onChange={(e) => setAccessDir(e.target.value)}
                  placeholder="Ex: Estrada vicinal após a ponte, portão verde à direita"
                  className="form-textarea"
                />
              </div>

              <div style={{ display: "flex", gap: "8px" }}>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setEditing(false)}
                  style={{ flex: 1 }}
                >
                  Cancelar
                </button>
                <button type="submit" className="btn-primary" disabled={saving} style={{ flex: 1 }}>
                  {saving ? "Salvando..." : "Salvar"}
                </button>
              </div>
            </form>
          ) : (
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" }}>
              <div
                style={{
                  backgroundColor: "var(--color-primary-surface)",
                  padding: "10px",
                  borderRadius: "8px",
                }}
              >
                <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Área Total</div>
                <div
                  style={{
                    fontSize: "15px",
                    fontWeight: 700,
                    color: "var(--color-primary-dark)",
                  }}
                >
                  {property.totalArea ? formatArea(property.totalArea) : "Não informada"}
                </div>
              </div>

              <div
                style={{
                  backgroundColor: "var(--color-bg)",
                  padding: "10px",
                  borderRadius: "8px",
                }}
              >
                <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                  Total de Talhões
                </div>
                <div style={{ fontSize: "15px", fontWeight: 700 }}>
                  {plots.length} talh{plots.length === 1 ? "ão" : "ões"}
                </div>
              </div>
            </div>
          )}
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
                  <div
                    key={plot.id}
                    className="mobile-card"
                    style={{ marginBottom: 0, padding: "12px 14px" }}
                  >
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "baseline",
                      }}
                    >
                      <span style={{ fontSize: "14px", fontWeight: 600 }}>{plot.name}</span>
                      <span
                        className={`badge-status ${
                          isOrganic ? "badge-complete" : "badge-incomplete"
                        }`}
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
          <Link to={`/registros/novo?propertyId=${property.id}`} className="btn-primary">
            + Registrar Manejo Nesta Propriedade
          </Link>
        </div>
      </div>
    </div>
  );
}
