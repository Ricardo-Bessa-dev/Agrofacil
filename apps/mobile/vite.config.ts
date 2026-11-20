// Configuração do Vite para o aplicativo mobile-first do agricultor
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const API_DEV_URL = "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: { "/api": API_DEV_URL },
  },
});
