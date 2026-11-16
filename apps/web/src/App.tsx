// Roteador principal e provedores (QueryClient + AuthProvider) do painel do coordenador AgroFácil (apps/web).
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
import { DashboardPage } from "./features/dashboard/DashboardPage";
import {
  FarmerDetailPage,
  FarmerFormPage,
  FarmersListPage,
} from "./features/farmers/FarmersPages";
import {
  PropertiesListPage,
  PropertyDetailPage,
} from "./features/properties/PropertiesPages";
import {
  RecordCreatePage,
  RecordDetailPage,
  RecordsListPage,
} from "./features/records/RecordsPages";
import { CertificationsPage } from "./features/certifications/CertificationsPages";
import { GroupsPage } from "./features/groups/GroupsPages";
import { DocumentsPage, SpgPeoplePage, VisitsPage } from "./features/spg/SpgPages";
import {
  AnnouncementsPage,
  CatalogPage,
  ReportsPage,
  SettingsPage,
} from "./features/management/ManagementPages";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

function RequireCoordinator({ children }: { children: React.ReactNode }) {
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

export function AppRoutes() {
  return (
    <Routes>
      {/* Rotas públicas de autenticação do coordenador */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/login/verificar" element={<Verify2faPage />} />
      <Route path="/esqueci-a-senha" element={<ForgotPasswordPage />} />
      <Route path="/redefinir-senha" element={<ResetPasswordPage />} />
      <Route path="/convite" element={<AcceptInvitePage />} />

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
