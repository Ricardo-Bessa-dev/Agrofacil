# Guia de Continuidade do Projeto Agrofácil (Handoff para Agentes de IA)

Este documento consolida toda a arquitetura, decisões de produto, estado atual de implementação e backlog técnico do **Agrofácil** para orientar o desenvolvimento contínuo assistido por Inteligência Artificial em outro ambiente.

---

## 1. Visão Geral e Decisões Arquiteturais

O **Agrofácil** é um sistema de Caderno de Campo Digital e gestão de **SPG (Sistema Participativo de Garantia)** voltado para associações de agricultores familiares em produção orgânica.

### 1.1 Estrutura do Monorepo

- [`api/`](../api): Backend em **Java 21 + Spring Boot 4 + PostgreSQL + Flyway** (API REST completa em `/api/v1`, documentada via OpenAPI/SpringDoc).
- [`packages/api-types/`](../packages/api-types): Tipos TypeScript gerados automaticamente do contrato OpenAPI (`schema.ts`) e aliases ergonômicos de domínio (`index.ts`).
- [`packages/domain/`](../packages/domain): Regras de negócio puras compartilhadas entre web e mobile (validação/máscara de CPF, conversão de áreas Hectare/m²/Tarefa, cálculo de completude de registros de manejo, status efetivo de certificação, formatação de datas PT-BR e dicionários de rótulos).
- [`apps/web/`](../apps/web): **Painel da Coordenação** (Desktop-first responsivo), construído em **React 19 + Vite + TypeScript + React Router 7 + TanStack Query v5 + Leaflet**.
- [`apps/mobile/`](../apps/mobile): **Aplicativo do Agricultor** (Mobile-first para navegadores de celular e responsivo no desktop), construído em **React 19 + Vite + TypeScript** (decisão explícita de arquitetura: **Web App em React puro**, sem React Native/Expo, rodando na porta `5174`).
- [`docs/`](./): Documentação de referência original:
  - [`1-arquitetura-e-contrato.md`](1-arquitetura-e-contrato.md) (ADRs, modelo de dados, regras RN01–RN25 e especificação OpenAPI).
  - [`2-guia-api.md`](2-guia-api.md) (detalhes de implementação do backend Spring Boot).
  - [`3-guia-front-end.md`](3-guia-front-end.md) (padrões de cliente HTTP, autenticação e fluxos de tela).

### 1.2 Regras Fundamentais de Autenticação (ADR-03)

1. **Agricultor não faz auto-cadastro**: O coordenador cadastra o agricultor no painel web (`apps/web`) e gera um **código de ativação de 6 dígitos** (`POST /api/v1/farmers/{id}/access-code`).
2. **Ativação do Agricultor (`apps/mobile`)**: O agricultor acessa o navegador do celular e entra apenas com **CPF + código de 6 dígitos** (`POST /api/v1/auth/farmer/activate`), recebendo um `accessToken` e um `refreshToken` de longa duração (180 dias de inatividade), persistidos em `localStorage`.
3. **Autenticação do Coordenador (`apps/web`)**: Login em duas etapas (`POST /api/v1/auth/coordinator/login` com e-mail/senha seguido de `POST /api/v1/auth/coordinator/verify` com código 2FA de 6 dígitos enviado por e-mail). O `accessToken` fica em memória e o `refreshToken` em cookie `HttpOnly` (`af_refresh`), renovado via `POST /api/v1/auth/refresh` com header `X-Requested-With: XMLHttpRequest`.

---

## 2. O Que Foi Proposto vs. O Que Já Foi Feito

### Etapa 0 — Fundação Compartilhada (`packages/api-types` e `packages/domain`) — **CONCLUÍDA**

- **Proposto**: Expandir os pacotes compartilhados para centralizar tipagens OpenAPI e regras de negócio no cliente.
- **Implementado**:
  - [`packages/api-types/src/index.ts`](../packages/api-types/src/index.ts): Exportação completa de todos os schemas OpenAPI (`Me`, `FarmerSummary`, `FarmerDetail`, `PropertyDetail`, `PlotView`, `RecordListItem`, `RecordDetail`, `RecordWrite`, `CertificationView`, `VisitView`, `DocumentView`, `AnnouncementView`, `ConflictView`, etc.).
  - [`packages/domain/src/cpf.ts`](../packages/domain/src/cpf.ts): `normalizeCpf`/`onlyDigits`, `isValidCpf`, `formatCpf`, `maskCpf`.
  - [`packages/domain/src/units.ts`](../packages/domain/src/units.ts): Conversão entre `HECTARE`, `SQUARE_METER` e `TAREFA` (padrão baiano de $4.356\text{ m}^2$ configurável por associação) e formatação numérica `pt-BR`.
  - [`packages/domain/src/completeness.ts`](../packages/domain/src/completeness.ts): Verificação local de campos pendentes por tipo de manejo (`missingFields`, `isRecordComplete`).
  - [`packages/domain/src/certification.ts`](../packages/domain/src/certification.ts): Cálculo de status efetivo de certificação (`VALID`, `EXPIRING_SOON`, `EXPIRED`, `NONE`) e metadados de badge.
  - [`packages/domain/src/dates.ts`](../packages/domain/src/dates.ts): `formatDatePtBr`, `formatDateTimePtBr`, `todayIso`, `addOneYearIso`.
  - [`packages/domain/src/labels.ts`](../packages/domain/src/labels.ts): Dicionários completos em português para todos os enums da API.
  - Testes unitários em [`packages/domain/src/cpf.test.ts`](../packages/domain/src/cpf.test.ts) e [`packages/domain/src/domain.test.ts`](../packages/domain/src/domain.test.ts).

