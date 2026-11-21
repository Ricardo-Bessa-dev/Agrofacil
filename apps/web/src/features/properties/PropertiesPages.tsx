// Telas de Propriedades do painel do coordenador: cards do Figma (49:1086 / 103:58), áreas (talhões), trabalhadores e caderno de campo.
import React, { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PlotView } from "@agrofacil/api-types";
import {
  AREA_UNIT_LABELS,
  DEFAULT_TAREFA_M2,
  formatArea,
  formatDatePtBr,
  freePropertyAreaM2,
  fromSquareMeters,
  plotFitsInProperty,
  PLOT_STATUS_LABELS,
  VISIT_CYCLE_STATE_LABELS,
  WORKER_RELATIONSHIP_LABELS,
} from "@agrofacil/domain";
import { api, ApiProblemError, downloadFile, unwrap } from "../../api/client";
import { fetchAllPages } from "../../api/pagination";
import { fetchMunicipalities } from "../../api/municipalities";
import { CertificationStatusBadge } from "../../components/StatusBadge";
import propertyImg1 from "../../assets/figma/property-placeholder-1.webp";
import propertyImg2 from "../../assets/figma/property-placeholder-2.webp";
import iconMapPin from "../../assets/figma/icon-map-pin.svg";
import iconFarm from "../../assets/figma/icon-farm.svg";
import iconArrowRight from "../../assets/figma/icon-arrow-right.svg";
import { Pagination } from "../../components/Pagination";

