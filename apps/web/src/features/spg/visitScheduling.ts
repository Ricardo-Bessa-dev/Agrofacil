// Regras do formulário de agendar visita: quem vem marcado, quem é enviado e por que não há visitantes (RN32, RN33).
import type { ParticipantWrite, VisitorSuggestion, VisitWrite } from "@agrofacil/api-types";

type Eligible = NonNullable<VisitorSuggestion["eligible"]>;
type VisitType = VisitWrite["type"];

/** Visitantes que já vêm marcados pela sugestão (RN33). */
export function preselectedIds(eligible: Eligible): string[] {
  return eligible.flatMap((e) => (e.preselected && e.person?.id ? [e.person.id] : []));
}

/**
 * Participantes da visita, cada um com o tipo de pessoa que a sugestão informou: na Comissão há
 * agricultores e colaboradores. O responsável pelo registro no app precisa ser agricultor; sem
 * nenhum, a visita é registrada no papel (RN32).
 */
export function buildParticipants(
  selectedIds: readonly string[],
  eligible: Eligible,
): { participants: ParticipantWrite[]; recorderId: string | undefined } {
  const people = eligible.flatMap((e) =>
    e.person && selectedIds.includes(e.person.id) ? [e.person] : [],
  );
  return {
    participants: people.map(({ kind, id }) => ({ person: { kind, id }, role: "VISITOR" })),
    recorderId: people.find((person) => person.kind === "FARMER")?.id,
  };
}

export interface NoVisitorsHelp {
  message: string;
  /** Tela onde a causa se resolve. */
  link?: { to: string; label: string };
}

/** Explica por que ninguém pode fazer a visita e onde resolver. */
export function noVisitorsHelp(input: {
  type: VisitType;
  farmer: { id?: string; name?: string; groupId?: string } | undefined;
  excludedCount: number;
}): NoVisitorsHelp {
  const { type, farmer, excludedCount } = input;
  const farmerName = farmer?.name ?? "O agricultor visitado";

  if (type === "PEER" && farmer && !farmer.groupId) {
    return {
      message: `${farmerName} não está em nenhum grupo do SPG. A visita de pares é feita por agricultores do mesmo grupo.`,
      link: farmer.id
        ? { to: `/agricultores/${farmer.id}`, label: "Abrir a ficha e escolher o grupo" }
        : undefined,
    };
  }
  if (excludedCount > 0) {
    return { message: "Ninguém pode fazer esta visita na data escolhida. Veja os motivos abaixo." };
  }
  if (type === "PEER") {
    return {
      message: `O grupo de ${farmerName} não tem outro agricultor para fazer a visita de pares.`,
      link: { to: "/grupos", label: "Ver grupos" },
    };
  }
  return {
    message: "A Comissão de Avaliação não tem membros ativos na data escolhida.",
    link: { to: "/spg", label: "Cadastrar a Comissão" },
  };
}
