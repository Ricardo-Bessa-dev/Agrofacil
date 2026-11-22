// Janela de agendar visita do SPG: propriedade, tipo, data e visitantes sugeridos (RN30–RN33).
import React, { useState } from "react";
import { Link } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { todayIso } from "@agrofacil/domain";
import { api, ApiProblemError, unwrap } from "../../api/client";
import { fetchAllPages } from "../../api/pagination";
import { buildParticipants, noVisitorsHelp, preselectedIds } from "./visitScheduling";

export function ScheduleVisitModal({
  defaultPropertyId,
  onClose,
}: {
  defaultPropertyId: string;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [propertyId, setPropertyId] = useState(defaultPropertyId);
  const [type, setType] = useState<"PEER" | "VERIFICATION">("PEER");
  const [plannedOn, setPlannedOn] = useState(todayIso());
  const [unannounced, setUnannounced] = useState(false);
  // null = ainda vale a sugestão; vira lista quando a coordenação marca ou desmarca alguém.
  const [chosenIds, setChosenIds] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const propertiesQuery = useQuery({
    queryKey: ["properties", "all-select"],
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/properties", {
          params: { query: { filter: {}, pageable: { ...pageable, sort: ["name,asc"] } } },
        }),
      ),
  });

  const suggestQuery = useQuery({
    queryKey: ["visits", "suggest", propertyId, type, plannedOn],
    enabled: Boolean(propertyId && plannedOn),
    queryFn: () =>
      unwrap(api.POST("/visits/suggest-visitors", { body: { propertyId, type, plannedOn } })),
  });

  const eligible = suggestQuery.data?.eligible ?? [];
  const excluded = suggestQuery.data?.excluded ?? [];
  const selectedVisitorIds = chosenIds ?? preselectedIds(eligible);
  const hasNoVisitors = suggestQuery.isSuccess && eligible.length === 0;

  // O grupo do agricultor só é preciso para explicar a lista vazia.
  const farmerId = (propertiesQuery.data ?? []).find((p) => p.id === propertyId)?.farmerId;
  const farmerQuery = useQuery({
    queryKey: ["farmers", farmerId],
    enabled: hasNoVisitors && Boolean(farmerId),
    queryFn: () => unwrap(api.GET("/farmers/{id}", { params: { path: { id: farmerId! } } })),
  });
  const help = noVisitorsHelp({
    type,
    farmer: farmerQuery.data,
    excludedCount: excluded.length,
  });

  const toggleVisitor = (id: string) => {
    setChosenIds(
      selectedVisitorIds.includes(id)
        ? selectedVisitorIds.filter((x) => x !== id)
        : [...selectedVisitorIds, id],
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!propertyId) {
      setError("Selecione a propriedade.");
      return;
    }
    const { participants, recorderId } = buildParticipants(selectedVisitorIds, eligible);
    if (participants.length === 0) {
      setError("Selecione pelo menos um visitante elegível (RN32).");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      await unwrap(
        api.POST("/visits", {
          body: {
            propertyId,
            type,
            unannounced: type === "VERIFICATION" ? unannounced : false,
            plannedOn,
            participants,
            recorderId,
          },
        }),
      );
      await queryClient.invalidateQueries({ queryKey: ["visits"] });
      onClose();
    } catch (err) {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao agendar visita.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="af-modal-backdrop" onClick={onClose}>
      <div className="af-modal" onClick={(e) => e.stopPropagation()}>
        <div className="af-modal-header">
          <h3 className="af-card-title">Agendar Visita do SPG (RN30–RN33)</h3>
          <button type="button" className="af-btn af-btn--ghost af-btn--sm" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="af-modal-body">
          {error && (
            <div className="af-alert af-alert--danger" style={{ marginBottom: 14 }}>
              {error}
            </div>
          )}
          <form className="af-form-grid" onSubmit={handleSubmit}>
            <div className="af-field">
              <label htmlFor="spg-propriedade-a-visitar" className="af-label">
                Propriedade a visitar *
              </label>
              <select
                id="spg-propriedade-a-visitar"
                className="af-select"
                value={propertyId}
                onChange={(e) => {
                  setPropertyId(e.target.value);
                  setChosenIds(null);
                }}
                required
              >
                <option value="">Selecione a propriedade</option>
                {(propertiesQuery.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.farmerName})
                  </option>
                ))}
              </select>
            </div>

            <div className="af-form-row">
              <div className="af-field">
                <label htmlFor="spg-tipo-de-visita" className="af-label">
                  Tipo de visita *
                </label>
                <select
                  id="spg-tipo-de-visita"
                  className="af-select"
                  value={type}
                  onChange={(e) => {
                    setType(e.target.value as "PEER" | "VERIFICATION");
                    setChosenIds(null);
                  }}
                >
                  <option value="PEER">Visita de Pares (mesmo grupo)</option>
                  <option value="VERIFICATION">Visita de Verificação (Comissão)</option>
                </select>
              </div>
              <div className="af-field">
                <label htmlFor="spg-data-planejada" className="af-label">
                  Data planejada *
                </label>
                <input
                  id="spg-data-planejada"
                  type="date"
                  className="af-input"
                  value={plannedOn}
                  onChange={(e) => {
                    setPlannedOn(e.target.value);
                    setChosenIds(null);
                  }}
                  required
                />
              </div>
            </div>

            {type === "VERIFICATION" && (
              <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
                <input
                  type="checkbox"
                  checked={unannounced}
                  onChange={(e) => setUnannounced(e.target.checked)}
                />
                <span style={{ fontSize: "0.88rem", fontWeight: 600 }}>
                  Visita sem aviso prévio ao agricultor visitado (RN31)
                </span>
              </label>
            )}

            {propertyId && (
              <div className="af-field">
                <span className="af-label">
                  Sugestão inteligente de visitantes (RN33 — proximidade e rodízio)
                </span>
                {!plannedOn ? (
                  <div className="af-alert af-alert--warning">Escolha a data da visita.</div>
                ) : suggestQuery.isError ? (
                  <div className="af-alert af-alert--danger" role="alert">
                    Não foi possível buscar os visitantes.{" "}
                    {suggestQuery.error instanceof ApiProblemError && suggestQuery.error.message}{" "}
                    <button
                      type="button"
                      className="af-btn af-btn--outline af-btn--sm"
                      onClick={() => void suggestQuery.refetch()}
                    >
                      Tentar novamente
                    </button>
                  </div>
                ) : suggestQuery.isPending || (hasNoVisitors && farmerQuery.isLoading) ? (
                  <div style={{ fontSize: "0.85rem", color: "var(--af-text-secondary)" }}>
                    Calculando visitantes elegíveis...
                  </div>
                ) : hasNoVisitors ? (
                  <div className="af-alert af-alert--warning">
                    {help.message}{" "}
                    {help.link && (
                      <Link to={help.link.to} style={{ fontWeight: 700 }}>
                        {help.link.label}
                      </Link>
                    )}
                  </div>
                ) : (
                  <div
                    style={{
                      display: "flex",
                      flexDirection: "column",
                      gap: 8,
                      maxHeight: 220,
                      overflowY: "auto",
                      padding: 10,
                      border: "1px solid var(--af-border)",
                      borderRadius: "var(--af-radius-md)",
                    }}
                  >
                    {eligible.map((el) => {
                      const pid = el.person?.id ?? "";
                      return (
                        <label
                          key={pid}
                          style={{
                            display: "flex",
                            alignItems: "flex-start",
                            gap: 10,
                            cursor: "pointer",
                            fontSize: "0.86rem",
                          }}
                        >
                          <input
                            type="checkbox"
                            checked={selectedVisitorIds.includes(pid)}
                            onChange={() => toggleVisitor(pid)}
                          />
                          <div>
                            <div style={{ fontWeight: 700 }}>{el.person?.name}</div>
                            <div style={{ fontSize: "0.76rem", color: "var(--af-text-secondary)" }}>
                              {(el.reasons ?? []).join(" • ")}
                            </div>
                          </div>
                        </label>
                      );
                    })}
                  </div>
                )}

                {excluded.length > 0 && (
                  <div style={{ fontSize: "0.78rem", color: "var(--af-text-muted)", marginTop: 6 }}>
                    <strong>Impedidos (RN32):</strong>{" "}
                    {excluded.map((ex) => `${ex.person?.name} (${ex.reason})`).join(", ")}
                  </div>
                )}
              </div>
            )}

            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <button type="button" className="af-btn af-btn--outline af-btn--sm" onClick={onClose}>
                Cancelar
              </button>
              <button type="submit" className="af-btn af-btn--primary af-btn--sm" disabled={saving}>
                {saving ? "Agendando..." : "Confirmar agendamento"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
