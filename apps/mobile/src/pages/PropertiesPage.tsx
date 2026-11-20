import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { formatArea } from "@agrofacil/domain";
import { api } from "../api/client";
import propertyImg1 from "../assets/figma/property-placeholder-1.png";
import propertyImg2 from "../assets/figma/property-placeholder-2.png";
import statusOkIcon from "../assets/figma/status-ok.svg";

export function PropertiesPage() {
  const propertiesQuery = useQuery({
    queryKey: ["farmer-properties-list"],
    queryFn: async () => {
      const { data } = await api.GET("/properties", {
        params: {
          query: {
            filter: {},
            pageable: { size: 20 },
          },
        },
      });
      return data?.content ?? [];
    },
  });

  const properties = propertiesQuery.data ?? [];

  return (
    <div>
      {/* Top Bar */}
      <div className="page-top-bar">
        <h1 className="page-top-bar-title">Propriedades</h1>
      </div>

      <div style={{ padding: "16px 16px 24px 16px" }}>
        {propertiesQuery.isLoading ? (
          <div className="empty-state">
            <div className="empty-state-title">Carregando propriedades...</div>
          </div>
        ) : properties.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">🏡</div>
            <div className="empty-state-title">Nenhuma propriedade cadastrada</div>
            <p className="empty-state-desc">
              Sua propriedade será vinculada ao seu cadastro pela coordenação da associação.
            </p>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
            {properties.map((prop, idx) => {
              const imageSrc = idx % 2 === 0 ? propertyImg1 : propertyImg2;

              return (
                <div
                  key={prop.id}
                  className="mobile-card"
                  style={{
                    padding: 0,
                    overflow: "hidden",
                    marginBottom: 0,
                  }}
                >
                  {/* Imagem de Capa */}
                  <div style={{ height: "140px", width: "100%", position: "relative" }}>
                    <img
                      src={imageSrc}
                      alt={prop.name}
                      style={{
                        width: "100%",
                        height: "100%",
                        objectFit: "cover",
                      }}
                    />
                    <div
                      style={{
                        position: "absolute",
                        bottom: "10px",
                        left: "12px",
                        backgroundColor: "rgba(0, 0, 0, 0.65)",
                        backdropFilter: "blur(4px)",
                        color: "#fff",
                        padding: "4px 10px",
                        borderRadius: "var(--radius-full)",
                        fontSize: "11px",
                        fontWeight: 600,
                        display: "flex",
                        alignItems: "center",
                        gap: "5px",
                      }}
                    >
                      <img src={statusOkIcon} alt="" width={14} height={14} />
                      <span>Certificação Orgânica Ativa</span>
                    </div>
                  </div>

                  {/* Informações da Propriedade */}
                  <div style={{ padding: "16px" }}>
                    <h2
                      style={{
                        fontSize: "16px",
                        fontWeight: 700,
                        color: "var(--color-text)",
                        marginBottom: "4px",
                      }}
                    >
                      {prop.name}
                    </h2>

                    <div
                      style={{
                        fontSize: "13px",
                        color: "var(--color-text-secondary)",
                        marginBottom: "12px",
                      }}
                    >
                      📍 {prop.address?.municipalityName ?? "Município"} -{" "}
                      {prop.address?.uf ?? "BA"}
                      {prop.address?.locality ? ` · ${prop.address.locality}` : ""}
                    </div>

                    <div
                      style={{
                        display: "grid",
                        gridTemplateColumns: "1fr 1fr",
                        gap: "8px",
                        marginBottom: "14px",
                      }}
                    >
                      <div
                        style={{
                          backgroundColor: "var(--color-bg)",
                          padding: "10px",
                          borderRadius: "var(--radius-sm)",
                        }}
                      >
                        <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                          Área Total
                        </div>
                        <div style={{ fontSize: "14px", fontWeight: 700 }}>
                          {prop.totalArea ? formatArea(prop.totalArea) : "Não informada"}
                        </div>
                      </div>

                      <div
                        style={{
                          backgroundColor: "var(--color-bg)",
                          padding: "10px",
                          borderRadius: "var(--radius-sm)",
                        }}
                      >
                        <div style={{ fontSize: "11px", color: "var(--color-text-muted)" }}>
                          Produção
                        </div>
                        <div
                          style={{
                            fontSize: "13px",
                            fontWeight: 600,
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                          }}
                        >
                          {prop.productionDescription || "Orgânica Diversificada"}
                        </div>
                      </div>
                    </div>

                    <Link
                      to={`/propriedades/${prop.id}`}
                      className="btn-secondary"
                      style={{ width: "100%", textAlign: "center" }}
                    >
                      Ver Talhões e Detalhes →
                    </Link>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
