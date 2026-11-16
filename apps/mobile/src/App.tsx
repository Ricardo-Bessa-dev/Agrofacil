import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./auth/AuthContext";
import { ProtectedRoute, PublicOnlyRoute } from "./auth/ProtectedRoute";
import { MobileShell } from "./layout/MobileShell";
import { ActivatePage } from "./pages/ActivatePage";
import { HomePage } from "./pages/HomePage";
import { PropertiesPage } from "./pages/PropertiesPage";
import { PropertyDetailPage } from "./pages/PropertyDetailPage";
import { RecordsPage } from "./pages/RecordsPage";
import { NewRecordPage } from "./pages/NewRecordPage";
import { ProfilePage } from "./pages/ProfilePage";
import { NotificationsPage } from "./pages/NotificationsPage";
import "./styles/theme.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
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
              <Route path="/propriedades/:id" element={<PropertyDetailPage />} />
              <Route path="/notificacoes" element={<NotificationsPage />} />
            </Route>

            {/* Redirecionamento padrão */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
