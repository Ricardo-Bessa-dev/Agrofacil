// Gera UUIDs v4 mesmo onde crypto.randomUUID não existe (HTTP sem contexto seguro ou Chrome anterior ao 92).

/** Posições dos hífens no formato 8-4-4-4-12 de um UUID. */
const HYPHEN_POSITIONS = [4, 6, 8, 10];
const UUID_BYTES = 16;

/**
 * Gera um UUID v4 aleatório.
 *
 * Usa crypto.randomUUID quando disponível. O navegador só o expõe em contexto seguro (HTTPS ou localhost),
 * então, ao abrir o app pelo IP da rede local, cai no crypto.getRandomValues, que existe em qualquer contexto.
 */
export function generateUuid(): string {
  if (typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return uuidFromRandomBytes(crypto.getRandomValues(new Uint8Array(UUID_BYTES)));
}

/** Monta um UUID v4 (RFC 9562) a partir de 16 bytes aleatórios. Exportada para os testes. */
export function uuidFromRandomBytes(bytes: Uint8Array): string {
  const b = Uint8Array.from(bytes);
  b[6] = ((b[6] ?? 0) & 0x0f) | 0x40; // versão 4
  b[8] = ((b[8] ?? 0) & 0x3f) | 0x80; // variante RFC
  return Array.from(
    b,
    (byte, i) => (HYPHEN_POSITIONS.includes(i) ? "-" : "") + byte.toString(16).padStart(2, "0"),
  ).join("");
}
