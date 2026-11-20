// Cálculo e frases de status da certificação (doc 1 §2.3.7 e doc 3 §A.6).

export type EffectiveCertificationStatus =
  | "VALID"
  | "EXPIRING"
  | "EXPIRED"
  | "SUSPENDED"
  | "CANCELLED"
  | "NONE";

export type CertificationBadgeInfo = {
  status: EffectiveCertificationStatus;
  shortLabel: string;
  figmaLabel: "Certificação regular" | "Certificação irregular";
  phrase: string;
  tone: "success" | "warning" | "danger" | "neutral";
};

/** Formata data ISO ("2026-10-23") para "23/10/2026". */
function formatIsoDate(iso?: string | null): string {
  if (!iso) return "";
  const parts = iso.slice(0, 10).split("-");
  if (parts.length !== 3) return iso;
  return `${parts[2]}/${parts[1]}/${parts[0]}`;
}

/** Retorna os textos e tom visual para exibir o status da certificação no painel e no app. */
export function certificationBadgeInfo(
  status?: string | null,
  expiresOn?: string | null,
  daysToExpire?: number | null,
): CertificationBadgeInfo {
  const normalized = (status ?? "NONE") as EffectiveCertificationStatus;
  const formattedDate = formatIsoDate(expiresOn);

  switch (normalized) {
    case "VALID":
      return {
        status: "VALID",
        shortLabel: "Em dia",
        figmaLabel: "Certificação regular",
        phrase: formattedDate ? `Certificação em dia até ${formattedDate}` : "Certificação em dia",
        tone: "success",
      };
    case "EXPIRING":
      return {
        status: "EXPIRING",
        shortLabel: daysToExpire !== undefined && daysToExpire !== null ? `Vence em ${daysToExpire}d` : "Vencendo",
        figmaLabel: "Certificação regular",
        phrase:
          daysToExpire !== undefined && daysToExpire !== null
            ? `Certificação vence em ${daysToExpire} dias (${formattedDate})`
            : `Certificação vence em breve (${formattedDate})`,
        tone: "warning",
      };
    case "EXPIRED":
      return {
        status: "EXPIRED",
        shortLabel: "Vencida",
        figmaLabel: "Certificação irregular",
        phrase: formattedDate ? `Certificação vencida desde ${formattedDate}` : "Certificação vencida",
        tone: "danger",
      };
    case "SUSPENDED":
      return {
        status: "SUSPENDED",
        shortLabel: "Suspensa",
        figmaLabel: "Certificação irregular",
        phrase: "Certificação suspensa. Fale com a associação",
        tone: "danger",
      };
    case "CANCELLED":
      return {
        status: "CANCELLED",
        shortLabel: "Cancelada",
        figmaLabel: "Certificação irregular",
        phrase: "Certificação cancelada",
        tone: "neutral",
      };
    default:
      return {
        status: "NONE",
        shortLabel: "Sem certificação",
        figmaLabel: "Certificação irregular",
        phrase: "Sem certificação cadastrada",
        tone: "neutral",
      };
  }
}