### Etapa 1 — Painel Web da Coordenação (`apps/web`) — **CONCLUÍDA**

- **Proposto**: Construir o painel completo para a coordenação com identidade visual inspirada no protótipo do Figma.
- **Implementado**:
  - **Cliente HTTP e Auth**: [`apps/web/src/api/client.ts`](../apps/web/src/api/client.ts) (com achatamento de query params aninhados para o formato plano do Spring Boot e refresh automático de token), [`apps/web/src/auth/AuthContext.tsx`](../apps/web/src/auth/AuthContext.tsx) e [`apps/web/src/auth/AuthPages.tsx`](../apps/web/src/auth/AuthPages.tsx) (Login, 2FA, Esqueci/Redefinir Senha, Aceitar Convite).
  - **Layout e Componentes**: [`apps/web/src/layout/AppLayout.tsx`](../apps/web/src/layout/AppLayout.tsx) (Sidebar com grupos de navegação, topbar com sino de notificações e perfil) e os componentes em [`apps/web/src/components/`](../apps/web/src/components).
  - **Dashboard** ([`DashboardPage.tsx`](../apps/web/src/features/dashboard/DashboardPage.tsx)): KPIs da associação, alertas de certificação, ciclos de visitas e atalhos rápidos.
  - **Agricultores** ([`FarmersPages.tsx`](../apps/web/src/features/farmers/FarmersPages.tsx)): Listagem paginada com filtros, cadastro/edição com busca de município IBGE, ficha detalhada com geração de **código de ativação de 6 dígitos**, gestão de co-participantes e dispositivos vinculados.
  - **Propriedades e Talhões** ([`PropertiesPages.tsx`](../apps/web/src/features/properties/PropertiesPages.tsx)): Listagem, mapa interativo Leaflet, criação de propriedade, CRUD de talhões (`plots`) e trabalhadores (`workers`).
  - **Caderno de Campo / Registros** ([`RecordsPages.tsx`](../apps/web/src/features/records/RecordsPages.tsx)): Listagem com filtros avançados, lançamento em nome do agricultor (RN16), visualização detalhada e abertura/resolução de **pedidos de correção**.
  - **Certificações e Grupos** ([`CertificationsPages.tsx`](../apps/web/src/features/certifications/CertificationsPages.tsx), [`GroupsPages.tsx`](../apps/web/src/features/groups/GroupsPages.tsx)): Emissão/renovação de certificados e gestão de grupos regionais.
  - **SPG — Visitas, Documentos e Pessoas** ([`SpgPages.tsx`](../apps/web/src/features/spg/SpgPages.tsx)): Agendamento e execução de visitas de pares/auditoria com checklist, controle de documentos obrigatórios (plano de manejo, termo de compromisso, ata) e gestão de colaboradores, comissão de avaliação e impedimentos/conflitos de interesse (RN19).
  - **Gestão — Comunicados, Catálogo, Relatórios e Configurações** ([`ManagementPages.tsx`](../apps/web/src/features/management/ManagementPages.tsx)): Publicação de avisos/dicas, catálogo oficial de culturas/insumos e resolução de itens livres ("unmapped"), exportação de relatórios e configurações da associação/coordenadores.

### Etapa 2 — Aplicativo Web Mobile-First do Agricultor (`apps/mobile`) — **CONCLUÍDA**

