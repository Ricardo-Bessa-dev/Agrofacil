// Telas de Certificações do painel do coordenador: listagem, emissão e edição (RN18), renovação com aviso de visita (RN39), suspensão, reativação e cancelamento (RN19).
import React, { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { addOneYearIso, formatDatePtBr, todayIso } from "@agrofacil/domain";
import type { CertificationView, CropView } from "@agrofacil/api-types";
import { api, ApiProblemError, downloadFile, unwrap } from "../../api/client";
import { fetchAllPages } from "../../api/pagination";
import { CertificationStatusBadge } from "../../components/StatusBadge";
import { Pagination } from "../../components/Pagination";

export function CertificationsPage() {
  const [searchParams] = useSearchParams();
  const initialPropertyId = searchParams.get("propertyId") ?? "";

  const [status, setStatus] = useState<
    "" | "VALID" | "EXPIRING" | "EXPIRED" | "SUSPENDED" | "CANCELLED"
  >((searchParams.get("status") as "" | "VALID" | "EXPIRING" | "EXPIRED") ?? "");
  const [currentOnly, setCurrentOnly] = useState(true);
  const [page, setPage] = useState(0);
  const [formModal, setFormModal] = useState<{
    mode: CertificationFormMode;
    certification?: CertificationView;
  } | null>(initialPropertyId ? { mode: "create" } : null);
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

  const cropsQuery = useQuery({
    queryKey: ["catalog", "crops"],
    queryFn: () => unwrap(api.GET("/catalog/crops")),
  });
  const cropNameById = new Map((cropsQuery.data ?? []).map((crop) => [crop.id, crop.name]));
  const productNames = (c: CertificationView) =>
    (c.products ?? [])
      .map((p) => (p.cropId ? cropNameById.get(p.cropId) : undefined) ?? p.productName)
      .filter(Boolean)
      .join(", ");

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
            onClick={() => setFormModal({ mode: "create" })}
          >
            + Emitir certificação
          </button>
        </div>
      </div>

      <div className="af-card">
        <div className="af-filters-bar" style={{ marginBottom: 16 }}>
          <div className="af-field" style={{ minWidth: 220 }}>
            <label htmlFor="cert-situacao-rn19" className="af-label">
              Situação (RN19)
            </label>
            <select
              id="cert-situacao-rn19"
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
                      {productNames(c) && (
                        <div style={{ fontSize: "0.78rem", color: "var(--af-text-muted)" }}>
                          Produtos: {productNames(c)}
                        </div>
                      )}
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
                            onClick={() => setFormModal({ mode: "renew", certification: c })}
                            title="Abre a renovação para conferir datas, produtos e a visita do ciclo (RN39)"
                          >
                            Renovar
                          </button>
                        )}
                        {c.id && c.status !== "CANCELLED" && (
                          <button
                            type="button"
                            className="af-btn af-btn--outline af-btn--sm"
                            onClick={() => setFormModal({ mode: "edit", certification: c })}
                          >
                            Editar
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

        <Pagination meta={meta} onPageChange={setPage} itemsLabel="certificações" />
      </div>

      {formModal && (
        <CertificationFormModal
          mode={formModal.mode}
          certification={formModal.certification}
          defaultPropertyId={initialPropertyId}
          onClose={() => setFormModal(null)}
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
                  <label htmlFor="cert-informe-o-motivo" className="af-label">
                    Informe o motivo ({reasonModal.propertyName ?? "propriedade"}) *
                  </label>
                  <textarea
                    id="cert-informe-o-motivo"
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

type CertificationFormMode = "create" | "edit" | "renew";

type CertificationFormModalProps = {
  mode: CertificationFormMode;
  /** Certificação editada ou renovada (obrigatória nesses modos). */
  certification?: CertificationView;
  defaultPropertyId?: string;
  onClose: () => void;
};

const FORM_TITLES: Record<CertificationFormMode, string> = {
  create: "Emitir Nova Certificação (RN18)",
  edit: "Editar Certificação",
  renew: "Renovar Certificação",
};

const SUBMIT_LABELS: Record<CertificationFormMode, [idle: string, saving: string]> = {
  create: ["Emitir certificação", "Emitindo..."],
  edit: ["Salvar alterações", "Salvando..."],
  renew: ["Renovar certificação", "Renovando..."],
};

/** Formulário único para emitir, editar e renovar (a renovação cria uma nova com previousId). */
function CertificationFormModal({
  mode,
  certification,
  defaultPropertyId,
  onClose,
}: CertificationFormModalProps) {
  const queryClient = useQueryClient();
  const isCreate = mode === "create";
  // Na renovação, a nova emissão começa hoje; na edição, mantém as datas atuais.
  const initialIssuedOn = mode === "edit" ? (certification?.issuedOn ?? todayIso()) : todayIso();
  const initialProducts = certification?.products ?? [];

  const [propertyId, setPropertyId] = useState(
    certification?.propertyId ?? certification?.property?.id ?? defaultPropertyId ?? "",
  );
  const [number, setNumber] = useState(mode === "edit" ? (certification?.number ?? "") : "");
  const [issuedOn, setIssuedOn] = useState(initialIssuedOn);
  const [expiresOn, setExpiresOn] = useState(
    mode === "edit" ? (certification?.expiresOn ?? "") : addOneYearIso(initialIssuedOn),
  );
  const [scope, setScope] = useState(certification?.scope ?? DEFAULT_SCOPE);
  const [notes, setNotes] = useState(mode === "edit" ? (certification?.notes ?? "") : "");
  const [cropIds, setCropIds] = useState<string[]>(
    initialProducts.flatMap((p) => (p.cropId ? [p.cropId] : [])),
  );
  const [otherProducts, setOtherProducts] = useState<string[]>(
    initialProducts.flatMap((p) => (!p.cropId && p.productName ? [p.productName] : [])),
  );
  const [workerIds, setWorkerIds] = useState<string[]>(certification?.workerIds ?? []);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const propertiesQuery = useQuery({
    queryKey: ["properties", "all-select"],
    enabled: isCreate,
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/properties", {
          params: { query: { filter: {}, pageable: { ...pageable, sort: ["name,asc"] } } },
        }),
      ),
  });

  const cropsQuery = useQuery({
    queryKey: ["catalog", "crops"],
    queryFn: () => unwrap(api.GET("/catalog/crops")),
  });

  const workersQuery = useQuery({
    queryKey: ["properties", propertyId, "workers"],
    enabled: Boolean(propertyId),
    queryFn: () =>
      unwrap(api.GET("/properties/{id}/workers", { params: { path: { id: propertyId } } })),
  });

  // RN39: antes de renovar, avisa se falta a visita de verificação do ciclo (a API não bloqueia).
  const visitStatusQuery = useQuery({
    queryKey: ["properties", propertyId, "visit-status"],
    enabled: mode === "renew" && Boolean(propertyId),
    queryFn: () =>
      unwrap(api.GET("/properties/{id}/visit-status", { params: { path: { id: propertyId } } })),
  });
  const verification = visitStatusQuery.data?.verification;
  const missingVerification =
    mode === "renew" &&
    verification !== undefined &&
    (verification.done ?? 0) < (verification.required ?? 1);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!propertyId) {
      setError("Selecione a propriedade.");
      return;
    }
    // RN18: a certificação cobre ao menos um produto (cultura do catálogo ou nome livre).
    if (cropIds.length === 0 && otherProducts.length === 0) {
      setError("Informe ao menos um produto certificado.");
      return;
    }
    setError(null);
    setSaving(true);
    const body = {
      propertyId,
      number: number.trim() || undefined,
      issuedOn,
      expiresOn,
      scope: scope.trim() || undefined,
      products: [
        ...cropIds.map((cropId) => ({ cropId })),
        ...otherProducts.map((productName) => ({ productName })),
      ],
      workerIds,
      notes: notes.trim() || undefined,
    };
    try {
      if (mode === "edit" && certification?.id) {
        await unwrap(
          api.PUT("/certifications/{id}", {
            params: { path: { id: certification.id } },
            body: { ...body, version: certification.version },
          }),
        );
      } else if (mode === "renew" && certification?.id) {
        await unwrap(
          api.POST("/certifications/{id}/renew", {
            params: { path: { id: certification.id } },
            body,
          }),
        );
      } else {
        await unwrap(api.POST("/certifications", { body }));
      }
      await queryClient.invalidateQueries({ queryKey: ["certifications"] });
      await queryClient.invalidateQueries({ queryKey: ["properties"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      onClose();
    } catch (err) {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao salvar a certificação.");
    } finally {
      setSaving(false);
    }
  };

  const workers = (workersQuery.data ?? []).filter(
    (w) => w.id && (w.active !== false || workerIds.includes(w.id)),
  );
  const [submitIdle, submitSaving] = SUBMIT_LABELS[mode];

  return (
    <div className="af-modal-backdrop" onClick={onClose}>
      <div className="af-modal" onClick={(e) => e.stopPropagation()}>
        <div className="af-modal-header">
          <h3 className="af-card-title">{FORM_TITLES[mode]}</h3>
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
          {missingVerification && (
            <div className="af-alert af-alert--warning" role="alert" style={{ marginBottom: 14 }}>
              Não há visita da comissão registrada neste ciclo. Renovar mesmo assim? (RN39)
            </div>
          )}
          <form className="af-form-grid" onSubmit={handleSubmit}>
            <div className="af-field">
              <label className="af-label" htmlFor="cert-property">
                Propriedade *
              </label>
              {isCreate ? (
                <select
                  id="cert-property"
                  className="af-select"
                  value={propertyId}
                  onChange={(e) => {
                    setPropertyId(e.target.value);
                    setWorkerIds([]);
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
              ) : (
                <input
                  id="cert-property"
                  className="af-input"
                  value={`${certification?.property?.name ?? "Propriedade"} (${certification?.property?.farmerName ?? ""})`}
                  disabled
                />
              )}
            </div>

            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label" htmlFor="cert-number">
                  Nº do Certificado
                </label>
                <input
                  id="cert-number"
                  className="af-input"
                  value={number}
                  onChange={(e) => setNumber(e.target.value)}
                  placeholder="Ex.: OPAC-2026-0042"
                />
              </div>
              <div className="af-field">
                <label className="af-label" htmlFor="cert-scope">
                  Escopo
                </label>
                <input
                  id="cert-scope"
                  className="af-input"
                  value={scope}
                  onChange={(e) => setScope(e.target.value)}
                />
              </div>
            </div>

            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label" htmlFor="cert-issued">
                  Data de emissão *
                </label>
                <input
                  id="cert-issued"
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
                <label className="af-label" htmlFor="cert-expires">
                  Validade (padrão +1 ano — RN18) *
                </label>
                <input
                  id="cert-expires"
                  type="date"
                  className="af-input"
                  value={expiresOn}
                  onChange={(e) => setExpiresOn(e.target.value)}
                  required
                />
              </div>
            </div>

            <ProductsPicker
              crops={cropsQuery.data ?? []}
              loading={cropsQuery.isLoading}
              cropIds={cropIds}
              onCropIdsChange={setCropIds}
              otherProducts={otherProducts}
              onOtherProductsChange={setOtherProducts}
            />

            {propertyId && (
              <fieldset className="af-field" style={{ border: 0, padding: 0, margin: 0 }}>
                <legend className="af-label">Trabalhadores cobertos</legend>
                {workersQuery.isLoading ? (
                  <div className="af-empty-state" style={{ padding: 12 }}>
                    Carregando trabalhadores...
                  </div>
                ) : workers.length === 0 ? (
                  <div style={{ fontSize: "0.84rem", color: "var(--af-text-muted)" }}>
                    Nenhum trabalhador cadastrado nesta propriedade.
                  </div>
                ) : (
                  <div
                    style={{
                      display: "flex",
                      flexWrap: "wrap",
                      gap: "6px 16px",
                      fontSize: "0.86rem",
                    }}
                  >
                    {workers.map((w) => (
                      <label
                        key={w.id}
                        style={{ display: "flex", alignItems: "center", gap: 6, cursor: "pointer" }}
                      >
                        <input
                          type="checkbox"
                          checked={workerIds.includes(w.id!)}
                          onChange={() =>
                            setWorkerIds((ids) =>
                              ids.includes(w.id!)
                                ? ids.filter((id) => id !== w.id)
                                : [...ids, w.id!],
                            )
                          }
                        />
                        {w.name}
                      </label>
                    ))}
                  </div>
                )}
              </fieldset>
            )}

            <div className="af-field">
              <label className="af-label" htmlFor="cert-notes">
                Observações
              </label>
              <textarea
                id="cert-notes"
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
                {saving ? submitSaving : missingVerification ? "Renovar mesmo assim" : submitIdle}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

/** Escopo sugerido para novas certificações. */
const DEFAULT_SCOPE = "Produção Primária Vegetal";

/** Limite de nomes livres por certificação, o mesmo da API (CertificationWrite.products, @Size max 100). */
const MAX_PRODUCTS = 100;
const PRODUCT_NAME_MAX_LENGTH = 120;

type ProductsPickerProps = {
  crops: CropView[];
  loading: boolean;
  cropIds: string[];
  onCropIdsChange: (ids: string[]) => void;
  otherProducts: string[];
  onOtherProductsChange: (names: string[]) => void;
};

/** Escolha dos produtos certificados: culturas do catálogo marcadas e nomes livres para o que não está nele. */
function ProductsPicker({
  crops,
  loading,
  cropIds,
  onCropIdsChange,
  otherProducts,
  onOtherProductsChange,
}: ProductsPickerProps) {
  const [otherName, setOtherName] = useState("");
  const total = cropIds.length + otherProducts.length;

  const toggleCrop = (id: string) =>
    onCropIdsChange(cropIds.includes(id) ? cropIds.filter((c) => c !== id) : [...cropIds, id]);

  const addOther = () => {
    const name = otherName.trim();
    const duplicated = otherProducts.some((p) => p.toLowerCase() === name.toLowerCase());
    if (!name || duplicated || total >= MAX_PRODUCTS) return;
    onOtherProductsChange([...otherProducts, name]);
    setOtherName("");
  };

  return (
    <div className="af-field">
      <span className="af-label" id="cert-products-label">
        Produtos certificados * ({total} selecionado{total === 1 ? "" : "s"})
      </span>
      {loading ? (
        <div className="af-empty-state" style={{ padding: 12 }}>
          Carregando culturas...
        </div>
      ) : (
        <div
          role="group"
          aria-labelledby="cert-products-label"
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))",
            gap: "6px 12px",
            maxHeight: 180,
            overflowY: "auto",
            padding: 10,
            border: "1px solid var(--af-border)",
            borderRadius: "var(--af-radius-md)",
            fontSize: "0.86rem",
          }}
        >
          {crops
            .filter((c) => c.id && c.active !== false)
            .map((c) => (
              <label
                key={c.id}
                style={{ display: "flex", alignItems: "center", gap: 6, cursor: "pointer" }}
              >
                <input
                  type="checkbox"
                  checked={cropIds.includes(c.id!)}
                  onChange={() => toggleCrop(c.id!)}
                />
                {c.name}
              </label>
            ))}
        </div>
      )}

      <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
        <input
          className="af-input"
          value={otherName}
          maxLength={PRODUCT_NAME_MAX_LENGTH}
          onChange={(e) => setOtherName(e.target.value)}
          onKeyDown={(e) => {
            // Enter adiciona o produto em vez de enviar o formulário inteiro.
            if (e.key === "Enter") {
              e.preventDefault();
              addOther();
            }
          }}
          placeholder="Produto fora do catálogo (ex.: Mel de abelha)"
        />
        <button type="button" className="af-btn af-btn--outline af-btn--sm" onClick={addOther}>
          Adicionar
        </button>
      </div>

      {otherProducts.length > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 8 }}>
          {otherProducts.map((name) => (
            <span
              key={name}
              className="af-badge af-badge--valid"
              style={{ display: "inline-flex", gap: 6 }}
            >
              {name}
              <button
                type="button"
                aria-label={`Remover ${name}`}
                onClick={() => onOtherProductsChange(otherProducts.filter((p) => p !== name))}
                style={{
                  border: 0,
                  background: "none",
                  cursor: "pointer",
                  padding: 0,
                  color: "inherit",
                }}
              >
                ✕
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
