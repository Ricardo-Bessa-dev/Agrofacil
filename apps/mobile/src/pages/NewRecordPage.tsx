import { useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router";
import type { RecordWrite } from "@agrofacil/api-types";
import { todayIso, type AreaUnitCode } from "@agrofacil/domain";
import { api } from "../api/client";
import { RecordTypeIcon } from "../components/RecordTypeIcon";

type RecordType =
  | "PLANTING"
  | "INPUT_APPLICATION"
  | "HARVEST"
  | "INPUT_PURCHASE"
  | "OUTFLOW";

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

  const [selectedType, setSelectedType] = useState<RecordType>("PLANTING");
  const [performedOn, setPerformedOn] = useState<string>(todayIso());
  const [propertyId, setPropertyId] = useState<string>("");
  const [plotId, setPlotId] = useState<string>("");
  const [notes, setNotes] = useState<string>("");

  // Específicos para Plantio
  const [cropId, setCropId] = useState<string>("");
  const [cropOtherName, setCropOtherName] = useState<string>("");
  const [variety, setVariety] = useState<string>("");
  const [plantedAreaVal, setPlantedAreaVal] = useState<string>("");
  const [plantedAreaUnit, setPlantedAreaUnit] = useState<AreaUnitCode>("SQUARE_METER");
  const [expectedHarvestOn, setExpectedHarvestOn] = useState<string>("");

  // Específicos para Aplicação
  const [inputId, setInputId] = useState<string>("");
  const [inputOtherName, setInputOtherName] = useState<string>("");
  const [purpose, setPurpose] = useState<
    "FERTILIZATION" | "PEST_DISEASE_CONTROL" | "SOIL_CORRECTION" | "OTHER"
  >("FERTILIZATION");
  const [applicationQtyVal, setApplicationQtyVal] = useState<string>("");
  const [applicationQtyUnit, setApplicationQtyUnit] = useState<string>("L");

  // Específicos para Colheita
  const [harvestCropName, setHarvestCropName] = useState<string>("");
  const [harvestQtyVal, setHarvestQtyVal] = useState<string>("");
  const [harvestQtyUnit, setHarvestQtyUnit] = useState<string>("KG");
  const [isFinal, setIsFinal] = useState<boolean>(false);

  // Específicos para Compra de Insumo
  const [purchaseInputName, setPurchaseInputName] = useState<string>("");
  const [purchaseQtyVal, setPurchaseQtyVal] = useState<string>("");
  const [purchaseQtyUnit, setPurchaseQtyUnit] = useState<string>("KG");
  const [supplier, setSupplier] = useState<string>("");

  // Específicos para Saída
  const [outflowProductName, setOutflowProductName] = useState<string>("");
  const [outflowQtyVal, setOutflowQtyVal] = useState<string>("");
  const [outflowQtyUnit, setOutflowQtyUnit] = useState<string>("KG");
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

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

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

  // Atualiza a primeira propriedade automaticamente se ainda não selecionada
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

  const plots = plotsQuery.data ?? [];
  const crops = cropsQuery.data ?? [];
  const farmInputs = farmInputsQuery.data ?? [];

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    const targetPropertyId = currentPropertyId;
    if (!targetPropertyId) {
      setErrorMsg("Selecione a propriedade.");
      return;
    }

    setIsSubmitting(true);
    try {
      let bodyPayload: RecordWrite;

      if (selectedType === "PLANTING") {
        bodyPayload = {
          type: "PLANTING",
          propertyId: targetPropertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes || undefined,
          cropId: cropId || undefined,
          cropOtherName: cropOtherName || undefined,
          variety: variety || undefined,
          plantedArea: plantedAreaVal
            ? { value: Number(plantedAreaVal), unit: plantedAreaUnit }
            : undefined,
          expectedHarvestOn: expectedHarvestOn || undefined,
        };
      } else if (selectedType === "INPUT_APPLICATION") {
        bodyPayload = {
          type: "INPUT_APPLICATION",
          propertyId: targetPropertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes || undefined,
          inputId: inputId || undefined,
          inputOtherName: inputOtherName || undefined,
          purpose,
          quantity: applicationQtyVal
            ? { value: Number(applicationQtyVal), unit: applicationQtyUnit }
            : undefined,
        };
      } else if (selectedType === "HARVEST") {
        bodyPayload = {
          type: "HARVEST",
          propertyId: targetPropertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes || undefined,
          quantity: harvestQtyVal
            ? { value: Number(harvestQtyVal), unit: harvestQtyUnit }
            : undefined,
          isFinal,
        };
      } else if (selectedType === "INPUT_PURCHASE") {
        bodyPayload = {
          type: "INPUT_PURCHASE",
          propertyId: targetPropertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes || undefined,
          inputOtherName: purchaseInputName || undefined,
          quantity: purchaseQtyVal
            ? { value: Number(purchaseQtyVal), unit: purchaseQtyUnit }
            : undefined,
          supplier: supplier || undefined,
        };
      } else {
        // OUTFLOW
        bodyPayload = {
          type: "OUTFLOW",
          propertyId: targetPropertyId,
          plotId: plotId || undefined,
          performedOn,
          notes: notes || undefined,
          productOtherName: outflowProductName || undefined,
          quantity: outflowQtyVal
            ? { value: Number(outflowQtyVal), unit: outflowQtyUnit }
            : undefined,
          destination,
          buyerName: buyerName || undefined,
        };
      }

      const { error, response } = await api.POST("/records", {
        body: bodyPayload,
      });

      if (error || !response.ok) {
        const errObj = (typeof error === "object" && error !== null ? error : {}) as Record<string, unknown>;
        throw new Error(
          typeof errObj.detail === "string"
            ? errObj.detail
            : "Não foi possível registrar o manejo. Verifique os dados e tente novamente."
        );
      }

      // Sucesso: invalida cache e volta para a lista
      await queryClient.invalidateQueries({ queryKey: ["records"] });
      await queryClient.invalidateQueries({ queryKey: ["recent-records"] });
      navigate("/registros", { replace: true });
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : "Erro ao salvar registro.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ paddingBottom: "32px" }}>
      {/* Top Bar */}
      <div className="page-top-bar">
        <button
          type="button"
          className="page-top-bar-back"
          onClick={() => navigate(-1)}
        >
          <span>←</span>
          <span>Voltar</span>
        </button>
        <h1 className="page-top-bar-title">Novo Manejo</h1>
        <div style={{ width: "48px" }} />
      </div>

      <div style={{ padding: "16px" }}>
        {/* Escolha do Tipo de Manejo */}
        <div style={{ marginBottom: "20px" }}>
          <label className="form-label" style={{ marginBottom: "10px" }}>
            Tipo de Atividade / Manejo
          </label>
          <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
            {RECORD_TYPES.map((t) => {
              const isSelected = selectedType === t.type;
              return (
                <div
                  key={t.type}
                  onClick={() => setSelectedType(t.type)}
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
                        color: isSelected
                          ? "var(--color-primary-dark)"
                          : "var(--color-text)",
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
                Talhão (opcional)
              </label>
              <select
                id="plot-select"
                value={plotId}
                onChange={(e) => setPlotId(e.target.value)}
                className="form-select"
              >
                <option value="">Selecione o talhão (opcional)</option>
                {plots.map((plot) => (
                  <option key={plot.id} value={plot.id}>
                    {plot.name} {plot.area?.value ? `(${plot.area.value} ${plot.area.unit})` : ""}
                  </option>
                ))}
              </select>
            </div>
          </div>

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
                    if (e.target.value) setCropOtherName("");
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
                <label className="form-label">Área Plantada</label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
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
                    onChange={(e) =>
                      setPlantedAreaUnit(e.target.value as AreaUnitCode)
                    }
                    className="form-select"
                    style={{ flex: 1 }}
                  >
                    <option value="SQUARE_METER">m²</option>
                    <option value="HECTARE">ha</option>
                    <option value="TAREFA">tarefas</option>
                  </select>
                </div>
              </div>

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
                    if (e.target.value) setInputOtherName("");
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
                        | "FERTILIZATION"
                        | "PEST_DISEASE_CONTROL"
                        | "SOIL_CORRECTION"
                        | "OTHER"
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
                <label className="form-label">Quantidade / Dose Aplicada</label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    type="number"
                    step="any"
                    placeholder="Ex: 20"
                    value={applicationQtyVal}
                    onChange={(e) => setApplicationQtyVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <select
                    value={applicationQtyUnit}
                    onChange={(e) => setApplicationQtyUnit(e.target.value)}
                    className="form-select"
                    style={{ flex: 1 }}
                  >
                    <option value="L">Litros (L)</option>
                    <option value="ML">Mililitros (mL)</option>
                    <option value="KG">Quilos (kg)</option>
                    <option value="G">Gramas (g)</option>
                  </select>
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
                  Cultura / Produto Colhido
                </label>
                <input
                  id="harvest-crop"
                  type="text"
                  placeholder="Ex: Mandioca, Maracujá da Caatinga, Feijão de Corda"
                  value={harvestCropName}
                  onChange={(e) => setHarvestCropName(e.target.value)}
                  className="form-input"
                />
              </div>

              <div className="form-group">
                <label className="form-label">Quantidade Colhida</label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    type="number"
                    step="any"
                    placeholder="Ex: 150"
                    value={harvestQtyVal}
                    onChange={(e) => setHarvestQtyVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <select
                    value={harvestQtyUnit}
                    onChange={(e) => setHarvestQtyUnit(e.target.value)}
                    className="form-select"
                    style={{ flex: 1 }}
                  >
                    <option value="KG">kg</option>
                    <option value="CX">caixas</option>
                    <option value="SACAS">sacas</option>
                    <option value="DZ">dúzias</option>
                    <option value="UN">unidades</option>
                  </select>
                </div>
              </div>

              <div style={{ display: "flex", alignItems: "center", gap: "10px", marginTop: "12px" }}>
                <input
                  id="is-final-check"
                  type="checkbox"
                  checked={isFinal}
                  onChange={(e) => setIsFinal(e.target.checked)}
                  style={{ width: "20px", height: "20px", accentColor: "var(--color-primary)" }}
                />
                <label htmlFor="is-final-check" style={{ fontSize: "14px", cursor: "pointer" }}>
                  Colheita final (encerra o ciclo do talhão)
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

              <div className="form-group">
                <label className="form-label">Quantidade Comprada</label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    type="number"
                    step="any"
                    placeholder="Ex: 5"
                    value={purchaseQtyVal}
                    onChange={(e) => setPurchaseQtyVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <select
                    value={purchaseQtyUnit}
                    onChange={(e) => setPurchaseQtyUnit(e.target.value)}
                    className="form-select"
                    style={{ flex: 1 }}
                  >
                    <option value="KG">kg</option>
                    <option value="SACAS">sacas</option>
                    <option value="L">litros</option>
                    <option value="UN">unidades</option>
                  </select>
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

              <div className="form-group">
                <label className="form-label">Quantidade</label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    type="number"
                    step="any"
                    placeholder="Ex: 50"
                    value={outflowQtyVal}
                    onChange={(e) => setOutflowQtyVal(e.target.value)}
                    className="form-input"
                    style={{ flex: 2 }}
                  />
                  <select
                    value={outflowQtyUnit}
                    onChange={(e) => setOutflowQtyUnit(e.target.value)}
                    className="form-select"
                    style={{ flex: 1 }}
                  >
                    <option value="KG">kg</option>
                    <option value="CX">caixas</option>
                    <option value="UN">unidades</option>
                    <option value="DZ">dúzias</option>
                  </select>
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
                        | "OTHER"
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
            {isSubmitting ? "Gravando no Caderno..." : "Salvar Manejo"}
          </button>
        </form>
      </div>
    </div>
  );
}
