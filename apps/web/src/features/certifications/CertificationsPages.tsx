// Telas de Certificações do painel do coordenador: listagem, emissão (RN18), renovação (RN21), suspensão, reativação e cancelamento (RN19).
import React, { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { addOneYearIso, formatDatePtBr, todayIso } from "@agrofacil/domain";
import { api, ApiProblemError, downloadFile, unwrap } from "../../api/client";
import { CertificationStatusBadge } from "../../components/StatusBadge";

export function CertificationsPage() {
  const [searchParams] = useSearchParams();
  const initialPropertyId = searchParams.get("propertyId") ?? "";

  const [status, setStatus] = useState<
    "" | "VALID" | "EXPIRING" | "EXPIRED" | "SUSPENDED" | "CANCELLED"
  >((searchParams.get("status") as "" | "VALID" | "EXPIRING" | "EXPIRED") ?? "");
  const [currentOnly, setCurrentOnly] = useState(true);
  const [page, setPage] = useState(0);
  const [showIssueModal, setShowIssueModal] = useState(Boolean(initialPropertyId));
  const [reasonModal, setReasonModal] = useState<{
    id: string;
    action: "suspend" | "cancel";
    propertyName?: string;
  } | null>(null);
  const [reasonText, setReasonText] = useState("");

  const queryClient = useQueryClient();

  const certsQuery = useQuery({
    queryKey: ["certifications", { status, currentOnly, initialPropertyId, page }],
    queryFn: () =>
      unwrap(
        api.GET("/certifications", {
          params: {
            query: {
              filter: {
                status: status || undefined,
                currentOnly: currentOnly || undefined,
                propertyId: initialPropertyId || undefined,
              },
              pageable: { page, size: 15, sort: ["expiresOn,asc"] },
            },
          },
        }),
      ),
  });

  const renewMutation = useMutation({
    mutationFn: (certId: string) => {
      const issuedOn = todayIso();
      return unwrap(
        api.POST("/certifications/{id}/renew", {
          params: { path: { id: certId } },
          body: {
            issuedOn,
            expiresOn: addOneYearIso(issuedOn),
          },
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["certifications"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });

  const reactivateMutation = useMutation({
    mutationFn: (certId: string) =>
      unwrap(
        api.POST("/certifications/{id}/reactivate", {
          params: { path: { id: certId } },
        }),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["certifications"] }),
  });

  const statusActionMutation = useMutation({
    mutationFn: async () => {
      if (!reasonModal) return;
      if (reasonModal.action === "suspend") {
        await unwrap(
          api.POST("/certifications/{id}/suspend", {
            params: { path: { id: reasonModal.id } },
            body: { reason: reasonText.trim() },
          }),
        );
      } else {
        await unwrap(
          api.POST("/certifications/{id}/cancel", {
            params: { path: { id: reasonModal.id } },
            body: { reason: reasonText.trim() },
          }),
        );
      }
    },
    onSuccess: () => {
      setReasonModal(null);
      setReasonText("");
      queryClient.invalidateQueries({ queryKey: ["certifications"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });

  const items = certsQuery.data?.content ?? [];
  const meta = certsQuery.data?.page;

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Certificações Orgânicas</h1>
          <p className="af-page-subtitle">
            Controle de validade dos certificados de conformidade orgânica, renovações e suspensões.
          </p>
        </div>
        <div className="af-topbar-actions">
          <button
            type="button"
            className="af-btn af-btn--outline af-btn--sm"
            onClick={() => downloadFile("/reports/certifications?format=csv", "certificacoes.csv")}
          >
            Exportar CSV
          </button>
          <button
            type="button"
            className="af-btn af-btn--primary af-btn--sm"
            onClick={() => setShowIssueModal(true)}
          >
            + Emitir certificação
          </button>
        </div>
      </div>

      <div className="af-card">
        <div className="af-filters-bar" style={{ marginBottom: 16 }}>
          <div className="af-field" style={{ minWidth: 220 }}>
            <label className="af-label">Situação (RN19)</label>
            <select
              className="af-select"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as typeof status);
                setPage(0);
              }}
            >
              <option value="">Todas as situações</option>
              <option value="VALID">Em dia</option>
              <option value="EXPIRING">Vencendo em breve</option>
              <option value="EXPIRED">Vencidas</option>
              <option value="SUSPENDED">Suspensas</option>
              <option value="CANCELLED">Canceladas</option>
            </select>
          </div>

          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: 8,
              paddingBottom: 10,
              cursor: "pointer",
              fontWeight: 600,
              fontSize: "0.86rem",
            }}
          >
            <input
              type="checkbox"
              checked={currentOnly}
              onChange={(e) => {
                setCurrentOnly(e.target.checked);
                setPage(0);
              }}
            />
            Exibir apenas a certificação vigente de cada propriedade
          </label>
        </div>

        {certsQuery.isLoading ? (
          <div className="af-empty-state">Carregando certificações...</div>
        ) : items.length === 0 ? (
          <div className="af-empty-state">Nenhuma certificação encontrada.</div>
        ) : (
          <div className="af-table-wrap">
            <table className="af-table">
              <thead>
                <tr>
                  <th>Propriedade / Agricultor</th>
                  <th>Nº Certificado</th>
                  <th>Emissão</th>
                  <th>Vencimento</th>
                  <th>Situação</th>
                  <th style={{ textAlign: "right" }}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {items.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <Link
                        to={`/propriedades/${c.property?.id ?? c.propertyId}`}
                        style={{ fontWeight: 700, color: "var(--af-green-darker)" }}
                      >
                        {c.property?.name ?? "Propriedade"}
                      </Link>
                      <div style={{ fontSize: "0.8rem", color: "var(--af-text-secondary)" }}>
                        {c.property?.farmerName ?? ""}
                        {c.scope ? ` • ${c.scope}` : ""}
                      </div>
                    </td>
                    <td style={{ fontWeight: 600 }}>{c.number ?? "S/N"}</td>
                    <td>{formatDatePtBr(c.issuedOn)}</td>
                    <td>{formatDatePtBr(c.expiresOn)}</td>
                    <td>
                      <CertificationStatusBadge status={c.status} daysToExpire={c.daysToExpire} />
                      {c.statusReason && (
                        <div style={{ fontSize: "0.75rem", color: "var(--af-text-muted)" }}>
                          Motivo: {c.statusReason}
                        </div>
                      )}
                    </td>
                    <td style={{ textAlign: "right" }}>
                      <div style={{ display: "inline-flex", gap: 6, flexWrap: "wrap" }}>
                        {c.id && c.isCurrent && c.status !== "CANCELLED" && (
                          <button
                            type="button"
                            className="af-btn af-btn--secondary af-btn--sm"
                            onClick={() => renewMutation.mutate(c.id!)}
                            title="Renovar por mais 1 ano (RN21)"
                          >
                            Renovar +1 ano
                          </button>
                        )}
                        {c.id &&
                          (c.status === "VALID" ||
                            c.status === "EXPIRING" ||
                            c.status === "EXPIRED") && (
                            <button
                              type="button"
                              className="af-btn af-btn--outline af-btn--sm"
                              onClick={() =>
                                setReasonModal({
                                  id: c.id!,
                                  action: "suspend",
                                  propertyName: c.property?.name,
                                })
                              }
                            >
                              Suspender
                            </button>
                          )}
                        {c.id && c.status === "SUSPENDED" && (
                          <button
                            type="button"
                            className="af-btn af-btn--outline af-btn--sm"
                            onClick={() => reactivateMutation.mutate(c.id!)}
                          >
                            Reativar
                          </button>
                        )}
                        {c.id && c.status !== "CANCELLED" && (
                          <button
                            type="button"
                            className="af-btn af-btn--ghost af-btn--sm"
                            onClick={() =>
                              setReasonModal({
                                id: c.id!,
                                action: "cancel",
                                propertyName: c.property?.name,
                              })
                            }
                          >
                            Cancelar
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {meta && (meta.totalPages ?? 1) > 1 && (
          <div className="af-pagination">
            <span>
              Página {(meta.number ?? 0) + 1} de {meta.totalPages}
            </span>
            <div style={{ display: "flex", gap: 8 }}>
              <button
                type="button"
                className="af-btn af-btn--outline af-btn--sm"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Anterior
              </button>
              <button
                type="button"
                className="af-btn af-btn--outline af-btn--sm"
                disabled={page + 1 >= (meta.totalPages ?? 1)}
                onClick={() => setPage((p) => p + 1)}
              >
                Próxima
              </button>
            </div>
          </div>
        )}
      </div>

      {showIssueModal && (
        <IssueCertificationModal
          defaultPropertyId={initialPropertyId}
          onClose={() => setShowIssueModal(false)}
        />
      )}

      {reasonModal && (
        <div className="af-modal-backdrop" onClick={() => setReasonModal(null)}>
          <div className="af-modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 440 }}>
            <div className="af-modal-header">
              <h3 className="af-card-title">
                {reasonModal.action === "suspend"
                  ? "Suspender Certificação"
                  : "Cancelar Certificação"}
              </h3>
              <button
                type="button"
                className="af-btn af-btn--ghost af-btn--sm"
                onClick={() => setReasonModal(null)}
              >
                ✕
              </button>
            </div>
            <div className="af-modal-body">
              <form
                className="af-form-grid"
                onSubmit={(e) => {
                  e.preventDefault();
                  if (reasonText.trim()) statusActionMutation.mutate();
                }}
              >
                <div className="af-field">
                  <label className="af-label">
                    Informe o motivo ({reasonModal.propertyName ?? "propriedade"}) *
                  </label>
                  <textarea
                    className="af-textarea"
                    value={reasonText}
                    onChange={(e) => setReasonText(e.target.value)}
                    required
                    placeholder="Justificativa deliberada pela Comissão de Avaliação..."
                  />
                </div>
                <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
                  <button
                    type="button"
                    className="af-btn af-btn--outline af-btn--sm"
                    onClick={() => setReasonModal(null)}
                  >
                    Voltar
                  </button>
                  <button type="submit" className="af-btn af-btn--danger af-btn--sm">
                    Confirmar
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function IssueCertificationModal({
  defaultPropertyId,
  onClose,
}: {
  defaultPropertyId?: string;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [propertyId, setPropertyId] = useState(defaultPropertyId ?? "");
  const [number, setNumber] = useState("");
  const [issuedOn, setIssuedOn] = useState(todayIso());
  const [expiresOn, setExpiresOn] = useState(addOneYearIso(todayIso()));
  const [scope, setScope] = useState("Produção Primária Vegetal");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const propertiesQuery = useQuery({
    queryKey: ["properties", "all-select"],
    queryFn: () =>
      unwrap(
        api.GET("/properties", {
          params: {
            query: {
              filter: {},
              pageable: { page: 0, size: 100, sort: ["name,asc"] },
            },
          },
        }),
      ),
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!propertyId) {
      setError("Selecione a propriedade.");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      await unwrap(
        api.POST("/certifications", {
          body: {
            propertyId,
            number: number.trim() || undefined,
            issuedOn,
            expiresOn,
            scope: scope.trim() || undefined,
            notes: notes.trim() || undefined,
          },
        }),
      );
      await queryClient.invalidateQueries({ queryKey: ["certifications"] });
      await queryClient.invalidateQueries({ queryKey: ["properties"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      onClose();
    } catch (err) {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao emitir certificação.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="af-modal-backdrop" onClick={onClose}>
      <div className="af-modal" onClick={(e) => e.stopPropagation()}>
        <div className="af-modal-header">
          <h3 className="af-card-title">Emitir Nova Certificação (RN18)</h3>
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
              <label className="af-label">Propriedade *</label>
              <select
                className="af-select"
                value={propertyId}
                onChange={(e) => setPropertyId(e.target.value)}
                required
              >
                <option value="">Selecione a propriedade</option>
                {(propertiesQuery.data?.content ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.farmerName})
                  </option>
                ))}
              </select>
            </div>

            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Nº do Certificado</label>
                <input
                  className="af-input"
                  value={number}
                  onChange={(e) => setNumber(e.target.value)}
                  placeholder="Ex.: OPAC-2026-0042"
                />
              </div>
              <div className="af-field">
                <label className="af-label">Escopo</label>
                <input
                  className="af-input"
                  value={scope}
                  onChange={(e) => setScope(e.target.value)}
                />
              </div>
            </div>

            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Data de emissão *</label>
                <input
                  type="date"
                  className="af-input"
                  value={issuedOn}
                  onChange={(e) => {
                    setIssuedOn(e.target.value);
                    setExpiresOn(addOneYearIso(e.target.value));
                  }}
                  required
                />
              </div>
              <div className="af-field">
                <label className="af-label">Validade (padrão +1 ano — RN18) *</label>
                <input
                  type="date"
                  className="af-input"
                  value={expiresOn}
                  onChange={(e) => setExpiresOn(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="af-field">
              <label className="af-label">Observações</label>
              <textarea
                className="af-textarea"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>

            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <button type="button" className="af-btn af-btn--outline af-btn--sm" onClick={onClose}>
                Cancelar
              </button>
              <button type="submit" className="af-btn af-btn--primary af-btn--sm" disabled={saving}>
                {saving ? "Emitindo..." : "Emitir certificação"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
