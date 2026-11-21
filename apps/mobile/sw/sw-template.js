// Service Worker offline-first do app do agricultor (ADR-02). Modelo: o build troca os marcadores
// de PRECACHE_URLS e CACHE_NAME pela lista de arquivos do app e por um hash dela (vite.config.ts).

/** Todos os arquivos do app (HTML, JS, CSS, imagens, ícones), guardados já na instalação. */
const PRECACHE_URLS = __PRECACHE_URLS__;
/** Muda a cada build com arquivos diferentes; o cache antigo é apagado na ativação. */
const CACHE_NAME = "agrofacil-mobile-__CACHE_VERSION__";
/** Sem resposta da rede nesse tempo (ms), a página abre pelo cache. Sinal fraco no campo não pode travar o app. */
const NAVIGATION_TIMEOUT_MS = 4000;
/**
 * ignoreVary: servidores costumam responder "Vary: Origin", e os scripts de módulo pedem com Origin;
 * sem isso o arquivo guardado na instalação (pedido sem Origin) não casa e o app abre em branco offline.
 * Os arquivos têm hash no nome, então ignorar o Vary é seguro.
 */
const MATCH_OPTIONS = { ignoreVary: true };

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  const url = new URL(req.url);

  // A API tem o próprio esquema offline (espelho do /sync/pull e fila de envio no app).
  if (
    req.method !== "GET" ||
    url.origin !== self.location.origin ||
    url.pathname.startsWith("/api/")
  ) {
    return;
  }

  // Telas (navegação): rede com prazo; sem rede, o index.html do cache (o roteamento é no cliente).
  if (req.mode === "navigate") {
    event.respondWith(
      withTimeout(fetch(req), NAVIGATION_TIMEOUT_MS).catch(() =>
        caches.match("/index.html", MATCH_OPTIONS).then((cached) => cached || Response.error()),
      ),
    );
    return;
  }

  // Arquivos do build têm hash no nome e não mudam: cache primeiro, rede só se faltar.
  event.respondWith(
    caches.match(req, MATCH_OPTIONS).then(
      (cached) =>
        cached ||
        fetch(req).then((res) => {
          if (res.ok) {
            const copy = res.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(req, copy));
          }
          return res;
        }),
    ),
  );
});

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timeout")), ms);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      },
    );
  });
}
