// Lista de unidades de um campo de quantidade, com os códigos que a API aceita.
import { quantityUnitOptions, type UnitOption } from "@agrofacil/domain";

interface QuantityUnitSelectProps {
  value: string;
  onChange: (code: string) => void;
  /** Catálogo de unidades do espelho local; vazio enquanto não chegou. */
  units: readonly UnitOption[];
}

export function QuantityUnitSelect({ value, onChange, units }: QuantityUnitSelectProps) {
  return (
    <select
      aria-label="Unidade da quantidade"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="form-select"
      style={{ flex: 1 }}
    >
      {quantityUnitOptions(units, value).map((unit) => (
        <option key={unit.code} value={unit.code}>
          {unit.label}
        </option>
      ))}
    </select>
  );
}
