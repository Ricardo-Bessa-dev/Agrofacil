// Configuração do Vite para o aplicativo mobile-first do agricultor
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const API_DEV_URL = "http://localhost:8080";

export default defineConfig({
  plugins: [
    react(),
    {
      name: "agrofacil-offline",
      apply: "build",
      generateBundle: {
        order: "post",
        handler(_options, bundle) {
          const worker = readFileSync(new URL("./service-worker.js", import.meta.url), "utf8");
          const files = [
            ...Object.keys(bundle).map((name) => `/${name}`),
            "/manifest.webmanifest",
            "/icons/icon-192.png",
            "/icons/icon-512.png",
          ].sort();
          if (!files.includes("/index.html"))
            this.error("index.html ausente do aplicativo offline.");
          const hash = createHash("sha256").update(worker).update(JSON.stringify(files));
          for (const name of files) {
            const asset = bundle[name.slice(1)];
            if (asset?.type === "asset") hash.update(asset.source);
            else if (!asset) hash.update(readFileSync(new URL(`./public${name}`, import.meta.url)));
          }
          this.emitFile({
            type: "asset",
            fileName: "sw.js",
            source: worker
              .replace("__CACHE_NAME__", `agrofacil-mobile-${hash.digest("hex").slice(0, 16)}`)
              .replace("__APP_FILES__", JSON.stringify(files)),
          });
        },
      },
    },
  ],
  preview: { proxy: { "/api": API_DEV_URL } },
  server: {
    port: 5174,
    proxy: { "/api": API_DEV_URL },
  },
});
