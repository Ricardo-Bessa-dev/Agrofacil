// Formulário de criação e edição de manejo do agricultor, com foto opcional; funciona sem internet (ADR-02).
import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams, useSearchParams } from "react-router";
import type { RecordWrite } from "@agrofacil/api-types";
import {
  DEFAULT_APPLICATION_UNIT,
  DEFAULT_QUANTITY_UNIT,
  formatDatePtBr,
  MISSING_FIELD_LABELS,
  todayIso,
  type AreaUnitCode,
} from "@agrofacil/domain";
import { api, fetchMobileRecordDetail, uploadRecordPhoto } from "../api/client";
import { generateUuid } from "../api/uuid";
import { QuantityUnitSelect } from "../components/QuantityUnitSelect";
import { RecordTypeIcon } from "../components/RecordTypeIcon";
import { onActivateKey } from "../a11y";

type RecordType = "PLANTING" | "INPUT_APPLICATION" | "HARVEST" | "INPUT_PURCHASE" | "OUTFLOW";

const RECORD_TYPES: { type: RecordType; title: string; subtitle: string }[] = [
  {
    type: "PLANTING",
    title: "Plantio",
    subtitle: "Registro de semeadura ou plantio de mudas",
  },
  {
    type: "INPUT_APPLICATION",
    title: "Aplicação de Insumo",
    subtitle: "Adubação, biofertilizante, calda ou defensivo",
  },
  {
    type: "HARVEST",
    title: "Colheita",
    subtitle: "Produção colhida nos seus talhões",
  },
  {
    type: "INPUT_PURCHASE",
    title: "Compra de Insumo",
    subtitle: "Sementes, adubos, ferramentas ou insumos",
  },
  {
    type: "OUTFLOW",
    title: "Saída / Venda",
    subtitle: "Venda na feira, cooperativa, PNAE ou doação",
  },
];

