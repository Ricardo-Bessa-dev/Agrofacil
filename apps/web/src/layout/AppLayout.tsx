// Layout principal do Painel do Coordenador (AppShell) com cabeçalho e navegação inspirados no Figma (3:188, 15:112).
import { useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { formatDateTimePtBr } from "@agrofacil/domain";
import { api, unwrap } from "../api/client";
import { useAuth } from "../auth/useAuth";
import { Logo } from "../components/Logo";
import headerHills from "../assets/figma/header-hills.png";
import sunSvg from "../assets/figma/sun.svg";
import bellSvg from "../assets/figma/bell.svg";
import plusCircleSvg from "../assets/figma/plus-circle.svg";
import navHomeSvg from "../assets/figma/nav-home.svg";
import navPropertiesSvg from "../assets/figma/nav-properties.svg";
import navRecordsSvg from "../assets/figma/nav-records.svg";
import navProfileSvg from "../assets/figma/nav-profile.svg";

type NavItem = {
  to: string;
  label: string;
  icon?: string;
  end?: boolean;
};

const PRIMARY_NAV: NavItem[] = [
  { to: "/", label: "Início (Painel)", icon: navHomeSvg, end: true },
  { to: "/agricultores", label: "Agricultores", icon: navProfileSvg },
  { to: "/propriedades", label: "Propriedades", icon: navPropertiesSvg },
  { to: "/registros", label: "Registros de Manejo", icon: navRecordsSvg },
  { to: "/certificacoes", label: "Certificações" },
  { to: "/grupos", label: "Grupos do SPG" },
];

const SPG_NAV: NavItem[] = [
  { to: "/visitas", label: "Visitas do SPG" },
  { to: "/documentos", label: "Documentos (D1–D8)" },
  { to: "/spg", label: "Pessoas e Impedimentos" },
];

const MANAGEMENT_NAV: NavItem[] = [
  { to: "/comunicados", label: "Comunicados e Dicas" },
  { to: "/catalogo", label: "Catálogos e Roteiro" },
  { to: "/relatorios", label: "Relatórios (PDF/CSV)" },
  { to: "/configuracoes", label: "Configurações" },
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  const notificationsQuery = useQuery({
    queryKey: ["notifications", { unreadOnly: false }],
    queryFn: () =>
      unwrap(
        api.GET("/notifications", {
          params: {
            query: {
              unreadOnly: false,
              pageable: { page: 0, size: 25 },
            },
          },
        }),
      ),
    refetchInterval: 60_000,
  });

  const markAllReadMutation = useMutation({
    mutationFn: () => unwrap(api.POST("/notifications/read-all")),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const markOneReadMutation = useMutation({
    mutationFn: (id: string) =>
      unwrap(api.POST("/notifications/{id}/read", { params: { path: { id } } })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const notifications = notificationsQuery.data?.content ?? [];
  const unreadCount = notifications.filter((n) => !n.readAt).length;
  const firstName = user?.name?.split(" ")[0] ?? "Coordenador";

  return (
    <div className="af-shell">
      <aside className={`af-sidebar ${sidebarOpen ? "open" : ""}`}>
        <div className="af-sidebar-header">
          <Link to="/" onClick={() => setSidebarOpen(false)}>
            <Logo subtitle="Coordenação" size="md" />
          </Link>
        </div>

        <nav className="af-sidebar-nav" aria-label="Menu principal">
          <div className="af-nav-section-label">Operação</div>
          {PRIMARY_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) => `af-nav-item ${isActive ? "active" : ""}`}
            >
              {item.icon && (
                <img src={item.icon} alt="" style={{ width: 22, height: 22, opacity: 0.85 }} />
              )}
              <span>{item.label}</span>
            </NavLink>
          ))}

          <div className="af-nav-section-label">Certificação Participativa (SPG)</div>
          {SPG_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) => `af-nav-item ${isActive ? "active" : ""}`}
            >
              <span>{item.label}</span>
            </NavLink>
          ))}

          <div className="af-nav-section-label">Gestão da Associação</div>
          {MANAGEMENT_NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) => `af-nav-item ${isActive ? "active" : ""}`}
            >
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="af-sidebar-footer">
          <div style={{ minWidth: 0 }}>
            <div
              style={{
                fontSize: 13,
                fontWeight: 600,
                color: "var(--af-green-primary)",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {user?.name}
            </div>
            <div
              style={{
                fontSize: 11,
                color: "var(--af-text-muted)",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {user?.association?.name ?? "Associação SPG"}
            </div>
          </div>
          <button
            type="button"
            className="af-btn-soft"
            onClick={() => void logout()}
            title="Sair do painel"
          >
            Sair
          </button>
        </div>
      </aside>

      <div className="af-main-area">
        <header className="af-hero-banner" data-node-id="3:188">
          <img src={headerHills} alt="" className="af-hero-hills" />
          <img src={sunSvg} alt="" className="af-hero-sun" />

          <div className="af-hero-top">
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <button
                type="button"
                className="af-btn-outline af-mobile-menu-btn"
                onClick={() => setSidebarOpen((v) => !v)}
              >
                ☰ Menu
              </button>
              <div className="af-hero-greeting" data-node-id="45:788">
                <h1>Olá, {firstName}!</h1>
                <p>Vamos manter as certificações em dia?</p>
              </div>
            </div>

            <div className="af-hero-actions">
              {location.pathname !== "/registros/novo" && (
                <Link to="/registros/novo" className="af-btn-cta" data-node-id="14:12">
                  <img src={plusCircleSvg} alt="" />
                  <span>Registrar manejo</span>
                </Link>
              )}

              <button
                type="button"
                className="af-bell-btn"
                aria-label="Notificações"
                onClick={() => setNotificationsOpen(true)}
                data-node-id="3:443"
              >
                <img src={bellSvg} alt="Avisos" />
                {unreadCount > 0 && <span className="af-bell-badge">{unreadCount}</span>}
              </button>
            </div>
          </div>
        </header>

        <main className="af-page-sheet">
          <Outlet />
        </main>
      </div>

      {notificationsOpen && (
        <div className="af-modal-backdrop" onClick={() => setNotificationsOpen(false)}>
          <div className="af-modal" onClick={(e) => e.stopPropagation()} data-node-id="103:361">
            <div className="af-section-header">
              <h2 className="af-section-title">Notificações e Alertas</h2>
              <div style={{ display: "flex", gap: 8 }}>
                {unreadCount > 0 && (
                  <button
                    type="button"
                    className="af-btn-soft"
                    onClick={() => markAllReadMutation.mutate()}
                  >
                    Marcar todas como lidas
                  </button>
                )}
                <button
                  type="button"
                  className="af-btn-outline"
                  onClick={() => setNotificationsOpen(false)}
                >
                  Fechar
                </button>
              </div>
            </div>

            {notifications.length === 0 ? (
              <p style={{ color: "var(--af-text-muted)", fontSize: 14 }}>
                Nenhuma notificação registrada até o momento.
              </p>
            ) : (
              <div className="af-record-list">
                {notifications.map((n) => (
                  <div
                    key={n.id}
                    className="af-record-row"
                    style={{
                      backgroundColor: n.readAt ? "#ffffff" : "rgba(111, 175, 123, 0.12)",
                      alignItems: "flex-start",
                    }}
                  >
                    <div style={{ flex: 1 }}>
                      <div
                        style={{
                          fontWeight: 600,
                          color: "var(--af-green-primary)",
                          fontSize: 14,
                        }}
                      >
                        {n.title}
                      </div>
                      <div style={{ fontSize: 13, color: "#333", margin: "4px 0" }}>{n.body}</div>
                      <div style={{ fontSize: 11, color: "var(--af-text-muted)" }}>
                        {formatDateTimePtBr(n.createdAt)}
                      </div>
                    </div>
                    {!n.readAt && n.id && (
                      <button
                        type="button"
                        className="af-btn-soft"
                        onClick={() => markOneReadMutation.mutate(n.id!)}
                      >
                        Lida
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
