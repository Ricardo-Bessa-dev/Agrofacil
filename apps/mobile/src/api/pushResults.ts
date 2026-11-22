// Interpreta a resposta do /sync/push item a item: o que foi aceito sai da fila, o que foi recusado fica com o motivo.
import type { PushResponse } from "@agrofacil/api-types";

type MutationResult = NonNullable<PushResponse["results"]>[number];

/** Mínimo de uma mutação enviada que precisamos para casar com o resultado. */
export interface SentMutation {
  mutationId: string;
}

/**
 * Separa as mutações enviadas pelo resultado de cada uma.
 * - APPLIED: sai da fila.
 * - REJECTED: guarda o motivo (não adianta reenviar: a API recusaria de novo).
 * - sem resultado: continua na fila para o próximo envio.
 */
export function splitPushResults<T extends SentMutation>(
  sent: T[],
  results: PushResponse["results"],
): { remaining: T[]; rejected: (T & { rejection: string })[] } {
  const byMutationId = new Map((results ?? []).map((r) => [r.mutationId, r]));
  const remaining: T[] = [];
  const rejected: (T & { rejection: string })[] = [];

  for (const mutation of sent) {
    const result = byMutationId.get(mutation.mutationId);
    if (result?.status === "APPLIED") continue;
    if (result?.status === "REJECTED") {
      rejected.push({ ...mutation, rejection: rejectionMessage(result) });
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
