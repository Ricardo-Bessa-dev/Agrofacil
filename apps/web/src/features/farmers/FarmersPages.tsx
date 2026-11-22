// Telas de Agricultores do painel do coordenador: listagem, cadastro, detalhe, aparelhos e código de acesso (RN03, RN25).
import React, { useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  formatCpf,
  formatDatePtBr,
  formatDateTimePtBr,
  isValidCpf,
  onlyDigits,
  RECORD_TYPE_LABELS,
  USER_STATUS_LABELS,
  whatsappLink,
} from "@agrofacil/domain";
import type { AccessCodeResponse } from "@agrofacil/api-types";
import { api, ApiProblemError, normalizePhoneE164, unwrap } from "../../api/client";
import { fetchMunicipalities } from "../../api/municipalities";
import { Pagination } from "../../components/Pagination";
import { CertificationStatusBadge, RecordTypeAvatar } from "../../components/StatusBadge";

/** Registros por página na ficha do agricultor. */
const FARMER_RECORDS_PAGE_SIZE = 20;

function isBrazilianMobile(phone: string | undefined): boolean {
  return /^\+55[1-9][0-9]9[0-9]{8}$/.test(phone ?? "");
}

function AccessCodeModal({
  farmerName,
  farmerPhone,
  codeData,
  onClose,
}: {
  farmerName: string;
  farmerPhone?: string;
  codeData: AccessCodeResponse;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const code = codeData.code ?? "";
  const formattedCode = code.length === 6 ? `${code.slice(0, 3)} ${code.slice(3)}` : code;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch {
      // Fallback silencioso
    }
  };

  const whatsappUrl = whatsappLink(
    farmerPhone,
    `Olá, ${farmerName}! Seu código de acesso ao aplicativo AgroFácil é: *${code}* (válido até ${formatDateTimePtBr(codeData.expiresAt)}).`,
  );

  return (
    <div className="af-modal-backdrop" onClick={onClose}>
      <div className="af-modal" onClick={(e) => e.stopPropagation()} style={{ maxWidth: 460 }}>
        <div className="af-modal-header">
          <h3 className="af-card-title">Código de acesso gerado</h3>
          <button type="button" className="af-btn af-btn--ghost af-btn--sm" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="af-modal-body" style={{ textAlign: "center" }}>
          <p className="af-card-subtitle" style={{ marginBottom: 14 }}>
            Entregue este código de 6 dígitos para <strong>{farmerName}</strong> ativar o aplicativo
            no celular. Por segurança, o código aparece apenas nesta tela (RN03).
          </p>

          <div
            style={{
              background: "var(--af-bg-cream)",
              border: "2px dashed var(--af-green-primary)",
              borderRadius: "var(--af-radius-md)",
              padding: "20px 16px",
              marginBottom: 14,
            }}
          >
            <div
              style={{
                fontSize: "2.25rem",
                fontWeight: 700,
                letterSpacing: "0.22em",
                color: "var(--af-green-darker)",
              }}
            >
              {formattedCode}
            </div>
            <div style={{ fontSize: "0.82rem", color: "var(--af-text-secondary)", marginTop: 6 }}>
              Válido até {formatDateTimePtBr(codeData.expiresAt)}
            </div>
          </div>

          <div style={{ display: "flex", gap: 10, justifyContent: "center", flexWrap: "wrap" }}>
            <button
              type="button"
              className="af-btn af-btn--primary af-btn--sm"
              onClick={handleCopy}
            >
              {copied ? "✓ Código copiado!" : "Copiar código"}
            </button>
            {whatsappUrl && (
              <a
                href={whatsappUrl}
                target="_blank"
                rel="noreferrer"
                className="af-btn af-btn--secondary af-btn--sm"
              >
                Enviar por WhatsApp
              </a>
            )}
            <button
              type="button"
              className="af-btn af-btn--outline af-btn--sm"
              onClick={() => window.print()}
            >
              Imprimir
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export function FarmersListPage() {
  const [searchParams] = useSearchParams();
  const [search, setSearch] = useState("");
  const [groupId, setGroupId] = useState("");
  const [status, setStatus] = useState<"" | "ACTIVE" | "INACTIVE">(
    (searchParams.get("status") as "" | "ACTIVE" | "INACTIVE") ?? "",
  );
  const [page, setPage] = useState(0);
  const [activeCodeModal, setActiveCodeModal] = useState<{
    name: string;
    phone?: string;
    codeData: AccessCodeResponse;
  } | null>(null);

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: () => unwrap(api.GET("/groups")),
  });

  const farmersQuery = useQuery({
    queryKey: ["farmers", { search, groupId, status, page }],
    queryFn: () =>
      unwrap(
        api.GET("/farmers", {
          params: {
            query: {
              filter: {
                search: search.trim() || undefined,
                groupId: groupId || undefined,
                status: status || undefined,
              },
              pageable: { page, size: 15, sort: ["name,asc"] },
            },
          },
        }),
      ),
  });

  const generateCodeMutation = useMutation({
    mutationFn: async (farmer: { id: string; name: string; phone?: string }) => {
      const codeData = await unwrap(
        api.POST("/farmers/{id}/access-codes", {
          params: { path: { id: farmer.id } },
        }),
      );
      return { farmer, codeData };
    },
    onSuccess: ({ farmer, codeData }) => {
      setActiveCodeModal({
        name: farmer.name,
        phone: farmer.phone,
        codeData,
      });
    },
  });

  const items = farmersQuery.data?.content ?? [];
  const meta = farmersQuery.data?.page;

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Agricultores</h1>
          <p className="af-page-subtitle">
            Cadastre agricultores da associação, acompanhe a sincronização e gere códigos de acesso.
          </p>
        </div>
        <div className="af-topbar-actions">
          <Link to="/agricultores/novo" className="af-btn af-btn--primary af-btn--sm">
            + Cadastrar agricultor
          </Link>
        </div>
      </div>

      <div className="af-card" style={{ marginBottom: 20 }}>
        <div className="af-filters-bar" style={{ marginBottom: 16 }}>
          <div className="af-field" style={{ minWidth: 240, flex: 1 }}>
            <label className="af-label" htmlFor="farmer-search">
              Buscar por nome ou CPF
            </label>
            <input
              id="farmer-search"
              type="search"
              className="af-input"
              placeholder="Ex.: João da Silva ou 123..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>

          <div className="af-field" style={{ minWidth: 190 }}>
            <label className="af-label" htmlFor="farmer-group">
              Grupo SPG
            </label>
            <select
              id="farmer-group"
              className="af-select"
              value={groupId}
              onChange={(e) => {
                setGroupId(e.target.value);
                setPage(0);
              }}
            >
              <option value="">Todos os grupos</option>
              {(groupsQuery.data ?? []).map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </select>
          </div>

          <div className="af-field" style={{ minWidth: 160 }}>
            <label className="af-label" htmlFor="farmer-status">
              Situação
            </label>
            <select
              id="farmer-status"
              className="af-select"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as "" | "ACTIVE" | "INACTIVE");
                setPage(0);
              }}
            >
              <option value="">Todos</option>
              <option value="ACTIVE">Ativos</option>
              <option value="INACTIVE">Inativos</option>
            </select>
          </div>
        </div>

        {farmersQuery.isLoading ? (
          <div className="af-empty-state">Carregando agricultores...</div>
        ) : items.length === 0 ? (
          <div className="af-empty-state">
            Nenhum agricultor encontrado com os filtros selecionados.
          </div>
        ) : (
          <div className="af-table-wrap">
            <table className="af-table">
              <thead>
                <tr>
                  <th>Nome / CPF</th>
                  <th>Grupo / Município</th>
                  <th>Propriedades</th>
                  <th>Certificação</th>
                  <th>Último manejo</th>
                  <th>Situação</th>
                  <th style={{ textAlign: "right" }}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {items.map((f) => (
                  <tr key={f.id}>
                    <td>
                      <Link
                        to={`/agricultores/${f.id}`}
                        style={{ fontWeight: 700, color: "var(--af-green-darker)" }}
                      >
                        {f.name}
                      </Link>
                      <div style={{ fontSize: "0.8rem", color: "var(--af-text-secondary)" }}>
                        CPF: {f.cpfMasked ?? "—"} {f.phone ? `• ${f.phone}` : ""}
                      </div>
                    </td>
                    <td>
                      <div>{f.group?.name ?? "Sem grupo"}</div>
                      <div style={{ fontSize: "0.78rem", color: "var(--af-text-muted)" }}>
                        {f.municipalityName ?? "—"}
                      </div>
                    </td>
                    <td>
                      <strong>{f.propertiesCount ?? 0}</strong>
                      {(f.incompleteRecords ?? 0) > 0 && (
                        <div style={{ fontSize: "0.75rem", color: "var(--af-terracotta)" }}>
                          {f.incompleteRecords} manejo(s) incompleto(s)
                        </div>
                      )}
                    </td>
                    <td>
                      <CertificationStatusBadge status={f.worstCertificationStatus} />
                    </td>
                    <td>
                      <div>{formatDatePtBr(f.lastRecordOn)}</div>
                      {f.lastSyncAt && (
                        <div style={{ fontSize: "0.75rem", color: "var(--af-text-muted)" }}>
                          Sync: {formatDateTimePtBr(f.lastSyncAt)}
                        </div>
                      )}
                    </td>
                    <td>
                      <span
                        className={`af-badge ${
                          f.status === "ACTIVE" ? "af-badge--valid" : "af-badge--neutral"
                        }`}
                      >
                        {f.status ? USER_STATUS_LABELS[f.status] : "Ativo"}
                      </span>
                    </td>
                    <td style={{ textAlign: "right" }}>
                      <div style={{ display: "inline-flex", gap: 6 }}>
                        <button
                          type="button"
                          className="af-btn af-btn--secondary af-btn--sm"
                          disabled={generateCodeMutation.isPending || f.status === "INACTIVE"}
                          onClick={() =>
                            f.id &&
                            generateCodeMutation.mutate({
                              id: f.id,
                              name: f.name ?? "Agricultor",
                              phone: f.phone,
                            })
                          }
                          title="Gerar código de 6 dígitos para o aplicativo"
                        >
                          Gerar código
                        </button>
                        <Link
                          to={`/agricultores/${f.id}`}
                          className="af-btn af-btn--outline af-btn--sm"
                        >
                          Abrir
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <Pagination meta={meta} onPageChange={setPage} itemsLabel="agricultores" />
      </div>

      {activeCodeModal && (
        <AccessCodeModal
          farmerName={activeCodeModal.name}
          farmerPhone={activeCodeModal.phone}
          codeData={activeCodeModal.codeData}
          onClose={() => setActiveCodeModal(null)}
        />
      )}
    </div>
  );
}

export function FarmerFormPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [name, setName] = useState("");
  const [cpf, setCpf] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [groupId, setGroupId] = useState("");
  const [currentYear] = useState(() => new Date().getFullYear());
  const [groupJoinedYear, setGroupJoinedYear] = useState(() => String(currentYear));
  const [cafNumber, setCafNumber] = useState("");
  const [uf, setUf] = useState("BA");
  const [municipalityId, setMunicipalityId] = useState<string>("");
  const [locality, setLocality] = useState("");
  const [postalCode, setPostalCode] = useState("");
  const [notes, setNotes] = useState("");
  const [generateCodeAfterCreate, setGenerateCodeAfterCreate] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createdResult, setCreatedResult] = useState<{
    farmerId: string;
    name: string;
    phone?: string;
    codeData: AccessCodeResponse;
  } | null>(null);

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: () => unwrap(api.GET("/groups")),
  });

  const municipalitiesQuery = useQuery({
    queryKey: ["municipalities", uf],
    queryFn: () => fetchMunicipalities(uf),
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError("Informe o nome completo do agricultor.");
      return;
    }

    const cpfDigits = onlyDigits(cpf);
    if (!isValidCpf(cpfDigits)) {
      setError("CPF inválido. Confira os 11 dígitos informados.");
      return;
    }

    const normalizedPhone = normalizePhoneE164(phone);
    if (phone.trim() && !isBrazilianMobile(normalizedPhone)) {
      setError("Informe um celular com DDD e 11 dígitos.");
      return;
    }

    const cepDigits = onlyDigits(postalCode);
    if (cepDigits && cepDigits.length !== 8) {
      setError("O CEP deve ter 8 dígitos (ou deixe em branco).");
      return;
    }

    setSubmitting(true);
    try {
      const created = await unwrap(
        api.POST("/farmers", {
          body: {
            name: name.trim(),
            cpf: cpfDigits,
            phone: normalizedPhone,
            email: email.trim() || undefined,
            groupId: groupId || undefined,
            groupJoinedYear: groupJoinedYear ? Number(groupJoinedYear) : undefined,
            cafNumber: cafNumber.trim() || undefined,
            address: {
              uf: uf || undefined,
              municipalityId: municipalityId ? Number(municipalityId) : undefined,
              locality: locality.trim() || undefined,
              postalCode: cepDigits || undefined,
            },
            notes: notes.trim() || undefined,
          },
        }),
      );

      await queryClient.invalidateQueries({ queryKey: ["farmers"] });

      if (generateCodeAfterCreate && created.id) {
        const codeData = await unwrap(
          api.POST("/farmers/{id}/access-codes", {
            params: { path: { id: created.id } },
          }),
        );
        setCreatedResult({
          farmerId: created.id,
          name: created.name ?? name,
          phone: created.phone,
          codeData,
        });
      } else {
        navigate(`/agricultores/${created.id}`);
      }
    } catch (err) {
      if (err instanceof ApiProblemError) {
        setError(err.message);
      } else {
        setError("Não foi possível cadastrar o agricultor.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Cadastrar Agricultor</h1>
          <p className="af-page-subtitle">
            O cadastro é realizado pela coordenação (ADR-03). Ao salvar, você já pode gerar o código
            de 6 dígitos para o agricultor entrar no aplicativo.
          </p>
        </div>
        <div className="af-topbar-actions">
          <Link to="/agricultores" className="af-btn af-btn--outline af-btn--sm">
            Voltar
          </Link>
        </div>
      </div>

      <div className="af-card" style={{ maxWidth: 820 }}>
        {error && (
          <div className="af-alert af-alert--danger" style={{ marginBottom: 16 }}>
            {error}
          </div>
        )}

        <form className="af-form-grid" onSubmit={handleSubmit}>
          <div className="af-form-row">
            <div className="af-field">
              <label className="af-label" htmlFor="f-name">
                Nome completo *
              </label>
              <input
                id="f-name"
                className="af-input"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                placeholder="Ex.: Maria Aparecida dos Santos"
              />
            </div>

            <div className="af-field">
              <label className="af-label" htmlFor="f-cpf">
                CPF (11 dígitos) *
              </label>
              <input
                id="f-cpf"
                className="af-input"
                value={cpf}
                onChange={(e) => setCpf(formatCpf(e.target.value))}
                required
                placeholder="000.000.000-00"
              />
            </div>
          </div>

          <div className="af-form-row">
            <div className="af-field">
              <label className="af-label" htmlFor="f-phone">
                Telefone / WhatsApp
              </label>
              <input
                id="f-phone"
                type="tel"
                autoComplete="tel-national"
                className="af-input"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="(74) 99999-0000"
              />
            </div>

            <div className="af-field">
              <label className="af-label" htmlFor="f-email">
                E-mail (opcional)
              </label>
              <input
                id="f-email"
                type="email"
                className="af-input"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="opcional@exemplo.com"
              />
            </div>
          </div>

          <div className="af-form-row">
            <div className="af-field">
              <label className="af-label" htmlFor="f-group">
                Grupo SPG
              </label>
              <select
                id="f-group"
                className="af-select"
                value={groupId}
                onChange={(e) => setGroupId(e.target.value)}
              >
                <option value="">Selecione um grupo</option>
                {(groupsQuery.data ?? []).map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="af-field">
              <label className="af-label" htmlFor="f-caf">
                Número do CAF / DAP
              </label>
              <input
                id="f-caf"
                className="af-input"
                value={cafNumber}
                onChange={(e) => setCafNumber(e.target.value)}
                placeholder="Ex.: BA0123456789"
              />
            </div>

            <div className="af-field">
              <label className="af-label" htmlFor="f-year">
                Ano de ingresso no grupo
              </label>
              <input
                id="f-year"
                type="number"
                className="af-input"
                value={groupJoinedYear}
                onChange={(e) => setGroupJoinedYear(e.target.value)}
                min={1950}
                max={currentYear}
              />
            </div>
          </div>

          <div className="af-form-row">
            <div className="af-field">
              <label className="af-label" htmlFor="f-uf">
                UF
              </label>
              <select
                id="f-uf"
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
                <option value="CE">CE</option>
                <option value="PB">PB</option>
                <option value="RN">RN</option>
                <option value="PI">PI</option>
                <option value="MA">MA</option>
                <option value="MG">MG</option>
                <option value="SP">SP</option>
              </select>
            </div>

            <div className="af-field">
              <label className="af-label" htmlFor="f-municipality">
                Município
              </label>
              <select
                id="f-municipality"
                className="af-select"
                value={municipalityId}
                onChange={(e) => setMunicipalityId(e.target.value)}
              >
                <option value="">Selecione o município</option>
                {(municipalitiesQuery.data ?? []).map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name} ({m.uf})
                  </option>
                ))}
              </select>
            </div>

            <div className="af-field">
              <label className="af-label" htmlFor="f-locality">
                Comunidade / Localidade rural
              </label>
              <input
                id="f-locality"
                className="af-input"
                value={locality}
                onChange={(e) => setLocality(e.target.value)}
                placeholder="Ex.: Povoado Serra da Canabrava"
              />
            </div>

            <div className="af-field">
              <label className="af-label" htmlFor="f-cep">
                CEP
              </label>
              <input
                id="f-cep"
                className="af-input"
                value={postalCode}
                onChange={(e) => setPostalCode(e.target.value)}
                placeholder="00000-000"
              />
            </div>
          </div>

          <div className="af-field">
            <label className="af-label" htmlFor="f-notes">
              Observações
            </label>
            <textarea
              id="f-notes"
              className="af-textarea"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Informações adicionais sobre o produtor..."
            />
          </div>

          <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
            <input
              type="checkbox"
              checked={generateCodeAfterCreate}
              onChange={(e) => setGenerateCodeAfterCreate(e.target.checked)}
            />
            <span style={{ fontSize: "0.9rem", fontWeight: 500 }}>
              Gerar código de acesso de 6 dígitos imediatamente após salvar
            </span>
          </label>

          {error && (
            <div className="af-alert af-alert--danger" style={{ marginTop: 8 }}>
              {error}
            </div>
          )}

          <div style={{ display: "flex", gap: 12, justifyContent: "flex-end" }}>
            <Link to="/agricultores" className="af-btn af-btn--outline af-btn--sm">
              Cancelar
            </Link>
            <button
              type="submit"
              className="af-btn af-btn--primary af-btn--sm"
              disabled={submitting}
            >
              {submitting ? "Salvando..." : "Salvar agricultor"}
            </button>
          </div>
        </form>
      </div>

      {createdResult && (
        <AccessCodeModal
          farmerName={createdResult.name}
          farmerPhone={createdResult.phone}
          codeData={createdResult.codeData}
          onClose={() => navigate(`/agricultores/${createdResult.farmerId}`)}
        />
      )}
    </div>
  );
}