export function PropertiesListPage() {
  const [searchParams] = useSearchParams();
  const initialFarmerId = searchParams.get("farmerId") ?? "";

  const [search, setSearch] = useState("");
  const [farmerId, setFarmerId] = useState(initialFarmerId);
  const [certStatus, setCertStatus] = useState<
    "" | "VALID" | "EXPIRING" | "EXPIRED" | "SUSPENDED" | "CANCELLED"
  >("");
  const [viewMode, setViewMode] = useState<"cards" | "table" | "map">("cards");
  const [page, setPage] = useState(0);
  const [showCreateModal, setShowCreateModal] = useState(Boolean(initialFarmerId));

  const propertiesQuery = useQuery({
    queryKey: ["properties", { search, farmerId, certStatus, page }],
    queryFn: () =>
      unwrap(
        api.GET("/properties", {
          params: {
            query: {
              filter: {
                search: search.trim() || undefined,
                farmerId: farmerId || undefined,
                certificationStatus: certStatus || undefined,
              },
              pageable: { page, size: 12, sort: ["name,asc"] },
            },
          },
        }),
      ),
  });

  const mapQuery = useQuery({
    queryKey: ["properties", "map"],
    enabled: viewMode === "map",
    queryFn: () => unwrap(api.GET("/properties/map")),
  });

  const items = propertiesQuery.data?.content ?? [];
  const meta = propertiesQuery.data?.page;

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Propriedades</h1>
          <p className="af-page-subtitle">
            Unidades de produção familiar, talhões, mão de obra e situação de conformidade orgânica.
          </p>
        </div>
        <div className="af-topbar-actions">
          <div style={{ display: "inline-flex", gap: 6 }}>
            <button
              type="button"
              className={`af-btn af-btn--sm ${
                viewMode === "cards" ? "af-btn--secondary" : "af-btn--outline"
              }`}
              onClick={() => setViewMode("cards")}
            >
              Cartões
            </button>
            <button
              type="button"
              className={`af-btn af-btn--sm ${
                viewMode === "table" ? "af-btn--secondary" : "af-btn--outline"
              }`}
              onClick={() => setViewMode("table")}
            >
              Tabela
            </button>
            <button
              type="button"
              className={`af-btn af-btn--sm ${
                viewMode === "map" ? "af-btn--secondary" : "af-btn--outline"
              }`}
              onClick={() => setViewMode("map")}
            >
              Georreferenciamento
            </button>
          </div>
          <button
            type="button"
            className="af-btn af-btn--primary af-btn--sm"
            onClick={() => setShowCreateModal(true)}
          >
            + Nova propriedade
          </button>
        </div>
      </div>

      <div className="af-card" style={{ marginBottom: 20 }}>
        <div className="af-filters-bar">
          <div className="af-field" style={{ minWidth: 240, flex: 1 }}>
            <label className="af-label" htmlFor="prop-search">
              Buscar por nome da propriedade ou agricultor
            </label>
            <input
              id="prop-search"
              type="search"
              className="af-input"
              placeholder="Ex.: Sítio Boa Esperança..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>

          <div className="af-field" style={{ minWidth: 200 }}>
            <label className="af-label" htmlFor="prop-cert">
              Situação da certificação
            </label>
            <select
              id="prop-cert"
              className="af-select"
              value={certStatus}
              onChange={(e) => {
                setCertStatus(e.target.value as typeof certStatus);
                setPage(0);
              }}
            >
              <option value="">Todas</option>
              <option value="VALID">Em dia</option>
              <option value="EXPIRING">Vencendo</option>
              <option value="EXPIRED">Vencidas</option>
              <option value="SUSPENDED">Suspensas</option>
              <option value="CANCELLED">Canceladas</option>
            </select>
          </div>

          {farmerId && (
            <button
              type="button"
              className="af-btn af-btn--ghost af-btn--sm"
              onClick={() => setFarmerId("")}
            >
              Limpar filtro de agricultor ✕
            </button>
          )}
        </div>
      </div>

      {viewMode === "map" ? (
        <div className="af-card">
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">Pontos georreferenciados das propriedades</h2>
              <p className="af-card-subtitle">
                Coordenadas GPS registradas para roteiro de visitas do SPG
              </p>
            </div>
          </div>
          {mapQuery.isLoading ? (
            <div className="af-empty-state">Carregando coordenadas...</div>
          ) : (mapQuery.data ?? []).length === 0 ? (
            <div className="af-empty-state">
              Nenhuma propriedade com localização GPS cadastrada.
            </div>
          ) : (
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Propriedade</th>
                    <th>Agricultor</th>
                    <th>Latitude / Longitude</th>
                    <th>Certificação</th>
                    <th style={{ textAlign: "right" }}>Mapa externo</th>
                  </tr>
                </thead>
                <tbody>
                  {(mapQuery.data ?? []).map((m) => (
                    <tr key={m.id}>
                      <td>
                        <Link
                          to={`/propriedades/${m.id}`}
                          style={{ fontWeight: 700, color: "var(--af-green-darker)" }}
                        >
                          {m.name}
                        </Link>
                      </td>
                      <td>{m.farmerName ?? "—"}</td>
                      <td>
                        {m.lat !== undefined && m.lng !== undefined
                          ? `${m.lat.toFixed(5)}, ${m.lng.toFixed(5)}`
                          : "Sem GPS"}
                      </td>
                      <td>
                        <CertificationStatusBadge status={m.certificationStatus} />
                      </td>
                      <td style={{ textAlign: "right" }}>
                        {m.lat !== undefined && m.lng !== undefined && (
                          <a
                            href={`https://www.openstreetmap.org/?mlat=${m.lat}&mlon=${m.lng}#map=16/${m.lat}/${m.lng}`}
                            target="_blank"
                            rel="noreferrer"
                            className="af-btn af-btn--outline af-btn--sm"
                          >
                            Ver no OpenStreetMap
                          </a>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : propertiesQuery.isLoading ? (
        <div className="af-empty-state">Carregando propriedades...</div>
      ) : items.length === 0 ? (
        <div className="af-card">
          <div className="af-empty-state">Nenhuma propriedade encontrada.</div>
        </div>
      ) : viewMode === "cards" ? (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(360px, 1fr))",
            gap: 16,
          }}
        >
          {items.map((prop, idx) => (
            <Link
              key={prop.id}
              to={`/propriedades/${prop.id}`}
              className="af-property-card"
              style={{ background: "var(--af-bg-surface)" }}
            >
              <img
                src={idx % 2 === 0 ? propertyImg1 : propertyImg2}
                alt={prop.name ?? "Propriedade"}
                className="af-property-card-thumb"
              />
              <div className="af-property-card-body">
                <div>
                  <div className="af-property-card-title">{prop.name}</div>
                  <div className="af-property-card-meta">
                    <img src={iconMapPin} alt="" width={11} height={11} />
                    <span>
                      {prop.address?.locality
                        ? `${prop.address.locality} — ${prop.address.municipalityName ?? ""}`
                        : (prop.address?.municipalityName ?? prop.farmerName ?? "Sem endereço")}
                    </span>
                  </div>
                  <div className="af-property-card-meta">
                    <img src={iconFarm} alt="" width={11} height={11} />
                    <span>
                      {prop.farmerName ?? "Agricultor"} • {formatArea(prop.totalArea)}
                    </span>
                  </div>
                </div>
                <div
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}
                >
                  <CertificationStatusBadge
                    status={prop.currentCertification?.status}
                    daysToExpire={prop.currentCertification?.daysToExpire}
                  />
                  <img src={iconArrowRight} alt="" width={14} height={14} />
                </div>
              </div>
            </Link>
          ))}
        </div>
      ) : (
        <div className="af-card">
          <div className="af-table-wrap">
            <table className="af-table">
              <thead>
                <tr>
                  <th>Propriedade</th>
                  <th>Agricultor</th>
                  <th>Município / Comunidade</th>
                  <th>Área total</th>
                  <th>Certificação atual</th>
                  <th style={{ textAlign: "right" }}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {items.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <Link
                        to={`/propriedades/${p.id}`}
                        style={{ fontWeight: 700, color: "var(--af-green-darker)" }}
                      >
                        {p.name}
                      </Link>
                    </td>
                    <td>
                      {p.farmerId ? (
                        <Link to={`/agricultores/${p.farmerId}`}>{p.farmerName}</Link>
                      ) : (
                        p.farmerName
                      )}
                    </td>
                    <td>
                      {p.address?.municipalityName ?? "—"}
                      {p.address?.locality ? ` (${p.address.locality})` : ""}
                    </td>
                    <td>{formatArea(p.totalArea)}</td>
                    <td>
                      <CertificationStatusBadge
                        status={p.currentCertification?.status}
                        daysToExpire={p.currentCertification?.daysToExpire}
                      />
                    </td>
                    <td style={{ textAlign: "right" }}>
                      <Link
                        to={`/propriedades/${p.id}`}
                        className="af-btn af-btn--outline af-btn--sm"
                      >
                        Detalhes
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {viewMode !== "map" && (
        <Pagination meta={meta} onPageChange={setPage} itemsLabel="propriedades" />
      )}

      {showCreateModal && (
        <CreatePropertyModal defaultFarmerId={farmerId} onClose={() => setShowCreateModal(false)} />
      )}
    </div>
  );
}

function CreatePropertyModal({
  defaultFarmerId,
  onClose,
}: {
  defaultFarmerId?: string;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [farmerId, setFarmerId] = useState(defaultFarmerId ?? "");
  const [name, setName] = useState("");
  const [areaValue, setAreaValue] = useState("");
  const [areaUnit, setAreaUnit] = useState<"HECTARE" | "TAREFA" | "SQUARE_METER">("TAREFA");
  const [uf, setUf] = useState("BA");
  const [municipalityId, setMunicipalityId] = useState("");
  const [locality, setLocality] = useState("");
  const [accessDirections, setAccessDirections] = useState("");
  const [productionDescription, setProductionDescription] = useState("");
  const [lat, setLat] = useState("");
  const [lng, setLng] = useState("");
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

  const municipalitiesQuery = useQuery({
    queryKey: ["municipalities", uf],
    queryFn: () => fetchMunicipalities(uf),
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!farmerId) {
      setError("Selecione o agricultor responsável pela propriedade.");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      await unwrap(
        api.POST("/properties", {
          body: {
            farmerId,
            name: name.trim(),
            totalArea: areaValue ? { value: Number(areaValue), unit: areaUnit } : undefined,
            productionDescription: productionDescription.trim() || undefined,
            accessDirections: accessDirections.trim() || undefined,
            address: {
              uf,
              municipalityId: municipalityId ? Number(municipalityId) : undefined,
              locality: locality.trim() || undefined,
            },
            location: lat && lng ? { lat: Number(lat), lng: Number(lng) } : undefined,
          },
        }),
      );
      await queryClient.invalidateQueries({ queryKey: ["properties"] });
      await queryClient.invalidateQueries({ queryKey: ["farmers"] });
      onClose();
    } catch (err) {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao cadastrar propriedade.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="af-modal-backdrop" onClick={onClose}>
      <div className="af-modal" onClick={(e) => e.stopPropagation()}>
        <div className="af-modal-header">
          <h3 className="af-card-title">Nova Propriedade</h3>
          <button type="button" className="af-btn af-btn--ghost af-btn--sm" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="af-modal-body">
          {error && (
            <div className="af-alert af-alert--danger" style={{ marginBottom: 14 }}>
              {error}
            </div>
          )}
          <form className="af-form-grid" onSubmit={handleSubmit}>
            <div className="af-field">
              <label htmlFor="prop-agricultor-responsavel" className="af-label">
                Agricultor responsável *
              </label>
              <select
                id="prop-agricultor-responsavel"
                className="af-select"
                value={farmerId}
                onChange={(e) => setFarmerId(e.target.value)}
                required
              >
                <option value="">Selecione o agricultor</option>
                {(farmersQuery.data ?? []).map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.name} ({f.cpfMasked})
                  </option>
                ))}
              </select>
            </div>

            <div className="af-field">
              <label htmlFor="prop-nome-da-propriedade-sitio" className="af-label">
                Nome da propriedade / sítio *
              </label>
              <input
                id="prop-nome-da-propriedade-sitio"
                className="af-input"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                placeholder="Ex.: Sítio Bela Vista"
              />
            </div>

            <div className="af-form-row">
              <div className="af-field">
                <label htmlFor="prop-area-total" className="af-label">
                  Área total
                </label>
                <input
                  id="prop-area-total"
                  type="number"
                  step="0.01"
                  min="0.01"
                  className="af-input"
                  value={areaValue}
                  onChange={(e) => setAreaValue(e.target.value)}
                  placeholder="Ex.: 4.5"
                />
              </div>
              <div className="af-field">
                <label htmlFor="prop-unidade-de-medida" className="af-label">
                  Unidade de medida
                </label>
                <select
                  id="prop-unidade-de-medida"
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

            <div className="af-form-row">
              <div className="af-field">
                <label htmlFor="prop-uf" className="af-label">
                  UF
                </label>
                <select
                  id="prop-uf"
                  className="af-select"
                  value={uf}
                  onChange={(e) => {
                    setUf(e.target.value);
                    setMunicipalityId("");
                  }}
                >
                  <option value="BA">BA</option>
                  <option value="PE">PE</option>
                  <option value="SE">SE</option>
                  <option value="AL">AL</option>
                </select>
              </div>
              <div className="af-field">
                <label htmlFor="prop-municipio" className="af-label">
                  Município
                </label>
                <select
                  id="prop-municipio"
                  className="af-select"
                  value={municipalityId}
                  onChange={(e) => setMunicipalityId(e.target.value)}
                >
                  <option value="">Selecione</option>
                  {(municipalitiesQuery.data ?? []).map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label htmlFor="prop-comunidade-povoado" className="af-label">
                  Comunidade / Povoado
                </label>
                <input
                  id="prop-comunidade-povoado"
                  className="af-input"
                  value={locality}
                  onChange={(e) => setLocality(e.target.value)}
                  placeholder="Ex.: Serra da Mangabeira"
                />
              </div>
            </div>

            <div className="af-form-row">
              <div className="af-field">
                <label htmlFor="prop-latitude-opcional" className="af-label">
                  Latitude (opcional)
                </label>
                <input
                  id="prop-latitude-opcional"
                  type="number"
                  step="any"
                  className="af-input"
                  value={lat}
                  onChange={(e) => setLat(e.target.value)}
                  placeholder="-10.4521"
                />
              </div>
              <div className="af-field">
                <label htmlFor="prop-longitude-opcional" className="af-label">
                  Longitude (opcional)
                </label>
                <input
                  id="prop-longitude-opcional"
                  type="number"
                  step="any"
                  className="af-input"
                  value={lng}
                  onChange={(e) => setLng(e.target.value)}
                  placeholder="-40.1892"
                />
              </div>
            </div>

            <div className="af-field">
              <label htmlFor="prop-descricao-da-producao" className="af-label">
                Descrição da produção
              </label>
              <input
                id="prop-descricao-da-producao"
                className="af-input"
                value={productionDescription}
                onChange={(e) => setProductionDescription(e.target.value)}
                placeholder="Ex.: Hortaliças orgânicas, mandioca e fruticultura"
              />
            </div>

            <div className="af-field">
              <label htmlFor="prop-roteiro-de-acesso" className="af-label">
                Roteiro de acesso
              </label>
              <textarea
                id="prop-roteiro-de-acesso"
                className="af-textarea"
                value={accessDirections}
                onChange={(e) => setAccessDirections(e.target.value)}
                placeholder="Como chegar à propriedade para visitas de pares..."
              />
            </div>

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button type="button" className="af-btn af-btn--outline af-btn--sm" onClick={onClose}>
                Cancelar
              </button>
              <button type="submit" className="af-btn af-btn--primary af-btn--sm" disabled={saving}>
                {saving ? "Salvando..." : "Criar propriedade"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

export function PropertyDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<"plots" | "workers" | "certifications" | "visits">("plots");

  // Formulário de área (talhão/canteiro): cria uma nova ou edita a escolhida na tabela.
  const [editingPlot, setEditingPlot] = useState<PlotView | null>(null);
  const [plotError, setPlotError] = useState<string | null>(null);
  const [plotName, setPlotName] = useState("");
  const [plotArea, setPlotArea] = useState("");
  const [plotUnit, setPlotUnit] = useState<"TAREFA" | "HECTARE" | "SQUARE_METER">("TAREFA");
  const [plotStatus, setPlotStatus] = useState<
    "ORGANIC" | "IN_CONVERSION" | "NON_ORGANIC" | "NOT_DEFINED"
  >("ORGANIC");

  // Formulário rápido de novo trabalhador
  const [workerName, setWorkerName] = useState("");
  const [workerRelation, setWorkerRelation] = useState<"FAMILY" | "EMPLOYEE" | "PARTNER" | "OTHER">(
    "FAMILY",
  );

  const propQuery = useQuery({
    queryKey: ["properties", id],
    enabled: Boolean(id),
    queryFn: () => unwrap(api.GET("/properties/{id}", { params: { path: { id: id! } } })),
  });

  const visitStatusQuery = useQuery({
    queryKey: ["properties", id, "visit-status"],
    enabled: Boolean(id) && tab === "visits",
    queryFn: () =>
      unwrap(api.GET("/properties/{id}/visit-status", { params: { path: { id: id! } } })),
  });

  // Tamanho da tarefa da associação, para converter o que foi digitado e conferir se cabe (RN41).
  const assocQuery = useQuery({
    queryKey: ["association"],
    queryFn: () => unwrap(api.GET("/association")),
  });
  const tarefaM2 = assocQuery.data?.tarefaM2 ?? DEFAULT_TAREFA_M2;

  const resetPlotForm = () => {
    setEditingPlot(null);
    setPlotError(null);
    setPlotName("");
    setPlotArea("");
    setPlotStatus("ORGANIC");
  };

  const startEditingPlot = (plot: PlotView) => {
    setEditingPlot(plot);
    setPlotError(null);
    setPlotName(plot.name ?? "");
    setPlotArea(plot.area?.value !== undefined ? String(plot.area.value) : "");
    setPlotUnit((plot.area?.unit as typeof plotUnit | undefined) ?? "TAREFA");
    setPlotStatus(plot.status ?? "NOT_DEFINED");
  };

  const savePlotMutation = useMutation({
    mutationFn: () => {
      const body = {
        name: plotName.trim(),
        area: plotArea ? { value: Number(plotArea), unit: plotUnit } : undefined,
        status: plotStatus,
      };
      if (editingPlot?.id) {
        return unwrap(
          api.PATCH("/plots/{id}", {
            params: { path: { id: editingPlot.id } },
            body: {
              ...body,
              // Sem tamanho: null apaga o tamanho (no PATCH, campo ausente mantém o valor antigo).
              area: body.area ?? (null as unknown as undefined),
              version: editingPlot.version,
            },
          }),
        );
      }
      return unwrap(api.POST("/properties/{id}/plots", { params: { path: { id: id! } }, body }));
    },
    onSuccess: () => {
      resetPlotForm();
      queryClient.invalidateQueries({ queryKey: ["properties", id] });
    },
    onError: (error) => {
      const fieldMessage =
        error instanceof ApiProblemError ? error.errors.find((e) => e.message)?.message : undefined;
      setPlotError(fieldMessage ?? error.message);
    },
  });

  const deletePlotMutation = useMutation({
    mutationFn: (plotId: string) =>
      unwrap(api.DELETE("/plots/{id}", { params: { path: { id: plotId } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["properties", id] }),
  });

  const createWorkerMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/properties/{id}/workers", {
          params: { path: { id: id! } },
          body: {
            name: workerName.trim(),
            relationship: workerRelation,
            active: true,
          },
        }),
      ),
    onSuccess: () => {
      setWorkerName("");
      queryClient.invalidateQueries({ queryKey: ["properties", id] });
    },
  });

  const deleteWorkerMutation = useMutation({
    mutationFn: (workerId: string) =>
      unwrap(api.DELETE("/workers/{id}", { params: { path: { id: workerId } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["properties", id] }),
  });

  const prop = propQuery.data;
  const freeM2 = prop
    ? freePropertyAreaM2(prop.totalArea, prop.plots ?? [], editingPlot?.id)
    : null;
  const totalUnit = (prop?.totalArea?.unit as typeof plotUnit | undefined) ?? "TAREFA";
  const typedArea = Number(plotArea);
  const plotTooBig =
    freeM2 !== null &&
    plotArea !== "" &&
    typedArea > 0 &&
    !plotFitsInProperty(freeM2, typedArea, plotUnit, tarefaM2);
  if (propQuery.isLoading) {
    return <div className="af-empty-state">Carregando propriedade...</div>;
  }
  if (!prop) {
    return <div className="af-empty-state">Propriedade não encontrada.</div>;
  }

  return (
    <div>
      {/* Cabeçalho visual inspirado na tela Propriedade - Exemplo (103:58) */}
      <div
        className="af-card"
        style={{
          marginBottom: 20,
          display: "grid",
          gridTemplateColumns: "180px 1fr",
          gap: 20,
          alignItems: "center",
        }}
      >
        <img
          src={propertyImg1}
          alt={prop.name ?? "Propriedade"}
          style={{
            width: "100%",
            height: 130,
            objectFit: "cover",
            borderRadius: "var(--af-radius-md)",
          }}
        />
        <div>
          <div
            style={{
              display: "flex",
              alignItems: "flex-start",
              justifyContent: "space-between",
              gap: 12,
              flexWrap: "wrap",
            }}
          >
            <div>
              <h1 className="af-page-title" style={{ marginBottom: 4 }}>
                {prop.name}
              </h1>
              <div style={{ fontSize: "0.9rem", color: "var(--af-text-secondary)" }}>
                Agricultor:{" "}
                {prop.farmerId ? (
                  <Link
                    to={`/agricultores/${prop.farmerId}`}
                    style={{ fontWeight: 700, color: "var(--af-green-primary)" }}
                  >
                    {prop.farmerName}
                  </Link>
                ) : (
                  prop.farmerName
                )}{" "}
                • Área total: <strong>{formatArea(prop.totalArea)}</strong>
              </div>
              <div style={{ fontSize: "0.84rem", color: "var(--af-text-muted)", marginTop: 4 }}>
                {prop.address?.locality ? `${prop.address.locality} — ` : ""}
                {prop.address?.municipalityName ?? "Município não informado"}
                {prop.productionDescription ? ` • ${prop.productionDescription}` : ""}
              </div>
            </div>

            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
              <CertificationStatusBadge
                status={prop.currentCertification?.status}
                daysToExpire={prop.currentCertification?.daysToExpire}
              />
              <button
                type="button"
                className="af-btn af-btn--secondary af-btn--sm"
                onClick={() =>
                  downloadFile(
                    `/reports/field-notebook?propertyId=${prop.id}&format=pdf`,
                    `caderno-de-campo-${prop.id}.pdf`,
                  )
                }
              >
                Baixar Caderno de Campo (PDF)
              </button>
              <Link
                to={`/registros/novo?propertyId=${prop.id}&farmerId=${prop.farmerId ?? ""}`}
                className="af-btn af-btn--primary af-btn--sm"
              >
                + Registrar manejo
              </Link>
            </div>
          </div>
        </div>
      </div>

      <div className="af-tabs">
        <button
          type="button"
          className={`af-tab ${tab === "plots" ? "af-tab--active" : ""}`}
          onClick={() => setTab("plots")}
        >
          Áreas / Talhões ({(prop.plots ?? []).length})
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "workers" ? "af-tab--active" : ""}`}
          onClick={() => setTab("workers")}
        >
          Trabalhadores ({(prop.workers ?? []).length})
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "certifications" ? "af-tab--active" : ""}`}
          onClick={() => setTab("certifications")}
        >
          Certificações ({(prop.certifications ?? []).length})
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "visits" ? "af-tab--active" : ""}`}
          onClick={() => setTab("visits")}
        >
          Ciclo de Visitas SPG
        </button>
      </div>

      {tab === "plots" && (
        <div className="af-grid-2">
          <div className="af-card">
            <div className="af-card-header">
              <div>
                <h2 className="af-card-title">Talhões e Canteiros</h2>
                <p className="af-card-subtitle">Subdivisões produtivas da propriedade</p>
              </div>
            </div>
            {(prop.plots ?? []).length === 0 ? (
              <div className="af-empty-state">Nenhuma área cadastrada ainda.</div>
            ) : (
              <div className="af-table-wrap">
                <table className="af-table">
                  <thead>
                    <tr>
                      <th>Nome da área</th>
                      <th>Tamanho</th>
                      <th>Situação orgânica</th>
                      <th style={{ textAlign: "right" }}>Ação</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(prop.plots ?? []).map((pl) => (
                      <tr key={pl.id}>
                        <td style={{ fontWeight: 600 }}>{pl.name}</td>
                        <td>{formatArea(pl.area)}</td>
                        <td>
                          <span className="af-badge af-badge--valid">
                            {pl.status ? PLOT_STATUS_LABELS[pl.status] : "—"}
                          </span>
                        </td>
                        <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                          {pl.id && (
                            <>
                              <button
                                type="button"
                                className="af-btn af-btn--ghost af-btn--sm"
                                onClick={() => startEditingPlot(pl)}
                              >
                                Editar
                              </button>
                              <button
                                type="button"
                                className="af-btn af-btn--ghost af-btn--sm"
                                onClick={() => deletePlotMutation.mutate(pl.id!)}
                              >
                                Excluir
                              </button>
                            </>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 4 }}>
              {editingPlot ? `Editar área: ${editingPlot.name}` : "Adicionar Área / Talhão"}
            </h2>
            <p className="af-card-subtitle" style={{ marginBottom: 12 }}>
              {freeM2 === null
                ? "A propriedade não tem tamanho cadastrado, então as áreas não têm limite."
                : `Livre na propriedade: ${formatArea({
                    value: fromSquareMeters(freeM2, totalUnit, tarefaM2),
                    unit: totalUnit,
                  })} de ${formatArea(prop.totalArea)}.`}
            </p>
            <form
              className="af-form-grid"
              onSubmit={(e) => {
                e.preventDefault();
                if (plotName.trim() && !plotTooBig) savePlotMutation.mutate();
              }}
            >
              <div className="af-field">
                <label className="af-label" htmlFor="plot-name">
                  Identificação da área *
                </label>
                <input
                  id="plot-name"
                  className="af-input"
                  value={plotName}
                  onChange={(e) => setPlotName(e.target.value)}
                  required
                  placeholder="Ex.: Talhão 1 — Horta Mandala"
                />
              </div>
              <div className="af-form-row">
                <div className="af-field">
                  <label className="af-label" htmlFor="plot-area">
                    Tamanho
                  </label>
                  <input
                    id="plot-area"
                    aria-invalid={plotTooBig}
                    aria-describedby={plotTooBig ? "plot-area-error" : undefined}
                    type="number"
                    step="0.01"
                    min="0.01"
                    className="af-input"
                    value={plotArea}
                    onChange={(e) => setPlotArea(e.target.value)}
                    placeholder="Ex.: 1.5"
                  />
                </div>
                <div className="af-field">
                  <label className="af-label" htmlFor="plot-unit">
                    Unidade
                  </label>
                  <select
                    id="plot-unit"
                    className="af-select"
                    value={plotUnit}
                    onChange={(e) => setPlotUnit(e.target.value as typeof plotUnit)}
                  >
                    {Object.entries(AREA_UNIT_LABELS).map(([k, v]) => (
                      <option key={k} value={k}>
                        {v}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              {plotTooBig && freeM2 !== null && (
                <div id="plot-area-error" className="af-alert af-alert--danger" role="alert">
                  Não cabe na propriedade: só restam{" "}
                  {formatArea({
                    value: fromSquareMeters(freeM2, plotUnit, tarefaM2),
                    unit: plotUnit,
                  })}
                  . Diminua outra área antes, se precisar.
                </div>
              )}
              {plotError && !plotTooBig && (
                <div className="af-alert af-alert--danger" role="alert">
                  {plotError}
                </div>
              )}
              <div className="af-field">
                <label className="af-label" htmlFor="plot-status">
                  Situação orgânica
                </label>
                <select
                  id="plot-status"
                  className="af-select"
                  value={plotStatus}
                  onChange={(e) => setPlotStatus(e.target.value as typeof plotStatus)}
                >
                  {Object.entries(PLOT_STATUS_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <button
                  type="submit"
                  className="af-btn af-btn--primary af-btn--sm"
                  disabled={savePlotMutation.isPending || plotTooBig}
                >
                  {editingPlot ? "Salvar alterações" : "+ Salvar talhão"}
                </button>
                {editingPlot && (
                  <button
                    type="button"
                    className="af-btn af-btn--ghost af-btn--sm"
                    onClick={resetPlotForm}
                  >
                    Cancelar
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      )}

      {tab === "workers" && (
        <div className="af-grid-2">
          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Mão de obra familiar e colaboradores da propriedade
            </h2>
            {(prop.workers ?? []).length === 0 ? (
              <div className="af-empty-state">Nenhum trabalhador cadastrado.</div>
            ) : (
              <div className="af-table-wrap">
                <table className="af-table">
                  <thead>
                    <tr>
                      <th>Nome</th>
                      <th>Vínculo</th>
                      <th style={{ textAlign: "right" }}>Ação</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(prop.workers ?? []).map((w) => (
                      <tr key={w.id}>
                        <td style={{ fontWeight: 600 }}>{w.name}</td>
                        <td>{w.relationship ? WORKER_RELATIONSHIP_LABELS[w.relationship] : "—"}</td>
                        <td style={{ textAlign: "right" }}>
                          {w.id && (
                            <button
                              type="button"
                              className="af-btn af-btn--ghost af-btn--sm"
                              onClick={() => deleteWorkerMutation.mutate(w.id!)}
                            >
                              Remover
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Cadastrar Trabalhador
            </h2>
            <form
              className="af-form-grid"
              onSubmit={(e) => {
                e.preventDefault();
                if (workerName.trim()) createWorkerMutation.mutate();
              }}
            >
              <div className="af-field">
                <label htmlFor="prop-nome-completo" className="af-label">
                  Nome completo *
                </label>
                <input
                  id="prop-nome-completo"
                  className="af-input"
                  value={workerName}
                  onChange={(e) => setWorkerName(e.target.value)}
                  required
                  placeholder="Ex.: José Carlos da Silva"
                />
              </div>
              <div className="af-field">
                <label htmlFor="prop-vinculo-com-a-unidade" className="af-label">
                  Vínculo com a unidade familiar
                </label>
                <select
                  id="prop-vinculo-com-a-unidade"
                  className="af-select"
                  value={workerRelation}
                  onChange={(e) => setWorkerRelation(e.target.value as typeof workerRelation)}
                >
                  {Object.entries(WORKER_RELATIONSHIP_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <button
                type="submit"
                className="af-btn af-btn--primary af-btn--sm"
                disabled={createWorkerMutation.isPending}
              >
                + Adicionar trabalhador
              </button>
            </form>
          </div>
        </div>
      )}

      {tab === "certifications" && (
        <div className="af-card">
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">Histórico de Certificações</h2>
              <p className="af-card-subtitle">Certificados de conformidade orgânica emitidos</p>
            </div>
            <Link
              to={`/certificacoes?propertyId=${prop.id}`}
              className="af-btn af-btn--primary af-btn--sm"
            >
              Emitir / Gerenciar Certificação
            </Link>
          </div>

          {(prop.certifications ?? []).length === 0 ? (
            <div className="af-empty-state">
              Nenhuma certificação emitida para esta propriedade.
            </div>
          ) : (
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Número</th>
                    <th>Emissão</th>
                    <th>Validade</th>
                    <th>Escopo</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {(prop.certifications ?? []).map((c) => (
                    <tr key={c.id}>
                      <td style={{ fontWeight: 700 }}>{c.number ?? "S/N"}</td>
                      <td>{formatDatePtBr(c.issuedOn)}</td>
                      <td>{formatDatePtBr(c.expiresOn)}</td>
                      <td>{c.scope ?? "Produção primária vegetal"}</td>
                      <td>
                        <CertificationStatusBadge status={c.status} daysToExpire={c.daysToExpire} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === "visits" && (
        <div className="af-card">
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">Situação no Ciclo de Visitas SPG</h2>
              <p className="af-card-subtitle">
                Acompanhamento de visitas de pares e de verificação da propriedade (RN27)
              </p>
            </div>
            <Link
              to={`/visitas?propertyId=${prop.id}`}
              className="af-btn af-btn--primary af-btn--sm"
            >
              Agendar visita
            </Link>
          </div>

          {visitStatusQuery.isLoading ? (
            <div className="af-empty-state">Consultando ciclo de visitas...</div>
          ) : !visitStatusQuery.data ? (
            <div className="af-empty-state">Sem dados de ciclo de visitas.</div>
          ) : (
            <div className="af-kpi-grid">
              <div className="af-kpi-card">
                <span className="af-kpi-label">Situação do ciclo</span>
                <span className="af-kpi-value" style={{ fontSize: "1.3rem" }}>
                  {visitStatusQuery.data.state
                    ? VISIT_CYCLE_STATE_LABELS[visitStatusQuery.data.state]
                    : "—"}
                </span>
                <span className="af-kpi-hint">
                  Janela aberta: {visitStatusQuery.data.windowOpen ? "Sim" : "Não"}
                </span>
              </div>
              <div className="af-kpi-card">
                <span className="af-kpi-label">Visitas de Pares</span>
                <span className="af-kpi-value">
                  {visitStatusQuery.data.peer?.done ?? 0} /{" "}
                  {visitStatusQuery.data.peer?.required ?? 0}
                </span>
                <span className="af-kpi-hint">
                  Agendadas: {visitStatusQuery.data.peer?.planned ?? 0}
                </span>
              </div>
              <div className="af-kpi-card">
                <span className="af-kpi-label">Visitas de Verificação</span>
                <span className="af-kpi-value">
                  {visitStatusQuery.data.verification?.done ?? 0} /{" "}
                  {visitStatusQuery.data.verification?.required ?? 0}
                </span>
                <span className="af-kpi-hint">
                  Agendadas: {visitStatusQuery.data.verification?.planned ?? 0}
                </span>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
