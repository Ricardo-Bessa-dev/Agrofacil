// Telas de Registros de Manejo do painel do coordenador: listagem estilo Figma (49:852), criação em nome do agricultor (RN16), detalhe, pedidos de correção (RN17) e histórico Envers (ADR-10).
import React, { useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  APPLICATION_PURPOSE_LABELS,
  AREA_UNIT_LABELS,
  COMPLETENESS_LABELS,
  formatArea,
  formatDatePtBr,
  formatDateTimePtBr,
  formatQuantity,
  MISSING_FIELD_LABELS,
  missingFields,
  OUTFLOW_DESTINATION_LABELS,
  PROPAGATION_TYPE_LABELS,
  RECORD_SOURCE_LABELS,
  RECORD_TYPE_LABELS,
  SEED_ORIGIN_LABELS,
  todayIso,
} from "@agrofacil/domain";
import type { RecordWrite } from "@agrofacil/api-types";
import { api, ApiProblemError, downloadFile, unwrap } from "../../api/client";
import { fetchAllPages } from "../../api/pagination";
import { AttachmentGallery } from "../../components/AttachmentGallery";
import { RecordTypeAvatar } from "../../components/StatusBadge";

type RecordType = "PLANTING" | "INPUT_APPLICATION" | "HARVEST" | "INPUT_PURCHASE" | "OUTFLOW";

