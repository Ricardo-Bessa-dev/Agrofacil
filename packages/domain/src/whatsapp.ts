// Monta links "click to chat" do WhatsApp (wa.me) a partir dos telefones guardados pela API.

const BRAZIL_COUNTRY_CODE = "55";
/** DDD + número sem o código do país: 10 dígitos (fixo) ou 11 (celular). */
const NATIONAL_LENGTHS = [10, 11];

/**
 * Número no formato do wa.me: só dígitos, com o código do país.
 * Aceita "+5571999998888" (E.164, como a API guarda), "71 99999-8888" ou "(71) 3333-4444".
 * @returns null se não houver dígitos suficientes
 */
export function whatsappNumber(phone: string | null | undefined): string | null {
  const raw = (phone ?? "").trim();
  const digits = raw.replace(/\D/g, "");
  if (digits.length < NATIONAL_LENGTHS[0]!) return null;
  // Com "+", o número já traz o código do país. Sem ele, 10/11 dígitos são um número nacional.
  if (!raw.startsWith("+") && NATIONAL_LENGTHS.includes(digits.length)) {
    return `${BRAZIL_COUNTRY_CODE}${digits}`;
  }
  return digits;
}

/** Link wa.me com mensagem opcional já preenchida; null se o telefone for inválido. */
export function whatsappLink(phone: string | null | undefined, message?: string): string | null {
  const number = whatsappNumber(phone);
  if (!number) return null;
  return message
    ? `https://wa.me/${number}?text=${encodeURIComponent(message)}`
    : `https://wa.me/${number}`;
}