export function FarmerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<"info" | "properties" | "records" | "devices">("info");
  const [recordsPage, setRecordsPage] = useState(0);
  const [codeModal, setCodeModal] = useState<AccessCodeResponse | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  const farmerQuery = useQuery({
    queryKey: ["farmers", id],
    enabled: Boolean(id),
    queryFn: () => unwrap(api.GET("/farmers/{id}", { params: { path: { id: id! } } })),
  });

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: () => unwrap(api.GET("/groups")),
  });

  const devicesQuery = useQuery({
    queryKey: ["farmers", id, "devices"],
    enabled: Boolean(id) && tab === "devices",
    queryFn: () => unwrap(api.GET("/farmers/{id}/devices", { params: { path: { id: id! } } })),
  });

  const recordsQuery = useQuery({
    queryKey: ["records", { farmerId: id, page: recordsPage }],
    enabled: Boolean(id) && tab === "records",
    queryFn: () =>
      unwrap(
        api.GET("/records", {
          params: {
            query: {
              filter: { farmerId: id },
              pageable: {
                page: recordsPage,
                size: FARMER_RECORDS_PAGE_SIZE,
                sort: ["performedOn,desc"],
              },
            },
          },
        }),
      ),
  });

  const generateCodeMutation = useMutation({
    mutationFn: () =>
      unwrap(api.POST("/farmers/{id}/access-codes", { params: { path: { id: id! } } })),
    onSuccess: (data) => {
      setCodeModal(data);
      queryClient.invalidateQueries({ queryKey: ["farmers", id] });
    },
  });

  const toggleStatusMutation = useMutation({
    mutationFn: async (currentStatus?: "ACTIVE" | "INACTIVE") => {
      if (currentStatus === "ACTIVE") {
        await unwrap(api.POST("/farmers/{id}/deactivate", { params: { path: { id: id! } } }));
      } else {
        await unwrap(api.POST("/farmers/{id}/reactivate", { params: { path: { id: id! } } }));
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["farmers", id] });
      queryClient.invalidateQueries({ queryKey: ["farmers"] });
      setFeedback("Situação do agricultor atualizada com sucesso.");
    },
  });

  const revokeDeviceMutation = useMutation({
    mutationFn: (deviceId: string) =>
      unwrap(api.DELETE("/devices/{id}", { params: { path: { id: deviceId } } })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["farmers", id, "devices"] });
      queryClient.invalidateQueries({ queryKey: ["farmers", id] });
    },
  });

  const farmer = farmerQuery.data;
  if (farmerQuery.isLoading) {
    return <div className="af-empty-state">Carregando dados do agricultor...</div>;
  }
  if (!farmer) {
    return <div className="af-empty-state">Agricultor não encontrado.</div>;
  }

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">{farmer.name}</h1>
          <p className="af-page-subtitle">
            CPF: {formatCpf(farmer.cpf ?? "")} •{" "}
            {farmer.status ? USER_STATUS_LABELS[farmer.status] : "Ativo"} • Aparelhos ativos:{" "}
            {farmer.activeDevices ?? 0}
          </p>
        </div>
        <div className="af-topbar-actions">
          <button
            type="button"
            className="af-btn af-btn--primary af-btn--sm"
            disabled={farmer.status === "INACTIVE" || generateCodeMutation.isPending}
            onClick={() => generateCodeMutation.mutate()}
          >
            Gerar código de acesso
          </button>
          <button
            type="button"
            className={`af-btn af-btn--sm ${
              farmer.status === "ACTIVE" ? "af-btn--danger" : "af-btn--secondary"
            }`}
            onClick={() => toggleStatusMutation.mutate(farmer.status)}
          >
            {farmer.status === "ACTIVE" ? "Desativar agricultor" : "Reativar agricultor"}
          </button>
          <Link to="/agricultores" className="af-btn af-btn--outline af-btn--sm">
            Voltar
          </Link>
        </div>
      </div>

      {feedback && (
        <div className="af-alert af-alert--success" style={{ marginBottom: 16 }}>
          {feedback}
        </div>
      )}

      <div className="af-tabs">
        <button
          type="button"
          className={`af-tab ${tab === "info" ? "af-tab--active" : ""}`}
          onClick={() => setTab("info")}
        >
          Dados cadastrais
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "properties" ? "af-tab--active" : ""}`}
          onClick={() => setTab("properties")}
        >
          Propriedades ({(farmer.properties ?? []).length})
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "records" ? "af-tab--active" : ""}`}
          onClick={() => setTab("records")}
        >
          Registros de manejo
        </button>
        <button
          type="button"
          className={`af-tab ${tab === "devices" ? "af-tab--active" : ""}`}
          onClick={() => setTab("devices")}
        >
          Aparelhos ({farmer.activeDevices ?? 0})
        </button>
      </div>

      {tab === "info" && (
        <FarmerEditCard
          farmer={farmer}
          groups={groupsQuery.data ?? []}
          onSaved={() => {
            queryClient.invalidateQueries({ queryKey: ["farmers", id] });
            setFeedback("Cadastro atualizado com sucesso.");
          }}
        />
      )}

      {tab === "properties" && (
        <div className="af-card">
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">Propriedades vinculadas</h2>
              <p className="af-card-subtitle">Unidades de produção familiar deste agricultor</p>
            </div>
            <Link
              to={`/propriedades?farmerId=${farmer.id}`}
              className="af-btn af-btn--primary af-btn--sm"
            >
              + Nova propriedade
            </Link>
          </div>
          {(farmer.properties ?? []).length === 0 ? (
            <div className="af-empty-state">
              Nenhuma propriedade cadastrada para este agricultor.
            </div>
          ) : (
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Propriedade</th>
                    <th>Município</th>
                    <th>Certificação atual</th>
                    <th style={{ textAlign: "right" }}>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {(farmer.properties ?? []).map((p) => (
                    <tr key={p.id}>
                      <td>
                        <Link
                          to={`/propriedades/${p.id}`}
                          style={{ fontWeight: 700, color: "var(--af-green-darker)" }}
                        >
                          {p.name}
                        </Link>
                      </td>
                      <td>{p.municipalityName ?? "—"}</td>
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
                          Ver propriedade
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === "records" && (
        <div className="af-card">
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">Registros de manejo do agricultor</h2>
              <p className="af-card-subtitle">Caderno de campo sincronizado ou lançado no painel</p>
            </div>
            <Link
              to={`/registros/novo?farmerId=${farmer.id}`}
              className="af-btn af-btn--primary af-btn--sm"
            >
              + Registrar manejo
            </Link>
          </div>

          {recordsQuery.isLoading ? (
            <div className="af-empty-state">Carregando manejos...</div>
          ) : (recordsQuery.data?.content ?? []).length === 0 ? (
            <div className="af-empty-state">Nenhum registro de manejo encontrado.</div>
          ) : (
            <div>
              {(recordsQuery.data?.content ?? []).map((rec) => (
                <Link key={rec.id} to={`/registros/${rec.id}`} className="af-record-row">
                  <RecordTypeAvatar type={rec.type} />
                  <div className="af-record-row-main">
                    <div className="af-record-row-title">
                      {rec.type ? RECORD_TYPE_LABELS[rec.type] : "Manejo"} • {rec.subject ?? ""}
                    </div>
                    <div className="af-record-row-meta">
                      {rec.property?.name} — {formatDatePtBr(rec.performedOn)}
                    </div>
                  </div>
                  <span
                    className={`af-badge ${
                      rec.completeness === "COMPLETE" ? "af-badge--valid" : "af-badge--warning"
                    }`}
                  >
                    {rec.completeness === "COMPLETE" ? "Completo" : "Incompleto"}
                  </span>
                </Link>
              ))}
            </div>
          )}
          <Pagination
            meta={recordsQuery.data?.page}
            onPageChange={setRecordsPage}
            itemsLabel="registros"
          />
        </div>
      )}

      {tab === "devices" && (
        <div className="af-card">
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">Aparelhos vinculados</h2>
              <p className="af-card-subtitle">
                Celulares ativados pelo agricultor com código de acesso (RN25)
              </p>
            </div>
          </div>

          {devicesQuery.isLoading ? (
            <div className="af-empty-state">Carregando aparelhos...</div>
          ) : (devicesQuery.data ?? []).length === 0 ? (
            <div className="af-empty-state">
              Nenhum aparelho ativado até o momento. Gere um código de acesso para o agricultor
              entrar.
            </div>
          ) : (
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Aparelho</th>
                    <th>Plataforma / Versão</th>
                    <th>Ativado em</th>
                    <th>Última sincronização</th>
                    <th>Status</th>
                    <th style={{ textAlign: "right" }}>Ação</th>
                  </tr>
                </thead>
                <tbody>
                  {(devicesQuery.data ?? []).map((d) => (
                    <tr key={d.id}>
                      <td style={{ fontWeight: 600 }}>{d.name ?? "Celular do agricultor"}</td>
                      <td>
                        {d.platform ?? "—"} (v{d.appVersion ?? "1.0.0"})
                      </td>
                      <td>{formatDateTimePtBr(d.createdAt)}</td>
                      <td>{formatDateTimePtBr(d.lastSeenAt)}</td>
                      <td>
                        {d.revokedAt ? (
                          <span className="af-badge af-badge--danger">Revogado</span>
                        ) : (
                          <span className="af-badge af-badge--valid">Ativo</span>
                        )}
                      </td>
                      <td style={{ textAlign: "right" }}>
                        {!d.revokedAt && d.id && (
                          <button
                            type="button"
                            className="af-btn af-btn--danger af-btn--sm"
                            onClick={() => revokeDeviceMutation.mutate(d.id!)}
                          >
                            Revogar acesso
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
      )}

      {codeModal && (
        <AccessCodeModal
          farmerName={farmer.name ?? "Agricultor"}
          farmerPhone={farmer.phone}
          codeData={codeModal}
          onClose={() => setCodeModal(null)}
        />
      )}
    </div>
  );
}

function FarmerEditCard({
  farmer,
  groups,
  onSaved,
}: {
  farmer: NonNullable<import("@agrofacil/api-types").FarmerView>;
  groups: import("@agrofacil/api-types").GroupView[];
  onSaved: () => void;
}) {
  const [name, setName] = useState(farmer.name ?? "");
  const [cpf, setCpf] = useState(formatCpf(farmer.cpf ?? ""));
  const [phone, setPhone] = useState(farmer.phone ?? "");
  const [email, setEmail] = useState(farmer.email ?? "");
  const [groupId, setGroupId] = useState(farmer.groupId ?? "");
  const [cafNumber, setCafNumber] = useState(farmer.cafNumber ?? "");
  const [locality, setLocality] = useState(farmer.address?.locality ?? "");
  const [notes, setNotes] = useState(farmer.notes ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const normalizedPhone = normalizePhoneE164(phone);
    const phoneChanged = phone !== (farmer.phone ?? "");
    if (phoneChanged && phone.trim() && !isBrazilianMobile(normalizedPhone)) {
      setError("Informe um celular com DDD e 11 dígitos.");
      return;
    }
    setSaving(true);
    try {
      await unwrap(
        api.PATCH("/farmers/{id}", {
          params: { path: { id: farmer.id! } },
          body: {
            name: name.trim(),
            cpf: onlyDigits(cpf),
            ...(phoneChanged ? { phone: normalizedPhone } : {}),
            email: email.trim() || undefined,
            groupId: groupId || undefined,
            cafNumber: cafNumber.trim() || undefined,
            address: {
              ...farmer.address,
              locality: locality.trim() || undefined,
            },
            notes: notes.trim() || undefined,
            version: farmer.version,
          },
        }),
      );
      onSaved();
    } catch (err) {
      setError(err instanceof ApiProblemError ? err.message : "Erro ao atualizar agricultor.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="af-card">
      {error && (
        <div className="af-alert af-alert--danger" style={{ marginBottom: 16 }}>
          {error}
        </div>
      )}
      <form className="af-form-grid" onSubmit={handleSave}>
        <div className="af-form-row">
          <div className="af-field">
            <label htmlFor="farmer-nome-completo" className="af-label">
              Nome completo
            </label>
            <input
              id="farmer-nome-completo"
              className="af-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>
          <div className="af-field">
            <label htmlFor="farmer-cpf" className="af-label">
              CPF
            </label>
            <input
              id="farmer-cpf"
              className="af-input"
              value={cpf}
              onChange={(e) => setCpf(formatCpf(e.target.value))}
              required
            />
          </div>
        </div>

        <div className="af-form-row">
          <div className="af-field">
            <label htmlFor="farmer-telefone-whatsapp" className="af-label">
              Telefone / WhatsApp
            </label>
            <input
              id="farmer-telefone-whatsapp"
              type="tel"
              className="af-input"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </div>
          <div className="af-field">
            <label htmlFor="farmer-e-mail" className="af-label">
              E-mail
            </label>
            <input
              id="farmer-e-mail"
              className="af-input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div className="af-field">
            <label htmlFor="farmer-grupo-spg" className="af-label">
              Grupo SPG
            </label>
            <select
              id="farmer-grupo-spg"
              className="af-select"
              value={groupId}
              onChange={(e) => setGroupId(e.target.value)}
            >
              <option value="">Sem grupo</option>
              {groups.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="af-form-row">
          <div className="af-field">
            <label htmlFor="farmer-caf-dap" className="af-label">
              CAF / DAP
            </label>
            <input
              id="farmer-caf-dap"
              className="af-input"
              value={cafNumber}
              onChange={(e) => setCafNumber(e.target.value)}
            />
          </div>
          <div className="af-field">
            <label htmlFor="farmer-comunidade-localidade" className="af-label">
              Comunidade / Localidade
            </label>
            <input
              id="farmer-comunidade-localidade"
              className="af-input"
              value={locality}
              onChange={(e) => setLocality(e.target.value)}
            />
          </div>
        </div>

        <div className="af-field">
          <label htmlFor="farmer-observacoes" className="af-label">
            Observações
          </label>
          <textarea
            id="farmer-observacoes"
            className="af-textarea"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <button type="submit" className="af-btn af-btn--primary af-btn--sm" disabled={saving}>
            {saving ? "Salvando..." : "Salvar alterações"}
          </button>
        </div>
      </form>
    </div>
  );
}
