// Formulário do resultado da visita, para o responsável pelo registro; sem internet, o resultado fica na fila.
import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { PullResponse, ResultWrite, VisitView } from "@agrofacil/api-types";
import { todayIso } from "@agrofacil/domain";
import { getOfflineQueue, submitMutation } from "../api/client";

type Checklist = NonNullable<NonNullable<PullResponse["changes"]>["visitChecklistItems"]>;
type Answer = ResultWrite["answers"][number]["answer"];

/** Limite da API para os textos do resultado. */
const MAX_TEXT_LENGTH = 2000;

const answers: Record<Answer, string> = {
  YES: "Sim",
  NO: "Não",
  NOT_APPLICABLE: "Não se aplica",
  NOT_CHECKED: "Não verificado",
};

export function VisitResultForm({ visit, checklist }: { visit: VisitView; checklist: Checklist }) {
  const queryClient = useQueryClient();
  const pending = getOfflineQueue().find(
    (item) => item.entity === "visit" && item.entityId === visit.id,
  );
  const draft = pending?.data as ResultWrite | undefined;
  const [date, setDate] = useState(draft?.doneOn ?? todayIso());
  const [attended, setAttended] = useState<string[]>(draft?.attendedParticipantIds ?? []);
  const [responses, setResponses] = useState<Record<string, Answer>>(() =>
    Object.fromEntries((draft?.answers ?? []).map((item) => [item.itemCode, item.answer])),
  );
  const [issuesFound, setIssuesFound] = useState(draft?.issuesFound ?? false);
  const [improvementPoints, setImprovementPoints] = useState(draft?.improvementPoints ?? "");
  const [recommendations, setRecommendations] = useState(draft?.recommendations ?? "");
  const [feedback, setFeedback] = useState(
    pending?.rejection ?? (pending ? "Resultado aguardando envio." : ""),
  );
  const [saving, setSaving] = useState(false);

  const items = checklist
    .filter(
      (item) =>
        item.code && item.active && (item.appliesTo === "BOTH" || item.appliesTo === visit.type),
    )
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!visit.id) return;
    if (
      !visit.participants?.some(
        (item) => item.id && item.role === "VISITOR" && attended.includes(item.id),
      )
    ) {
      setFeedback("Marque pelo menos um visitador presente.");
      return;
    }
    setSaving(true);
    try {
      const data: ResultWrite = {
        doneOn: date,
        attendedParticipantIds: attended,
        answers: items.flatMap(({ code }) =>
          code && responses[code] ? [{ itemCode: code, answer: responses[code] }] : [],
        ),
        issuesFound,
        improvementPoints: issuesFound ? improvementPoints.trim() : undefined,
        recommendations: recommendations.trim() || undefined,
      };
      const result = await submitMutation("visit", visit.id, { ...data });
      setFeedback(
        result === "queued"
          ? "Resultado salvo no celular. Envio pendente."
          : "Resultado registrado.",
      );
      await queryClient.invalidateQueries({ queryKey: ["mobile-visits"] });
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : "Erro ao registrar a visita.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={save}>
      <section className="mobile-card">
        <h2 className="visit-section-title">Registrar resultado</h2>
        <label className="form-label" htmlFor="visit-done-on">
          Data de realização
        </label>
        <input
          id="visit-done-on"
          type="date"
          className="form-input"
          required
          max={todayIso()}
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />
      </section>
      <fieldset className="mobile-card visit-fieldset">
        <legend className="visit-section-title">Quem participou</legend>
        {visit.participants?.map((item) => {
          const id = item.id;
          if (!id) return null;
          return (
            <label className="visit-check" key={id}>
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
              />
              <span>
                {item.person?.name ?? "Participante"}
                {item.role === "VISITOR" && <small> (visitador)</small>}
              </span>
            </label>
          );
        })}
      </fieldset>
      <fieldset className="mobile-card visit-fieldset">
        <legend className="visit-section-title">Roteiro</legend>
        {items.map((item) => {
          const code = item.code!;
          return (
            <div className="form-group" key={code}>
              <label className="form-label" htmlFor={`visit-item-${code}`}>
                {item.question}
              </label>
              {item.help && <p className="visit-help">{item.help}</p>}
              <select
                id={`visit-item-${code}`}
                className="form-select"
                required
                value={responses[code] ?? ""}
                onChange={(event) =>
                  setResponses((previous) => ({
                    ...previous,
                    [code]: event.target.value as Answer,
                  }))
                }
              >
                <option value="">Escolha uma resposta</option>
                {Object.entries(answers).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
          );
        })}
      </fieldset>
      <section className="mobile-card">
        <h2 className="visit-section-title">Conclusão</h2>
        <label className="visit-check">
          <input
            type="checkbox"
            checked={issuesFound}
            onChange={(event) => setIssuesFound(event.target.checked)}
          />
          <span>Foram encontrados pontos a melhorar</span>
        </label>
        {issuesFound && (
          <div className="form-group">
            <label className="form-label" htmlFor="visit-improvements">
              Pontos a melhorar
            </label>
            <textarea
              id="visit-improvements"
              className="form-textarea"
              rows={3}
              value={improvementPoints}
              onChange={(event) => setImprovementPoints(event.target.value)}
              required
              maxLength={MAX_TEXT_LENGTH}
            />
          </div>
        )}
        <label className="form-label" htmlFor="visit-recommendations">
          Recomendações (opcional)
        </label>
        <textarea
          id="visit-recommendations"
          className="form-textarea"
          rows={3}
          value={recommendations}
          onChange={(event) => setRecommendations(event.target.value)}
          maxLength={MAX_TEXT_LENGTH}
        />
      </section>
      {feedback && (
        <p role="status" className="mobile-card">
          {feedback}
        </p>
      )}
      <button className="btn-primary" type="submit" disabled={saving}>
        {saving ? "Salvando…" : "Salvar resultado"}
      </button>
    </form>
  );
}