- **Proposto**: Criar a interface do agricultor no diretório `apps/mobile` utilizando React Web (para navegador de celular), fiel às telas do protótipo Figma.
- **Implementado**:
  - **Ativação por CPF + Código** ([`ActivatePage.tsx`](../apps/mobile/src/pages/ActivatePage.tsx) — Figma `54:1667`).
  - **Shell Mobile com Barra Inferior** ([`MobileShell.tsx`](../apps/mobile/src/layout/MobileShell.tsx)): 4 abas (_Início_, _Propriedades_, _Registros_, _Perfil_).
  - **Tela Inicial** ([`HomePage.tsx`](../apps/mobile/src/pages/HomePage.tsx) — Figma `3:188`): Header com ilustração das colinas e sol, saudação, badge do grupo SPG, botão "+ Registrar Manejo", card da propriedade principal, últimos manejos e mural de avisos da coordenação.
  - **Listagem do Caderno de Campo** ([`RecordsPage.tsx`](../apps/mobile/src/pages/RecordsPage.tsx) — Figma `49:852`): Chips de filtro por tipo (_Plantio_, _Aplicação_, _Colheita_, _Compra_, _Saída/Venda_), cartões temáticos, modal de detalhes e botão flutuante (FAB).
  - **Formulário de Novo Manejo** ([`NewRecordPage.tsx`](../apps/mobile/src/pages/NewRecordPage.tsx)): Seleção visual dos 5 tipos de manejo e campos dinâmicos integrados aos catálogos da API.
  - **Propriedades e Detalhes** ([`PropertiesPage.tsx`](../apps/mobile/src/pages/PropertiesPage.tsx) — Figma `49:1086` e [`PropertyDetailPage.tsx`](../apps/mobile/src/pages/PropertyDetailPage.tsx) — Figma `103:58`): Cards com capa ilustrativa, status de certificação, lista de talhões e trabalhadores familiares.
  - **Perfil e Notificações** ([`ProfilePage.tsx`](../apps/mobile/src/pages/ProfilePage.tsx) e [`NotificationsPage.tsx`](../apps/mobile/src/pages/NotificationsPage.tsx)): Dados do agricultor, botão de suporte direto no WhatsApp da associação, logout seguro e central de notificações.

---

## 3. Evoluções do Backlog Implementadas Nesta Etapa — **CONCLUÍDAS**

Todas as melhorias previstas no backlog técnico foram implementadas e validadas:

### Prioridade Alta (Funcionalidades de Produto no `apps/mobile`) — **CONCLUÍDA**

1. **Edição de Registros e Resposta a Pedidos de Correção no Mobile** (`CONCLUÍDO`):
   - Em [`RecordsPage.tsx`](../apps/mobile/src/pages/RecordsPage.tsx) e [`NewRecordPage.tsx`](../apps/mobile/src/pages/NewRecordPage.tsx), o agricultor agora visualiza os alertas de pedidos de correção da coordenação (`openCorrectionRequests > 0`) e os campos pendentes (`missingFields`), podendo editar qualquer registro em `/registros/:id/editar` (`PUT /api/v1/records/{id}` via `/sync/push`), resolvendo automaticamente o pedido de correção na API.
2. **Upload de Fotos / Anexos nos Registros Mobile** (`CONCLUÍDO`):
   - Integrado em [`NewRecordPage.tsx`](../apps/mobile/src/pages/NewRecordPage.tsx) e [`client.ts`](../apps/mobile/src/api/client.ts) (`uploadRecordPhoto`) com captura direta da câmera (`capture="environment"`), cálculo de hash `SHA-256`, sincronização do metadado via `POST /api/v1/sync/push` (`attachment`) e envio do binário via `PUT /api/v1/files/{id}/content`.
3. **Vínculo de Plantio Ativo (`plantingId`) nos Registros de Aplicação, Colheita e Saída** (`CONCLUÍDO`):
   - Em [`NewRecordPage.tsx`](../apps/mobile/src/pages/NewRecordPage.tsx), ao selecionar _Aplicação_, _Colheita_ ou _Saída_, os plantios ativos da propriedade são listados para vínculo direto (`plantingId`).
4. **Aceite de Termos de Uso (LGPD)** (`CONCLUÍDO`):
   - Em [`MobileShell.tsx`](../apps/mobile/src/layout/MobileShell.tsx), quando `user.termsAcceptedVersion !== user.currentTermsVersion`, exibe o modal de consentimento LGPD acionando `POST /api/v1/me/terms/accept`.

### Prioridade Média (PWA, Offline-First e Experiência Mobile) — **CONCLUÍDA**

5. **Transformar `apps/mobile` em PWA Instalável com Suporte Offline (ADR-02)** (`CONCLUÍDO`):
   - Adicionados [`manifest.webmanifest`](../apps/mobile/public/manifest.webmanifest) e Service Worker (modelo em [`sw-template.js`](../apps/mobile/sw/sw-template.js); o `sw.js` é gerado no build com a lista de arquivos para uso offline), além de persistência local do espelho `/sync/pull` e fila offline de mutações (`af_mobile_offline_queue`) em [`client.ts`](../apps/mobile/src/api/client.ts) com sincronização automática ao recuperar sinal.
