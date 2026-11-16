import { NavLink, Outlet, useLocation } from "react-router";
import navHome from "../assets/figma/nav-home.svg";
import navHomeActive from "../assets/figma/nav-home-active.svg";
import navProperties from "../assets/figma/nav-properties.svg";
import navPropertiesActive from "../assets/figma/nav-properties-active.svg";
import navRecords from "../assets/figma/nav-records.svg";
import navRecordsActive from "../assets/figma/nav-records-active.svg";
import navProfile from "../assets/figma/nav-profile.svg";

export function MobileShell() {
  const location = useLocation();

  const isHomeActive = location.pathname === "/";
  const isPropertiesActive = location.pathname.startsWith("/propriedades");
  const isRecordsActive = location.pathname.startsWith("/registros");
  const isProfileActive = location.pathname.startsWith("/perfil");

  return (
    <div className="mobile-app-container">
      <main className="mobile-shell-content">
        <Outlet />
      </main>

      <nav className="bottom-nav" aria-label="Navegação inferior">
        <NavLink
          to="/"
          className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}
        >
          <span className="bottom-nav-icon">
            <img
              src={isHomeActive ? navHomeActive : navHome}
              alt=""
              width={22}
              height={22}
            />
          </span>
          <span>Início</span>
        </NavLink>

        <NavLink
          to="/propriedades"
          className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}
        >
          <span className="bottom-nav-icon">
            <img
              src={isPropertiesActive ? navPropertiesActive : navProperties}
              alt=""
              width={22}
              height={22}
            />
          </span>
          <span>Propriedades</span>
        </NavLink>

        <NavLink
          to="/registros"
          className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}
        >
          <span className="bottom-nav-icon">
            <img
              src={isRecordsActive ? navRecordsActive : navRecords}
              alt=""
              width={22}
              height={22}
            />
          </span>
          <span>Registros</span>
        </NavLink>

        <NavLink
          to="/perfil"
          className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}
        >
          <span className="bottom-nav-icon">
            <img
              src={navProfile}
              alt=""
              width={22}
              height={22}
              style={{
                filter: isProfileActive
                  ? "invert(36%) sepia(87%) saturate(471%) hue-rotate(86deg) brightness(91%) contrast(92%)"
                  : "none",
              }}
            />
          </span>
          <span>Perfil</span>
        </NavLink>
      </nav>
    </div>
  );
}
