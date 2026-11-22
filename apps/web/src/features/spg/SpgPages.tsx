// Telas do SPG (Sistema Participativo de Garantia): Visitas (RN29–RN36), Documentos D1–D8 (RN37–RN38) e Pessoas do SPG (Comissão, Colaboradores e Impedimentos).
import React, { useState } from "react";
import { useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  COLLABORATOR_KIND_LABELS,
  COMMITTEE_ROLE_LABELS,
  CONFLICT_REASON_LABELS,
  DOCUMENT_KIND_LABELS,
  DOCUMENT_STATUS_LABELS,
  formatDatePtBr,
  formatDateTimePtBr,
  todayIso,
  VISIT_CYCLE_STATE_LABELS,
  VISIT_STATUS_LABELS,
  VISIT_TYPE_LABELS,
} from "@agrofacil/domain";
import type { GenerateDocumentRequest, ParticipantWrite, ResultWrite } from "@agrofacil/api-types";
import { api, ApiProblemError, downloadFile, normalizePhoneE164, unwrap } from "../../api/client";
import { fetchAllPages } from "../../api/pagination";
import { Pagination } from "../../components/Pagination";

/** Itens por página nas listas de visitas e documentos. */
const LIST_PAGE_SIZE = 20;

export function VisitsPage() {
  const [searchParams] = useSearchParams();
  const initialPropertyId = searchParams.get("propertyId") ?? "";

  const [tab, setTab] = useState<"status" | "list">("status");
  const [schedulePropertyId, setSchedulePropertyId] = useState<string | null>(
    initialPropertyId || null,
  );
  const [resultVisitId, setResultVisitId] = useState<string | null>(null);
  const [statusPage, setStatusPage] = useState(0);
  const [visitsPage, setVisitsPage] = useState(0);

  const statusQuery = useQuery({
    queryKey: ["visits", "status", statusPage],
    enabled: tab === "status",
    queryFn: () =>
      unwrap(
        api.GET("/visits/status", {
          params: {
            query: {
              pageable: { page: statusPage, size: LIST_PAGE_SIZE },
            },
          },
        }),
      ),
  });

  const visitsQuery = useQuery({
    queryKey: ["visits", "list", visitsPage],
    enabled: tab === "list",
    queryFn: () =>
      unwrap(
        api.GET("/visits", {
          params: {
            query: {
              filter: {},
              pageable: { page: visitsPage, size: LIST_PAGE_SIZE, sort: ["plannedOn,desc"] },
            },
          },
        }),
      ),
  });

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Visitas do SPG</h1>
          <p className="af-page-subtitle">
            Planejamento do ciclo de visitas de pares e de verificação da Comissão de Avaliação
            (RN29–RN36).
          </p>
        </div>
        <div className="af-topbar-actions">
          <button
            type="button"
            className="af-btn af-btn--primary af-btn--sm"
            onClick={() => setSchedulePropertyId("")}
          >
            + Agendar visita
          </button>
        </div>
      </div>

      <div className="af-tabs">
        <button
          type="button"
          className={`af-tab ${tab === "status" ? "af-tab--active" : ""}`}
          onClick={() => setTab("status")}
        >
          Planejamento por Propriedade (Ciclo Atual)
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "list" ? "af-tab--active" : ""}`}
          onClick={() => setTab("list")}
        >
          Agenda de Visitas
        </button>
      </div>

      {tab === "status" && (
        <div className="af-card">
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">Situação das Propriedades no Ciclo</h2>
              <p className="af-card-subtitle">
                Ordenado por urgência e proximidade do vencimento da certificação
              </p>
            </div>
          </div>

          {statusQuery.isLoading ? (
            <div className="af-empty-state">Carregando situação do ciclo de visitas...</div>
          ) : (statusQuery.data?.content ?? []).length === 0 ? (
            <div className="af-empty-state">Nenhuma propriedade em ciclo de visitas.</div>
          ) : (
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Propriedade / Agricultor</th>
                    <th>Grupo</th>
                    <th>Visitas de Pares</th>
                    <th>Verificação</th>
                    <th>Situação</th>
                    <th style={{ textAlign: "right" }}>Ação</th>
                  </tr>
                </thead>
                <tbody>
                  {(statusQuery.data?.content ?? []).map((st, i) => (
                    <tr key={st.property?.id ?? i}>
                      <td>
                        <div style={{ fontWeight: 700, color: "var(--af-green-darker)" }}>
                          {st.property?.name}
                        </div>
                        <div style={{ fontSize: "0.8rem", color: "var(--af-text-secondary)" }}>
                          {st.property?.farmerName}
                        </div>
                      </td>
                      <td>{st.group?.name ?? "—"}</td>
                      <td>
                        <strong>
                          {st.peer?.done ?? 0} / {st.peer?.required ?? 0}
                        </strong>{" "}
                        concluídas
                        {(st.peer?.planned ?? 0) > 0 && (
                          <div style={{ fontSize: "0.75rem", color: "var(--af-text-muted)" }}>
                            {st.peer?.planned} agendada(s)
                          </div>
                        )}
                      </td>
                      <td>
                        <strong>
                          {st.verification?.done ?? 0} / {st.verification?.required ?? 0}
                        </strong>{" "}
                        concluídas
                      </td>
                      <td>
                        <span
                          className={`af-badge ${
                            st.state === "URGENT"
                              ? "af-badge--danger"
                              : st.state === "PENDING"
                                ? "af-badge--warning"
                                : "af-badge--valid"
                          }`}
                        >
                          {st.state ? VISIT_CYCLE_STATE_LABELS[st.state] : "—"}
                        </span>
                      </td>
                      <td style={{ textAlign: "right" }}>
                        <button
                          type="button"
                          className="af-btn af-btn--secondary af-btn--sm"
                          onClick={() => setSchedulePropertyId(st.property?.id ?? "")}
                        >
                          Agendar visita
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination
            meta={statusQuery.data?.page}
            onPageChange={setStatusPage}
            itemsLabel="propriedades"
          />
        </div>
      )}

      {tab === "list" && (
        <div className="af-card">
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">Visitas Agendadas e Realizadas</h2>
              <p className="af-card-subtitle">
                Registre o resultado da visita ou gere a Ficha / Ata nos Documentos
              </p>
            </div>
          </div>

          {visitsQuery.isLoading ? (
            <div className="af-empty-state">Carregando visitas...</div>
          ) : (visitsQuery.data?.content ?? []).length === 0 ? (
            <div className="af-empty-state">Nenhuma visita cadastrada.</div>
          ) : (
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Data</th>
                    <th>Tipo</th>
                    <th>Propriedade visitada</th>
                    <th>Visitantes</th>
                    <th>Status</th>
                    <th style={{ textAlign: "right" }}>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {(visitsQuery.data?.content ?? []).map((v) => (
                    <tr key={v.id}>
                      <td style={{ fontWeight: 600 }}>{formatDatePtBr(v.plannedOn)}</td>
                      <td>
                        {v.type ? VISIT_TYPE_LABELS[v.type] : "—"}
                        {v.unannounced && (
                          <span className="af-badge af-badge--warning" style={{ marginLeft: 6 }}>
                            Sem aviso prévio
                          </span>
                        )}
                      </td>
                      <td>
                        <div style={{ fontWeight: 700 }}>{v.property?.name}</div>
                        <div style={{ fontSize: "0.78rem", color: "var(--af-text-secondary)" }}>
                          {v.property?.farmerName}
                        </div>
                      </td>
                      <td>
                        {(v.participants ?? [])
                          .map((p) => p.person?.name)
                          .filter(Boolean)
                          .join(", ") || "—"}
                      </td>
                      <td>
                        <span
                          className={`af-badge ${
                            v.status === "DONE"
                              ? "af-badge--valid"
                              : v.status === "CANCELLED"
                                ? "af-badge--neutral"
                                : "af-badge--warning"
                          }`}
                        >
                          {v.status ? VISIT_STATUS_LABELS[v.status] : "—"}
                        </span>
                      </td>
                      <td style={{ textAlign: "right" }}>
                        {v.id && v.status === "PLANNED" && (
                          <button
                            type="button"
                            className="af-btn af-btn--primary af-btn--sm"
                            onClick={() => setResultVisitId(v.id!)}
                          >
                            Lançar resultado
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination
            meta={visitsQuery.data?.page}
            onPageChange={setVisitsPage}
            itemsLabel="visitas"
          />
        </div>
      )}

      {schedulePropertyId !== null && (
        <ScheduleVisitModal
          defaultPropertyId={schedulePropertyId}
          onClose={() => setSchedulePropertyId(null)}
        />
      )}

      {resultVisitId && (
        <RecordVisitResultModal visitId={resultVisitId} onClose={() => setResultVisitId(null)} />
      )}
    </div>
  );
}

function ScheduleVisitModal({
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
  const [selectedVisitorIds, setSelectedVisitorIds] = useState<string[]>([]);
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
    queryFn: async () => {
      const res = await unwrap(
        api.POST("/visits/suggest-visitors", {
          body: { propertyId, type, plannedOn },
        }),
      );
      const preselected = (res.eligible ?? [])
        .filter((e) => e.preselected && e.person?.id)
        .map((e) => e.person!.id);
      if (preselected.length > 0 && selectedVisitorIds.length === 0) {
        setSelectedVisitorIds(preselected);
      }
      return res;
    },
  });

  const toggleVisitor = (id: string) => {
    setSelectedVisitorIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!propertyId) {
      setError("Selecione a propriedade.");
      return;
    }
    if (selectedVisitorIds.length === 0) {
      setError("Selecione pelo menos um visitante elegível (RN32).");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      const participants: ParticipantWrite[] = selectedVisitorIds.map((vid) => ({
        person: { kind: "FARMER", id: vid },
        role: "VISITOR",
      }));

      await unwrap(
        api.POST("/visits", {
          body: {
            propertyId,
            type,
            unannounced: type === "VERIFICATION" ? unannounced : false,
            plannedOn,
            participants,
            recorderId: selectedVisitorIds[0],
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
                  setSelectedVisitorIds([]);
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
                    setSelectedVisitorIds([]);
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
                  onChange={(e) => setPlannedOn(e.target.value)}
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
                {suggestQuery.isLoading ? (
                  <div style={{ fontSize: "0.85rem", color: "var(--af-text-secondary)" }}>
                    Calculando visitantes elegíveis...
                  </div>
                ) : (suggestQuery.data?.eligible ?? []).length === 0 ? (
                  <div className="af-alert af-alert--warning">
                    Nenhum visitante elegível encontrado para este tipo de visita. Confira se o
                    grupo possui outros membros ativos ou se a Comissão de Avaliação está
                    cadastrada.
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
                    {(suggestQuery.data?.eligible ?? []).map((el) => {
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

                {(suggestQuery.data?.excluded ?? []).length > 0 && (
                  <div style={{ fontSize: "0.78rem", color: "var(--af-text-muted)", marginTop: 6 }}>
                    <strong>Impedidos (RN32):</strong>{" "}
                    {(suggestQuery.data?.excluded ?? [])
                      .map((ex) => `${ex.person?.name} (${ex.reason})`)
                      .join(", ")}
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

function RecordVisitResultModal({ visitId, onClose }: { visitId: string; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [doneOn, setDoneOn] = useState(todayIso());
  const [issuesFound, setIssuesFound] = useState(false);
  const [improvementPoints, setImprovementPoints] = useState("");
  const [recommendations, setRecommendations] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [attended, setAttended] = useState<string[]>([]);
  const [answers, setAnswers] = useState<Record<string, ResultWrite["answers"][number]["answer"]>>(
    {},
  );

  const visitQuery = useQuery({
    queryKey: ["visits", visitId],
    queryFn: () => unwrap(api.GET("/visits/{id}", { params: { path: { id: visitId } } })),
  });

  const checklistQuery = useQuery({
    queryKey: ["catalog", "visit-checklist"],
    queryFn: () => unwrap(api.GET("/catalog/visit-checklist")),
  });

  const applicable = (checklistQuery.data ?? []).filter(
    (item) => item.code && (item.appliesTo === "BOTH" || item.appliesTo === visitQuery.data?.type),
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const visit = visitQuery.data;
    if (!visit || !checklistQuery.isSuccess) return;
    setError(null);
    if (
      !visit.participants?.some(
        (item) => item.id && item.role === "VISITOR" && attended.includes(item.id),
      )
    ) {
      setError("Marque pelo menos um visitador presente.");
      return;
    }
    setSaving(true);
    try {
      await unwrap(
        api.PUT("/visits/{id}/result", {
          params: { path: { id: visitId } },
          body: {
            doneOn,
            attendedParticipantIds: attended,
            answers: applicable.flatMap(({ code }) =>
              code && answers[code] ? [{ itemCode: code, answer: answers[code] }] : [],
            ),
            issuesFound,
            improvementPoints: issuesFound ? improvementPoints.trim() : undefined,
            recommendations: recommendations.trim() || undefined,
            notes: notes.trim() || undefined,
            version: visit.version,
          },
        }),
      );
      await queryClient.invalidateQueries({ queryKey: ["visits"] });
      onClose();
    } catch (err) {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao registrar resultado.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="af-modal-backdrop" onClick={onClose}>
      <div className="af-modal" onClick={(e) => e.stopPropagation()}>
        <div className="af-modal-header">
          <h3 className="af-card-title">Lançar Resultado da Visita (RN35)</h3>
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
              <label htmlFor="spg-data-de-realizacao" className="af-label">
                Data de realização *
              </label>
              <input
                id="spg-data-de-realizacao"
                type="date"
                className="af-input"
                value={doneOn}
                onChange={(e) => setDoneOn(e.target.value)}
                max={todayIso()}
                required
              />
            </div>

            <fieldset>
              <legend>Quem participou</legend>
              {visitQuery.data?.participants?.map((participant) => {
                const id = participant.id;
                if (!id) return null;
                return (
                  <label className="af-label" key={id}>
                    <input
                      type="checkbox"
                      checked={attended.includes(id)}
                      onChange={(event) =>
                        setAttended((selected) =>
                          event.target.checked
                            ? [...selected, id]
                            : selected.filter((value) => value !== id),
                        )
                      }
                    />{" "}
                    {participant.person?.name ?? "Participante"}
                    {participant.role === "VISITOR" && " (visitador)"}
                  </label>
                );
              })}
            </fieldset>
            <fieldset>
              <legend>Roteiro da visita</legend>
              {checklistQuery.isError && <p role="alert">Não foi possível carregar o roteiro.</p>}
              {applicable.map((item) => {
                const code = item.code!;
                return (
                  <label className="af-label" key={code}>
                    {item.question}
                    <select
                      className="af-select"
                      value={answers[code] ?? ""}
                      required
                      onChange={(event) =>
                        setAnswers((previous) => ({
                          ...previous,
                          [code]: event.target.value as ResultWrite["answers"][number]["answer"],
                        }))
                      }
                    >
                      <option value="">Escolha uma resposta</option>
                      <option value="YES">Sim</option>
                      <option value="NO">Não</option>
                      <option value="NOT_APPLICABLE">Não se aplica</option>
                      <option value="NOT_CHECKED">Não verificado</option>
                    </select>
                  </label>
                );
              })}
            </fieldset>

            <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
              <input
                type="checkbox"
                checked={issuesFound}
                onChange={(e) => setIssuesFound(e.target.checked)}
              />
              <span style={{ fontWeight: 600 }}>
                Foram encontrados pontos que precisam melhorar / não conformidades?
              </span>
            </label>

            {issuesFound && (
              <div className="af-field">
                <label htmlFor="spg-pontos-a-melhorar" className="af-label">
                  Pontos a melhorar *
                </label>
                <textarea
                  id="spg-pontos-a-melhorar"
                  className="af-textarea"
                  value={improvementPoints}
                  onChange={(e) => setImprovementPoints(e.target.value)}
                  required
                />
              </div>
            )}

            <div className="af-field">
              <label htmlFor="spg-recomendacoes-ao-agricultor" className="af-label">
                Recomendações ao agricultor
              </label>
              <textarea
                id="spg-recomendacoes-ao-agricultor"
                className="af-textarea"
                value={recommendations}
                onChange={(e) => setRecommendations(e.target.value)}
              />
            </div>

            <div className="af-field">
              <label htmlFor="spg-observacoes-gerais-da-ata" className="af-label">
                Observações gerais da ata
              </label>
              <textarea
                id="spg-observacoes-gerais-da-ata"
                className="af-textarea"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>

            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <button type="button" className="af-btn af-btn--outline af-btn--sm" onClick={onClose}>
                Cancelar
              </button>
              <button
                type="submit"
                className="af-btn af-btn--primary af-btn--sm"
                disabled={saving || !visitQuery.isSuccess || !checklistQuery.isSuccess}
              >
                {saving ? "Salvando..." : "Concluir visita"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

export function DocumentsPage() {
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<GenerateDocumentRequest["kind"]>("MEMBERSHIP_REQUEST");
  const [farmerId, setFarmerId] = useState("");
  const [propertyId, setPropertyId] = useState("");
  const [groupId, setGroupId] = useState("");
  const [visitId, setVisitId] = useState("");
  const [certificationId, setCertificationId] = useState("");
  const [verifyCode, setVerifyCode] = useState("");
  const [verifyResult, setVerifyResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [docsPage, setDocsPage] = useState(0);
  const docsQuery = useQuery({
    queryKey: ["documents", docsPage],
    queryFn: () =>
      unwrap(
        api.GET("/documents", {
          params: {
            query: {
              filter: {},
              pageable: { page: docsPage, size: LIST_PAGE_SIZE },
            },
          },
        }),
      ),
  });

  const farmersQuery = useQuery({
    queryKey: ["farmers", "all-select", "ALL"],
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/farmers", {
          params: { query: { filter: {}, pageable: { ...pageable, sort: ["name,asc"] } } },
        }),
      ),
  });

  const propertiesQuery = useQuery({
    queryKey: ["properties", "all-select"],
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/properties", {
          params: { query: { filter: {}, pageable: { ...pageable, sort: ["name,asc"] } } },
        }),
      ),
  });

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: () => unwrap(api.GET("/groups")),
  });

  const visitsQuery = useQuery({
    queryKey: ["visits", "all-select"],
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/visits", { params: { query: { filter: {}, pageable } } }),
      ),
  });

  const certsQuery = useQuery({
    queryKey: ["certifications", "all-select"],
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/certifications", { params: { query: { filter: {}, pageable } } }),
      ),
  });

  const generateMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/documents", {
          body: {
            kind,
            farmerId: farmerId || undefined,
            propertyId: propertyId || undefined,
            groupId: groupId || undefined,
            visitId: visitId || undefined,
            certificationId: certificationId || undefined,
          },
        }),
      ),
    onSuccess: () => {
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["documents"] });
    },
    onError: (err) => {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao gerar documento.");
    },
  });

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!verifyCode.trim()) return;
    try {
      const doc = await unwrap(
        api.GET("/documents/verify/{code}", {
          params: { path: { code: verifyCode.trim().toUpperCase() } },
        }),
      );
      setVerifyResult(
        `✓ Documento autêntico: "${doc.title}" — Status: ${
          doc.status ? DOCUMENT_STATUS_LABELS[doc.status] : doc.status
        } — Gerado em ${formatDateTimePtBr(doc.generatedAt)}.`,
      );
    } catch {
      setVerifyResult("✕ Código de verificação não encontrado.");
    }
  };

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Documentos Oficiais do SPG (D1–D8)</h1>
          <p className="af-page-subtitle">
            Geração de documentos padronizados conforme a IN MAPA nº 19/2009 com código de
            verificação e hash SHA-256 (RN37–RN38).
          </p>
        </div>
      </div>

      <div className="af-grid-2" style={{ marginBottom: 20 }}>
        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 12 }}>
            Gerar Documento Oficial (PDF)
          </h2>
          {error && (
            <div className="af-alert af-alert--danger" style={{ marginBottom: 12 }}>
              {error}
            </div>
          )}
          <form
            className="af-form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              generateMutation.mutate();
            }}
          >
            <div className="af-field">
              <label htmlFor="spg-modelo-de-documento-d1" className="af-label">
                Modelo de documento (D1 a D8) *
              </label>
              <select
                id="spg-modelo-de-documento-d1"
                className="af-select"
                value={kind}
                onChange={(e) => setKind(e.target.value as GenerateDocumentRequest["kind"])}
              >
                {Object.entries(DOCUMENT_KIND_LABELS).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
            </div>

            {kind === "MEMBERSHIP_REQUEST" && (
              <div className="af-form-row">
                <div className="af-field">
                  <label htmlFor="spg-agricultor" className="af-label">
                    Agricultor *
                  </label>
                  <select
                    id="spg-agricultor"
                    className="af-select"
                    value={farmerId}
                    onChange={(e) => setFarmerId(e.target.value)}
                    required
                  >
                    <option value="">Selecione</option>
                    {(farmersQuery.data ?? []).map((f) => (
                      <option key={f.id} value={f.id}>
                        {f.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="af-field">
                  <label htmlFor="spg-propriedade" className="af-label">
                    Propriedade *
                  </label>
                  <select
                    id="spg-propriedade"
                    className="af-select"
                    value={propertyId}
                    onChange={(e) => setPropertyId(e.target.value)}
                    required
                  >
                    <option value="">Selecione</option>
                    {(propertiesQuery.data ?? []).map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            )}

            {kind === "CONFORMITY_DECISION" && (
              <div className="af-field">
                <label htmlFor="spg-propriedade-2" className="af-label">
                  Propriedade *
                </label>
                <select
                  id="spg-propriedade-2"
                  className="af-select"
                  value={propertyId}
                  onChange={(e) => setPropertyId(e.target.value)}
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
            )}

            {(kind === "EVALUATION_REQUEST" || kind === "VISIT_PLAN") && (
              <div className="af-field">
                <label htmlFor="spg-grupo-spg" className="af-label">
                  Grupo SPG *
                </label>
                <select
                  id="spg-grupo-spg"
                  className="af-select"
                  value={groupId}
                  onChange={(e) => setGroupId(e.target.value)}
                  required
                >
                  <option value="">Selecione o grupo</option>
                  {(groupsQuery.data ?? []).map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.name}
                    </option>
                  ))}
                </select>
              </div>
            )}

            {(kind === "VISIT_FORM" ||
              kind === "PEER_VISIT_RECORD" ||
              kind === "VERIFICATION_REPORT") && (
              <div className="af-field">
                <label htmlFor="spg-visita" className="af-label">
                  Visita *
                </label>
                <select
                  id="spg-visita"
                  className="af-select"
                  value={visitId}
                  onChange={(e) => setVisitId(e.target.value)}
                  required
                >
                  <option value="">Selecione a visita</option>
                  {(visitsQuery.data ?? []).map((v) => (
                    <option key={v.id} value={v.id}>
                      {formatDatePtBr(v.plannedOn)} — {v.property?.name} (
                      {v.type ? VISIT_TYPE_LABELS[v.type] : ""})
                    </option>
                  ))}
                </select>
              </div>
            )}

            {kind === "CERTIFICATE" && (
              <div className="af-field">
                <label htmlFor="spg-certificacao" className="af-label">
                  Certificação *
                </label>
                <select
                  id="spg-certificacao"
                  className="af-select"
                  value={certificationId}
                  onChange={(e) => setCertificationId(e.target.value)}
                  required
                >
                  <option value="">Selecione a certificação</option>
                  {(certsQuery.data ?? []).map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.property?.name} — Nº {c.number ?? "S/N"}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <button
              type="submit"
              className="af-btn af-btn--primary af-btn--sm"
              disabled={generateMutation.isPending}
            >
              {generateMutation.isPending ? "Gerando PDF..." : "Gerar documento oficial"}
            </button>
          </form>
        </div>

        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 8 }}>
            Conferir Autenticidade de Documento Impresso
          </h2>
          <p className="af-card-subtitle" style={{ marginBottom: 14 }}>
            Digite o código impresso no rodapé do PDF (ex.: K7QM-4TZP) para conferir sua validade.
          </p>
          <form className="af-form-grid" onSubmit={handleVerify}>
            <div className="af-field">
              <label htmlFor="spg-codigo-de-verificacao" className="af-label">
                Código de verificação
              </label>
              <input
                id="spg-codigo-de-verificacao"
                className="af-input"
                value={verifyCode}
                onChange={(e) => setVerifyCode(e.target.value)}
                placeholder="XXXX-XXXX"
              />
            </div>
            <button type="submit" className="af-btn af-btn--secondary af-btn--sm">
              Verificar autenticidade
            </button>
          </form>
          {verifyResult && (
            <div className="af-alert af-alert--info" style={{ marginTop: 14 }}>
              {verifyResult}
            </div>
          )}
        </div>
      </div>

      <div className="af-card">
        <h2 className="af-card-title" style={{ marginBottom: 14 }}>
          Documentos Emitidos
        </h2>
        {docsQuery.isLoading ? (
          <div className="af-empty-state">Carregando documentos...</div>
        ) : (docsQuery.data?.content ?? []).length === 0 ? (
          <div className="af-empty-state">Nenhum documento gerado ainda.</div>
        ) : (
          <div className="af-table-wrap">
            <table className="af-table">
              <thead>
                <tr>
                  <th>Título / Tipo</th>
                  <th>Código de verificação</th>
                  <th>Gerado em</th>
                  <th>Status</th>
                  <th style={{ textAlign: "right" }}>Download</th>
                </tr>
              </thead>
              <tbody>
                {(docsQuery.data?.content ?? []).map((d) => (
                  <tr key={d.id}>
                    <td>
                      <div style={{ fontWeight: 700 }}>{d.title}</div>
                      <div style={{ fontSize: "0.78rem", color: "var(--af-text-secondary)" }}>
                        {d.kind ? DOCUMENT_KIND_LABELS[d.kind] : ""}
                      </div>
                    </td>
                    <td>
                      <code>{d.verificationCode}</code>
                    </td>
                    <td>{formatDateTimePtBr(d.generatedAt)}</td>
                    <td>
                      <span
                        className={`af-badge ${
                          d.status === "SIGNED" || d.status === "GENERATED"
                            ? "af-badge--valid"
                            : "af-badge--neutral"
                        }`}
                      >
                        {d.status ? DOCUMENT_STATUS_LABELS[d.status] : "—"}
                      </span>
                    </td>
                    <td style={{ textAlign: "right" }}>
                      {d.id && (
                        <button
                          type="button"
                          className="af-btn af-btn--outline af-btn--sm"
                          onClick={() =>
                            downloadFile(
                              `/documents/${d.id}/content`,
                              `documento-${d.verificationCode ?? d.id}.pdf`,
                            )
                          }
                        >
                          Baixar PDF
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pagination
          meta={docsQuery.data?.page}
          onPageChange={setDocsPage}
          itemsLabel="documentos"
        />
      </div>
    </div>
  );
}

export function SpgPeoplePage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<"committee" | "collaborators" | "conflicts">("committee");

  // Formulário Comissão
  const [commFarmerId, setCommFarmerId] = useState("");
  const [commGroupId, setCommGroupId] = useState("");
  const [commRole, setCommRole] = useState<"HOLDER" | "ALTERNATE">("HOLDER");

  // Formulário Colaborador
  const [collabName, setCollabName] = useState("");
  const [collabKind, setCollabKind] = useState<
    "TECHNICIAN" | "CONSUMER" | "ORGANIZATION" | "OTHER"
  >("TECHNICIAN");
  const [collabOrg, setCollabOrg] = useState("");
  const [collabPhone, setCollabPhone] = useState("");

  // Formulário Impedimentos
  const [farmerAId, setFarmerAId] = useState("");
  const [farmerBId, setFarmerBId] = useState("");
  const [conflictReason, setConflictReason] = useState<
    "FAMILY" | "SAME_HOUSEHOLD" | "BUSINESS" | "OTHER"
  >("FAMILY");

  const committeeQuery = useQuery({
    queryKey: ["committee-members"],
    queryFn: () => unwrap(api.GET("/committee-members")),
  });

  const collaboratorsQuery = useQuery({
    queryKey: ["collaborators"],
    queryFn: () => unwrap(api.GET("/collaborators")),
  });

  const conflictsQuery = useQuery({
    queryKey: ["member-conflicts"],
    queryFn: () => unwrap(api.GET("/member-conflicts")),
  });

  const farmersQuery = useQuery({
    queryKey: ["farmers", "all-select", "ALL"],
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/farmers", {
          params: { query: { filter: {}, pageable: { ...pageable, sort: ["name,asc"] } } },
        }),
      ),
  });

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: () => unwrap(api.GET("/groups")),
  });

  const createCommitteeMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/committee-members", {
          body: {
            person: { kind: "FARMER", id: commFarmerId },
            representedGroupId: commGroupId,
            role: commRole,
            startedOn: todayIso(),
          },
        }),
      ),
    onSuccess: () => {
      setCommFarmerId("");
      queryClient.invalidateQueries({ queryKey: ["committee-members"] });
    },
  });

  const createCollabMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/collaborators", {
          body: {
            name: collabName.trim(),
            kind: collabKind,
            organization: collabOrg.trim() || undefined,
            phone: normalizePhoneE164(collabPhone),
            active: true,
          } as unknown as import("@agrofacil/api-types").Schemas["Write"],
        }),
      ),
    onSuccess: () => {
      setCollabName("");
      setCollabOrg("");
      setCollabPhone("");
      queryClient.invalidateQueries({ queryKey: ["collaborators"] });
    },
  });

  const createConflictMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/member-conflicts", {
          body: {
            farmerAId,
            farmerBId,
            reason: conflictReason,
          },
        }),
      ),
    onSuccess: () => {
      setFarmerAId("");
      setFarmerBId("");
      queryClient.invalidateQueries({ queryKey: ["member-conflicts"] });
    },
  });

  const deleteConflictMutation = useMutation({
    mutationFn: (id: string) =>
      unwrap(api.DELETE("/member-conflicts/{id}", { params: { path: { id } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["member-conflicts"] }),
  });

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Pessoas do SPG</h1>
          <p className="af-page-subtitle">
            Comissão de Avaliação, técnicos/consumidores colaboradores e impedimentos entre membros
            (RN32).
          </p>
        </div>
      </div>

      <div className="af-tabs">
        <button
          type="button"
          className={`af-tab ${tab === "committee" ? "af-tab--active" : ""}`}
          onClick={() => setTab("committee")}
        >
          Comissão de Avaliação ({(committeeQuery.data ?? []).length})
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "collaborators" ? "af-tab--active" : ""}`}
          onClick={() => setTab("collaborators")}
        >
          Colaboradores ({(collaboratorsQuery.data ?? []).length})
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "conflicts" ? "af-tab--active" : ""}`}
          onClick={() => setTab("conflicts")}
        >
          Impedimentos de Visita ({(conflictsQuery.data ?? []).length})
        </button>
      </div>

      {tab === "committee" && (
        <div className="af-grid-2">
          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Membros da Comissão de Avaliação
            </h2>
            {(committeeQuery.data ?? []).length === 0 ? (
              <div className="af-empty-state">Nenhum membro na comissão.</div>
            ) : (
              <div className="af-table-wrap">
                <table className="af-table">
                  <thead>
                    <tr>
                      <th>Membro</th>
                      <th>Função</th>
                      <th>Início</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(committeeQuery.data ?? []).map((cm) => (
                      <tr key={cm.id}>
                        <td style={{ fontWeight: 700 }}>{cm.person?.name}</td>
                        <td>{cm.role ? COMMITTEE_ROLE_LABELS[cm.role] : "—"}</td>
                        <td>{formatDatePtBr(cm.startedOn)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Adicionar à Comissão de Avaliação
            </h2>
            <form
              className="af-form-grid"
              onSubmit={(e) => {
                e.preventDefault();
                if (commFarmerId && commGroupId) createCommitteeMutation.mutate();
              }}
            >
              <div className="af-field">
                <label htmlFor="spg-agricultor-2" className="af-label">
                  Agricultor *
                </label>
                <select
                  id="spg-agricultor-2"
                  className="af-select"
                  value={commFarmerId}
                  onChange={(e) => setCommFarmerId(e.target.value)}
                  required
                >
                  <option value="">Selecione</option>
                  {(farmersQuery.data ?? []).map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label htmlFor="spg-grupo-representado" className="af-label">
                  Grupo representado *
                </label>
                <select
                  id="spg-grupo-representado"
                  className="af-select"
                  value={commGroupId}
                  onChange={(e) => setCommGroupId(e.target.value)}
                  required
                >
                  <option value="">Selecione</option>
                  {(groupsQuery.data ?? []).map((g) => (
                    <option key={g.id} value={g.id}>
                      {g.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label htmlFor="spg-papel" className="af-label">
                  Papel
                </label>
                <select
                  id="spg-papel"
                  className="af-select"
                  value={commRole}
                  onChange={(e) => setCommRole(e.target.value as "HOLDER" | "ALTERNATE")}
                >
                  {Object.entries(COMMITTEE_ROLE_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <button type="submit" className="af-btn af-btn--primary af-btn--sm">
                + Salvar na comissão
              </button>
            </form>
          </div>
        </div>
      )}

      {tab === "collaborators" && (
        <div className="af-grid-2">
          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Técnicos, Consumidores e Parceiros
            </h2>
            {(collaboratorsQuery.data ?? []).length === 0 ? (
              <div className="af-empty-state">Nenhum colaborador cadastrado.</div>
            ) : (
              <div className="af-table-wrap">
                <table className="af-table">
                  <thead>
                    <tr>
                      <th>Nome</th>
                      <th>Perfil</th>
                      <th>Entidade</th>
                      <th>Telefone</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(collaboratorsQuery.data ?? []).map((c) => (
                      <tr key={c.id}>
                        <td style={{ fontWeight: 700 }}>{c.name}</td>
                        <td>{c.kind ? COLLABORATOR_KIND_LABELS[c.kind] : "—"}</td>
                        <td>{c.organization ?? "—"}</td>
                        <td>{c.phone ?? "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Cadastrar Colaborador
            </h2>
            <form
              className="af-form-grid"
              onSubmit={(e) => {
                e.preventDefault();
                if (collabName.trim()) createCollabMutation.mutate();
              }}
            >
              <div className="af-field">
                <label htmlFor="spg-nome-completo" className="af-label">
                  Nome completo *
                </label>
                <input
                  id="spg-nome-completo"
                  className="af-input"
                  value={collabName}
                  onChange={(e) => setCollabName(e.target.value)}
                  required
                />
              </div>
              <div className="af-field">
                <label htmlFor="spg-perfil" className="af-label">
                  Perfil *
                </label>
                <select
                  id="spg-perfil"
                  className="af-select"
                  value={collabKind}
                  onChange={(e) => setCollabKind(e.target.value as typeof collabKind)}
                >
                  {Object.entries(COLLABORATOR_KIND_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label htmlFor="spg-instituicao-organizacao" className="af-label">
                  Instituição / Organização
                </label>
                <input
                  id="spg-instituicao-organizacao"
                  className="af-input"
                  value={collabOrg}
                  onChange={(e) => setCollabOrg(e.target.value)}
                  placeholder="Ex.: IFBA / ATER / Universidade"
                />
              </div>
              <div className="af-field">
                <label htmlFor="spg-telefone" className="af-label">
                  Telefone
                </label>
                <input
                  id="spg-telefone"
                  className="af-input"
                  value={collabPhone}
                  onChange={(e) => setCollabPhone(e.target.value)}
                />
              </div>
              <button type="submit" className="af-btn af-btn--primary af-btn--sm">
                + Cadastrar colaborador
              </button>
            </form>
          </div>
        </div>
      )}

      {tab === "conflicts" && (
        <div className="af-grid-2">
          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Impedimentos Cadastrados (RN32)
            </h2>
            <p className="af-card-subtitle" style={{ marginBottom: 14 }}>
              Agricultores com vínculo familiar ou societário não são escalados para avaliar um ao
              outro.
            </p>
            {(conflictsQuery.data ?? []).length === 0 ? (
              <div className="af-empty-state">Nenhum impedimento registrado.</div>
            ) : (
              <div className="af-table-wrap">
                <table className="af-table">
                  <thead>
                    <tr>
                      <th>Agricultor A</th>
                      <th>Agricultor B</th>
                      <th>Motivo</th>
                      <th style={{ textAlign: "right" }}>Ação</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(conflictsQuery.data ?? []).map((cf) => (
                      <tr key={cf.id}>
                        <td style={{ fontWeight: 600 }}>{cf.farmerAName}</td>
                        <td style={{ fontWeight: 600 }}>{cf.farmerBName}</td>
                        <td>{cf.reason ? CONFLICT_REASON_LABELS[cf.reason] : "—"}</td>
                        <td style={{ textAlign: "right" }}>
                          {cf.id && (
                            <button
                              type="button"
                              className="af-btn af-btn--ghost af-btn--sm"
                              onClick={() => deleteConflictMutation.mutate(cf.id!)}
                            >
                              Remover
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Registrar Impedimento entre Agricultores
            </h2>
            <form
              className="af-form-grid"
              onSubmit={(e) => {
                e.preventDefault();
                if (farmerAId && farmerBId && farmerAId !== farmerBId) {
                  createConflictMutation.mutate();
                }
              }}
            >
              <div className="af-field">
                <label htmlFor="spg-agricultor-a" className="af-label">
                  Agricultor A *
                </label>
                <select
                  id="spg-agricultor-a"
                  className="af-select"
                  value={farmerAId}
                  onChange={(e) => setFarmerAId(e.target.value)}
                  required
                >
                  <option value="">Selecione</option>
                  {(farmersQuery.data ?? []).map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label htmlFor="spg-agricultor-b" className="af-label">
                  Agricultor B *
                </label>
                <select
                  id="spg-agricultor-b"
                  className="af-select"
                  value={farmerBId}
                  onChange={(e) => setFarmerBId(e.target.value)}
                  required
                >
                  <option value="">Selecione</option>
                  {(farmersQuery.data ?? []).map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label htmlFor="spg-motivo-do-impedimento" className="af-label">
                  Motivo do impedimento *
                </label>
                <select
                  id="spg-motivo-do-impedimento"
                  className="af-select"
                  value={conflictReason}
                  onChange={(e) => setConflictReason(e.target.value as typeof conflictReason)}
                >
                  {Object.entries(CONFLICT_REASON_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <button type="submit" className="af-btn af-btn--primary af-btn--sm">
                + Registrar impedimento
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
