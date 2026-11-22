// Configuração do Vite para o aplicativo mobile-first do agricultor (PWA offline-first, ADR-02).
import { createHash } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

const API_DEV_URL = "http://localhost:8080";
const SW_TEMPLATE = resolve(import.meta.dirname, "sw/sw-template.js");
/** Tamanho do trecho do hash usado no nome do cache. */
const CACHE_VERSION_LENGTH = 12;

/**
 * Gera dist/sw.js a partir de sw/sw-template.js com a lista de TODOS os arquivos do build e da pasta
 * public. Assim o app inteiro fica guardado já na instalação, e não só as telas que o agricultor abriu.
 */
function serviceWorkerPrecache(): Plugin {
  let publicDir = "";
  return {
    name: "agrofacil-sw-precache",
    apply: "build",
    configResolved(config) {
      publicDir = config.publicDir;
    },
    generateBundle(_options, bundle) {
      const hash = createHash("sha256");
      const bundled = Object.keys(bundle).filter((file) => !file.endsWith(".map"));
      const publicFiles = publicDir ? listFiles(publicDir) : [];
      for (const file of publicFiles) {
        hash.update(file).update(readFileSync(file));
      }
      // O index.html ainda não está no bundle nesta etapa (o Vite o gera depois), mas é a base do offline.
      const urls = [
        ...new Set([
          "/index.html",
          ...bundled.map((file) => `/${file}`),
          ...publicFiles.map((file) => `/${relative(publicDir, file).split("\\").join("/")}`),
        ]),
      ].sort();
      hash.update(urls.join("\n"));

      const source = readFileSync(SW_TEMPLATE, "utf8")
        .replace("__PRECACHE_URLS__", JSON.stringify(urls, null, 2))
        .replace("__CACHE_VERSION__", hash.digest("hex").slice(0, CACHE_VERSION_LENGTH));
      this.emitFile({ type: "asset", fileName: "sw.js", source });
    },
  };
}

function listFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? listFiles(path) : [path];
  });
}

export default defineConfig({
  plugins: [react(), serviceWorkerPrecache()],
  preview: { proxy: { "/api": API_DEV_URL } },
  server: {
    port: 5174,
    proxy: { "/api": API_DEV_URL },
  },
});
