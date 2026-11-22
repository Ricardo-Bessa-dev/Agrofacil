// Campo de plantio vinculado do painel, com os em andamento e os de colheita encerrada (RN11, RN42).
import { useState } from "react";
import {
  groupPlantings,
  plantingLabel,
  showsFinishedPlantings,
  type PlantingItem,
} from "@agrofacil/domain";

type NumberedPlanting = PlantingItem & { number?: number };

interface PlantingSelectProps {
  recordType: "INPUT_APPLICATION" | "HARVEST" | "OUTFLOW";
  value: string;
  onChange: (plantingId: string) => void;
  plantings: readonly NumberedPlanting[];
}

export function PlantingSelect({ recordType, value, onChange, plantings }: PlantingSelectProps) {
  const [expanded, setExpanded] = useState(false);
  const { active, finished, hasHiddenFinished } = groupPlantings(plantings, {
    includeFinished: expanded || showsFinishedPlantings(recordType),
    selectedId: value,
  });
  const isRequired = recordType === "HARVEST";

  return (
    <div className="af-field">
      <label htmlFor="rec-plantio-vinculado" className="af-label">
        Plantio vinculado {isRequired ? "*" : "(recomendado)"}
      </label>
      <select
        id="rec-plantio-vinculado"
        className="af-select"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={isRequired}
      >
        <option value="">Selecione o ciclo de plantio</option>
        {active.length > 0 && <PlantingGroup label="Em andamento" items={active} />}
        {finished.length > 0 && <PlantingGroup label="Colheita encerrada" items={finished} />}
      </select>
      {hasHiddenFinished && (
        <button
          type="button"
          className="af-btn af-btn--outline af-btn--sm"
          style={{ marginTop: 8, alignSelf: "flex-start" }}
          onClick={() => setExpanded(true)}
        >
          Ver plantações terminadas
        </button>
      )}
    </div>
  );
}

function PlantingGroup({ label, items }: { label: string; items: NumberedPlanting[] }) {
  return (
    <optgroup label={label}>
      {items.map((item) => (
        <option key={item.id} value={item.id}>
          #{item.number} — {plantingLabel(item)}
        </option>
      ))}
    </optgroup>
  );
}
