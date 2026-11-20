// Roteador principal e provedores (QueryClient + AuthProvider) do painel do coordenador AgroFácil (apps/web) com Code Splitting (React.lazy).
import { lazy, Suspense, type ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { AuthProvider } from "./auth/AuthContext";
import { useAuth } from "./auth/useAuth";
import {
  AcceptInvitePage,
  ForgotPasswordPage,
  LoginPage,
  ResetPasswordPage,
  Verify2faPage,
} from "./auth/AuthPages";
import { AppLayout } from "./layout/AppLayout";

const DashboardPage = lazy(() =>
  import("./features/dashboard/DashboardPage").then((m) => ({ default: m.DashboardPage })),
);
const FarmersListPage = lazy(() =>
  import("./features/farmers/FarmersPages").then((m) => ({ default: m.FarmersListPage })),
);
const FarmerFormPage = lazy(() =>
  import("./features/farmers/FarmersPages").then((m) => ({ default: m.FarmerFormPage })),
);
const FarmerDetailPage = lazy(() =>
  import("./features/farmers/FarmersPages").then((m) => ({ default: m.FarmerDetailPage })),
);
const PropertiesListPage = lazy(() =>
  import("./features/properties/PropertiesPages").then((m) => ({
    default: m.PropertiesListPage,
  })),
);
const PropertyDetailPage = lazy(() =>
  import("./features/properties/PropertiesPages").then((m) => ({
    default: m.PropertyDetailPage,
  })),
);
const RecordsListPage = lazy(() =>
  import("./features/records/RecordsPages").then((m) => ({ default: m.RecordsListPage })),
);
const RecordCreatePage = lazy(() =>
  import("./features/records/RecordsPages").then((m) => ({ default: m.RecordCreatePage })),
);
const RecordDetailPage = lazy(() =>
  import("./features/records/RecordsPages").then((m) => ({ default: m.RecordDetailPage })),
);
const CertificationsPage = lazy(() =>
  import("./features/certifications/CertificationsPages").then((m) => ({
    default: m.CertificationsPage,
  })),
);
const GroupsPage = lazy(() =>
  import("./features/groups/GroupsPages").then((m) => ({ default: m.GroupsPage })),
);
const VisitsPage = lazy(() =>
  import("./features/spg/SpgPages").then((m) => ({ default: m.VisitsPage })),
);
const DocumentsPage = lazy(() =>
  import("./features/spg/SpgPages").then((m) => ({ default: m.DocumentsPage })),
);
const SpgPeoplePage = lazy(() =>
  import("./features/spg/SpgPages").then((m) => ({ default: m.SpgPeoplePage })),
);
const AnnouncementsPage = lazy(() =>
  import("./features/management/ManagementPages").then((m) => ({
    default: m.AnnouncementsPage,
  })),
);
const CatalogPage = lazy(() =>
  import("./features/management/ManagementPages").then((m) => ({ default: m.CatalogPage })),
);
const ReportsPage = lazy(() =>
  import("./features/management/ManagementPages").then((m) => ({ default: m.ReportsPage })),
);
const SettingsPage = lazy(() =>
  import("./features/management/ManagementPages").then((m) => ({ default: m.SettingsPage })),
);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

function RequireCoordinator({ children }: { children: ReactNode }) {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div
        style={{
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "var(--af-bg-cream)",
          color: "var(--af-green-darker)",
          fontWeight: 600,
        }}
      >
        Carregando AgroFácil...
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

function PageLoader() {
  return <div className="af-empty-state">Carregando módulo...</div>;
}

export function AppRoutes() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        {/* Rotas públicas de autenticação do coordenador */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/entrar" element={<LoginPage />} />
        <Route path="/login/verificar" element={<Verify2faPage />} />
        <Route path="/entrar/codigo" element={<Verify2faPage />} />
        <Route path="/esqueci-a-senha" element={<ForgotPasswordPage />} />
        <Route path="/esqueci-senha" element={<ForgotPasswordPage />} />
        <Route path="/redefinir-senha" element={<ResetPasswordPage />} />
        <Route path="/convite" element={<AcceptInvitePage />} />
        <Route path="/aceitar-convite" element={<AcceptInvitePage />} />

        {/* Rotas autenticadas dentro do AppShell */}
        <Route
          element={
            <RequireCoordinator>
              <AppLayout />
            </RequireCoordinator>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="agricultores" element={<FarmersListPage />} />
          <Route path="agricultores/novo" element={<FarmerFormPage />} />
          <Route path="agricultores/:id" element={<FarmerDetailPage />} />

          <Route path="propriedades" element={<PropertiesListPage />} />
          <Route path="propriedades/:id" element={<PropertyDetailPage />} />

          <Route path="registros" element={<RecordsListPage />} />
          <Route path="registros/novo" element={<RecordCreatePage />} />
          <Route path="registros/:id" element={<RecordDetailPage />} />

          <Route path="certificacoes" element={<CertificationsPage />} />
          <Route path="certificacoes/nova" element={<CertificationsPage />} />
          <Route path="grupos" element={<GroupsPage />} />

          <Route path="visitas" element={<VisitsPage />} />
          <Route path="documentos" element={<DocumentsPage />} />
          <Route path="spg" element={<SpgPeoplePage />} />

          <Route path="comunicados" element={<AnnouncementsPage />} />
          <Route path="catalogo" element={<CatalogPage />} />
          <Route path="relatorios" element={<ReportsPage />} />
          <Route path="configuracoes" element={<SettingsPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <AppRoutes />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
