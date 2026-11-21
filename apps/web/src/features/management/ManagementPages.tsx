// Telas de Gestão da Associação: Comunicados (RN23), Catálogo e Itens sem catálogo (RN22), Relatórios (RF17–RF18) e Configurações / Coordenadores (RN24).
import { useState } from "react";
import { useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ANNOUNCEMENT_TYPE_LABELS,
  FARM_INPUT_TYPE_LABELS,
  formatDatePtBr,
  formatDateTimePtBr,
  onlyDigits,
  USER_STATUS_LABELS,
} from "@agrofacil/domain";
import { api, ApiProblemError, downloadFile, normalizePhoneE164, unwrap } from "../../api/client";
import { fetchAllPages } from "../../api/pagination";

export function AnnouncementsPage() {
  const queryClient = useQueryClient();
  const [type, setType] = useState<"NOTICE" | "TIP">("NOTICE");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [groupId, setGroupId] = useState("");

  const listQuery = useQuery({
    queryKey: ["announcements"],
    queryFn: () =>
      unwrap(
        api.GET("/announcements", {
          params: { query: { pageable: { page: 0, size: 25, sort: ["publishedAt,desc"] } } },
        }),
      ),
  });

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: () => unwrap(api.GET("/groups")),
  });

  const publishMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/announcements", {
          body: {
            type,
            title: title.trim(),
            body: body.trim(),
            groupId: groupId || undefined,
          },
        }),
      ),
    onSuccess: () => {
      setTitle("");
      setBody("");
      setGroupId("");
      queryClient.invalidateQueries({ queryKey: ["announcements"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) =>
      unwrap(api.DELETE("/announcements/{id}", { params: { path: { id } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["announcements"] }),
  });

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Comunicados e Orientações Técnicas</h1>
          <p className="af-page-subtitle">
            Envie avisos da associação ou dicas agroecológicas para os celulares dos agricultores
            (RN23).
          </p>
        </div>
      </div>

      <div className="af-grid-2">
        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 12 }}>
            Publicar Novo Comunicado
          </h2>
          <form
            className="af-form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              if (title.trim() && body.trim()) publishMutation.mutate();
            }}
          >
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Tipo *</label>
                <select
                  className="af-select"
                  value={type}
                  onChange={(e) => setType(e.target.value as "NOTICE" | "TIP")}
                >
                  {Object.entries(ANNOUNCEMENT_TYPE_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <div className="af-field">
                <label className="af-label">Público-alvo</label>
                <select
                  className="af-select"
                  value={groupId}
                  onChange={(e) => setGroupId(e.target.value)}
                >
                  <option value="">Todos os agricultores da associação</option>
                  {(groupsQuery.data ?? []).map((g) => (
                    <option key={g.id} value={g.id}>
                      Apenas grupo: {g.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="af-field">
              <label className="af-label">Título *</label>
              <input
                className="af-input"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                placeholder="Ex.: Reunião Ordinária do SPG neste sábado"
              />
            </div>

            <div className="af-field">
              <label className="af-label">Mensagem *</label>
              <textarea
                className="af-textarea"
                value={body}
                onChange={(e) => setBody(e.target.value)}
                required
                placeholder="Escreva o conteúdo do comunicado..."
              />
            </div>

            <button
              type="submit"
              className="af-btn af-btn--primary af-btn--sm"
              disabled={publishMutation.isPending}
            >
              {publishMutation.isPending ? "Publicando..." : "Publicar e notificar agricultores"}
            </button>
          </form>
        </div>

        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 12 }}>
            Comunicados Publicados
          </h2>
          {listQuery.isLoading ? (
            <div className="af-empty-state">Carregando comunicados...</div>
          ) : (listQuery.data?.content ?? []).length === 0 ? (
            <div className="af-empty-state">Nenhum comunicado publicado.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {(listQuery.data?.content ?? []).map((a) => (
                <div
                  key={a.id}
                  style={{
                    padding: 14,
                    borderRadius: "var(--af-radius-md)",
                    border: "1px solid var(--af-border)",
                    background: "var(--af-bg-surface-soft)",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      marginBottom: 6,
                    }}
                  >
                    <span className="af-badge af-badge--valid">
                      {a.type ? ANNOUNCEMENT_TYPE_LABELS[a.type] : "Aviso"}
                    </span>
                    <span style={{ fontSize: "0.75rem", color: "var(--af-text-muted)" }}>
                      {formatDateTimePtBr(a.publishedAt)}
                    </span>
                  </div>
                  <div style={{ fontWeight: 700, marginBottom: 4 }}>{a.title}</div>
                  <div
                    style={{
                      fontSize: "0.86rem",
                      color: "var(--af-text-secondary)",
                      whiteSpace: "pre-wrap",
                    }}
                  >
                    {a.body}
                  </div>
                  {a.id && (
                    <div style={{ marginTop: 8, textAlign: "right" }}>
                      <button
                        type="button"
                        className="af-btn af-btn--ghost af-btn--sm"
                        onClick={() => deleteMutation.mutate(a.id!)}
                      >
                        Excluir
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export function CatalogPage() {
  const queryClient = useQueryClient();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<"crops" | "inputs" | "unmapped" | "units">(() =>
    searchParams.get("aba") === "sem-catalogo" ? "unmapped" : "crops",
  );

  // Nova cultura
  const [cropName, setCropName] = useState("");
  const [cropCategoryId, setCropCategoryId] = useState("");

  // Novo insumo
  const [inputName, setInputName] = useState("");
  const [inputType, setInputType] = useState<
    | "FERTILIZER"
    | "SOIL_CORRECTIVE"
    | "BIOFERTILIZER"
    | "PEST_DISEASE_CONTROL"
    | "INOCULANT"
    | "OTHER"
  >("BIOFERTILIZER");
  const [needsAuthorization, setNeedsAuthorization] = useState(false);
  const [homeMade, setHomeMade] = useState(true);

  // Resolver item sem catálogo
  const [resolveTargetId, setResolveTargetId] = useState<Record<string, string>>({});

  const cropsQuery = useQuery({
    queryKey: ["catalog", "crops"],
    queryFn: () =>
      unwrap(api.GET("/catalog/crops", { params: { query: { includeInactive: true } } })),
  });

  const categoriesQuery = useQuery({
    queryKey: ["catalog", "crop-categories"],
    queryFn: () => unwrap(api.GET("/catalog/crop-categories")),
  });

  const inputsQuery = useQuery({
    queryKey: ["catalog", "farm-inputs"],
    queryFn: () =>
      unwrap(api.GET("/catalog/farm-inputs", { params: { query: { includeInactive: true } } })),
  });

  const unmappedQuery = useQuery({
    queryKey: ["catalog", "unmapped"],
    queryFn: () => unwrap(api.GET("/catalog/unmapped")),
  });

  const unitsQuery = useQuery({
    queryKey: ["catalog", "units"],
    queryFn: () => unwrap(api.GET("/catalog/units")),
  });

  const createCropMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/catalog/crops", {
          body: {
            name: cropName.trim(),
            categoryId: cropCategoryId || undefined,
            active: true,
          },
        }),
      ),
    onSuccess: () => {
      setCropName("");
      queryClient.invalidateQueries({ queryKey: ["catalog", "crops"] });
    },
  });

  const createInputMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/catalog/farm-inputs", {
          body: {
            name: inputName.trim(),
            inputType,
            needsAuthorization,
            homeMade,
            active: true,
          },
        }),
      ),
    onSuccess: () => {
      setInputName("");
      queryClient.invalidateQueries({ queryKey: ["catalog", "farm-inputs"] });
    },
  });

  const resolveUnmappedMutation = useMutation({
    mutationFn: (payload: { kind: "CROP" | "INPUT"; text: string; targetId: string }) =>
      unwrap(api.POST("/catalog/unmapped/resolve", { body: payload })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["catalog", "unmapped"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Catálogo de Culturas, Insumos e Unidades</h1>
          <p className="af-page-subtitle">
            Padronize itens sincronizados com os celulares e associe nomes digitados manualmente
            pelos agricultores (RN22).
          </p>
        </div>
      </div>

      <div className="af-tabs">
        <button
          type="button"
          className={`af-tab ${tab === "crops" ? "af-tab--active" : ""}`}
          onClick={() => setTab("crops")}
        >
          Culturas ({(cropsQuery.data ?? []).length})
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "inputs" ? "af-tab--active" : ""}`}
          onClick={() => setTab("inputs")}
        >
          Insumos ({(inputsQuery.data ?? []).length})
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "unmapped" ? "af-tab--active" : ""}`}
          onClick={() => setTab("unmapped")}
        >
          Itens sem catálogo ({(unmappedQuery.data ?? []).length})
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "units" ? "af-tab--active" : ""}`}
          onClick={() => setTab("units")}
        >
          Unidades ({(unitsQuery.data ?? []).length})
        </button>
      </div>

      {tab === "crops" && (
        <div className="af-grid-2">
          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Culturas Agrícolas
            </h2>
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Cultura</th>
                    <th>Categoria</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {(cropsQuery.data ?? []).map((c) => (
                    <tr key={c.id}>
                      <td style={{ fontWeight: 700 }}>{c.name}</td>
                      <td>{c.category?.name ?? "Geral"}</td>
                      <td>
                        <span
                          className={`af-badge ${
                            c.active ? "af-badge--valid" : "af-badge--neutral"
                          }`}
                        >
                          {c.active ? "Ativa" : "Inativa"}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Nova Cultura
            </h2>
            <form
              className="af-form-grid"
              onSubmit={(e) => {
                e.preventDefault();
                if (cropName.trim()) createCropMutation.mutate();
              }}
            >
              <div className="af-field">
                <label className="af-label">Nome da cultura *</label>
                <input
                  className="af-input"
                  value={cropName}
                  onChange={(e) => setCropName(e.target.value)}
                  required
                  placeholder="Ex.: Maracujá da Caatinga"
                />
              </div>
              <div className="af-field">
                <label className="af-label">Categoria</label>
                <select
                  className="af-select"
                  value={cropCategoryId}
                  onChange={(e) => setCropCategoryId(e.target.value)}
                >
                  <option value="">Selecione</option>
                  {(categoriesQuery.data ?? []).map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {cat.name}
                    </option>
                  ))}
                </select>
              </div>
              <button type="submit" className="af-btn af-btn--primary af-btn--sm">
                + Cadastrar cultura
              </button>
            </form>
          </div>
        </div>
      )}

      {tab === "inputs" && (
        <div className="af-grid-2">
          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Insumos Permitidos
            </h2>
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Insumo</th>
                    <th>Tipo</th>
                    <th>Autorização prévia</th>
                  </tr>
                </thead>
                <tbody>
                  {(inputsQuery.data ?? []).map((inp) => (
                    <tr key={inp.id}>
                      <td style={{ fontWeight: 700 }}>{inp.name}</td>
                      <td>{inp.inputType ? FARM_INPUT_TYPE_LABELS[inp.inputType] : "—"}</td>
                      <td>
                        {inp.needsAuthorization ? (
                          <span className="af-badge af-badge--warning">Exige autorização</span>
                        ) : (
                          <span className="af-badge af-badge--valid">Uso livre</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="af-card">
            <h2 className="af-card-title" style={{ marginBottom: 12 }}>
              Novo Insumo
            </h2>
            <form
              className="af-form-grid"
              onSubmit={(e) => {
                e.preventDefault();
                if (inputName.trim()) createInputMutation.mutate();
              }}
            >
              <div className="af-field">
                <label className="af-label">Nome do insumo *</label>
                <input
                  className="af-input"
                  value={inputName}
                  onChange={(e) => setInputName(e.target.value)}
                  required
                  placeholder="Ex.: Calda Bordalesa"
                />
              </div>
              <div className="af-field">
                <label className="af-label">Classificação *</label>
                <select
                  className="af-select"
                  value={inputType}
                  onChange={(e) => setInputType(e.target.value as typeof inputType)}
                >
                  {Object.entries(FARM_INPUT_TYPE_LABELS).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
                <input
                  type="checkbox"
                  checked={homeMade}
                  onChange={(e) => setHomeMade(e.target.checked)}
                />
                <span>Preparo caseiro / na própria propriedade</span>
              </label>
              <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
                <input
                  type="checkbox"
                  checked={needsAuthorization}
                  onChange={(e) => setNeedsAuthorization(e.target.checked)}
                />
                <span>Exige autorização do OAC / OPAC conforme legislação orgânica</span>
              </label>
              <button type="submit" className="af-btn af-btn--primary af-btn--sm">
                + Cadastrar insumo
              </button>
            </form>
          </div>
        </div>
      )}

      {tab === "unmapped" && (
        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 8 }}>
            Itens Digitados Manualmente pelos Agricultores (RN22)
          </h2>
          <p className="af-card-subtitle" style={{ marginBottom: 16 }}>
            Vincule os textos livres a uma cultura ou insumo oficial para atualizar os registros em
            lote.
          </p>
          {(unmappedQuery.data ?? []).length === 0 ? (
            <div className="af-empty-state">
              Nenhum texto livre pendente de vinculação ao catálogo!
            </div>
          ) : (
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Texto digitado</th>
                    <th>Tipo</th>
                    <th>Ocorrências</th>
                    <th>Último uso</th>
                    <th>Vincular ao item oficial</th>
                  </tr>
                </thead>
                <tbody>
                  {(unmappedQuery.data ?? []).map((u, idx) => {
                    const key = `${u.kind}:${u.text}:${idx}`;
                    const targetId = resolveTargetId[key] ?? "";
                    return (
                      <tr key={key}>
                        <td style={{ fontWeight: 700 }}>"{u.text}"</td>
                        <td>{u.kind === "CROP" ? "Cultura" : "Insumo"}</td>
                        <td>{u.occurrences ?? 1}</td>
                        <td>{formatDatePtBr(u.lastUsedOn)}</td>
                        <td>
                          <div style={{ display: "flex", gap: 8 }}>
                            <select
                              className="af-select"
                              value={targetId}
                              onChange={(e) =>
                                setResolveTargetId((prev) => ({ ...prev, [key]: e.target.value }))
                              }
                            >
                              <option value="">Escolha o item oficial...</option>
                              {u.kind === "CROP"
                                ? (cropsQuery.data ?? []).map((c) => (
                                    <option key={c.id} value={c.id}>
                                      {c.name}
                                    </option>
                                  ))
                                : (inputsQuery.data ?? []).map((inp) => (
                                    <option key={inp.id} value={inp.id}>
                                      {inp.name}
                                    </option>
                                  ))}
                            </select>
                            <button
                              type="button"
                              className="af-btn af-btn--secondary af-btn--sm"
                              disabled={!targetId || !u.text || !u.kind}
                              onClick={() =>
                                u.kind &&
                                u.text &&
                                targetId &&
                                resolveUnmappedMutation.mutate({
                                  kind: u.kind,
                                  text: u.text,
                                  targetId,
                                })
                              }
                            >
                              Vincular
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === "units" && (
        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 12 }}>
            Unidades de Medida
          </h2>
          <div className="af-table-wrap">
            <table className="af-table">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Singular / Plural</th>
                  <th>Grandeza</th>
                  <th>Fator p/ base</th>
                </tr>
              </thead>
              <tbody>
                {(unitsQuery.data ?? []).map((u) => (
                  <tr key={u.code}>
                    <td>
                      <code>{u.code}</code>
                    </td>
                    <td style={{ fontWeight: 600 }}>
                      {u.nameSingular} / {u.namePlural}
                    </td>
                    <td>{u.kind}</td>
                    <td>{u.toBaseFactor ?? 1}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

export function ReportsPage() {
  const [propertyId, setPropertyId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  const propertiesQuery = useQuery({
    queryKey: ["properties", "all-select"],
    queryFn: () =>
      fetchAllPages((pageable) =>
        api.GET("/properties", {
          params: { query: { filter: {}, pageable: { ...pageable, sort: ["name,asc"] } } },
        }),
      ),
  });

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Relatórios e Exportações (RF17–RF18)</h1>
          <p className="af-page-subtitle">
            Exporte o Caderno de Campo em PDF ou planilhas CSV compatíveis com Excel (UTF-8 com BOM
            e separador ponto e vírgula).
          </p>
        </div>
      </div>

      <div className="af-grid-2">
        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 8 }}>
            Caderno de Campo por Propriedade
          </h2>
          <p className="af-card-subtitle" style={{ marginBottom: 14 }}>
            Documento consolidado com talhões, certificação atual e todos os manejos do período.
          </p>
          <div className="af-form-grid">
            <div className="af-field">
              <label className="af-label">Propriedade *</label>
              <select
                className="af-select"
                value={propertyId}
                onChange={(e) => setPropertyId(e.target.value)}
              >
                <option value="">Selecione a propriedade</option>
                {(propertiesQuery.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.farmerName})
                  </option>
                ))}
              </select>
            </div>
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Período inicial (opcional)</label>
                <input
                  type="date"
                  className="af-input"
                  value={from}
                  onChange={(e) => setFrom(e.target.value)}
                />
              </div>
              <div className="af-field">
                <label className="af-label">Período final (opcional)</label>
                <input
                  type="date"
                  className="af-input"
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                />
              </div>
            </div>
            <div style={{ display: "flex", gap: 10 }}>
              <button
                type="button"
                className="af-btn af-btn--primary af-btn--sm"
                disabled={!propertyId}
                onClick={() => {
                  const qs = new URLSearchParams({ propertyId, format: "pdf" });
                  if (from) qs.set("from", from);
                  if (to) qs.set("to", to);
                  downloadFile(`/reports/field-notebook?${qs}`, `caderno-campo-${propertyId}.pdf`);
                }}
              >
                Baixar PDF
              </button>
              <button
                type="button"
                className="af-btn af-btn--outline af-btn--sm"
                disabled={!propertyId}
                onClick={() => {
                  const qs = new URLSearchParams({ propertyId, format: "csv" });
                  if (from) qs.set("from", from);
                  if (to) qs.set("to", to);
                  downloadFile(`/reports/field-notebook?${qs}`, `caderno-campo-${propertyId}.csv`);
                }}
              >
                Baixar CSV
              </button>
            </div>
          </div>
        </div>

        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 8 }}>
            Relatórios Gerais da Associação
          </h2>
          <p className="af-card-subtitle" style={{ marginBottom: 14 }}>
            Quadro social de agricultores, situação das certificações e exportação bruta de manejos.
          </p>

          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: 12,
                border: "1px solid var(--af-border)",
                borderRadius: "var(--af-radius-md)",
              }}
            >
              <div>
                <div style={{ fontWeight: 700 }}>Quadro de Associados e Atividade</div>
                <div style={{ fontSize: "0.8rem", color: "var(--af-text-secondary)" }}>
                  Agricultores, grupos, propriedades e manejos nos últimos 90 dias
                </div>
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                <button
                  type="button"
                  className="af-btn af-btn--secondary af-btn--sm"
                  onClick={() => downloadFile("/reports/members?format=pdf", "associados.pdf")}
                >
                  PDF
                </button>
                <button
                  type="button"
                  className="af-btn af-btn--outline af-btn--sm"
                  onClick={() => downloadFile("/reports/members?format=csv", "associados.csv")}
                >
                  CSV
                </button>
              </div>
            </div>

            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: 12,
                border: "1px solid var(--af-border)",
                borderRadius: "var(--af-radius-md)",
              }}
            >
              <div>
                <div style={{ fontWeight: 700 }}>Relatório de Certificações e Vencimentos</div>
                <div style={{ fontSize: "0.8rem", color: "var(--af-text-secondary)" }}>
                  Certificações vigentes, datas de validade e escopos
                </div>
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                <button
                  type="button"
                  className="af-btn af-btn--secondary af-btn--sm"
                  onClick={() =>
                    downloadFile("/reports/certifications?format=pdf", "certificacoes.pdf")
                  }
                >
                  PDF
                </button>
                <button
                  type="button"
                  className="af-btn af-btn--outline af-btn--sm"
                  onClick={() =>
                    downloadFile("/reports/certifications?format=csv", "certificacoes.csv")
                  }
                >
                  CSV
                </button>
              </div>
            </div>

            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: 12,
                border: "1px solid var(--af-border)",
                borderRadius: "var(--af-radius-md)",
              }}
            >
              <div>
                <div style={{ fontWeight: 700 }}>Planilha Completa de Registros de Manejo</div>
                <div style={{ fontSize: "0.8rem", color: "var(--af-text-secondary)" }}>
                  Exportação bruta em CSV para análise externa ou integração
                </div>
              </div>
              <button
                type="button"
                className="af-btn af-btn--outline af-btn--sm"
                onClick={() => downloadFile("/reports/records?format=csv", "registros-manejo.csv")}
              >
                Baixar CSV
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export function SettingsPage() {
  const queryClient = useQueryClient();
  const [inviteName, setInviteName] = useState("");
  const [inviteEmail, setInviteEmail] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const assocQuery = useQuery({
    queryKey: ["association"],
    queryFn: () => unwrap(api.GET("/association")),
  });

  const coordsQuery = useQuery({
    queryKey: ["coordinators"],
    queryFn: () => unwrap(api.GET("/coordinators")),
  });

  const [nameDraft, setName] = useState<string | null>(null);
  const [opacNameDraft, setOpacName] = useState<string | null>(null);
  const [cnpjDraft, setCnpj] = useState<string | null>(null);
  const [whatsappDraft, setWhatsapp] = useState<string | null>(null);
  const [tarefaM2Draft, setTarefaM2] = useState<string | null>(null);
  const [peerVisitsPerYearDraft, setPeerVisitsPerYear] = useState<string | null>(null);
  const [visitWindowDaysDraft, setVisitWindowDays] = useState<string | null>(null);

  const name = nameDraft ?? assocQuery.data?.name ?? "";
  const opacName = opacNameDraft ?? assocQuery.data?.opacName ?? "";
  const cnpj = cnpjDraft ?? assocQuery.data?.cnpj ?? "";
  const whatsapp = whatsappDraft ?? assocQuery.data?.whatsapp ?? "";
  const tarefaM2 = tarefaM2Draft ?? String(assocQuery.data?.tarefaM2 ?? 4356);
  const peerVisitsPerYear =
    peerVisitsPerYearDraft ?? String(assocQuery.data?.peerVisitsPerYear ?? 2);
  const visitWindowDays = visitWindowDaysDraft ?? String(assocQuery.data?.visitWindowDays ?? 90);

  const saveAssocMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.PATCH("/association", {
          body: {
            name: name.trim(),
            opacName: opacName.trim() || undefined,
            cnpj: onlyDigits(cnpj) || undefined,
            whatsapp: normalizePhoneE164(whatsapp),
            tarefaM2: Number(tarefaM2) || 4356,
            alertDays: assocQuery.data?.alertDays ?? [60, 30, 15],
            peerVisitsPerYear: Number(peerVisitsPerYear) || 2,
            visitWindowDays: Number(visitWindowDays) || 90,
            version: assocQuery.data?.version ?? 0,
          },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["association"] });
      setFeedback("Configurações da associação salvas com sucesso!");
    },
    onError: (err) => {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao salvar associação.");
    },
  });

  const inviteMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/coordinators/invitations", {
          body: { name: inviteName.trim(), email: inviteEmail.trim() },
        }),
      ),
    onSuccess: () => {
      setInviteName("");
      setInviteEmail("");
      queryClient.invalidateQueries({ queryKey: ["coordinators"] });
      setFeedback("Convite enviado por e-mail para o novo coordenador!");
    },
    onError: (err) => {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao enviar convite.");
    },
  });

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Configurações e Coordenadores</h1>
          <p className="af-page-subtitle">
            Parâmetros regionais da associação/OPAC (conversão da tarefa em m², alertas e regras de
            visita) e equipe de coordenação (RN24).
          </p>
        </div>
      </div>

      {feedback && (
        <div className="af-alert af-alert--success" style={{ marginBottom: 16 }}>
          {feedback}
        </div>
      )}
      {error && (
        <div className="af-alert af-alert--danger" style={{ marginBottom: 16 }}>
          {error}
        </div>
      )}

      <div className="af-grid-2">
        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 12 }}>
            Dados e Parâmetros da Associação / OPAC
          </h2>
          <form
            className="af-form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              saveAssocMutation.mutate();
            }}
          >
            <div className="af-field">
              <label className="af-label">Nome da Associação *</label>
              <input
                className="af-input"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
              />
            </div>
            <div className="af-field">
              <label className="af-label">
                Nome do OPAC (Organismo de Avaliação da Conformidade)
              </label>
              <input
                className="af-input"
                value={opacName}
                onChange={(e) => setOpacName(e.target.value)}
              />
            </div>
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">CNPJ</label>
                <input
                  className="af-input"
                  value={cnpj}
                  onChange={(e) => setCnpj(e.target.value)}
                />
              </div>
              <div className="af-field">
                <label className="af-label">WhatsApp de suporte</label>
                <input
                  className="af-input"
                  value={whatsapp}
                  onChange={(e) => setWhatsapp(e.target.value)}
                />
              </div>
            </div>
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">1 Tarefa equivale a quantos m²? (RN14)</label>
                <input
                  type="number"
                  className="af-input"
                  value={tarefaM2}
                  onChange={(e) => setTarefaM2(e.target.value)}
                  required
                />
              </div>
              <div className="af-field">
                <label className="af-label">Visitas de pares por ano (RN29)</label>
                <input
                  type="number"
                  className="af-input"
                  value={peerVisitsPerYear}
                  onChange={(e) => setPeerVisitsPerYear(e.target.value)}
                  required
                />
              </div>
              <div className="af-field">
                <label className="af-label">Janela de visitas (dias)</label>
                <input
                  type="number"
                  className="af-input"
                  value={visitWindowDays}
                  onChange={(e) => setVisitWindowDays(e.target.value)}
                  required
                />
              </div>
            </div>
            <button
              type="submit"
              className="af-btn af-btn--primary af-btn--sm"
              disabled={saveAssocMutation.isPending}
            >
              Salvar configurações
            </button>
          </form>
        </div>

        <div className="af-card">
          <h2 className="af-card-title" style={{ marginBottom: 12 }}>
            Equipe de Coordenação (RN24)
          </h2>
          <div className="af-table-wrap" style={{ marginBottom: 18 }}>
            <table className="af-table">
              <thead>
                <tr>
                  <th>Nome / E-mail</th>
                  <th>Status</th>
                  <th>Último acesso</th>
                </tr>
              </thead>
              <tbody>
                {(coordsQuery.data ?? []).map((c) => (
                  <tr key={c.id}>
                    <td>
                      <div style={{ fontWeight: 700 }}>{c.name}</div>
                      <div style={{ fontSize: "0.78rem", color: "var(--af-text-secondary)" }}>
                        {c.email}
                      </div>
                    </td>
                    <td>
                      {c.pendingInvite ? (
                        <span className="af-badge af-badge--warning">Convite pendente</span>
                      ) : (
                        <span
                          className={`af-badge ${
                            c.status === "ACTIVE" ? "af-badge--valid" : "af-badge--neutral"
                          }`}
                        >
                          {c.status ? USER_STATUS_LABELS[c.status] : "—"}
                        </span>
                      )}
                    </td>
                    <td>{formatDateTimePtBr(c.lastLoginAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h3 className="af-card-title" style={{ fontSize: "0.95rem", marginBottom: 10 }}>
            Convidar Novo Coordenador
          </h3>
          <form
            className="af-form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              if (inviteName.trim() && inviteEmail.trim()) inviteMutation.mutate();
            }}
          >
            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">Nome *</label>
                <input
                  className="af-input"
                  value={inviteName}
                  onChange={(e) => setInviteName(e.target.value)}
                  required
                />
              </div>
              <div className="af-field">
                <label className="af-label">E-mail *</label>
                <input
                  type="email"
                  className="af-input"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  required
                />
              </div>
            </div>
            <button
              type="submit"
              className="af-btn af-btn--secondary af-btn--sm"
              disabled={inviteMutation.isPending}
            >
              Enviar convite por e-mail
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
