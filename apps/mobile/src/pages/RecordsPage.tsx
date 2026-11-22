// Lista os registros de manejo do agricultor, com filtro por tipo e o detalhe de cada registro (fotos incluídas).
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useLocation, useNavigate } from "react-router";
import type { RecordListItem } from "@agrofacil/api-types";
import {
  formatArea,
  formatDatePtBr,
  formatQuantity,
  MISSING_FIELD_LABELS,
  RECORD_TYPE_FARMER_LABELS,
} from "@agrofacil/domain";
import { api, fetchMobileRecordDetail } from "../api/client";
import { usePendingRecordIds } from "../api/pendingRecords";
import { SORT_BY_CREATION } from "../api/recordOrder";
import { onActivateKey } from "../a11y";
import { RecordPhotos } from "../components/RecordPhotos";
import { RecordTypeIcon } from "../components/RecordTypeIcon";
import plusIcon from "../assets/figma/plus-circle.svg";

/** Selo azul de "aguardando envio": não é erro nem pendência do agricultor. */
const PENDING_BADGE_STYLE = { backgroundColor: "#e0f2fe", color: "#075985" } as const;

const FILTER_OPTIONS = [
  { label: "Todos", value: "" },
  { label: "Plantio", value: "PLANTING" },
  { label: "Aplicação", value: "INPUT_APPLICATION" },
  { label: "Colheita", value: "HARVEST" },
  { label: "Compra", value: "INPUT_PURCHASE" },
  { label: "Saída/Venda", value: "OUTFLOW" },
] as const;