6. **Atualização de Dados da Propriedade / Talhões pelo Próprio Agricultor** (`CONCLUÍDO`):
   - Em [`PropertyDetailPage.tsx`](../apps/mobile/src/pages/PropertyDetailPage.tsx), o agricultor pode atualizar a descrição da produção/talhões, área total e roteiro de acesso via `upsertFarmerProperty` (`/sync/push`).

### Prioridade Baixa (Qualidade, Performance e Testes) — **CONCLUÍDA**

7. **Code Splitting (Lazy Loading) no `apps/web` e `apps/mobile`** (`CONCLUÍDO`):
   - Aplicado `React.lazy()` + `<Suspense>` nas rotas de [`apps/web/src/App.tsx`](../apps/web/src/App.tsx) e [`apps/mobile/src/App.tsx`](../apps/mobile/src/App.tsx).
8. **Testes Automatizados e Correções de Integração Ponta a Ponta** (`CONCLUÍDO`):
   - Adicionados testes unitários cobrindo normalização `E.164`, catálogo completo dos 417 municípios da Bahia, formatação de erros de validação por campo e fila offline mobile.

---

## 3.1 Revisão de novembro de 2026

- **Sincronização incremental:** o app guarda o cursor do `/sync/pull` e pede só o que mudou, página a página ([`syncState.ts`](../apps/mobile/src/api/syncState.ts)). Mutações recusadas pela API saem da fila e aparecem num aviso ([`pushResults.ts`](../apps/mobile/src/api/pushResults.ts)).
- **Fotos offline:** ficam no IndexedDB ([`photoQueue.ts`](../apps/mobile/src/api/photoQueue.ts)) até o arquivo chegar à API; o detalhe do registro mostra as fotos enviadas e as que aguardam envio.
- **Avisos só no app:** o envio de push pelo Expo fica desligado (`PUSH_ENABLED=false`), porque o PWA não registra token de push.
- **RN41:** a soma das áreas não pode passar do tamanho da propriedade (API, painel e app).
- **Painel:** listas paginadas com o componente [`Pagination`](../apps/web/src/components/Pagination.tsx), avisos de erro das ações ([`Toaster`](../apps/web/src/components/Toaster.tsx)) e menus de seleção que buscam todas as páginas ([`pagination.ts`](../apps/web/src/api/pagination.ts)).
- **Municípios da Bahia** ficam em `@agrofacil/domain`, usados pelos dois apps.

## 4. Como Rodar e Validar o Projeto no Novo Ambiente

### Pré-requisitos

- **Node.js** `>= 22` e **pnpm** `>= 10`
- **Java 21** e **Docker / Docker Compose** (para rodar o PostgreSQL/PostGIS e a API Spring Boot)

### Comandos do Monorepo (Raiz)

```bash
# Instalar dependências de todos os pacotes
pnpm install

# Verificar tipos TypeScript em todos os pacotes (api-types, domain, web, mobile)
pnpm -r typecheck

# Executar linter (oxlint) em todos os pacotes
pnpm -r lint

# Executar testes unitários (vitest) em todos os pacotes
pnpm -r test

# Gerar tipos novamente caso o contrato OpenAPI da API mude (requer API rodando em localhost:8080)
pnpm gen:api
```

### Rodando os Serviços em Desenvolvimento

```bash
# 1. Subir banco de dados e e-mail de teste (PostgreSQL + Mailpit)
docker compose -f infra/docker-compose.dev.yml up -d

# 2. Subir a API Backend (porta 8080)
cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Subir o Painel da Coordenação (porta 5173)
pnpm --filter @agrofacil/web dev

# 4. Subir o Aplicativo Mobile do Agricultor (porta 5174)
pnpm --filter @agrofacil/mobile dev --host
```

---

## 5. Convenções de Código para o Próximo Agente de IA

- **Idioma**: Todas as respostas ao usuário, comentários de código, mensagens de erro e rótulos de interface devem estar em **Português (pt-BR)**.
- **Tipagem Estrita**: Nunca crie interfaces manuais duplicando o contrato do backend; importe sempre de `@agrofacil/api-types`.
- **Regras Compartilhadas**: Qualquer regra de cálculo, formatação ou validação usada tanto pelo coordenador quanto pelo agricultor deve residir em `@agrofacil/domain` com testes unitários correspondentes.
- **Serialização de Query Params**: O Spring Boot espera parâmetros de filtro e paginação achatados na query string (`?page=0&size=20&type=PLANTING`), o que já é tratado automaticamente pelo `serializeSpringQuery` em `src/api/client.ts` de ambos os apps.
- **Verificação Obrigatória**: Antes de concluir qualquer tarefa, execute sempre `pnpm -r typecheck && pnpm -r lint && pnpm -r test`.