export function NewRecordPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { id: routeRecordId } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const editingRecordId = routeRecordId || searchParams.get("editId") || "";
  const isEditMode = Boolean(editingRecordId);
  // ID do registro novo gerado uma vez por formulário: se o salvamento for repetido (falha de rede,
  // erro na foto), o mesmo registro é reenviado em vez de criar uma cópia.
  const [newRecordId] = useState(generateUuid);

  const [selectedType, setSelectedType] = useState<RecordType>("PLANTING");
  const [performedOn, setPerformedOn] = useState<string>(todayIso());
  const [propertyId, setPropertyId] = useState<string>(searchParams.get("propertyId") ?? "");
  const [plotId, setPlotId] = useState<string>("");
  const [notes, setNotes] = useState<string>("");
  const [plantingId, setPlantingId] = useState<string>("");

  // Específicos para Plantio
  const [cropId, setCropId] = useState<string>("");
  const [cropOtherName, setCropOtherName] = useState<string>("");
  const [variety, setVariety] = useState<string>("");
  const [plantedAreaVal, setPlantedAreaVal] = useState<string>("");
  const [plantedAreaUnit, setPlantedAreaUnit] = useState<AreaUnitCode>("SQUARE_METER");
  const [propagationType, setPropagationType] = useState<
    "" | "SEED" | "SEEDLING" | "CUTTING" | "OTHER"
  >("");
  const [seedOrigin, setSeedOrigin] = useState<
    "" | "OWN" | "SEED_BANK" | "ORGANIC_PURCHASED" | "CONVENTIONAL_PURCHASED"
  >("");
  const [seedSupplier, setSeedSupplier] = useState<string>("");
  const [expectedHarvestOn, setExpectedHarvestOn] = useState<string>("");

  // Específicos para Aplicação
  const [inputId, setInputId] = useState<string>("");
  const [inputOtherName, setInputOtherName] = useState<string>("");
  const [purpose, setPurpose] = useState<
    "FERTILIZATION" | "PEST_DISEASE_CONTROL" | "SOIL_CORRECTION" | "OTHER"
  >("FERTILIZATION");
  const [applicationQtyVal, setApplicationQtyVal] = useState<string>("");
  const [applicationQtyUnit, setApplicationQtyUnit] = useState<string>(DEFAULT_APPLICATION_UNIT);

  // Específicos para Colheita
  const [harvestCropName, setHarvestCropName] = useState<string>("");
  const [harvestQtyVal, setHarvestQtyVal] = useState<string>("");
  const [harvestQtyUnit, setHarvestQtyUnit] = useState<string>(DEFAULT_QUANTITY_UNIT);
  const [isFinal, setIsFinal] = useState<boolean>(false);

  // Específicos para Compra de Insumo
  const [purchaseInputId, setPurchaseInputId] = useState<string>("");
  const [purchaseInputName, setPurchaseInputName] = useState<string>("");
  const [purchaseQtyVal, setPurchaseQtyVal] = useState<string>("");
  const [purchaseQtyUnit, setPurchaseQtyUnit] = useState<string>(DEFAULT_QUANTITY_UNIT);
  const [supplier, setSupplier] = useState<string>("");

  // Específicos para Saída
  const [outflowCropId, setOutflowCropId] = useState<string>("");
  const [outflowProductName, setOutflowProductName] = useState<string>("");
  const [outflowQtyVal, setOutflowQtyVal] = useState<string>("");
  const [outflowQtyUnit, setOutflowQtyUnit] = useState<string>(DEFAULT_QUANTITY_UNIT);
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
  const [buyerName, setBuyerName] = useState<string>("");

  // Foto / Anexo (Item 2 do Backlog)
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreviewUrl, setPhotoPreviewUrl] = useState<string | null>(null);
  const [prefilled, setPrefilled] = useState(false);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Detalhe do registro existente quando em modo de edição (Item 1 do Backlog)
  const editDetailQuery = useQuery({
    queryKey: ["mobile-record-detail", editingRecordId],
    queryFn: () => fetchMobileRecordDetail(editingRecordId),
    enabled: isEditMode,
  });

  useEffect(() => {
    const detail = editDetailQuery.data;
    if (!detail || prefilled) return;
    const raw = detail.raw;
    setSelectedType((detail.type as RecordType) || "PLANTING");
    if (detail.performedOn) setPerformedOn(detail.performedOn);
    if (detail.propertyId) setPropertyId(detail.propertyId);
    if (detail.plotId) setPlotId(detail.plotId);
    if (detail.notes) setNotes(detail.notes);
    if (typeof raw.plantingId === "string") setPlantingId(raw.plantingId);

    if (detail.type === "PLANTING") {
      if (typeof raw.cropId === "string") setCropId(raw.cropId);
      else if (typeof raw.cropOtherName === "string") {
        setCropId("OTHER");
        setCropOtherName(raw.cropOtherName);
      }
      if (typeof raw.variety === "string") setVariety(raw.variety);
      const area = raw.plantedArea as { value?: number; unit?: AreaUnitCode } | undefined;
      if (area?.value !== undefined) setPlantedAreaVal(String(area.value));
      if (area?.unit) setPlantedAreaUnit(area.unit);
      if (typeof raw.propagationType === "string") {
        setPropagationType(raw.propagationType as typeof propagationType);
      }
      if (typeof raw.seedOrigin === "string") {
        setSeedOrigin(raw.seedOrigin as typeof seedOrigin);
      }
      if (typeof raw.seedSupplier === "string") setSeedSupplier(raw.seedSupplier);
      if (typeof raw.expectedHarvestOn === "string") setExpectedHarvestOn(raw.expectedHarvestOn);
    } else if (detail.type === "INPUT_APPLICATION") {
      if (typeof raw.inputId === "string") setInputId(raw.inputId);
      else if (typeof raw.inputOtherName === "string") {
        setInputId("OTHER");
        setInputOtherName(raw.inputOtherName);
      }
      if (typeof raw.purpose === "string") setPurpose(raw.purpose as typeof purpose);
      const qty = raw.quantity as { value?: number; unit?: string } | undefined;
      if (qty?.value !== undefined) setApplicationQtyVal(String(qty.value));
      if (qty?.unit) setApplicationQtyUnit(qty.unit);
    } else if (detail.type === "HARVEST") {
      const qty = raw.quantity as { value?: number; unit?: string } | undefined;
      if (qty?.value !== undefined) setHarvestQtyVal(String(qty.value));
      if (qty?.unit) setHarvestQtyUnit(qty.unit);
      if (typeof raw.isFinal === "boolean") setIsFinal(raw.isFinal);
    } else if (detail.type === "INPUT_PURCHASE") {
      if (typeof raw.inputId === "string") setPurchaseInputId(raw.inputId);
      if (typeof raw.inputOtherName === "string") setPurchaseInputName(raw.inputOtherName);
      const qty = raw.quantity as { value?: number; unit?: string } | undefined;
      if (qty?.value !== undefined) setPurchaseQtyVal(String(qty.value));
      if (qty?.unit) setPurchaseQtyUnit(qty.unit);
      if (typeof raw.supplier === "string") setSupplier(raw.supplier);
    } else if (detail.type === "OUTFLOW") {
      if (typeof raw.cropId === "string") setOutflowCropId(raw.cropId);
      if (typeof raw.productOtherName === "string") setOutflowProductName(raw.productOtherName);
      const qty = raw.quantity as { value?: number; unit?: string } | undefined;
      if (qty?.value !== undefined) setOutflowQtyVal(String(qty.value));
      if (qty?.unit) setOutflowQtyUnit(qty.unit);
      if (typeof raw.destination === "string")
        setDestination(raw.destination as typeof destination);
      if (typeof raw.buyerName === "string") setBuyerName(raw.buyerName);
    }
    setPrefilled(true);
  }, [editDetailQuery.data, prefilled]);

  // Busca propriedades do agricultor
  const propertiesQuery = useQuery({
    queryKey: ["farmer-properties"],
    queryFn: async () => {
      const { data } = await api.GET("/properties", {
        params: { query: { filter: {}, pageable: { size: 20 } } },
      });
      return data?.content ?? [];
    },
  });

  const properties = propertiesQuery.data ?? [];
  const currentPropertyId = propertyId || (properties[0]?.id ?? "");

  // Busca talhões da propriedade selecionada
  const plotsQuery = useQuery({
    queryKey: ["property-plots", currentPropertyId],
    queryFn: async () => {
      if (!currentPropertyId) return [];
      const { data } = await api.GET("/properties/{id}/plots", {
        params: { path: { id: currentPropertyId } },
      });
      return data ?? [];
    },
    enabled: Boolean(currentPropertyId),
  });

  // Busca plantios ativos da propriedade para vínculo (Item 3 do Backlog)
  const activePlantingsQuery = useQuery({
    queryKey: ["active-plantings", currentPropertyId],
    queryFn: async () => {
      if (!currentPropertyId) return [];
      const { data } = await api.GET("/records", {
        params: {
          query: {
            filter: {
              propertyId: currentPropertyId,
              type: "PLANTING",
              cycleStatus: "ACTIVE",
            },
            pageable: { size: 50, sort: ["performedOn,desc"] },
          },
        },
      });
      return data?.content ?? [];
    },
    enabled:
      Boolean(currentPropertyId) &&
      (selectedType === "INPUT_APPLICATION" ||
        selectedType === "HARVEST" ||
        selectedType === "OUTFLOW"),
  });

  // Catálogo de Culturas
  const cropsQuery = useQuery({
    queryKey: ["catalog-crops"],
    queryFn: async () => {
      const { data } = await api.GET("/catalog/crops");
      return data ?? [];
    },
    staleTime: 300_000,
  });

  // Catálogo de Insumos
  const farmInputsQuery = useQuery({
    queryKey: ["catalog-farm-inputs"],
    queryFn: async () => {
      const { data } = await api.GET("/catalog/farm-inputs");
      return data ?? [];
    },
    staleTime: 300_000,
  });

  // Unidades de medida aceitas pela API
  const unitsQuery = useQuery({
    queryKey: ["catalog-units"],
    queryFn: async () => {
      const { data } = await api.GET("/catalog/units");
      return data ?? [];
    },
    staleTime: 300_000,
  });

  const plots = plotsQuery.data ?? [];
  const crops = cropsQuery.data ?? [];
  const farmInputs = farmInputsQuery.data ?? [];
  const units = unitsQuery.data ?? [];
  const activePlantings = activePlantingsQuery.data ?? [];

  // Só escolhe o plantio sozinho quando há um único em andamento; com mais de um, o agricultor escolhe,
  // para a colheita não ficar ligada ao plantio errado.
  const onlyPlantingId = activePlantings.length === 1 ? activePlantings[0]?.id : undefined;
  useEffect(() => {
    if (selectedType === "HARVEST" && !plantingId && onlyPlantingId) {
      setPlantingId(onlyPlantingId);
    }
  }, [selectedType, plantingId, onlyPlantingId]);

  const handlePhotoChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] ?? null;
    setPhotoFile(file);
    if (photoPreviewUrl) {
      URL.revokeObjectURL(photoPreviewUrl);
    }
    if (file) {
      setPhotoPreviewUrl(URL.createObjectURL(file));
    } else {
      setPhotoPreviewUrl(null);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    const targetPropertyId = currentPropertyId;
    if (!targetPropertyId) {
      setErrorMsg("Selecione a propriedade.");
      return;
    }

    if (selectedType === "HARVEST" && activePlantings.length === 0) {
      setErrorMsg(
        "Para registrar uma colheita, é necessário ter um Plantio ativo cadastrado nesta propriedade.",
      );
      return;
    }
    if (selectedType === "HARVEST" && !plantingId) {
      setErrorMsg("Escolha de qual plantio é esta colheita.");
      return;
    }

    // RN: plantio sempre tem área; aplicação precisa de área quando não está ligada a um plantio.
    const targetPlotId = plotId || (plots.length === 1 ? plots[0]?.id : undefined);
    const needsPlot =
      selectedType === "PLANTING" || (selectedType === "INPUT_APPLICATION" && !plantingId);
    if (needsPlot && !targetPlotId) {
      setErrorMsg("Escolha o talhão / área.");
      return;
    }

    setIsSubmitting(true);
    try {
      let bodyPayload: RecordWrite;

      if (selectedType === "PLANTING") {
        bodyPayload = {
          type: "PLANTING",
          propertyId: targetPropertyId,
          plotId: targetPlotId,
          performedOn,
          notes: notes || undefined,
          cropId: cropId && cropId !== "OTHER" ? cropId : undefined,
          cropOtherName: !cropId || cropId === "OTHER" ? cropOtherName || undefined : undefined,
          variety: variety || undefined,
          plantedArea: plantedAreaVal
            ? { value: Number(plantedAreaVal), unit: plantedAreaUnit }
            : undefined,
          propagationType: propagationType || undefined,
          seedOrigin: seedOrigin || undefined,
          seedSupplier: seedSupplier.trim() || undefined,
          expectedHarvestOn: expectedHarvestOn || undefined,
        };
      } else if (selectedType === "INPUT_APPLICATION") {
        bodyPayload = {
          type: "INPUT_APPLICATION",
          propertyId: targetPropertyId,
          plotId: targetPlotId,
          performedOn,
          notes: notes || undefined,
          plantingId: plantingId || undefined,
          inputId: inputId && inputId !== "OTHER" ? inputId : undefined,
          inputOtherName: !inputId || inputId === "OTHER" ? inputOtherName || undefined : undefined,
          purpose,
          quantity: applicationQtyVal
            ? { value: Number(applicationQtyVal), unit: applicationQtyUnit }
            : undefined,
        };
      } else if (selectedType === "HARVEST") {
        bodyPayload = {
          type: "HARVEST",
          propertyId: targetPropertyId,
          plotId: targetPlotId,
          performedOn,
          notes:
            harvestCropName && !notes.includes(harvestCropName)
              ? `${harvestCropName}${notes ? ` — ${notes}` : ""}`
              : notes || undefined,
          plantingId,
          quantity: harvestQtyVal
            ? { value: Number(harvestQtyVal), unit: harvestQtyUnit }
            : undefined,
          isFinal,
        };
      } else if (selectedType === "INPUT_PURCHASE") {
        bodyPayload = {
          type: "INPUT_PURCHASE",
          propertyId: targetPropertyId,
          plotId: targetPlotId,
          performedOn,
          notes: notes || undefined,
          inputId: purchaseInputId && purchaseInputId !== "OTHER" ? purchaseInputId : undefined,
          inputOtherName:
            !purchaseInputId || purchaseInputId === "OTHER"
              ? purchaseInputName || undefined
              : undefined,
          quantity: purchaseQtyVal
            ? { value: Number(purchaseQtyVal), unit: purchaseQtyUnit }
            : undefined,
          supplier: supplier || undefined,
        };
      } else {
        bodyPayload = {
          type: "OUTFLOW",
          propertyId: targetPropertyId,
          plotId: targetPlotId,
          performedOn,
          notes: notes || undefined,
          plantingId: plantingId || undefined,
          cropId: outflowCropId && outflowCropId !== "OTHER" ? outflowCropId : undefined,
          productOtherName:
            !outflowCropId || outflowCropId === "OTHER"
              ? outflowProductName || undefined
              : undefined,
          quantity: outflowQtyVal
            ? { value: Number(outflowQtyVal), unit: outflowQtyUnit }
            : undefined,
          destination,
          buyerName: buyerName || undefined,
        };
      }

      const resultCall = isEditMode
        ? await api.PUT("/records/{id}", {
            params: { path: { id: editingRecordId } },
            body: bodyPayload,
          })
        : await api.POST("/records", {
            body: { ...bodyPayload, id: newRecordId },
          });

      const { data: savedData, error, response } = resultCall;

      if (error || !response.ok) {
        const errObj = (typeof error === "object" && error !== null ? error : {}) as Record<
          string,
          unknown
        >;
        const errs = Array.isArray(errObj.errors)
          ? (errObj.errors as { field?: string; message?: string }[])
          : [];
        const extra =
          errs.length > 0
            ? ` (${errs.map((it) => `${it.field ?? "campo"}: ${it.message ?? ""}`).join("; ")})`
            : "";
        throw new Error(
          typeof errObj.detail === "string"
            ? `${errObj.detail}${extra}`
            : "Não foi possível salvar o manejo. Verifique os dados e tente novamente.",
        );
      }

      const targetRecordId =
        editingRecordId || ((savedData as { id?: string } | undefined)?.id ?? newRecordId);

      if (photoFile && targetRecordId) {
        await uploadRecordPhoto(targetRecordId, photoFile);
      }

      await queryClient.invalidateQueries({ queryKey: ["records"] });
      await queryClient.invalidateQueries({ queryKey: ["recent-records"] });
      await queryClient.invalidateQueries({ queryKey: ["active-plantings"] });
      await queryClient.invalidateQueries({
        queryKey: ["mobile-record-detail", editingRecordId],
      });
      navigate("/registros", { replace: true });
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Erro ao salvar registro.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const openCorrections = (editDetailQuery.data?.correctionRequests ?? []).filter(
    (c) => c.status === "OPEN",
  );
  const missingFields = editDetailQuery.data?.missingFields ?? [];

  return (
    <div style={{ paddingBottom: "32px" }}>
      {/* Top Bar */}
      <div className="page-top-bar">
        <button type="button" className="page-top-bar-back" onClick={() => navigate(-1)}>
          <span>←</span>
          <span>Voltar</span>
        </button>
        <h1 className="page-top-bar-title">{isEditMode ? "Editar Manejo" : "Novo Manejo"}</h1>
        <div style={{ width: "48px" }} />
      </div>

      <div style={{ padding: "16px" }}>
        {/* Alerta de Pedido de Correção da Coordenação (Item 1) */}
        {openCorrections.length > 0 && (
          <div
            style={{
              backgroundColor: "#fff8e1",
              border: "1.5px solid #f59e0b",
              borderRadius: "var(--radius-md)",
              padding: "14px",
              marginBottom: "16px",
            }}
          >
            <div style={{ fontWeight: 700, fontSize: "14px", color: "#92400e", marginBottom: 6 }}>
              🔔 Pedido de correção da coordenação
            </div>
            {openCorrections.map((c) => (
              <div
                key={c.id}
                style={{ fontSize: "13px", color: "#78350f", marginBottom: 4, lineHeight: 1.4 }}
              >
                “{c.message}”
              </div>
            ))}
            <div style={{ fontSize: "11px", color: "#92400e", marginTop: 6 }}>
              Ao salvar as alterações abaixo, este pedido de correção será marcado como atendido.
            </div>
          </div>
        )}

        {/* Alerta de Campos Pendentes para Completude */}
        {isEditMode && missingFields.length > 0 && (
          <div
            style={{
              backgroundColor: "var(--color-primary-surface)",
              border: "1px dashed var(--color-primary)",
              borderRadius: "var(--radius-sm)",
              padding: "12px",
              marginBottom: "16px",
              fontSize: "12px",
              color: "var(--color-primary-dark)",
            }}
          >
            <strong>Campos pendentes para completude (RN09):</strong>{" "}
            {missingFields.map((f) => MISSING_FIELD_LABELS[f] ?? f).join(", ")}.
          </div>
        )}

        {/* Escolha do Tipo de Manejo (bloqueado na edição pois a API não permite trocar o subtipo) */}
        {!isEditMode && (
          <div style={{ marginBottom: "20px" }}>
            <span className="form-label" id="record-type-label" style={{ marginBottom: "10px" }}>
              Tipo de Atividade / Manejo
            </span>
            <div
              role="radiogroup"
              aria-labelledby="record-type-label"
              style={{ display: "flex", flexDirection: "column", gap: "8px" }}
            >
              {RECORD_TYPES.map((t) => {
                const isSelected = selectedType === t.type;
                return (
                  <div
                    key={t.type}
                    role="radio"
                    aria-checked={isSelected}
                    tabIndex={0}
                    onClick={() => setSelectedType(t.type)}
                    onKeyDown={onActivateKey(() => setSelectedType(t.type))}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "12px",
                      padding: "12px 14px",
                      borderRadius: "var(--radius-md)",
                      backgroundColor: isSelected
                        ? "var(--color-primary-surface)"
                        : "var(--color-surface)",
                      border: `1.5px solid ${
                        isSelected ? "var(--color-primary)" : "var(--color-border)"
                      }`,
                      cursor: "pointer",
                      transition: "all 0.15s ease",
                    }}
                  >
                    <RecordTypeIcon type={t.type} size={38} />
                    <div style={{ flex: 1 }}>
                      <div
                        style={{
                          fontSize: "14px",
                          fontWeight: 600,
                          color: isSelected ? "var(--color-primary-dark)" : "var(--color-text)",
                        }}
                      >
                        {t.title}
                      </div>
                      <div style={{ fontSize: "12px", color: "var(--color-text-secondary)" }}>
                        {t.subtitle}
                      </div>
                    </div>
                    <div
                      style={{
                        width: "18px",
                        height: "18px",
                        borderRadius: "50%",
                        border: `2px solid ${
                          isSelected ? "var(--color-primary)" : "var(--color-border)"
                        }`,
                        backgroundColor: isSelected ? "var(--color-primary)" : "transparent",
                      }}
                    />
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {errorMsg && (
          <div
            role="alert"
            style={{
              backgroundColor: "var(--color-error-bg)",
              border: "1px solid var(--color-error)",
              borderRadius: "var(--radius-sm)",
              padding: "12px",
              marginBottom: "16px",
              color: "var(--color-error)",
              fontSize: "13px",
            }}
          >
            ⚠️ {errorMsg}
          </div>
        )}

        {/* Formulário com campos dinâmicos */}
        <form onSubmit={handleSubmit} noValidate>
          {/* Dados Gerais: Data e Propriedade/Talhão */}
          <div className="mobile-card">
            <h2
              style={{
                fontSize: "14px",
                fontWeight: 600,
                marginBottom: "12px",
                color: "var(--color-text)",
              }}
            >
              Quando e Onde?
            </h2>

            <div className="form-group">
              <label htmlFor="performed-on" className="form-label">
                Data do Manejo
              </label>
              <input
                id="performed-on"
                type="date"
                value={performedOn}
                onChange={(e) => setPerformedOn(e.target.value)}
                className="form-input"
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="property-select" className="form-label">
                Propriedade
              </label>
              <select
                id="property-select"
                value={currentPropertyId}
                onChange={(e) => {
                  setPropertyId(e.target.value);
                  setPlotId("");
                  setPlantingId("");
                }}
                className="form-select"
                required
              >
                {properties.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group" style={{ marginBottom: 0 }}>
              <label htmlFor="plot-select" className="form-label">
                Talhão / Área
              </label>
              <select
                id="plot-select"
                value={plotId}
                onChange={(e) => setPlotId(e.target.value)}
                className="form-select"
              >
                <option value="">
                  {plots.length === 1 ? "Área principal (automático)" : "Selecione a área..."}
                </option>
                {plots.map((plot) => (
                  <option key={plot.id} value={plot.id}>
                    {plot.name} {plot.area?.value ? `(${plot.area.value} ${plot.area.unit})` : ""}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Vínculo com Plantio Ativo para Aplicação, Colheita e Saída (Item 3 do Backlog) */}
          {(selectedType === "INPUT_APPLICATION" ||
            selectedType === "HARVEST" ||
            selectedType === "OUTFLOW") && (
            <div className="mobile-card">
              <h2 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "8px" }}>
                Vínculo com Plantio Ativo
              </h2>
              <p
                style={{
                  fontSize: "12px",
                  color: "var(--color-text-secondary)",
                  marginBottom: "10px",
                }}
              >
                {selectedType === "HARVEST"
                  ? "Selecione de qual plantio em andamento esta colheita está sendo realizada (obrigatório):"
                  : "Vincule opcionalmente a um plantio em andamento para rastreabilidade do lote:"}
              </p>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="planting-select" className="form-label">
                  Plantio {selectedType === "HARVEST" ? "*" : "(opcional)"}
                </label>
                <select
                  id="planting-select"
                  value={plantingId}
                  onChange={(e) => setPlantingId(e.target.value)}
                  className="form-select"
                  required={selectedType === "HARVEST"}
                >
                  <option value="">
                    {selectedType === "HARVEST"
                      ? "Selecione o plantio..."
                      : "Sem vínculo com plantio específico"}
                  </option>
                  {activePlantings.map((pl) => (
                    <option key={pl.id} value={pl.id}>
                      {pl.subject || "Plantio"} —{" "}
                      {pl.performedOn ? formatDatePtBr(pl.performedOn) : ""}
                      {pl.plot?.name ? ` (${pl.plot.name})` : ""}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {/* Campos específicos: PLANTIO */}
          {selectedType === "PLANTING" && (
            <div className="mobile-card">
              <h2 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "12px" }}>
                Detalhes do Plantio
              </h2>

              <div className="form-group">
                <label htmlFor="crop-select" className="form-label">
                  Cultura
                </label>
                <select
                  id="crop-select"
                  value={cropId}
                  onChange={(e) => {
                    setCropId(e.target.value);
                    if (e.target.value && e.target.value !== "OTHER") setCropOtherName("");
                  }}
                  className="form-select"
                >
                  <option value="">Selecione a cultura do catálogo</option>
                  {crops.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                  <option value="OTHER">Outra cultura (digitar nome)</option>
                </select>
              </div>

              {(!cropId || cropId === "OTHER") && (
                <div className="form-group">
                  <label htmlFor="crop-other" className="form-label">
                    Nome da Cultura
                  </label>
                  <input
                    id="crop-other"
                    type="text"
                    placeholder="Ex: Mandioca, Milho Crioulo, Alface"
                    value={cropOtherName}
                    onChange={(e) => setCropOtherName(e.target.value)}
                    className="form-input"
                  />
                </div>
              )}

              <div className="form-group">
                <label htmlFor="variety" className="form-label">
                  Variedade
                </label>
                <input
                  id="variety"
                  type="text"
                  placeholder="Ex: Crioula, Amarelão, Manteiga"
                  value={variety}
                  onChange={(e) => setVariety(e.target.value)}
                  className="form-input"
                />
              </div>

              <div className="form-group">
                <label htmlFor="newrecord-area-plantada" className="form-label">
                  Área Plantada
                </label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    id="newrecord-area-plantada"
                    type="number"
                    step="any"
                    placeholder="Ex: 500"
                    value={plantedAreaVal}
                    onChange={(e) => setPlantedAreaVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <select
                    value={plantedAreaUnit}
                    onChange={(e) => setPlantedAreaUnit(e.target.value as AreaUnitCode)}
                    className="form-select"
                    style={{ flex: 1 }}
                  >
                    <option value="SQUARE_METER">m²</option>
                    <option value="HECTARE">ha</option>
                    <option value="TAREFA">tarefas</option>
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="propagation-select" className="form-label">
                  Forma de Propagação
                </label>
                <select
                  id="propagation-select"
                  value={propagationType}
                  onChange={(e) => setPropagationType(e.target.value as typeof propagationType)}
                  className="form-select"
                >
                  <option value="">Selecione (Semente, Muda, Maniva...)</option>
                  <option value="SEED">Semente</option>
                  <option value="SEEDLING">Muda</option>
                  <option value="CUTTING">Estaca / Maniva</option>
                  <option value="OTHER">Outra</option>
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="seed-origin-select" className="form-label">
                  Origem da Semente / Muda
                </label>
                <select
                  id="seed-origin-select"
                  value={seedOrigin}
                  onChange={(e) => setSeedOrigin(e.target.value as typeof seedOrigin)}
                  className="form-select"
                >
                  <option value="">Selecione a procedência</option>
                  <option value="OWN">Produção própria / Crioula</option>
                  <option value="SEED_BANK">Banco de sementes / Troca</option>
                  <option value="ORGANIC_PURCHASED">Comprada (Orgânica)</option>
                  <option value="CONVENTIONAL_PURCHASED">
                    Comprada (Convencional autorizada pelo SPG)
                  </option>
                </select>
              </div>

              {(seedOrigin === "ORGANIC_PURCHASED" || seedOrigin === "CONVENTIONAL_PURCHASED") && (
                <div className="form-group">
                  <label htmlFor="seed-supplier" className="form-label">
                    Fornecedor da Semente / Muda
                  </label>
                  <input
                    id="seed-supplier"
                    type="text"
                    placeholder="Ex: Casa do Agricultor / Viveiro Cooperativa"
                    value={seedSupplier}
                    onChange={(e) => setSeedSupplier(e.target.value)}
                    className="form-input"
                  />
                </div>
              )}

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="expected-harvest" className="form-label">
                  Previsão de Colheita
                </label>
                <input
                  id="expected-harvest"
                  type="date"
                  value={expectedHarvestOn}
                  onChange={(e) => setExpectedHarvestOn(e.target.value)}
                  className="form-input"
                />
              </div>
            </div>
          )}

          {/* Campos específicos: APLICAÇÃO DE INSUMO */}
          {selectedType === "INPUT_APPLICATION" && (
            <div className="mobile-card">
              <h2 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "12px" }}>
                Detalhes da Aplicação
              </h2>

              <div className="form-group">
                <label htmlFor="input-select" className="form-label">
                  Insumo
                </label>
                <select
                  id="input-select"
                  value={inputId}
                  onChange={(e) => {
                    setInputId(e.target.value);
                    if (e.target.value && e.target.value !== "OTHER") setInputOtherName("");
                  }}
                  className="form-select"
                >
                  <option value="">Selecione o insumo cadastrado</option>
                  {farmInputs.map((i) => (
                    <option key={i.id} value={i.id}>
                      {i.name}
                    </option>
                  ))}
                  <option value="OTHER">Outro insumo (digitar)</option>
                </select>
              </div>

              {(!inputId || inputId === "OTHER") && (
                <div className="form-group">
                  <label htmlFor="input-other" className="form-label">
                    Nome do Insumo
                  </label>
                  <input
                    id="input-other"
                    type="text"
                    placeholder="Ex: Calda Bordalesa, Esterco Bovino, Biofertilizante"
                    value={inputOtherName}
                    onChange={(e) => setInputOtherName(e.target.value)}
                    className="form-input"
                  />
                </div>
              )}

              <div className="form-group">
                <label htmlFor="purpose-select" className="form-label">
                  Finalidade da Aplicação
                </label>
                <select
                  id="purpose-select"
                  value={purpose}
                  onChange={(e) =>
                    setPurpose(
                      e.target.value as
                        "FERTILIZATION" | "PEST_DISEASE_CONTROL" | "SOIL_CORRECTION" | "OTHER",
                    )
                  }
                  className="form-select"
                >
                  <option value="FERTILIZATION">Adubação / Nutrição</option>
                  <option value="PEST_DISEASE_CONTROL">Controle de Pragas e Doenças</option>
                  <option value="SOIL_CORRECTION">Correção do Solo</option>
                  <option value="OTHER">Outra Finalidade</option>
                </select>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="newrecord-quantidade-dose-aplicada" className="form-label">
                  Quantidade / Dose Aplicada
                </label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    id="newrecord-quantidade-dose-aplicada"
                    type="number"
                    step="any"
                    placeholder="Ex: 20"
                    value={applicationQtyVal}
                    onChange={(e) => setApplicationQtyVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <QuantityUnitSelect
                    value={applicationQtyUnit}
                    onChange={setApplicationQtyUnit}
                    units={units}
                  />
                </div>
              </div>
            </div>
          )}

          {/* Campos específicos: COLHEITA */}
          {selectedType === "HARVEST" && (
            <div className="mobile-card">
              <h2 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "12px" }}>
                Detalhes da Colheita
              </h2>

              <div className="form-group">
                <label htmlFor="harvest-crop" className="form-label">
                  Complemento / Lote Colhido (opcional)
                </label>
                <input
                  id="harvest-crop"
                  type="text"
                  placeholder="Ex: Primeira apanha, Lote Feira de Sábado"
                  value={harvestCropName}
                  onChange={(e) => setHarvestCropName(e.target.value)}
                  className="form-input"
                />
              </div>

              <div className="form-group">
                <label htmlFor="newrecord-quantidade-colhida" className="form-label">
                  Quantidade Colhida
                </label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    id="newrecord-quantidade-colhida"
                    type="number"
                    step="any"
                    placeholder="Ex: 150"
                    value={harvestQtyVal}
                    onChange={(e) => setHarvestQtyVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <QuantityUnitSelect
                    value={harvestQtyUnit}
                    onChange={setHarvestQtyUnit}
                    units={units}
                  />
                </div>
              </div>

              <div
                style={{ display: "flex", alignItems: "center", gap: "10px", marginTop: "12px" }}
              >
                <input
                  id="is-final-check"
                  type="checkbox"
                  checked={isFinal}
                  onChange={(e) => setIsFinal(e.target.checked)}
                  style={{ width: "20px", height: "20px", accentColor: "var(--color-primary)" }}
                />
                <label htmlFor="is-final-check" style={{ fontSize: "14px", cursor: "pointer" }}>
                  Colheita final (encerra o ciclo do plantio)
                </label>
              </div>
            </div>
          )}

          {/* Campos específicos: COMPRA DE INSUMO */}
          {selectedType === "INPUT_PURCHASE" && (
            <div className="mobile-card">
              <h2 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "12px" }}>
                Detalhes da Compra
              </h2>

              <div className="form-group">
                <label htmlFor="purchase-input-select" className="form-label">
                  Insumo do Catálogo
                </label>
                <select
                  id="purchase-input-select"
                  value={purchaseInputId}
                  onChange={(e) => {
                    setPurchaseInputId(e.target.value);
                    if (e.target.value && e.target.value !== "OTHER") setPurchaseInputName("");
                  }}
                  className="form-select"
                >
                  <option value="">Outro / Digitar manualmente</option>
                  {farmInputs.map((i) => (
                    <option key={i.id} value={i.id}>
                      {i.name}
                    </option>
                  ))}
                  <option value="OTHER">Digitar nome manualmente</option>
                </select>
              </div>

              {(!purchaseInputId || purchaseInputId === "OTHER") && (
                <div className="form-group">
                  <label htmlFor="purchase-name" className="form-label">
                    Nome do Insumo Comprado
                  </label>
                  <input
                    id="purchase-name"
                    type="text"
                    placeholder="Ex: Sementes de Milho Crioulo, Termofosfato"
                    value={purchaseInputName}
                    onChange={(e) => setPurchaseInputName(e.target.value)}
                    className="form-input"
                  />
                </div>
              )}

              <div className="form-group">
                <label htmlFor="newrecord-quantidade-comprada" className="form-label">
                  Quantidade Comprada
                </label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    id="newrecord-quantidade-comprada"
                    type="number"
                    step="any"
                    placeholder="Ex: 5"
                    value={purchaseQtyVal}
                    onChange={(e) => setPurchaseQtyVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <QuantityUnitSelect
                    value={purchaseQtyUnit}
                    onChange={setPurchaseQtyUnit}
                    units={units}
                  />
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="supplier" className="form-label">
                  Fornecedor / Estabelecimento
                </label>
                <input
                  id="supplier"
                  type="text"
                  placeholder="Ex: Cooperativa Agropecuária, Agropecuária Central"
                  value={supplier}
                  onChange={(e) => setSupplier(e.target.value)}
                  className="form-input"
                />
              </div>
            </div>
          )}

          {/* Campos específicos: SAÍDA / VENDA */}
          {selectedType === "OUTFLOW" && (
            <div className="mobile-card">
              <h2 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "12px" }}>
                Detalhes da Saída / Venda
              </h2>

              <div className="form-group">
                <label htmlFor="outflow-crop-select" className="form-label">
                  Cultura do Catálogo
                </label>
                <select
                  id="outflow-crop-select"
                  value={outflowCropId}
                  onChange={(e) => {
                    setOutflowCropId(e.target.value);
                    if (e.target.value && e.target.value !== "OTHER") setOutflowProductName("");
                  }}
                  className="form-select"
                >
                  <option value="">Outro produto / Digitar manualmente</option>
                  {crops.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                  <option value="OTHER">Digitar nome do produto</option>
                </select>
              </div>

              {(!outflowCropId || outflowCropId === "OTHER") && (
                <div className="form-group">
                  <label htmlFor="outflow-product" className="form-label">
                    Produto Vendido / Destinado
                  </label>
                  <input
                    id="outflow-product"
                    type="text"
                    placeholder="Ex: Polpa de Fruta, Mandioca embalada, Hortaliças"
                    value={outflowProductName}
                    onChange={(e) => setOutflowProductName(e.target.value)}
                    className="form-input"
                  />
                </div>
              )}

              <div className="form-group">
                <label htmlFor="newrecord-quantidade" className="form-label">
                  Quantidade
                </label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    id="newrecord-quantidade"
                    type="number"
                    step="any"
                    placeholder="Ex: 50"
                    value={outflowQtyVal}
                    onChange={(e) => setOutflowQtyVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <QuantityUnitSelect
                    value={outflowQtyUnit}
                    onChange={setOutflowQtyUnit}
                    units={units}
                  />
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="destination-select" className="form-label">
                  Destino do Produto
                </label>
                <select
                  id="destination-select"
                  value={destination}
                  onChange={(e) =>
                    setDestination(
                      e.target.value as
                        | "FAIR"
                        | "COOPERATIVE"
                        | "PNAE"
                        | "PAA"
                        | "MIDDLEMAN"
                        | "DIRECT_SALE"
                        | "OWN_CONSUMPTION"
                        | "DONATION"
                        | "OTHER",
                    )
                  }
                  className="form-select"
                >
                  <option value="FAIR">Feira Livre / Produtor</option>
                  <option value="COOPERATIVE">Cooperativa</option>
                  <option value="PNAE">PNAE (Merenda Escolar)</option>
                  <option value="PAA">PAA (Aquisição de Alimentos)</option>
                  <option value="DIRECT_SALE">Venda Direta ao Consumidor</option>
                  <option value="OWN_CONSUMPTION">Consumo Próprio Familiar</option>
                  <option value="DONATION">Doação</option>
                  <option value="OTHER">Outro Destino</option>
                </select>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="buyer-name" className="form-label">
                  Comprador / Destinatário
                </label>
                <input
                  id="buyer-name"
                  type="text"
                  placeholder="Ex: Prefeitura Municipal, Consumidores da Feira"
                  value={buyerName}
                  onChange={(e) => setBuyerName(e.target.value)}
                  className="form-input"
                />
              </div>
            </div>
          )}

          {/* Upload de Foto / Anexo da Câmera (Item 2 do Backlog) */}
          <div className="mobile-card">
            <h2 style={{ fontSize: "14px", fontWeight: 600, marginBottom: "6px" }}>
              📷 Foto / Comprovante (opcional)
            </h2>
            <p
              style={{
                fontSize: "12px",
                color: "var(--color-text-secondary)",
                marginBottom: "12px",
              }}
            >
              {selectedType === "INPUT_PURCHASE"
                ? "Fotografe a nota fiscal, rótulo ou embalagem do insumo adquirido (necessário para completude RN09)."
                : "Tire uma foto do talhão, sementes ou atividade realizada."}
            </p>

            <input
              id="record-photo-input"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              capture="environment"
              onChange={handlePhotoChange}
              style={{ fontSize: "13px", width: "100%" }}
            />

            {photoPreviewUrl && (
              <div style={{ marginTop: "12px" }}>
                <img
                  src={photoPreviewUrl}
                  alt="Pré-visualização da foto"
                  style={{
                    width: "100%",
                    maxHeight: "180px",
                    objectFit: "cover",
                    borderRadius: "8px",
                    border: "1px solid var(--color-border)",
                  }}
                />
              </div>
            )}

            {isEditMode && (editDetailQuery.data?.attachments ?? []).length > 0 && (
              <div
                style={{ marginTop: "10px", fontSize: "12px", color: "var(--color-primary-dark)" }}
              >
                ✓ Este registro já possui {(editDetailQuery.data?.attachments ?? []).length} foto(s)
                anexada(s).
              </div>
            )}
          </div>

          {/* Observações Livres */}
          <div className="mobile-card">
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label htmlFor="notes" className="form-label">
                Observações Adicionais (opcional)
              </label>
              <textarea
                id="notes"
                rows={3}
                placeholder="Ex: Chuva boa no dia anterior; solo úmido e adubado com composto."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                className="form-textarea"
              />
            </div>
          </div>

          {/* Botão de Envio */}
          <button
            type="submit"
            className="btn-primary"
            disabled={isSubmitting}
            style={{ marginTop: "12px" }}
          >
            {isSubmitting
              ? "Gravando no Caderno..."
              : isEditMode
                ? "Salvar Alterações"
                : "Salvar Manejo"}
          </button>
        </form>
      </div>
    </div>
  );
}
