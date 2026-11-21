// Logotipo do AgroFácil (símbolo + nome) com o subtítulo do painel, como na Identidade Visual do Figma (3:51 e 140:6).
import logoMark from "../assets/figma/logo-mark.png";
import logoText from "../assets/figma/logo-text.png";

type LogoProps = {
  subtitle?: string;
  size?: "sm" | "md" | "lg";
};

/** Medidas em px de cada tamanho. textH segue a proporção da imagem do nome (252 × 83). */
const SIZES = {
  sm: { markW: 36, markH: 38, textW: 105, textH: 35, subSize: 11 },
  md: { markW: 44, markH: 46, textW: 123, textH: 41, subSize: 13 },
  lg: { markW: 66, markH: 70, textW: 187, textH: 62, subSize: 18 },
} as const;

const SUBTITLE_COLOR = "rgba(129, 94, 62, 0.97)";

export function Logo({ subtitle = "Coordenação", size = "md" }: LogoProps) {
  const dimensions = SIZES[size];

  return (
    <div style={{ display: "inline-flex", alignItems: "center", gap: 2, userSelect: "none" }}>
      <img
        src={logoMark}
        alt=""
        style={{
          width: dimensions.markW,
          height: dimensions.markH,
          objectFit: "contain",
          flexShrink: 0,
        }}
      />
      {/* O subtítulo fica numa coluna abaixo do nome, e não por cima dele: assim não se sobrepõem em nenhum tamanho. */}
      <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end" }}>
        <img
          src={logoText}
          alt="AgroFácil"
          style={{ width: dimensions.textW, height: dimensions.textH, objectFit: "contain" }}
        />
        {subtitle && (
          <span
            style={{
              fontSize: dimensions.subSize,
              fontWeight: 600,
              color: SUBTITLE_COLOR,
              whiteSpace: "nowrap",
              lineHeight: 1.1,
              paddingRight: 4,
            }}
          >
            {subtitle}
          </span>
        )}
      </div>
    </div>
  );
}
