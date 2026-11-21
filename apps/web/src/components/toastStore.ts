// Estado dos avisos rápidos (toasts): fila em memória que qualquer parte do painel pode alimentar.

export type ToastTone = "danger" | "success";

export interface Toast {
  id: number;
  message: string;
  tone: ToastTone;
}

/** Tempo (ms) que o aviso fica na tela. */
const TOAST_DURATION_MS = 7000;
/** Avisos visíveis ao mesmo tempo; os mais antigos saem primeiro. */
const MAX_TOASTS = 4;

let toasts: Toast[] = [];
let nextId = 1;
const listeners = new Set<() => void>();

function emit(): void {
  for (const listener of listeners) listener();
}

export function dismissToast(id: number): void {
  toasts = toasts.filter((t) => t.id !== id);
  emit();
}

/** Mostra um aviso. Pode ser chamado de fora do React (ex.: no MutationCache). */
export function showToast(message: string, tone: ToastTone = "danger"): void {
  const id = nextId++;
  toasts = [...toasts, { id, message, tone }].slice(-MAX_TOASTS);
  emit();
  setTimeout(() => dismissToast(id), TOAST_DURATION_MS);
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getToasts(): Toast[] {
  return toasts;
}
