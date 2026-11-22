import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import type { PullResponse, ResultWrite, VisitView } from "@agrofacil/api-types";
import {
  formatDatePtBr,
  todayIso,
  VISIT_STATUS_LABELS,
  VISIT_TYPE_LABELS,
} from "@agrofacil/domain";
import { fetchSyncSnapshot, getOfflineQueue, submitMutation } from "../api/client";
import { useAuth } from "../auth/useAuth";

type Checklist = NonNullable<NonNullable<PullResponse["changes"]>["visitChecklistItems"]>;
type Answer = ResultWrite["answers"][number]["answer"];

const answers: Record<Answer, string> = {
  YES: "Sim",
  NO: "Não",
  NOT_APPLICABLE: "Não se aplica",
  NOT_CHECKED: "Não verificado",
};

export function VisitsPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const query = useQuery({
    queryKey: ["mobile-visits"],
    queryFn: fetchSyncSnapshot,
  });
  const visits = query.data?.changes?.visits ?? [];
  const visit = visits.find((item) => item.id === id);
  const recorder = visit?.participants?.find((item) => item.id === visit.recorderId);

  return (
    <div>
      <div className="page-top-bar">
        <Link className="page-top-bar-back" to={id ? "/visitas" : "/"}>
          Voltar
        </Link>
        <h1 className="page-top-bar-title">{id ? "Detalhes da visita" : "Visitas do SPG"}</h1>
      </div>
      {query.isPending ? (
        <p className="mobile-card">Carregando visitas…</p>
      ) : query.isError ? (
        <p role="alert" className="mobile-card">
          Não foi possível carregar as visitas.
        </p>
      ) : id && !visit ? (
        <p className="mobile-card">Visita indisponível.</p>
      ) : visit ? (
        <div className="mobile-card">
          <h2>{visit.property?.name ?? "Propriedade"}</h2>
          <p>
            {visit.type && VISIT_TYPE_LABELS[visit.type]} ·{" "}
            {visit.plannedOn && formatDatePtBr(visit.plannedOn)}
          </p>
          {visit.result?.doneOn && <p>Realizada em {formatDatePtBr(visit.result.doneOn)}.</p>}
          {visit.result?.recommendations && <p>{visit.result.recommendations}</p>}
          {visit.cancelReason && <p>{visit.cancelReason}</p>}
          {user?.id &&
            recorder?.person?.kind === "FARMER" &&
            recorder.person.id === user.id &&
            visit.status === "PLANNED" && (
              <VisitResultForm
                key={id}
                visit={visit}
                checklist={query.data?.changes?.visitChecklistItems ?? []}
              />
            )}
        </div>
      ) : visits.length ? (
        <ul className="mobile-card">
          {visits.map((item) => (
            <li key={item.id}>
              <Link to={`/visitas/${item.id}`}>
                {item.property?.name ?? "Propriedade"} ·{" "}
                {item.plannedOn && formatDatePtBr(item.plannedOn)}
                {item.status && ` · ${VISIT_STATUS_LABELS[item.status]}`}
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mobile-card">Nenhuma visita disponível.</p>
      )}
    </div>
  );
}

function VisitResultForm({ visit, checklist }: { visit: VisitView; checklist: Checklist }) {
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
      <h3>Registrar resultado</h3>
      <label className="form-label">
        Data de realização
        <input
          type="date"
          className="form-input"
          required
          max={todayIso()}
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />
      </label>
      <fieldset>
        <legend>Quem participou</legend>
        {visit.participants?.map((item) => {
          const id = item.id;
          if (!id) return null;
          return (
            <label className="form-label" key={id}>
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
              {item.person?.name ?? "Participante"}
              {item.role === "VISITOR" && " (visitador)"}
            </label>
          );
        })}
      </fieldset>
      <fieldset>
        <legend>Roteiro</legend>
        {items.map((item) => {
          const code = item.code!;
          return (
            <label className="form-label" key={code}>
              {item.question}
              {item.help && <small>{item.help}</small>}
              <select
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
            </label>
          );
        })}
      </fieldset>
      <label className="form-label">
        <input
          type="checkbox"
          checked={issuesFound}
          onChange={(event) => setIssuesFound(event.target.checked)}
        />{" "}
        Foram encontrados pontos a melhorar
      </label>
      {issuesFound && (
        <label className="form-label">
          Pontos a melhorar
          <textarea
            className="form-input"
            value={improvementPoints}
            onChange={(event) => setImprovementPoints(event.target.value)}
            required
            maxLength={2000}
          />
        </label>
      )}
      <label className="form-label">
        Recomendações
        <textarea
          className="form-input"
          value={recommendations}
          onChange={(event) => setRecommendations(event.target.value)}
          maxLength={2000}
        />
      </label>
      {feedback && <p role="status">{feedback}</p>}
      <button className="btn-primary" type="submit" disabled={saving}>
        {saving ? "Salvando…" : "Salvar resultado"}
      </button>
    </form>
  );
}
