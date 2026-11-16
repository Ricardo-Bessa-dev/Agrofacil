// Telas de Grupos do SPG: listagem com aviso de mínimo de 3 agricultores (RN01), criação e detalhe de membros.
import { useState } from "react";
import { Link } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "../../api/client";
import { CertificationStatusBadge } from "../../components/StatusBadge";

export function GroupsPage() {
  const queryClient = useQueryClient();
  const [selectedGroupId, setSelectedGroupId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [uf, setUf] = useState("BA");
  const [municipalityId, setMunicipalityId] = useState("");
  const [notes, setNotes] = useState("");

  const groupsQuery = useQuery({
    queryKey: ["groups"],
    queryFn: () => unwrap(api.GET("/groups")),
  });

  const groupDetailQuery = useQuery({
    queryKey: ["groups", selectedGroupId],
    enabled: Boolean(selectedGroupId),
    queryFn: () =>
      unwrap(api.GET("/groups/{id}", { params: { path: { id: selectedGroupId! } } })),
  });

  const municipalitiesQuery = useQuery({
    queryKey: ["municipalities", uf],
    queryFn: () =>
      unwrap(api.GET("/municipalities", { params: { query: { uf } } })),
  });

  const createGroupMutation = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST("/groups", {
          body: {
            name: name.trim(),
            municipalityId: municipalityId ? Number(municipalityId) : undefined,
            notes: notes.trim() || undefined,
          },
        }),
      ),
    onSuccess: (created) => {
      setName("");
      setNotes("");
      queryClient.invalidateQueries({ queryKey: ["groups"] });
      if (created.id) setSelectedGroupId(created.id);
    },
  });

  const deleteGroupMutation = useMutation({
    mutationFn: (groupId: string) =>
      unwrap(api.DELETE("/groups/{id}", { params: { path: { id: groupId } } })),
    onSuccess: () => {
      setSelectedGroupId(null);
      queryClient.invalidateQueries({ queryKey: ["groups"] });
    },
  });

  const groups = groupsQuery.data ?? [];

  return (
    <div>
      <div className="af-topbar">
        <div>
          <h1 className="af-page-title">Grupos do SPG</h1>
          <p className="af-page-subtitle">
            Núcleos regionais de agricultores para visitas de pares e controle social (RN01).
          </p>
        </div>
      </div>

      <div className="af-grid-2">
        <div className="af-card">
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">Grupos Cadastrados ({groups.length})</h2>
              <p className="af-card-subtitle">
                Pela RN01, recomenda-se o mínimo de 3 agricultores ativos por grupo.
              </p>
            </div>
          </div>

          {groupsQuery.isLoading ? (
            <div className="af-empty-state">Carregando grupos...</div>
          ) : groups.length === 0 ? (
            <div className="af-empty-state">Nenhum grupo cadastrado.</div>
          ) : (
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Nome do grupo</th>
                    <th>Município</th>
                    <th>Agricultores</th>
                    <th style={{ textAlign: "right" }}>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {groups.map((g) => (
                    <tr
                      key={g.id}
                      style={{
                        background:
                          selectedGroupId === g.id ? "var(--af-bg-surface-soft)" : undefined,
                      }}
                    >
                      <td>
                        <div style={{ fontWeight: 700, color: "var(--af-green-darker)" }}>
                          {g.name}
                        </div>
                        {g.notes && (
                          <div style={{ fontSize: "0.78rem", color: "var(--af-text-muted)" }}>
                            {g.notes}
                          </div>
                        )}
                      </td>
                      <td>{g.municipalityName ?? "—"}</td>
                      <td>
                        <strong>{g.farmersCount ?? 0}</strong>{" "}
                        {g.belowMinimum && (
                          <span
                            className="af-badge af-badge--warning"
                            title="Abaixo do mínimo recomendado de 3 agricultores ativos (RN01)"
                          >
                            Abaixo do mínimo (RN01)
                          </span>
                        )}
                      </td>
                      <td style={{ textAlign: "right" }}>
                        <div style={{ display: "inline-flex", gap: 6 }}>
                          <button
                            type="button"
                            className="af-btn af-btn--outline af-btn--sm"
                            onClick={() => setSelectedGroupId(g.id ?? null)}
                          >
                            Ver membros
                          </button>
                          {g.id && (g.farmersCount ?? 0) === 0 && (
                            <button
                              type="button"
                              className="af-btn af-btn--ghost af-btn--sm"
                              onClick={() => deleteGroupMutation.mutate(g.id!)}
                            >
                              Excluir
                            </button>
                          )}
                        </div>
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
            Criar Novo Grupo SPG
          </h2>
          <form
            className="af-form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              if (name.trim()) createGroupMutation.mutate();
            }}
          >
            <div className="af-field">
              <label className="af-label">Nome do grupo *</label>
              <input
                className="af-input"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                placeholder="Ex.: Grupo Agroecológico Serra Azul"
              />
            </div>

            <div className="af-form-row">
              <div className="af-field">
                <label className="af-label">UF</label>
                <select
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
                </select>
              </div>
              <div className="af-field">
                <label className="af-label">Município de referência</label>
                <select
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
            </div>

            <div className="af-field">
              <label className="af-label">Observações</label>
              <textarea
                className="af-textarea"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Dia de reunião mensal, pontos focais..."
              />
            </div>

            <button
              type="submit"
              className="af-btn af-btn--primary af-btn--sm"
              disabled={createGroupMutation.isPending}
            >
              + Salvar grupo
            </button>
          </form>
        </div>
      </div>

      {selectedGroupId && groupDetailQuery.data && (
        <div className="af-card" style={{ marginTop: 20 }}>
          <div className="af-card-header">
            <div>
              <h2 className="af-card-title">
                Membros do grupo: {groupDetailQuery.data.name}
              </h2>
              <p className="af-card-subtitle">
                {groupDetailQuery.data.municipalityName ?? "Sem município"} •{" "}
                {groupDetailQuery.data.farmersCount ?? 0} agricultor(es)
              </p>
            </div>
            <button
              type="button"
              className="af-btn af-btn--ghost af-btn--sm"
              onClick={() => setSelectedGroupId(null)}
            >
              Fechar ✕
            </button>
          </div>

          {(groupDetailQuery.data.farmers ?? []).length === 0 ? (
            <div className="af-empty-state">Nenhum agricultor vinculado a este grupo.</div>
          ) : (
            <div className="af-table-wrap">
              <table className="af-table">
                <thead>
                  <tr>
                    <th>Agricultor</th>
                    <th>CPF</th>
                    <th>Propriedades</th>
                    <th>Certificação</th>
                    <th style={{ textAlign: "right" }}>Perfil</th>
                  </tr>
                </thead>
                <tbody>
                  {(groupDetailQuery.data.farmers ?? []).map((f) => (
                    <tr key={f.id}>
                      <td style={{ fontWeight: 700 }}>{f.name}</td>
                      <td>{f.cpfMasked}</td>
                      <td>{f.propertiesCount ?? 0}</td>
                      <td>
                        <CertificationStatusBadge status={f.worstCertificationStatus} />
                      </td>
                      <td style={{ textAlign: "right" }}>
                        <Link
                          to={`/agricultores/${f.id}`}
                          className="af-btn af-btn--outline af-btn--sm"
                        >
                          Ver agricultor
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
    </div>
  );
}
