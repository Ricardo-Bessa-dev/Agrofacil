// Visitas do SPG no app: lista, detalhe e, para o responsável pelo registro, o formulário do resultado.
import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import type { VisitView } from "@agrofacil/api-types";
import { formatDatePtBr, VISIT_STATUS_LABELS, VISIT_TYPE_LABELS } from "@agrofacil/domain";
import { fetchSyncSnapshot } from "../api/client";
import { sortVisits, visitDate } from "../api/visitOrder";
import visitsIcon from "../assets/visits.svg";
import { useAuth } from "../auth/useAuth";
import { VisitResultForm } from "../components/VisitResultForm";

const STATUS_BADGE_CLASS: Record<NonNullable<VisitView["status"]>, string> = {
  PLANNED: "badge-planned",
  DONE: "badge-complete",
  CANCELLED: "badge-cancelled",
};

export function VisitsPage() {
  const { id } = useParams();
  const query = useQuery({ queryKey: ["mobile-visits"], queryFn: fetchSyncSnapshot });
  const visits = query.data?.changes?.visits ?? [];
  const visit = visits.find((item) => item.id === id);

  return (
    <div>
      <div className="page-top-bar">
        <Link className="page-top-bar-back" to={id ? "/visitas" : "/"}>
          <span aria-hidden="true">←</span>
          <span>Voltar</span>
        </Link>
        <h1 className="page-top-bar-title">{id ? "Detalhes da visita" : "Visitas do SPG"}</h1>
        <div style={{ width: "48px" }} />
      </div>

      <div className="visit-page">
        {query.isPending ? (
          <EmptyState title="Carregando visitas..." />
        ) : query.isError ? (
          <div className="empty-state">
            <div className="empty-state-title" role="alert">
              Não foi possível carregar as visitas
            </div>
            <button type="button" className="btn-secondary" onClick={() => void query.refetch()}>
              Tentar novamente
            </button>
          </div>
        ) : id && !visit ? (
          <EmptyState
            title="Visita indisponível"
            description="Ela pode ter sido cancelada ou não aparecer mais para você."
          />
        ) : visit ? (
          <VisitDetail visit={visit} checklist={query.data?.changes?.visitChecklistItems ?? []} />
        ) : visits.length === 0 ? (
          <EmptyState
            title="Nenhuma visita por enquanto"
            description="Quando a associação marcar uma visita na sua roça, ou uma em que você participe, ela aparece aqui."
          />
        ) : (
          sortVisits(visits).map((item) => <VisitCard key={item.id} visit={item} />)
        )}
      </div>
    </div>
  );
}

function VisitCard({ visit }: { visit: VisitView }) {
  return (
    <Link to={`/visitas/${visit.id}`} className="mobile-card shortcut-card visit-card">
      <span className="shortcut-card-icon">
        <img src={visitsIcon} alt="" width={22} height={22} />
      </span>
      <span className="shortcut-card-text">
        <span className="shortcut-card-title">{visit.property?.name ?? "Propriedade"}</span>
        <span className="shortcut-card-subtitle">{visitSummary(visit)}</span>
      </span>
      <StatusBadge visit={visit} />
    </Link>
  );
}

function VisitDetail({
  visit,
  checklist,
}: {
  visit: VisitView;
  checklist: Parameters<typeof VisitResultForm>[0]["checklist"];
}) {
  const { user } = useAuth();
  const recorder = visit.participants?.find((item) => item.id === visit.recorderId);
  const canRecord =
    visit.status === "PLANNED" &&
    user?.id !== undefined &&
    recorder?.person?.kind === "FARMER" &&
    recorder.person.id === user.id;

  return (
    <>
      <section className="mobile-card">
        <div className="mobile-card-header">
          <h2 className="mobile-card-title">{visit.property?.name ?? "Propriedade"}</h2>
          <StatusBadge visit={visit} />
        </div>
        <p className="visit-text">{visitSummary(visit)}</p>
      </section>

      {(visit.participants?.length ?? 0) > 0 && (
        <section className="mobile-card">
          <h2 className="visit-section-title">Quem participa</h2>
          <ul className="visit-people">
            {visit.participants?.map((item) => (
              <li key={item.id}>
                {item.person?.name ?? "Participante"}
                {item.role === "VISITOR" && <small> (visitador)</small>}
              </li>
            ))}
          </ul>
        </section>
      )}

      {visit.result?.recommendations && (
        <section className="mobile-card">
          <h2 className="visit-section-title">Recomendações</h2>
          <p className="visit-text">{visit.result.recommendations}</p>
        </section>
      )}

      {visit.cancelReason && (
        <section className="mobile-card">
          <h2 className="visit-section-title">Motivo do cancelamento</h2>
          <p className="visit-text">{visit.cancelReason}</p>
        </section>
      )}

      {canRecord && <VisitResultForm key={visit.id} visit={visit} checklist={checklist} />}
    </>
  );
}

function StatusBadge({ visit }: { visit: VisitView }) {
  if (!visit.status) return null;
  return (
    <span className={`badge-status ${STATUS_BADGE_CLASS[visit.status]}`}>
      {VISIT_STATUS_LABELS[visit.status]}
    </span>
  );
}

function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="empty-state">
      <img src={visitsIcon} alt="" width={40} height={40} className="empty-state-icon" />
      <div className="empty-state-title">{title}</div>
      {description && <p className="empty-state-desc">{description}</p>}
    </div>
  );
}

/** Ex.: "Visita do grupo · 25/11/2026". */
function visitSummary(visit: VisitView): string {
  const type = visit.type ? VISIT_TYPE_LABELS[visit.type] : undefined;
  const date = visitDate(visit);
  return [type, date ? formatDatePtBr(date) : undefined].filter(Boolean).join(" · ");
}
