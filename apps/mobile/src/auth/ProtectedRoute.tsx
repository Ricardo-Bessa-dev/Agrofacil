import { Navigate, Outlet } from "react-router";
import { useAuth } from "./useAuth";
import logoMark from "../assets/figma/logo-mark.png";

export function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div
        className="mobile-app-container"
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          minHeight: "100vh",
          backgroundColor: "#ffffff",
        }}
      >
        <img
          src={logoMark}
          alt="Agrofácil"
          style={{ width: "64px", height: "64px", marginBottom: "16px" }}
        />
        <div style={{ fontSize: "14px", fontWeight: 600, color: "var(--color-primary)" }}>
          Carregando Agrofácil...
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}

export function PublicOnlyRoute() {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return null;
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
