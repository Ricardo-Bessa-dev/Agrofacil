// Configuração do Vite: repassa /api para a API local, para que painel e API fiquem na mesma origem (sem CORS).
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const API_DEV_URL = "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: { proxy: { "/api": API_DEV_URL } },
});
