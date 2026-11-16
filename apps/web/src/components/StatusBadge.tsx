// Componentes visuais de selo de certificação e ícones de manejo fiéis ao protótipo do Figma.
import { certificationBadgeInfo } from "@agrofacil/domain";
import statusOkSvg from "../assets/figma/status-ok.svg";
import statusErrorSvg from "../assets/figma/status-error.svg";
import recordInputSvg from "../assets/figma/record-input.svg";
import recordPlantingBgSvg from "../assets/figma/record-planting-bg.svg";
import recordPlantingSeedlingSvg from "../assets/figma/record-planting-seedling.svg";
import recordHarvestSvg from "../assets/figma/record-harvest.svg";

type CertBadgeProps = {
  status?: string | null;
  expiresOn?: string | null;
  daysToExpire?: number | null;
  variant?: "figma" | "detailed";
};

export function CertificationStatusBadge({
  status,
  expiresOn,
  daysToExpire,
  variant = "figma",
}: CertBadgeProps) {
  const info = certificationBadgeInfo(status, expiresOn, daysToExpire);
  const isRegular = info.status === "VALID" || info.status === "EXPIRING";

  return (
    <span className={`af-cert-badge ${info.tone}`} title={info.phrase}>
      <img src={isRegular ? statusOkSvg : statusErrorSvg} alt="" />
      <span>{variant === "figma" ? info.figmaLabel : info.shortLabel}</span>
    </span>
  );
}

export function RecordTypeAvatar({ type }: { type?: string | null }) {
  if (type === "PLANTING") {
    return (
      <span
        style={{
          position: "relative",
          width: 36,
          height: 36,
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          flexShrink: 0,
        }}
      >
        <img
          src={recordPlantingBgSvg}
          alt=""
          style={{ position: "absolute", inset: 0, width: 36, height: 36 }}
        />
        <img
          src={recordPlantingSeedlingSvg}
          alt="Plantio"
          style={{ position: "relative", width: 22, height: 22 }}
        />
      </span>
    );
  }

  if (type === "HARVEST") {
    return (
      <img
        src={recordHarvestSvg}
        alt="Colheita"
        style={{ width: 36, height: 36, flexShrink: 0 }}
      />
    );
  }

  return (
    <img
      src={recordInputSvg}
      alt="Registro"
      style={{ width: 36, height: 36, flexShrink: 0 }}
    />
  );
}