export function RecordsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const feedback = (location.state as { feedback?: string } | null)?.feedback;
  const [selectedType, setSelectedType] = useState<string>("");
  const [selectedRecord, setSelectedRecord] = useState<RecordListItem | null>(null);

  const recordsQuery = useQuery({
    queryKey: ["records", selectedType],
    queryFn: async () => {
      const { data, response } = await api.GET("/records", {
        params: {
          query: {
            filter: selectedType
              ? {
                  type: selectedType as
                    "PLANTING" | "INPUT_APPLICATION" | "HARVEST" | "INPUT_PURCHASE" | "OUTFLOW",
                }
              : {},
            pageable: {
              size: 50,
              sort: [SORT_BY_CREATION],
            },
          },
        },
      });
      if (!response.ok) throw new Error("Falha ao carregar a lista de manejos.");
      return data?.content ?? [];
    },
  });

  const detailQuery = useQuery({
    queryKey: ["mobile-record-detail", selectedRecord?.id],
    queryFn: () => fetchMobileRecordDetail(selectedRecord!.id!),
    enabled: Boolean(selectedRecord?.id),
  });

  const records = recordsQuery.data ?? [];
  // Registros na fila offline: a completude só é calculada pela API depois do envio.
  const pendingIds = usePendingRecordIds();
  const selectedIsPending = Boolean(selectedRecord?.id && pendingIds.has(selectedRecord.id));
  const detail = detailQuery.data;
  const openCorrections = (detail?.correctionRequests ?? []).filter((c) => c.status === "OPEN");

  return (
    <div>
      {/* Top Bar */}
      <div className="page-top-bar">
        <h1 className="page-top-bar-title">Caderno de Campo</h1>
        <Link
          to="/registros/novo"
          style={{
            color: "var(--color-primary)",
            fontWeight: 600,
            fontSize: "14px",
            textDecoration: "none",
            display: "flex",
            alignItems: "center",
            gap: "4px",
          }}
        >
          <span>+ Anotar</span>
        </Link>
      </div>

      {feedback && (
        <p role="status" className="mobile-card">
          {feedback}
        </p>
      )}

      {/* Chips de Filtro (Figma 49:852) */}
      <div className="chips-scroll">
        {FILTER_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            className={`chip-btn ${selectedType === opt.value ? "active" : ""}`}
            onClick={() => setSelectedType(opt.value)}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* Lista de Registros */}
      <div style={{ padding: "8px 16px 24px 16px" }}>
        {recordsQuery.isLoading ? (
          <div className="empty-state">
            <div className="empty-state-title">Carregando manejos...</div>
          </div>
        ) : recordsQuery.isError ? (
          <div className="empty-state">
            <p role="alert">Não foi possível carregar os manejos.</p>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void recordsQuery.refetch()}
            >
              Tentar novamente
            </button>
          </div>
        ) : records.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">📋</div>
            <div className="empty-state-title">Nenhum manejo encontrado</div>
            <p className="empty-state-desc">
              {selectedType
                ? "Nenhum registro para este filtro selecionado."
                : "Seu caderno de campo está vazio. Comece anotando sua primeira atividade!"}
            </p>
            <Link to="/registros/novo" className="btn-primary" style={{ display: "inline-flex" }}>
              + Novo Manejo
            </Link>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            {records.map((rec) => {
              const typeName = RECORD_TYPE_FARMER_LABELS[rec.type ?? ""] ?? rec.type ?? "Manejo";
              const isComplete = rec.completeness === "COMPLETE";
              const isPending = Boolean(rec.id && pendingIds.has(rec.id));
              const hasCorrection = (rec.openCorrectionRequests ?? 0) > 0;

              return (
                <div
                  key={rec.id}
                  className="mobile-card"
                  role="button"
                  tabIndex={0}
                  onClick={() => setSelectedRecord(rec)}
                  onKeyDown={onActivateKey(() => setSelectedRecord(rec))}
                  style={{
                    marginBottom: 0,
                    cursor: "pointer",
                    padding: "14px",
                    display: "flex",
                    alignItems: "flex-start",
                    gap: "12px",
                    transition: "background-color 0.15s ease",
                    borderColor: hasCorrection ? "#f59e0b" : undefined,
                  }}
                >
                  <RecordTypeIcon type={rec.type ?? ""} size={44} />

                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "flex-start",
                        gap: "6px",
                      }}
                    >
                      <div>
                        <div
                          style={{
                            fontSize: "14px",
                            fontWeight: 600,
                            color: "var(--color-text)",
                            lineHeight: 1.3,
                          }}
                        >
                          {typeName}
                        </div>
                        {rec.subject && (
                          <div
                            style={{
                              fontSize: "13px",
                              color: "var(--color-primary-dark)",
                              fontWeight: 500,
                              marginTop: "2px",
                            }}
                          >
                            {rec.subject}
                          </div>
                        )}
                      </div>

                      <span
                        style={{
                          fontSize: "11px",
                          color: "var(--color-text-muted)",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {rec.performedOn ? formatDatePtBr(rec.performedOn) : ""}
                      </span>
                    </div>

                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        marginTop: "8px",
                        fontSize: "12px",
                        color: "var(--color-text-secondary)",
                      }}
                    >
                      <div
                        style={{
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        📍{" "}
                        {rec.plot?.name
                          ? `Talhão: ${rec.plot.name}`
                          : (rec.property?.name ?? "Propriedade")}
                      </div>

                      {rec.area?.value ? (
                        <span style={{ fontWeight: 600, color: "var(--color-text)" }}>
                          {formatArea(rec.area)}
                        </span>
                      ) : rec.quantity?.value ? (
                        <span style={{ fontWeight: 600, color: "var(--color-text)" }}>
                          {formatQuantity(rec.quantity)}
                        </span>
                      ) : null}
                    </div>

                    {/* Status badges */}
                    <div
                      style={{ marginTop: "6px", display: "flex", gap: "6px", flexWrap: "wrap" }}
                    >
                      {isPending ? (
                        <span className="badge-status" style={PENDING_BADGE_STYLE}>
                          ⏳ Aguardando envio
                        </span>
                      ) : (
                        <span
                          className={`badge-status ${
                            isComplete ? "badge-complete" : "badge-incomplete"
                          }`}
                        >
                          {isComplete ? "✓ Completo" : "⚠️ Pendente de dados"}
                        </span>
                      )}
                      {hasCorrection && (
                        <span
                          className="badge-status badge-incomplete"
                          style={{ backgroundColor: "#fef3c7", color: "#92400e" }}
                        >
                          🔔 Correção solicitada
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Floating Action Button (FAB) */}
      <div className="fab-container">
        <Link to="/registros/novo" className="fab-btn">
          <img
            src={plusIcon}
            alt=""
            width={20}
            height={20}
            style={{ filter: "brightness(0) invert(1)" }}
          />
          <span>Novo Manejo</span>
        </Link>
      </div>

      {/* Modal de Detalhes do Registro */}
      {selectedRecord && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="modal-record-title"
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: "rgba(0, 0, 0, 0.5)",
            display: "flex",
            alignItems: "flex-end",
            justifyContent: "center",
            zIndex: 200,
          }}
          onClick={() => setSelectedRecord(null)}
        >
          <div
            className="mobile-app-container"
            style={{
              backgroundColor: "var(--color-surface)",
              borderTopLeftRadius: "24px",
              borderTopRightRadius: "24px",
              padding: "20px 20px calc(24px + var(--safe-bottom)) 20px",
              minHeight: "auto",
              maxHeight: "85vh",
              overflowY: "auto",
              boxShadow: "var(--shadow-lg)",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div
              style={{
                width: "40px",
                height: "4px",
                backgroundColor: "#e0e0e0",
                borderRadius: "2px",
                margin: "0 auto 16px auto",
              }}
            />

            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "12px",
                marginBottom: "16px",
              }}
            >
              <RecordTypeIcon type={selectedRecord.type ?? ""} size={48} />
              <div>
                <h2 id="modal-record-title" style={{ fontSize: "17px", fontWeight: 700 }}>
                  {RECORD_TYPE_FARMER_LABELS[selectedRecord.type ?? ""] ?? selectedRecord.type}
                </h2>
                <div style={{ fontSize: "13px", color: "var(--color-text-secondary)" }}>
                  {selectedRecord.subject || "Detalhes do manejo"}
                </div>
              </div>
            </div>

            {openCorrections.length > 0 && (
              <div
                style={{
                  backgroundColor: "#fff8e1",
                  border: "1.5px solid #f59e0b",
                  borderRadius: "10px",
                  padding: "12px",
                  marginBottom: "14px",
                }}
              >
                <div
                  style={{ fontSize: "13px", fontWeight: 700, color: "#92400e", marginBottom: 4 }}
                >
                  🔔 Pedido de Correção da Coordenação:
                </div>
                {openCorrections.map((c) => (
                  <div key={c.id} style={{ fontSize: "13px", color: "#78350f" }}>
                    “{c.message}”
                  </div>
                ))}
              </div>
            )}

            <div
              style={{
                display: "flex",
                flexDirection: "column",
                gap: "12px",
                marginBottom: "20px",
              }}
            >
              <div
                style={{ backgroundColor: "var(--color-bg)", padding: "12px", borderRadius: "8px" }}
              >
                <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                  Data de Execução
                </div>
                <div style={{ fontSize: "14px", fontWeight: 600 }}>
                  {selectedRecord.performedOn
                    ? formatDatePtBr(selectedRecord.performedOn)
                    : "Não informada"}
                </div>
              </div>

              <div
                style={{ backgroundColor: "var(--color-bg)", padding: "12px", borderRadius: "8px" }}
              >
                <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Local</div>
                <div style={{ fontSize: "14px", fontWeight: 600 }}>
                  {selectedRecord.property?.name ?? "Propriedade principal"}
                  {selectedRecord.plot?.name ? ` · Talhão ${selectedRecord.plot.name}` : ""}
                </div>
              </div>

              {selectedRecord.area?.value && (
                <div
                  style={{
                    backgroundColor: "var(--color-bg)",
                    padding: "12px",
                    borderRadius: "8px",
                  }}
                >
                  <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Área</div>
                  <div style={{ fontSize: "14px", fontWeight: 600 }}>
                    {formatArea(selectedRecord.area)}
                  </div>
                </div>
              )}

              {selectedRecord.quantity?.value && (
                <div
                  style={{
                    backgroundColor: "var(--color-bg)",
                    padding: "12px",
                    borderRadius: "8px",
                  }}
                >
                  <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                    Quantidade
                  </div>
                  <div style={{ fontSize: "14px", fontWeight: 600 }}>
                    {formatQuantity(selectedRecord.quantity)}
                  </div>
                </div>
              )}

              {detail?.notes && (
                <div
                  style={{
                    backgroundColor: "var(--color-bg)",
                    padding: "12px",
                    borderRadius: "8px",
                  }}
                >
                  <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                    Observações
                  </div>
                  <div style={{ fontSize: "13px", marginTop: 2 }}>{detail.notes}</div>
                </div>
              )}

              <div
                style={{ backgroundColor: "var(--color-bg)", padding: "12px", borderRadius: "8px" }}
              >
                <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                  Status do Registro
                </div>
                <div style={{ marginTop: "4px" }}>
                  {selectedIsPending ? (
                    <span className="badge-status" style={PENDING_BADGE_STYLE}>
                      ⏳ Aguardando envio: será conferido quando houver internet
                    </span>
                  ) : (
                    <span
                      className={`badge-status ${
                        selectedRecord.completeness === "COMPLETE"
                          ? "badge-complete"
                          : "badge-incomplete"
                      }`}
                    >
                      {selectedRecord.completeness === "COMPLETE"
                        ? "✓ Registro Completo"
                        : "⚠️ Contém pendências ou dados incompletos"}
                    </span>
                  )}
                </div>
                {!selectedIsPending && (detail?.missingFields ?? []).length > 0 && (
                  <div
                    style={{
                      fontSize: "12px",
                      color: "var(--color-text-secondary)",
                      marginTop: "6px",
                    }}
                  >
                    Falta informar:{" "}
                    {(detail?.missingFields ?? [])
                      .map((f: string) => MISSING_FIELD_LABELS[f] ?? f)
                      .join(", ")}
                    .
                  </div>
                )}
              </div>

              {selectedRecord.id && (
                <RecordPhotos
                  recordId={selectedRecord.id}
                  attachments={detail?.attachments ?? []}
                />
              )}
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
              {selectedRecord.id && (
                <button
                  type="button"
                  className="btn-primary"
                  onClick={() => {
                    const id = selectedRecord.id;
                    setSelectedRecord(null);
                    navigate(`/registros/${id}/editar`);
                  }}
                >
                  {openCorrections.length > 0
                    ? "✏️ Atender Pedido de Correção"
                    : selectedRecord.completeness === "INCOMPLETE"
                      ? "✏️ Completar Dados do Registro"
                      : "✏️ Editar Registro"}
                </button>
              )}

              <button
                type="button"
                className="btn-secondary"
                onClick={() => setSelectedRecord(null)}
              >
                Fechar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
