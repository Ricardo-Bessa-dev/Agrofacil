// Funções de CPF: tira a máscara, valida os dígitos verificadores e mascara para exibir (RN02).

const CPF_LENGTH = 11;

/** Remove tudo que não é dígito: "123.456.789-09" → "12345678909". */
export function normalizeCpf(value: string): string {
  return value.replace(/\D/g, "");
}

/** Calcula um dígito verificador a partir dos dígitos anteriores (pesos decrescentes até 2). */
function checkDigit(digits: number[]): number {
  const firstWeight = digits.length + 1;
  const sum = digits.reduce((acc, digit, index) => acc + digit * (firstWeight - index), 0);
  const rest = (sum * 10) % 11;
  return rest === 10 ? 0 : rest;
}

/**
 * Confere se o CPF tem 11 dígitos e os dígitos verificadores certos. Aceita com ou sem máscara.
 * Sequências repetidas ("111.111.111-11") passam no cálculo, mas não são CPFs válidos.
 */
export function isValidCpf(value: string): boolean {
  const cpf = normalizeCpf(value);
  if (cpf.length !== CPF_LENGTH || /^(\d)\1+$/.test(cpf)) return false;
  const digits = [...cpf].map(Number);
  const first = checkDigit(digits.slice(0, 9));
  const second = checkDigit(digits.slice(0, 10));
  return digits[9] === first && digits[10] === second;
}

/** Formata para exibir: "12345678909" → "123.456.789-09". Valores incompletos voltam sem máscara. */
export function formatCpf(value: string): string {
  const cpf = normalizeCpf(value);
  if (cpf.length !== CPF_LENGTH) return cpf;
  return `${cpf.slice(0, 3)}.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-${cpf.slice(9)}`;
}

/** Esconde o começo e o fim, como nas listas do painel: "***.456.789-**" (doc 1 §3.7.4). */
export function maskCpf(value: string): string {
  const cpf = normalizeCpf(value);
  if (cpf.length !== CPF_LENGTH) return "***.***.***-**";
  return `***.${cpf.slice(3, 6)}.${cpf.slice(6, 9)}-**`;
}
