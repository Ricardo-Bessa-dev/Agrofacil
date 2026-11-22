// Lista de plantios de um manejo, com os em andamento e os de colheita encerrada (RN11, RN42).
import { useState } from "react";
import {
  groupPlantings,
  plantingLabel,
  showsFinishedPlantings,
  type PlantingItem,
} from "@agrofacil/domain";

interface PlantingSelectProps {
  recordType: "INPUT_APPLICATION" | "HARVEST" | "OUTFLOW";
  value: string;
  onChange: (plantingId: string) => void;
  plantings: readonly PlantingItem[];
}

export function PlantingSelect({ recordType, value, onChange, plantings }: PlantingSelectProps) {
  const [expanded, setExpanded] = useState(false);
  const { active, finished, hasHiddenFinished } = groupPlantings(plantings, {
    includeFinished: expanded || showsFinishedPlantings(recordType),
    selectedId: value,
  });
  const isRequired = recordType === "HARVEST";

  return (
    <div className="form-group" style={{ marginBottom: 0 }}>
      <label htmlFor="planting-select" className="form-label">
        Plantio {isRequired ? "*" : "(opcional)"}
      </label>
      <select
        id="planting-select"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="form-select"
        required={isRequired}
      >
        <option value="">
          {isRequired ? "Selecione o plantio..." : "Sem vínculo com plantio específico"}
        </option>
        {active.length > 0 && <PlantingGroup label="Em andamento" items={active} />}
        {finished.length > 0 && <PlantingGroup label="Colheita encerrada" items={finished} />}
      </select>
      {hasHiddenFinished && (
        <button
          type="button"
          onClick={() => setExpanded(true)}
          style={{
            marginTop: "8px",
            padding: "8px 0",
            border: "none",
            background: "none",
            color: "var(--color-primary)",
            fontSize: "13px",
            fontWeight: 600,
            cursor: "pointer",
          }}
        >
          Ver plantações terminadas
        </button>
      )}
    </div>
  );
}

function PlantingGroup({ label, items }: { label: string; items: PlantingItem[] }) {
  return (
    <optgroup label={label}>
      {items.map((item) => (
        <option key={item.id} value={item.id}>
          {plantingLabel(item)}
        </option>
      ))}
    </optgroup>
  );
}
