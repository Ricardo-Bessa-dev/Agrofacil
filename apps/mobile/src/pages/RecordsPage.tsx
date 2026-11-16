import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import type { RecordListItem } from "@agrofacil/api-types";
import {
  formatArea,
  formatDatePtBr,
  formatQuantity,
  RECORD_TYPE_FARMER_LABELS,
} from "@agrofacil/domain";
import { api } from "../api/client";
import { RecordTypeIcon } from "../components/RecordTypeIcon";
import plusIcon from "../assets/figma/plus-circle.svg";

const FILTER_OPTIONS = [
  { label: "Todos", value: "" },
  { label: "Plantio", value: "PLANTING" },
  { label: "Aplicação", value: "INPUT_APPLICATION" },
  { label: "Colheita", value: "HARVEST" },
  { label: "Compra", value: "INPUT_PURCHASE" },
  { label: "Saída/Venda", value: "OUTFLOW" },
] as const;

export function RecordsPage() {
  const [selectedType, setSelectedType] = useState<string>("");
  const [selectedRecord, setSelectedRecord] = useState<RecordListItem | null>(null);

  const recordsQuery = useQuery({
    queryKey: ["records", selectedType],
    queryFn: async () => {
      const { data } = await api.GET("/records", {
        params: {
          query: {
            filter: selectedType
              ? {
                  type: selectedType as
                    | "PLANTING"
                    | "INPUT_APPLICATION"
                    | "HARVEST"
                    | "INPUT_PURCHASE"
                    | "OUTFLOW",
                }
              : {},
            pageable: {
              size: 50,
              sort: ["performedOn,desc"],
            },
          },
        },
      });
      return data?.content ?? [];
    },
  });

  const records = recordsQuery.data ?? [];

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
              const typeName =
                RECORD_TYPE_FARMER_LABELS[rec.type ?? ""] ?? rec.type ?? "Manejo";
              const isComplete = rec.completeness === "COMPLETE";

              return (
                <div
                  key={rec.id}
                  className="mobile-card"
                  onClick={() => setSelectedRecord(rec)}
                  style={{
                    marginBottom: 0,
                    cursor: "pointer",
                    padding: "14px",
                    display: "flex",
                    alignItems: "flex-start",
                    gap: "12px",
                    transition: "background-color 0.15s ease",
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
                        📍 {rec.plot?.name ? `Talhão: ${rec.plot.name}` : rec.property?.name ?? "Propriedade"}
                      </div>

                      {/* Informações de quantidade ou área */}
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

                    {/* Status badge */}
                    <div style={{ marginTop: "6px" }}>
                      <span
                        className={`badge-status ${
                          isComplete ? "badge-complete" : "badge-incomplete"
                        }`}
                      >
                        {isComplete ? "✓ Completo" : "⚠️ Pendente de dados"}
                      </span>
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
          <img src={plusIcon} alt="" width={20} height={20} style={{ filter: "brightness(0) invert(1)" }} />
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

            <div style={{ display: "flex", flexDirection: "column", gap: "12px", marginBottom: "20px" }}>
              <div style={{ backgroundColor: "var(--color-bg)", padding: "12px", borderRadius: "8px" }}>
                <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Data de Execução</div>
                <div style={{ fontSize: "14px", fontWeight: 600 }}>
                  {selectedRecord.performedOn ? formatDatePtBr(selectedRecord.performedOn) : "Não informada"}
                </div>
              </div>

              <div style={{ backgroundColor: "var(--color-bg)", padding: "12px", borderRadius: "8px" }}>
                <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Local</div>
                <div style={{ fontSize: "14px", fontWeight: 600 }}>
                  {selectedRecord.property?.name ?? "Propriedade principal"}
                  {selectedRecord.plot?.name ? ` · Talhão ${selectedRecord.plot.name}` : ""}
                </div>
              </div>

              {selectedRecord.area?.value && (
                <div style={{ backgroundColor: "var(--color-bg)", padding: "12px", borderRadius: "8px" }}>
                  <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Área</div>
                  <div style={{ fontSize: "14px", fontWeight: 600 }}>{formatArea(selectedRecord.area)}</div>
                </div>
              )}

              {selectedRecord.quantity?.value && (
                <div style={{ backgroundColor: "var(--color-bg)", padding: "12px", borderRadius: "8px" }}>
                  <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Quantidade</div>
                  <div style={{ fontSize: "14px", fontWeight: 600 }}>{formatQuantity(selectedRecord.quantity)}</div>
                </div>
              )}

              <div style={{ backgroundColor: "var(--color-bg)", padding: "12px", borderRadius: "8px" }}>
                <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>Status do Registro</div>
                <div style={{ marginTop: "4px" }}>
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
                </div>
              </div>
            </div>

            <button
              type="button"
              className="btn-secondary"
              onClick={() => setSelectedRecord(null)}
            >
              Fechar
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