export function RecordsListPage() {
  const [searchParams] = useSearchParams();
  const [type, setType] = useState<"" | RecordType>(
    (searchParams.get("type") as "" | RecordType) ?? "",
  );
  const [completeness, setCompleteness] = useState<"" | "COMPLETE" | "INCOMPLETE">(
    (searchParams.get("completeness") as "" | "COMPLETE" | "INCOMPLETE") ?? "",
  );
  const [hasOpenCorrection, setHasOpenCorrection] = useState<boolean>(
    searchParams.get("hasOpenCorrection") === "true",
  );
  const [search, setSearch] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);

  const recordsQuery = useQuery({
    queryKey: ["records", { type, completeness, hasOpenCorrection, search, from, to, page }],
    queryFn: () =>
      unwrap(
        api.GET("/records", {
          params: {
            query: {
              filter: {
                type: type || undefined,
                completeness: completeness || undefined,
                hasOpenCorrection: hasOpenCorrection ? true : undefined,
                search: search.trim() || undefined,
                from: from || undefined,
                to: to || undefined,
              },
              pageable: { page, size: 15, sort: ["performedOn,desc"] },
            },
          },
        }),
      ),
  });

  const items = recordsQuery.data?.content ?? [];
  const meta = recordsQuery.data?.page;

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Registros de Manejo</h1>
          <p className="af-page-subtitle">
            Caderno de campo consolidado: plantios, aplicações de insumos, colheitas, compras e
            saídas.
          </p>
        </div>
        <div className="af-topbar-actions">
          <button
            type="button"
            className="af-btn af-btn--outline af-btn--sm"
            onClick={() => downloadFile("/reports/records?format=csv", "registros-manejo.csv")}
          >
            Exportar CSV
          </button>
          <Link to="/registros/novo" className="af-btn af-btn--primary af-btn--sm">
            + Registrar manejo
          </Link>
        </div>
      </div>

      {/* Chips de filtro rápido inspirados no protótipo do Figma (49:852) */}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 16 }}>
        {(
          [
            ["", "Todos os manejos"],
            ["PLANTING", "Plantio"],
            ["INPUT_APPLICATION", "Aplicação de insumo"],
            ["HARVEST", "Colheita"],
            ["INPUT_PURCHASE", "Compra de insumo"],
            ["OUTFLOW", "Saída / Venda"],
          ] as const
        ).map(([val, label]) => (
          <button
            key={val}
            type="button"
            className={`af-btn af-btn--sm ${type === val ? "af-btn--primary" : "af-btn--outline"}`}
            onClick={() => {
              setType(val);
              setPage(0);
            }}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="af-card">
        <div className="af-filters-bar" style={{ marginBottom: 16 }}>
          <div className="af-field" style={{ minWidth: 220, flex: 1 }}>
            <label className="af-label">Buscar por cultura, insumo ou agricultor</label>
            <input
              type="search"
              className="af-input"
              placeholder="Ex.: Alface, Biofertilizante, João..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>

          <div className="af-field" style={{ minWidth: 160 }}>
            <label className="af-label">Completude (RN09)</label>
            <select
              className="af-select"
              value={completeness}
              onChange={(e) => {
                setCompleteness(e.target.value as typeof completeness);
                setPage(0);
              }}
            >
              <option value="">Todos</option>
              <option value="COMPLETE">Completos</option>
              <option value="INCOMPLETE">Incompletos</option>
            </select>
          </div>

          <div className="af-field" style={{ minWidth: 140 }}>
            <label className="af-label">De</label>
            <input
              type="date"
              className="af-input"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(0);
              }}
            />
          </div>

          <div className="af-field" style={{ minWidth: 140 }}>
            <label className="af-label">Até</label>
            <input
              type="date"
              className="af-input"
              value={to}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(0);
              }}
            />
          </div>

          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: 6,
              paddingBottom: 10,
              cursor: "pointer",
              fontSize: "0.85rem",
              fontWeight: 600,
            }}
          >
            <input
              type="checkbox"
              checked={hasOpenCorrection}
              onChange={(e) => {
                setHasOpenCorrection(e.target.checked);
                setPage(0);
              }}
            />
            Com correção aberta
          </label>
        </div>

        {recordsQuery.isLoading ? (
          <div className="af-empty-state">Carregando registros de manejo...</div>
        ) : items.length === 0 ? (
          <div className="af-empty-state">
            Nenhum registro de manejo encontrado para os filtros aplicados.
          </div>
        ) : (
          <div>
            {items.map((rec) => (
              <Link key={rec.id} to={`/registros/${rec.id}`} className="af-record-row">
                <RecordTypeAvatar type={rec.type} />
                <div>
                  <div className="af-record-row-title">
                    #{rec.number} • {rec.type ? RECORD_TYPE_LABELS[rec.type] : "Manejo"}
                    {rec.subject ? ` — ${rec.subject}` : ""}
                  </div>
                  <div className="af-record-row-meta">
                    <strong>{rec.farmer?.name ?? "Agricultor"}</strong> •{" "}
                    {rec.property?.name ?? "Propriedade"}
                    {rec.plot?.name ? ` (${rec.plot.name})` : ""} •{" "}
                    {formatDatePtBr(rec.performedOn)}
                    {rec.quantity ? ` • Qtd: ${formatQuantity(rec.quantity)}` : ""}
                    {rec.area ? ` • Área: ${formatArea(rec.area)}` : ""}
                  </div>
                </div>
                <div
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "flex-end",
                    gap: 4,
                  }}
                >
                  <span
                    className={`af-badge ${
                      rec.completeness === "COMPLETE" ? "af-badge--valid" : "af-badge--warning"
                    }`}
                  >
                    {rec.completeness ? COMPLETENESS_LABELS[rec.completeness] : "—"}
                  </span>
                  {(rec.openCorrectionRequests ?? 0) > 0 && (
                    <span className="af-badge af-badge--danger">
                      {rec.openCorrectionRequests} correção(ões)
                    </span>
                  )}
                </div>
              </Link>
            ))}
          </div>
        )}

        {meta && (meta.totalPages ?? 1) > 1 && (
          <div className="af-pagination">
            <span>
              Página {(meta.number ?? 0) + 1} de {meta.totalPages} ({meta.totalElements} registros)
            </span>
            <div style={{ display: "flex", gap: 8 }}>
              <button
                type="button"
                className="af-btn af-btn--outline af-btn--sm"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Anterior
              </button>
              <button
                type="button"
                className="af-btn af-btn--outline af-btn--sm"
                disabled={page + 1 >= (meta.totalPages ?? 1)}
                onClick={() => setPage((p) => p + 1)}
              >
                Próxima
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function RecordCreatePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();

  const [type, setType] = useState<RecordType>("PLANTING");
  const [farmerId, setFarmerId] = useState(searchParams.get("farmerId") ?? "");
  const [propertyId, setPropertyId] = useState(searchParams.get("propertyId") ?? "");
  const [plotId, setPlotId] = useState("");
  const [performedOn, setPerformedOn] = useState(todayIso());
  const [notes, setNotes] = useState("");

  // Campos de Plantio / Saída / Aplicação / Colheita
  const [cropId, setCropId] = useState("");
  const [cropOtherName, setCropOtherName] = useState("");
  const [variety, setVariety] = useState("");
  const [areaValue, setAreaValue] = useState("");
  const [areaUnit, setAreaUnit] = useState<"TAREFA" | "HECTARE" | "SQUARE_METER">("TAREFA");
  const [seedOrigin, setSeedOrigin] = useState<
    "" | "OWN" | "ORGANIC_PURCHASED" | "CONVENTIONAL_PURCHASED" | "SEED_BANK"
  >("OWN");
  const [propagationType, setPropagationType] = useState<
    "" | "SEED" | "SEEDLING" | "CUTTING" | "OTHER"
  >("SEED");
  const [expectedHarvestOn, setExpectedHarvestOn] = useState("");

  const [plantingId, setPlantingId] = useState("");
  const [inputId, setInputId] = useState("");
  const [inputOtherName, setInputOtherName] = useState("");
  const [purpose, setPurpose] = useState<
    "FERTILIZATION" | "PEST_DISEASE_CONTROL" | "SOIL_CORRECTION" | "OTHER"
  >("FERTILIZATION");
  const [qtyValue, setQtyValue] = useState("");
  const [qtyUnit, setQtyUnit] = useState("KG");
  const [isFinal, setIsFinal] = useState(false);
  const [supplier, setSupplier] = useState("");
  const [destination, setDestination] = useState<
    | "FAIR"
    | "COOPERATIVE"
    | "PNAE"
    | "PAA"
    | "MIDDLEMAN"
    | "DIRECT_SALE"
    | "OWN_CONSUMPTION"
    | "DONATION"
    | "OTHER"
  >("FAIR");
  const [buyerName, setBuyerName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const farmersQuery = useQuery({
    queryKey: ["farmers", "all-select", "ACTIVE"],
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/farmers", {
          params: {
            query: { filter: { status: "ACTIVE" }, pageable: { ...pageable, sort: ["name,asc"] } },
          },
        }),
      ),
  });

  const propertiesQuery = useQuery({
    queryKey: ["properties", "by-farmer", farmerId],
    enabled: Boolean(farmerId),
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/properties", { params: { query: { filter: { farmerId }, pageable } } }),
      ),
  });

  const plotsQuery = useQuery({
    queryKey: ["properties", propertyId, "plots"],
    enabled: Boolean(propertyId),
    queryFn: () =>
      unwrap(api.GET("/properties/{id}/plots", { params: { path: { id: propertyId } } })),
  });

  const plantingsQuery = useQuery({
    queryKey: ["records", "plantings", propertyId],
    enabled: Boolean(propertyId) && type !== "PLANTING" && type !== "INPUT_PURCHASE",
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/records", {
          params: {
            query: { filter: { propertyId, type: "PLANTING", cycleStatus: "ACTIVE" }, pageable },
          },
        }),
      ),
  });

  const cropsQuery = useQuery({
    queryKey: ["catalog", "crops"],
    queryFn: () => unwrap(api.GET("/catalog/crops")),
  });

  const inputsQuery = useQuery({
    queryKey: ["catalog", "farm-inputs"],
    queryFn: () => unwrap(api.GET("/catalog/farm-inputs")),
  });

  const unitsQuery = useQuery({
    queryKey: ["catalog", "units"],
    queryFn: () => unwrap(api.GET("/catalog/units")),
  });

  const draftCompletenessMissing = missingFields({
    type,
    plantedArea: areaValue ? { value: Number(areaValue), unit: areaUnit } : null,
    seedOrigin: seedOrigin || null,
    expectedHarvestOn: expectedHarvestOn || null,
    quantity: qtyValue ? { value: Number(qtyValue), unit: qtyUnit } : null,
    purpose: purpose || null,
    supplier: supplier || null,
    destination: destination || null,
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!propertyId) {
      setError("Selecione a propriedade onde o manejo foi realizado.");
      return;
    }
    setError(null);
    setSaving(true);

    try {
      let body: RecordWrite;
      if (type === "PLANTING") {
        body = {
          type: "PLANTING",
          propertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes.trim() || undefined,
          cropId: cropId || undefined,
          cropOtherName: !cropId && cropOtherName.trim() ? cropOtherName.trim() : undefined,
          variety: variety.trim() || undefined,
          plantedArea: areaValue ? { value: Number(areaValue), unit: areaUnit } : undefined,
          seedOrigin: seedOrigin || undefined,
          propagationType: propagationType || undefined,
          expectedHarvestOn: expectedHarvestOn || undefined,
        };
      } else if (type === "INPUT_APPLICATION") {
        body = {
          type: "INPUT_APPLICATION",
          propertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes.trim() || undefined,
          plantingId: plantingId || undefined,
          inputId: inputId || undefined,
          inputOtherName: !inputId && inputOtherName.trim() ? inputOtherName.trim() : undefined,
          purpose,
          quantity: qtyValue ? { value: Number(qtyValue), unit: qtyUnit } : undefined,
          appliedArea: areaValue ? { value: Number(areaValue), unit: areaUnit } : undefined,
        };
      } else if (type === "HARVEST") {
        body = {
          type: "HARVEST",
          propertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes.trim() || undefined,
          plantingId: plantingId || undefined,
          quantity: qtyValue ? { value: Number(qtyValue), unit: qtyUnit } : undefined,
          harvestedArea: areaValue ? { value: Number(areaValue), unit: areaUnit } : undefined,
          isFinal,
        };
      } else if (type === "INPUT_PURCHASE") {
        body = {
          type: "INPUT_PURCHASE",
          propertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes.trim() || undefined,
          inputId: inputId || undefined,
          inputOtherName: !inputId && inputOtherName.trim() ? inputOtherName.trim() : undefined,
          quantity: qtyValue ? { value: Number(qtyValue), unit: qtyUnit } : undefined,
          supplier: supplier.trim() || undefined,
        };
      } else {
        body = {
          type: "OUTFLOW",
          propertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes.trim() || undefined,
          cropId: cropId || undefined,
          productOtherName: !cropId && cropOtherName.trim() ? cropOtherName.trim() : undefined,
          plantingId: plantingId || undefined,
          quantity: qtyValue ? { value: Number(qtyValue), unit: qtyUnit } : undefined,
          destination,
          buyerName: buyerName.trim() || undefined,
        };
      }

      const created = await unwrap(api.POST("/records", { body }));
      await queryClient.invalidateQueries({ queryKey: ["records"] });
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      navigate(`/registros/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao registrar manejo.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Registrar Manejo</h1>
          <p className="af-page-subtitle">
            Lançamento pelo painel em nome do agricultor. O agricultor será notificado no aplicativo
            (RN16).
          </p>
        </div>
        <div className="af-topbar-actions">
          <Link to="/registros" className="af-btn af-btn--outline af-btn--sm">
            Voltar
          </Link>
        </div>
      </div>

      <div className="af-card" style={{ maxWidth: 840 }}>
        {error && (
          <div className="af-alert af-alert--danger" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        <form className="af-form-grid" onSubmit={handleSubmit}>
          <div className="af-field">
            <label className="af-label">Tipo de manejo *</label>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {(Object.entries(RECORD_TYPE_LABELS) as [RecordType, string][]).map(([k, label]) => (
                <button
                  key={k}
                  type="button"
                  className={`af-btn af-btn--sm ${
                    type === k ? "af-btn--primary" : "af-btn--outline"
                  }`}
                  onClick={() => setType(k)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div className="af-form-row">
            <div className="af-field">
              <label className="af-label">Agricultor *</label>
              <select
                className="af-select"
                value={farmerId}
                onChange={(e) => {
                  setFarmerId(e.target.value);
                  setPropertyId("");
                  setPlotId("");
                }}
                required
              >
                <option value="">Selecione o agricultor</option>
                {(farmersQuery.data ?? []).map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="af-field">
              <label className="af-label">Propriedade *</label>
              <select
                className="af-select"
                value={propertyId}
                onChange={(e) => {
                  setPropertyId(e.target.value);
                  setPlotId("");
                }}
                required
                disabled={!farmerId}
              >
                <option value="">Selecione a propriedade</option>
                {(propertiesQuery.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="af-field">
              <label className="af-label">Talhão / Área</label>
              <select
                className="af-select"
                value={plotId}
                onChange={(e) => setPlotId(e.target.value)}
                disabled={!propertyId}
              >
                <option value="">Opcional / Toda a propriedade</option>
                {(plotsQuery.data ?? []).map((pl) => (
                  <option key={pl.id} value={pl.id}>
                    {pl.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="af-form-row">
            <div className="af-field">
              <label className="af-label">Data de realização *</label>
              <input
                type="date"
                className="af-input"
                value={performedOn}
                onChange={(e) => setPerformedOn(e.target.value)}
                max={todayIso()}
                required
              />
            </div>

            {type !== "PLANTING" && type !== "INPUT_PURCHASE" && (
              <div className="af-field">
                <label className="af-label">
                  Plantio vinculado {type === "HARVEST" ? "*" : "(recomendado)"}
                </label>
                <select
                  className="af-select"
                  value={plantingId}
                  onChange={(e) => setPlantingId(e.target.value)}
                  required={type === "HARVEST"}
                >
                  <option value="">Selecione o ciclo de plantio</option>
                  {(plantingsQuery.data ?? []).map((pl) => (
                    <option key={pl.id} value={pl.id}>
                      #{pl.number} — {pl.subject ?? "Plantio"} ({formatDatePtBr(pl.performedOn)})
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>

          {(type === "PLANTING" || type === "OUTFLOW") && (
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Cultura / Produto do catálogo</label>
                <select
                  className="af-select"
                  value={cropId}
                  onChange={(e) => setCropId(e.target.value)}
                >
                  <option value="">Outro (digitar nome manual)</option>
                  {(cropsQuery.data ?? []).map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
              {!cropId && (
                <div className="af-field">
                  <label className="af-label">Nome da cultura / produto *</label>
                  <input
                    className="af-input"
                    value={cropOtherName}
                    onChange={(e) => setCropOtherName(e.target.value)}
                    required={!cropId}
                    placeholder="Ex.: Coentro Verdão"
                  />
                </div>
              )}
              {type === "PLANTING" && (
                <div className="af-field">
                  <label className="af-label">Variedade</label>
                  <input
                    className="af-input"
                    value={variety}
                    onChange={(e) => setVariety(e.target.value)}
                    placeholder="Opcional"
                  />
                </div>
              )}
            </div>
          )}

          {(type === "INPUT_APPLICATION" || type === "INPUT_PURCHASE") && (
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Insumo do catálogo</label>
                <select
                  className="af-select"
                  value={inputId}
                  onChange={(e) => setInputId(e.target.value)}
                >
                  <option value="">Outro (digitar nome manual)</option>
                  {(inputsQuery.data ?? []).map((inp) => (
                    <option key={inp.id} value={inp.id}>
                      {inp.name}
                    </option>
                  ))}
                </select>
              </div>
              {!inputId && (
                <div className="af-field">
                  <label className="af-label">Nome do insumo *</label>
                  <input
                    className="af-input"
                    value={inputOtherName}
                    onChange={(e) => setInputOtherName(e.target.value)}
                    required={!inputId}
                    placeholder="Ex.: Composto orgânico caseiro"
                  />
                </div>
              )}
              {type === "INPUT_APPLICATION" && (
                <div className="af-field">
                  <label className="af-label">Finalidade *</label>
                  <select
                    className="af-select"
                    value={purpose}
                    onChange={(e) => setPurpose(e.target.value as typeof purpose)}
                  >
                    {Object.entries(APPLICATION_PURPOSE_LABELS).map(([k, v]) => (
                      <option key={k} value={k}>
                        {v}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </div>
          )}

          {type !== "PLANTING" && (
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Quantidade</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  className="af-input"
                  value={qtyValue}
                  onChange={(e) => setQtyValue(e.target.value)}
                  placeholder="Ex.: 25"
                />
              </div>
              <div className="af-field">
                <label className="af-label">Unidade</label>
                <select
                  className="af-select"
                  value={qtyUnit}
                  onChange={(e) => setQtyUnit(e.target.value)}
                >
                  {(unitsQuery.data ?? []).map((u) => (
                    <option key={u.code} value={u.code}>
                      {u.namePlural} ({u.code})
                    </option>
                  ))}
                  {(unitsQuery.data ?? []).length === 0 && (
                    <option value="KG">Quilogramas (KG)</option>
                  )}
                </select>
              </div>
            </div>
          )}

          {(type === "PLANTING" || type === "INPUT_APPLICATION" || type === "HARVEST") && (
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">
                  {type === "PLANTING"
                    ? "Área plantada"
                    : type === "INPUT_APPLICATION"
                      ? "Área aplicada"
                      : "Área colhida"}
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  className="af-input"
                  value={areaValue}
                  onChange={(e) => setAreaValue(e.target.value)}
                  placeholder="Ex.: 1.0"
                />
              </div>
              <div className="af-field">
                <label className="af-label">Unidade de área</label>
                <select
                  className="af-select"
                  value={areaUnit}
                  onChange={(e) => setAreaUnit(e.target.value as typeof areaUnit)}
                >
                  {Object.entries(AREA_UNIT_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {type === "PLANTING" && (
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Origem das sementes / mudas</label>
                <select
                  className="af-select"
                  value={seedOrigin}
                  onChange={(e) => setSeedOrigin(e.target.value as typeof seedOrigin)}
                >
                  <option value="">Não informada</option>
                  {Object.entries(SEED_ORIGIN_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label className="af-label">Tipo de propagação</label>
                <select
                  className="af-select"
                  value={propagationType}
                  onChange={(e) => setPropagationType(e.target.value as typeof propagationType)}
                >
                  <option value="">Não informado</option>
                  {Object.entries(PROPAGATION_TYPE_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label className="af-label">Previsão de colheita</label>
                <input
                  type="date"
                  className="af-input"
                  value={expectedHarvestOn}
                  onChange={(e) => setExpectedHarvestOn(e.target.value)}
                />
              </div>
            </div>
          )}

          {type === "HARVEST" && (
            <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
              <input
                type="checkbox"
                checked={isFinal}
                onChange={(e) => setIsFinal(e.target.checked)}
              />
              <span style={{ fontWeight: 600, fontSize: "0.9rem" }}>
                Colheita final (encerra o ciclo deste plantio — RN12)
              </span>
            </label>
          )}

          {type === "INPUT_PURCHASE" && (
            <div className="af-field">
              <label className="af-label">Fornecedor / Origem da compra</label>
              <input
                className="af-input"
                value={supplier}
                onChange={(e) => setSupplier(e.target.value)}
                placeholder="Ex.: Cooperativa Agropecuária Regional"
              />
            </div>
          )}

          {type === "OUTFLOW" && (
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Destino da produção</label>
                <select
                  className="af-select"
                  value={destination}
                  onChange={(e) => setDestination(e.target.value as typeof destination)}
                >
                  {Object.entries(OUTFLOW_DESTINATION_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label className="af-label">Comprador / Feira (opcional)</label>
                <input
                  className="af-input"
                  value={buyerName}
                  onChange={(e) => setBuyerName(e.target.value)}
                  placeholder="Ex.: Feira Agroecológica de Senhor do Bonfim"
                />
              </div>
            </div>
          )}

          <div className="af-field">
            <label className="af-label">Observações do manejo</label>
            <textarea
              className="af-textarea"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Anotações para o caderno de campo..."
            />
          </div>

          {draftCompletenessMissing.length > 0 ? (
            <div className="af-alert af-alert--warning">
              <strong>Atenção (RN09):</strong> O manejo pode ser salvo, mas ficará marcado como{" "}
              <em>Incompleto</em> até que sejam preenchidos:{" "}
              {draftCompletenessMissing.map((f) => MISSING_FIELD_LABELS[f] ?? f).join(", ")}.
            </div>
          ) : (
            <div className="af-alert af-alert--success">
              ✓ Todos os campos recomendados para o Caderno de Campo estão preenchidos!
            </div>
          )}

          <div style={{ display: "flex", gap: 12, justifyContent: "flex-end" }}>
            <Link to="/registros" className="af-btn af-btn--outline af-btn--sm">
              Cancelar
            </Link>
            <button type="submit" className="af-btn af-btn--primary af-btn--sm" disabled={saving}>
              {saving ? "Salvando..." : "Salvar registro de manejo"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export function RecordDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const [correctionMsg, setCorrectionMsg] = useState("");

  const detailQuery = useQuery({
    queryKey: ["records", id],
    enabled: Boolean(id),
    queryFn: () => unwrap(api.GET("/records/{id}", { params: { path: { id: id! } } })),
  });

  const historyQuery = useQuery({
    queryKey: ["records", id, "history"],
    enabled: Boolean(id),
    queryFn: () => unwrap(api.GET("/records/{id}/history", { params: { path: { id: id! } } })),
  });

  const createCorrectionMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/records/{id}/correction-requests", {
          params: { path: { id: id! } },
          body: { message: correctionMsg.trim() },
        }),
      ),
    onSuccess: () => {
      setCorrectionMsg("");
      queryClient.invalidateQueries({ queryKey: ["records", id] });
    },
  });

  const resolveCorrectionMutation = useMutation({
    mutationFn: (corrId: string) =>
      unwrap(
        api.POST("/correction-requests/{id}/resolve", {
          params: { path: { id: corrId } },
        }),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["records", id] }),
  });

  const rec = detailQuery.data as import("@agrofacil/api-types").RecordDetail | undefined;
  if (detailQuery.isLoading) {
    return <div className="af-empty-state">Carregando detalhe do manejo...</div>;
  }
  if (!rec) {
    return <div className="af-empty-state">Registro de manejo não encontrado.</div>;
  }

  return (
    <div>
      <div className="af-topbar">
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <RecordTypeAvatar type={rec.type} />
          <div>
            <h1 className="af-page-title">
              Registro #{rec.number} — {rec.type ? RECORD_TYPE_LABELS[rec.type] : "Manejo"}
            </h1>
            <p className="af-page-subtitle">
              Realizado em {formatDatePtBr(rec.performedOn)} • Origem:{" "}
              {rec.source ? RECORD_SOURCE_LABELS[rec.source] : "—"}
            </p>
          </div>
        </div>
        <div className="af-topbar-actions">
          <span
            className={`af-badge ${
              rec.completeness === "COMPLETE" ? "af-badge--valid" : "af-badge--warning"
            }`}
          >
            {rec.completeness ? COMPLETENESS_LABELS[rec.completeness] : "—"}
          </span>
          <Link to="/registros" className="af-btn af-btn--outline af-btn--sm">
            Voltar
          </Link>
        </div>
      </div>

      {(rec.missingFields ?? []).length > 0 && (
        <div className="af-alert af-alert--warning" style={{ marginBottom: 20 }}>
          <strong>Campos recomendados pendentes (RN09):</strong>{" "}
          {(rec.missingFields ?? []).map((f) => MISSING_FIELD_LABELS[f] ?? f).join(", ")}.
        </div>
      )}

      <div className="af-grid-2">
        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 14 }}>
            Dados do Registro
          </h2>
          <div style={{ display: "grid", gap: 10, fontSize: "0.9rem" }}>
            <div>
              <strong>Data do manejo:</strong> {formatDatePtBr(rec.performedOn)}
            </div>
            <div>
              <strong>Propriedade:</strong>{" "}
              {rec.propertyId ? (
                <Link
                  to={`/propriedades/${rec.propertyId}`}
                  style={{ color: "var(--af-green-primary)", fontWeight: 600 }}
                >
                  Ver propriedade
                </Link>
              ) : (
                "—"
              )}
            </div>
            {rec.type === "PLANTING" && (
              <>
                <div>
                  <strong>Área plantada:</strong> {formatArea(rec.plantedArea)}
                </div>
                <div>
                  <strong>Origem da semente/muda:</strong>{" "}
                  {rec.seedOrigin ? SEED_ORIGIN_LABELS[rec.seedOrigin] : "Não informada"}
                </div>
                <div>
                  <strong>Previsão de colheita:</strong> {formatDatePtBr(rec.expectedHarvestOn)}
                </div>
              </>
            )}
            {rec.type === "INPUT_APPLICATION" && (
              <>
                <div>
                  <strong>Finalidade:</strong>{" "}
                  {rec.purpose ? APPLICATION_PURPOSE_LABELS[rec.purpose] : "—"}
                </div>
                <div>
                  <strong>Quantidade aplicada:</strong> {formatQuantity(rec.quantity)}
                </div>
                <div>
                  <strong>Área aplicada:</strong> {formatArea(rec.appliedArea)}
                </div>
              </>
            )}
            {rec.type === "HARVEST" && (
              <>
                <div>
                  <strong>Quantidade colhida:</strong> {formatQuantity(rec.quantity)}
                </div>
                <div>
                  <strong>Área colhida:</strong> {formatArea(rec.harvestedArea)}
                </div>
                <div>
                  <strong>Colheita final:</strong> {rec.isFinal ? "Sim" : "Não"}
                </div>
              </>
            )}
            {rec.type === "INPUT_PURCHASE" && (
              <>
                <div>
                  <strong>Quantidade comprada:</strong> {formatQuantity(rec.quantity)}
                </div>
                <div>
                  <strong>Fornecedor:</strong> {rec.supplier ?? "—"}
                </div>
              </>
            )}
            {rec.type === "OUTFLOW" && (
              <>
                <div>
                  <strong>Quantidade comercializada/saída:</strong> {formatQuantity(rec.quantity)}
                </div>
                <div>
                  <strong>Destino:</strong>{" "}
                  {rec.destination ? OUTFLOW_DESTINATION_LABELS[rec.destination] : "—"}
                </div>
                <div>
                  <strong>Comprador:</strong> {rec.buyerName ?? "—"}
                </div>
              </>
            )}
            <div>
              <strong>Observações:</strong> {rec.notes || "Nenhuma observação informada."}
            </div>
          </div>
        </div>

        {/* Pedidos de Correção (RN17) */}
        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 6 }}>
            Pedidos de Correção (RN17)
          </h2>
          <p className="af-card-subtitle" style={{ marginBottom: 14 }}>
            Solicite complemento ou revisão deste manejo ao agricultor.
          </p>

          {(rec.correctionRequests ?? []).length === 0 ? (
            <div className="af-empty-state" style={{ padding: "18px 10px" }}>
              Nenhum pedido de correção registrado.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: 16 }}>
              {(rec.correctionRequests ?? []).map((cr) => (
                <div
                  key={cr.id}
                  style={{
                    padding: 12,
                    borderRadius: "var(--af-radius-md)",
                    border: "1px solid var(--af-border)",
                    background:
                      cr.status === "OPEN" ? "var(--af-warning-bg)" : "var(--af-bg-surface-soft)",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      marginBottom: 4,
                    }}
                  >
                    <span
                      className={`af-badge ${
                        cr.status === "OPEN" ? "af-badge--warning" : "af-badge--valid"
                      }`}
                    >
                      {cr.status === "OPEN" ? "Aberto" : "Resolvido"}
                    </span>
                    <span style={{ fontSize: "0.75rem", color: "var(--af-text-muted)" }}>
                      {formatDateTimePtBr(cr.createdAt)}
                    </span>
                  </div>
                  <div style={{ fontSize: "0.88rem", marginBottom: 8 }}>{cr.message}</div>
                  {cr.status === "OPEN" && cr.id && (
                    <button
                      type="button"
                      className="af-btn af-btn--secondary af-btn--sm"
                      onClick={() => resolveCorrectionMutation.mutate(cr.id!)}
                    >
                      Marcar como resolvido
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}

          <form
            className="af-form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              if (correctionMsg.trim()) createCorrectionMutation.mutate();
            }}
          >
            <div className="af-field">
              <label className="af-label">Novo pedido de correção para o agricultor</label>
              <textarea
                className="af-textarea"
                value={correctionMsg}
                onChange={(e) => setCorrectionMsg(e.target.value)}
                placeholder="Ex.: Favor informar a área plantada e a origem das mudas..."
                required
              />
            </div>
            <button
              type="submit"
              className="af-btn af-btn--primary af-btn--sm"
              disabled={createCorrectionMutation.isPending}
            >
              Enviar pedido de correção
            </button>
          </form>
        </div>
      </div>

      <div className="af-card" style={{ marginTop: 20 }}>
        <h2 className="af-card-title" style={{ marginBottom: 14 }}>
          Fotos e anexos
        </h2>
        <AttachmentGallery attachments={rec.attachments ?? []} />
      </div>

      {/* Histórico de Auditoria Envers (ADR-10) */}
      <div className="af-card" style={{ marginTop: 20 }}>
        <div className="af-card-header">
          <div>
            <h2 className="af-card-title">Histórico de Alterações (Auditoria ADR-10)</h2>
            <p className="af-card-subtitle">
              Rastreabilidade completa de quem criou ou alterou este registro
            </p>
          </div>
        </div>

        {historyQuery.isLoading ? (
          <div className="af-empty-state">Carregando histórico...</div>
        ) : (historyQuery.data ?? []).length === 0 ? (
          <div className="af-empty-state">Nenhuma revisão encontrada.</div>
        ) : (
          <div className="af-table-wrap">
            <table className="af-table">
              <thead>
                <tr>
                  <th>Revisão</th>
                  <th>Data / Hora</th>
                  <th>Autor</th>
                  <th>Origem</th>
                  <th>Alterações</th>
                </tr>
              </thead>
              <tbody>
                {(historyQuery.data ?? []).map((h) => (
                  <tr key={h.revision}>
                    <td style={{ fontWeight: 700 }}>#{h.revision}</td>
                    <td>{formatDateTimePtBr(h.at)}</td>
                    <td>{h.by?.name ?? "Sistema"}</td>
                    <td>{h.source ?? "—"}</td>
                    <td>
                      {h.changeType === "CREATED"
                        ? "Registro criado"
                        : (h.changes ?? [])
                            .map((c) => `${c.field}: ${JSON.stringify(c.after)}`)
                            .join("; ") || h.changeType}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
