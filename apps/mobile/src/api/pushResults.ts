// Interpreta a resposta do /sync/push item a item: o que foi aceito sai da fila, o que foi recusado é guardado para o agricultor ver.
import type { PushResponse } from "@agrofacil/api-types";

type MutationResult = NonNullable<PushResponse["results"]>[number];

/** Mínimo de uma mutação enviada que precisamos para casar com o resultado. */
export interface SentMutation {
  mutationId: string;
  entity: string;
  entityId: string;
  data?: Record<string, unknown>;
}

/** Alteração que a API recusou. Fica guardada no aparelho (com os dados) para não sumir sem aviso. */
export interface RejectedChange {
  mutationId: string;
  entity: string;
  entityId: string;
  message: string;
  /** ISO 8601 */
  rejectedAt: string;
  data?: Record<string, unknown>;
}

/**
 * Separa as mutações enviadas pelo resultado de cada uma.
 * - APPLIED: sai da fila.
 * - REJECTED: vira RejectedChange (não adianta reenviar: a API recusaria de novo).
 * - sem resultado: continua na fila para o próximo envio.
 */
export function splitPushResults<T extends SentMutation>(
  sent: T[],
  results: PushResponse["results"],
  nowIso: string,
): { remaining: T[]; rejected: RejectedChange[] } {
  const byMutationId = new Map((results ?? []).map((r) => [r.mutationId, r]));
  const remaining: T[] = [];
  const rejected: RejectedChange[] = [];

  for (const mutation of sent) {
    const result = byMutationId.get(mutation.mutationId);
    if (result?.status === "APPLIED") continue;
    if (result?.status === "REJECTED") {
      rejected.push({
        mutationId: mutation.mutationId,
        entity: mutation.entity,
        entityId: mutation.entityId,
        message: rejectionMessage(result),
        rejectedAt: nowIso,
        data: mutation.data,
      });
      continue;
    }
    remaining.push(mutation);
  }
  return { remaining, rejected };
}

/** Mensagem para o agricultor: o texto da API mais os campos com problema, se houver. */
export function rejectionMessage(result: MutationResult): string {
  const base = result.error?.message ?? "A alteração foi recusada pelo servidor.";
  const fields = (result.error?.errors ?? [])
    .map((e) => e.message)
    .filter((m): m is string => Boolean(m));
  return fields.length > 0 ? `${base} (${fields.join(" ")})` : base;
}
