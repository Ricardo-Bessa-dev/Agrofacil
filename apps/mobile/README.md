# App do agricultor (PWA)

App web mobile-first e offline-first do agricultor, feito com Vite + React. Instalável no celular como PWA. As telas e textos seguem o [guia do front-end, Parte A](../../docs/3-guia-front-end.md#parte-a-experiência-do-agricultor-ux).

> O guia do front-end descreve a primeira proposta em Expo (React Native). O app foi construído como PWA; o que mudou está em [`docs/CONTINUIDADE-DO-PROJETO.md`](../../docs/CONTINUIDADE-DO-PROJETO.md).

## Comandos (na raiz do repositório)

- `pnpm --filter @agrofacil/mobile dev --host`: servidor de desenvolvimento em http://localhost:5174. O `--host` permite abrir no celular pelo IP do computador.
- `pnpm --filter @agrofacil/mobile build`: gera `dist/`, com o service worker (`sw.js`) e a lista de arquivos guardados para uso offline.
- `pnpm --filter @agrofacil/mobile test`: testes unitários (Vitest).

## Como funciona sem internet

- **Leitura:** a sincronização (`/sync/pull`) é incremental, pelo cursor, e o espelho fica no `localStorage` (`src/api/syncState.ts`).
- **Escrita:** registros e propriedades entram numa fila e são enviados por `/sync/push` quando a internet volta. O que a API recusa aparece num aviso (`src/api/pushResults.ts`).
- **Fotos:** ficam no IndexedDB até o arquivo chegar à API (`src/api/photoQueue.ts`).
- **Avisos:** aparecem só dentro do app, na tela de notificações. Não há push.

## Atenção

`crypto.subtle`, usado no hash das fotos, só existe em contexto seguro (HTTPS ou `localhost`). Pelo IP da rede local, o app abre, mas não envia fotos.
