// Componente de Logotipo oficial do AgroFácil extraído da seção Identidade Visual (3:51 e 140:6 do Figma).
import logoMark from "../assets/figma/logo-mark.png";
import logoText from "../assets/figma/logo-text.png";

type LogoProps = {
  subtitle?: string;
  size?: "sm" | "md" | "lg";
};

export function Logo({ subtitle = "Coordenação", size = "md" }: LogoProps) {
  const dimensions = {
    sm: { markW: 36, markH: 38, textW: 105, textH: 34, subSize: 11, subTop: 28, subLeft: 76 },
    md: { markW: 44, markH: 46, textW: 123, textH: 40, subSize: 13, subTop: 33, subLeft: 92 },
    lg: { markW: 66, markH: 70, textW: 187, textH: 62, subSize: 18, subTop: 51, subLeft: 132 },
  }[size];

  return (
    <div
      style={{
        display: "inline-flex",
        alignItems: "center",
        position: "relative",
        height: dimensions.markH + 10,
        paddingRight: 8,
        userSelect: "none",
      }}
    >
      <img
        src={logoMark}
        alt="Símbolo AgroFácil"
        style={{
          width: dimensions.markW,
          height: dimensions.markH,
          objectFit: "contain",
          flexShrink: 0,
        }}
      />
      <img
        src={logoText}
        alt="AgroFácil"
        style={{
          width: dimensions.textW,
          height: dimensions.textH,
          objectFit: "contain",
          marginLeft: -4,
        }}
      />
      {subtitle && (
        <span
          style={{
            position: "absolute",
            left: dimensions.subLeft,
            top: dimensions.subTop,
            fontSize: dimensions.subSize,
            fontWeight: 600,
            color: "rgba(129, 94, 62, 0.97)",
            whiteSpace: "nowrap",
            lineHeight: 1,
          }}
        >
          {subtitle}
        </span>
      )}
    </div>
  );
}
