// Diz quais registros ainda estão só no aparelho, esperando internet para chegar à API.
import { useMemo, useSyncExternalStore } from "react";
import { getOfflineQueue, OFFLINE_QUEUE_EVENT, type QueuedMutation } from "./client";

/** Ids dos registros esperando envio (criados ou editados sem internet); os recusados não esperam. */
export function pendingRecordIds(queue: QueuedMutation[]): Set<string> {
  return new Set(queue.filter((m) => m.entity === "record" && !m.rejection).map((m) => m.entityId));
}

function subscribe(onChange: () => void): () => void {
  window.addEventListener(OFFLINE_QUEUE_EVENT, onChange);
  return () => window.removeEventListener(OFFLINE_QUEUE_EVENT, onChange);
}

// O snapshot precisa ser um valor estável entre leituras iguais; por isso uma string, não um Set.
function snapshot(): string {
  return [...pendingRecordIds(getOfflineQueue())].sort().join(",");
}

/** Conjunto atualizado a cada mudança da fila offline. */
export function usePendingRecordIds(): Set<string> {
  const key = useSyncExternalStore(subscribe, snapshot);
  return useMemo(() => new Set(key ? key.split(",") : []), [key]);
}
