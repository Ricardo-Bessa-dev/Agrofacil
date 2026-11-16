import plantingIcon from "../assets/figma/record-planting-seedling.svg";
import inputIcon from "../assets/figma/record-input.svg";
import harvestIcon from "../assets/figma/record-harvest.svg";

interface Props {
  type: string;
  size?: number;
}

export function RecordTypeIcon({ type, size = 24 }: Props) {
  let bg = "var(--color-planting-bg)";
  let color = "var(--color-planting)";
  let icon = plantingIcon;
  let fallbackEmoji = "🌱";

  switch (type) {
    case "PLANTING":
      bg = "var(--color-planting-bg)";
      color = "var(--color-planting)";
      icon = plantingIcon;
      fallbackEmoji = "🌱";
      break;
    case "INPUT_APPLICATION":
      bg = "var(--color-input-bg)";
      color = "var(--color-input)";
      icon = inputIcon;
      fallbackEmoji = "💧";
      break;
    case "HARVEST":
      bg = "var(--color-harvest-bg)";
      color = "var(--color-harvest)";
      icon = harvestIcon;
      fallbackEmoji = "🌾";
      break;
    case "INPUT_PURCHASE":
      bg = "var(--color-purchase-bg)";
      color = "var(--color-purchase)";
      fallbackEmoji = "🛒";
      icon = "";
      break;
    case "OUTFLOW":
      bg = "var(--color-outflow-bg)";
      color = "var(--color-outflow)";
      fallbackEmoji = "🚚";
      icon = "";
      break;
    default:
      break;
  }

  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: "50%",
        backgroundColor: bg,
        color: color,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
      }}
    >
      {icon ? (
        <img
          src={icon}
          alt=""
          style={{
            width: size * 0.58,
            height: size * 0.58,
            objectFit: "contain",
          }}
        />
      ) : (
        <span style={{ fontSize: size * 0.48 }}>{fallbackEmoji}</span>
      )}
    </div>
  );
}
