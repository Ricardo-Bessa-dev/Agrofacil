import { lazy, Suspense } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./auth/AuthContext";
import { ProtectedRoute, PublicOnlyRoute } from "./auth/ProtectedRoute";
import { MobileShell } from "./layout/MobileShell";
import "./styles/theme.css";

const ActivatePage = lazy(() =>
  import("./pages/ActivatePage").then((m) => ({ default: m.ActivatePage })),
);
const HomePage = lazy(() => import("./pages/HomePage").then((m) => ({ default: m.HomePage })));
const PropertiesPage = lazy(() =>
  import("./pages/PropertiesPage").then((m) => ({ default: m.PropertiesPage })),
);
const PropertyDetailPage = lazy(() =>
  import("./pages/PropertyDetailPage").then((m) => ({ default: m.PropertyDetailPage })),
);
const RecordsPage = lazy(() =>
  import("./pages/RecordsPage").then((m) => ({ default: m.RecordsPage })),
);
const NewRecordPage = lazy(() =>
  import("./pages/NewRecordPage").then((m) => ({ default: m.NewRecordPage })),
);
const ProfilePage = lazy(() =>
  import("./pages/ProfilePage").then((m) => ({ default: m.ProfilePage })),
);
const NotificationsPage = lazy(() =>
  import("./pages/NotificationsPage").then((m) => ({ default: m.NotificationsPage })),
);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

function MobileRouteFallback() {
  return (
    <div
      style={{
        padding: "32px 16px",
        textAlign: "center",
        fontSize: "13px",
        color: "var(--color-text-muted)",
      }}
    >
      Carregando tela...
    </div>
  );
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Suspense fallback={<MobileRouteFallback />}>
            <Routes>
              {/* Rota pública de ativação */}
              <Route element={<PublicOnlyRoute />}>
                <Route path="/login" element={<ActivatePage />} />
              </Route>

              {/* Rotas autenticadas do agricultor */}
              <Route element={<ProtectedRoute />}>
                {/* Abas com Bottom Navigation */}
                <Route element={<MobileShell />}>
                  <Route path="/" element={<HomePage />} />
                  <Route path="/propriedades" element={<PropertiesPage />} />
                  <Route path="/registros" element={<RecordsPage />} />
                  <Route path="/perfil" element={<ProfilePage />} />
                </Route>

                {/* Telas internas sem bottom nav para melhor aproveitamento do espaço */}
                <Route path="/registros/novo" element={<NewRecordPage />} />
                <Route path="/registros/:id/editar" element={<NewRecordPage />} />
                <Route path="/propriedades/:id" element={<PropertyDetailPage />} />
                <Route path="/notificacoes" element={<NotificationsPage />} />
              </Route>

              {/* Redirecionamento padrão */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Suspense>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
