# Guia de Continuidade do Projeto Agrofácil (Handoff para Agentes de IA)

Este documento consolida toda a arquitetura, decisões de produto, estado atual de implementação e backlog técnico do **Agrofácil** para orientar o desenvolvimento contínuo assistido por Inteligência Artificial em outro ambiente.

---

## 1. Visão Geral e Decisões Arquiteturais

O **Agrofácil** é um sistema de Caderno de Campo Digital e gestão de **SPG (Sistema Participativo de Garantia)** voltado para associações de agricultores familiares em produção orgânica.

### 1.1 Estrutura do Monorepo
- [`api/`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/api): Backend em **Java 21 + Spring Boot 3.4 + PostgreSQL/PostGIS + Flyway** (API REST completa em `/api/v1`, documentada via OpenAPI/SpringDoc).
- [`packages/api-types/`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/api-types): Tipos TypeScript gerados automaticamente do contrato OpenAPI (`schema.ts`) e aliases ergonômicos de domínio (`index.ts`).
- [`packages/domain/`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/domain): Regras de negócio puras compartilhadas entre web e mobile (validação/máscara de CPF, conversão de áreas Hectare/m²/Tarefa, cálculo de completude de registros de manejo, status efetivo de certificação, formatação de datas PT-BR e dicionários de rótulos).
- [`apps/web/`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web): **Painel da Coordenação** (Desktop-first responsivo), construído em **React 19 + Vite + TypeScript + React Router 7 + TanStack Query v5 + Leaflet**.
- [`apps/mobile/`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile): **Aplicativo do Agricultor** (Mobile-first para navegadores de celular e responsivo no desktop), construído em **React 19 + Vite + TypeScript** (decisão explícita de arquitetura: **Web App em React puro**, sem React Native/Expo, rodando na porta `5174`).
- [`documentacao/`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/documentacao): Documentação de referência original:
  - [`1-arquitetura-e-contrato.md`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/documentacao/1-arquitetura-e-contrato.md) (ADRs, modelo de dados, regras RN01–RN25 e especificação OpenAPI).
  - [`2-guia-api.md`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/documentacao/2-guia-api.md) (detalhes de implementação do backend Spring Boot).
  - [`3-guia-front-end.md`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/documentacao/3-guia-front-end.md) (padrões de cliente HTTP, autenticação e fluxos de tela).

### 1.2 Regras Fundamentais de Autenticação (ADR-03)
1. **Agricultor não faz auto-cadastro**: O coordenador cadastra o agricultor no painel web (`apps/web`) e gera um **código de ativação de 6 dígitos** (`POST /api/v1/farmers/{id}/access-code`).
2. **Ativação do Agricultor (`apps/mobile`)**: O agricultor acessa o navegador do celular e entra apenas com **CPF + código de 6 dígitos** (`POST /api/v1/auth/farmer/activate`), recebendo um `accessToken` e um `refreshToken` de longa duração (180 dias de inatividade), persistidos em `localStorage`.
3. **Autenticação do Coordenador (`apps/web`)**: Login em duas etapas (`POST /api/v1/auth/coordinator/login` com e-mail/senha seguido de `POST /api/v1/auth/coordinator/verify` com código 2FA de 6 dígitos enviado por e-mail). O `accessToken` fica em memória e o `refreshToken` em cookie `HttpOnly` (`af_refresh`), renovado via `POST /api/v1/auth/refresh` com header `X-Requested-With: XMLHttpRequest`.

---

## 2. O Que Foi Proposto vs. O Que Já Foi Feito

### Etapa 0 — Fundação Compartilhada (`packages/api-types` e `packages/domain`) — **CONCLUÍDA**
- **Proposto**: Expandir os pacotes compartilhados para centralizar tipagens OpenAPI e regras de negócio no cliente.
- **Implementado**:
  - [`packages/api-types/src/index.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/api-types/src/index.ts): Exportação completa de todos os schemas OpenAPI (`Me`, `FarmerSummary`, `FarmerDetail`, `PropertyDetail`, `PlotView`, `RecordListItem`, `RecordDetail`, `RecordWrite`, `CertificationView`, `VisitView`, `DocumentView`, `AnnouncementView`, `ConflictView`, etc.).
  - [`packages/domain/src/cpf.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/domain/src/cpf.ts): `normalizeCpf`/`onlyDigits`, `isValidCpf`, `formatCpf`, `maskCpf`.
  - [`packages/domain/src/units.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/domain/src/units.ts): Conversão entre `HECTARE`, `SQUARE_METER` e `TAREFA` (padrão baiano de $4.356\text{ m}^2$ configurável por associação) e formatação numérica `pt-BR`.
  - [`packages/domain/src/completeness.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/domain/src/completeness.ts): Verificação local de campos pendentes por tipo de manejo (`missingFields`, `isRecordComplete`).
  - [`packages/domain/src/certification.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/domain/src/certification.ts): Cálculo de status efetivo de certificação (`VALID`, `EXPIRING_SOON`, `EXPIRED`, `NONE`) e metadados de badge.
  - [`packages/domain/src/dates.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/domain/src/dates.ts): `formatDatePtBr`, `formatDateTimePtBr`, `todayIso`, `addOneYearIso`.
  - [`packages/domain/src/labels.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/domain/src/labels.ts): Dicionários completos em português para todos os enums da API.
  - Testes unitários em [`packages/domain/src/cpf.test.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/domain/src/cpf.test.ts) e [`packages/domain/src/domain.test.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/packages/domain/src/domain.test.ts).

### Etapa 1 — Painel Web da Coordenação (`apps/web`) — **CONCLUÍDA**
- **Proposto**: Construir o painel completo para a coordenação com identidade visual inspirada no protótipo do Figma.
- **Implementado**:
  - **Cliente HTTP e Auth**: [`apps/web/src/api/client.ts`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/api/client.ts) (com achatamento de query params aninhados para o formato plano do Spring Boot e refresh automático de token), [`apps/web/src/auth/AuthContext.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/auth/AuthContext.tsx) e [`apps/web/src/auth/AuthPages.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/auth/AuthPages.tsx) (Login, 2FA, Esqueci/Redefinir Senha, Aceitar Convite).
  - **Layout e Componentes**: [`apps/web/src/layout/AppLayout.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/layout/AppLayout.tsx) (Sidebar com grupos de navegação, topbar com sino de notificações e perfil) e [`apps/web/src/components/ui.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/components/ui.tsx).
  - **Dashboard** ([`DashboardPage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/features/dashboard/DashboardPage.tsx)): KPIs da associação, alertas de certificação, ciclos de visitas e atalhos rápidos.
  - **Agricultores** ([`FarmersPages.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/features/farmers/FarmersPages.tsx)): Listagem paginada com filtros, cadastro/edição com busca de município IBGE, ficha detalhada com geração de **código de ativação de 6 dígitos**, gestão de co-participantes e dispositivos vinculados.
  - **Propriedades e Talhões** ([`PropertiesPages.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/features/properties/PropertiesPages.tsx)): Listagem, mapa interativo Leaflet, criação de propriedade, CRUD de talhões (`plots`) e trabalhadores (`workers`).
  - **Caderno de Campo / Registros** ([`RecordsPages.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/features/records/RecordsPages.tsx)): Listagem com filtros avançados, lançamento em nome do agricultor (RN16), visualização detalhada e abertura/resolução de **pedidos de correção**.
  - **Certificações e Grupos** ([`CertificationsPages.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/features/certifications/CertificationsPages.tsx), [`GroupsPages.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/features/groups/GroupsPages.tsx)): Emissão/renovação de certificados e gestão de grupos regionais.
  - **SPG — Visitas, Documentos e Pessoas** ([`SpgPages.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/features/spg/SpgPages.tsx)): Agendamento e execução de visitas de pares/auditoria com checklist, controle de documentos obrigatórios (plano de manejo, termo de compromisso, ata) e gestão de colaboradores, comissão de avaliação e impedimentos/conflitos de interesse (RN19).
  - **Gestão — Comunicados, Catálogo, Relatórios e Configurações** ([`ManagementPages.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/features/management/ManagementPages.tsx)): Publicação de avisos/dicas, catálogo oficial de culturas/insumos e resolução de itens livres ("unmapped"), exportação de relatórios e configurações da associação/coordenadores.

### Etapa 2 — Aplicativo Web Mobile-First do Agricultor (`apps/mobile`) — **CONCLUÍDA**
- **Proposto**: Criar a interface do agricultor no diretório `apps/mobile` utilizando React Web (para navegador de celular), fiel às telas do protótipo Figma.
- **Implementado**:
  - **Ativação por CPF + Código** ([`ActivatePage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/ActivatePage.tsx) — Figma `54:1667`).
  - **Shell Mobile com Barra Inferior** ([`MobileShell.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/layout/MobileShell.tsx)): 4 abas (*Início*, *Propriedades*, *Registros*, *Perfil*).
  - **Tela Inicial** ([`HomePage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/HomePage.tsx) — Figma `3:188`): Header com ilustração das colinas e sol, saudação, badge do grupo SPG, botão "+ Registrar Manejo", card da propriedade principal, últimos manejos e mural de avisos da coordenação.
  - **Listagem do Caderno de Campo** ([`RecordsPage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/RecordsPage.tsx) — Figma `49:852`): Chips de filtro por tipo (*Plantio*, *Aplicação*, *Colheita*, *Compra*, *Saída/Venda*), cartões temáticos, modal de detalhes e botão flutuante (FAB).
  - **Formulário de Novo Manejo** ([`NewRecordPage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/NewRecordPage.tsx)): Seleção visual dos 5 tipos de manejo e campos dinâmicos integrados aos catálogos da API.
  - **Propriedades e Detalhes** ([`PropertiesPage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/PropertiesPage.tsx) — Figma `49:1086` e [`PropertyDetailPage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/PropertyDetailPage.tsx) — Figma `103:58`): Cards com capa ilustrativa, status de certificação, lista de talhões e trabalhadores familiares.
  - **Perfil e Notificações** ([`ProfilePage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/ProfilePage.tsx) e [`NotificationsPage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/NotificationsPage.tsx)): Dados do agricultor, botão de suporte direto no WhatsApp da associação, logout seguro e central de notificações.

---

## 3. O Que Ainda Pode Ser Feito ou Melhorado (Backlog Recomendado)

Para as próximas iterações no novo ambiente de desenvolvimento, estas são as evoluções prioritárias identificadas:

### Prioridade Alta (Funcionalidades de Produto no `apps/mobile`)
1. **Edição de Registros e Resposta a Pedidos de Correção no Mobile**:
   - Atualmente o agricultor cria e visualiza registros em `apps/mobile`. Falta permitir que ele **edite um registro existente** (`PUT /api/v1/records/{id}`) especialmente quando `completeness === "INCOMPLETE"` ou quando houver um pedido de correção aberto pela coordenação (`openCorrectionRequests > 0`), marcando a correção como atendida.
2. **Upload de Fotos / Anexos nos Registros Mobile**:
   - Integrar os endpoints de anexo (`POST /api/v1/attachments/presign` ou upload multipart) no [`NewRecordPage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/NewRecordPage.tsx) para que o agricultor possa fotografar notas fiscais de insumos, sementes ou o desenvolvimento do plantio direto da câmera do celular (`<input type="file" accept="image/*" capture="environment" />`).
3. **Vínculo de Plantio Ativo (`plantingId`) nos Registros de Aplicação, Colheita e Saída**:
   - No [`NewRecordPage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/NewRecordPage.tsx), ao selecionar o tipo *Aplicação*, *Colheita* ou *Saída*, buscar os plantios ativos da propriedade/talhão (`GET /api/v1/records?type=PLANTING&cycleStatus=ACTIVE`) para permitir que o agricultor vincule a colheita ou aplicação diretamente a um plantio em andamento (`plantingId`).
4. **Aceite de Termos de Uso (LGPD)**:
   - Caso `user.termsAcceptedVersion !== user.currentTermsVersion` no retorno de `GET /api/v1/me`, exibir um modal ou tela de aceite chamando `POST /api/v1/me/terms/accept`.

### Prioridade Média (PWA, Offline-First e Experiência Mobile)
5. **Transformar `apps/mobile` em PWA Instalável com Suporte Offline (ADR-02)**:
   - Adicionar `manifest.webmanifest` e Service Worker (`vite-plugin-pwa`) para permitir "Adicionar à tela inicial" no Android/iOS.
   - Persistir o cache do TanStack Query em `IndexedDB` e criar uma fila local para submissão de manejos (`POST /api/v1/records/sync`) quando o agricultor estiver na roça sem sinal de internet.
6. **Cadastro/Edição de Talhões pelo Próprio Agricultor (Opcional)**:
   - Se a associação permitir, adicionar botão em [`PropertyDetailPage.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/mobile/src/pages/PropertyDetailPage.tsx) para o agricultor cadastrar um novo talhão ou atualizar observações.

### Prioridade Baixa (Qualidade, Performance e Testes)
7. **Code Splitting (Lazy Loading) no `apps/web` e `apps/mobile`**:
   - Utilizar `React.lazy()` nas rotas de [`apps/web/src/App.tsx`](file:///home/ariel_marquess/Ariel_Marques/Projetos/Agrofacil/apps/web/src/App.tsx) para dividir o bundle principal (hoje ~524 kB devido ao Leaflet e todas as páginas carregadas juntas) em múltiplos chunks menores.
8. **Testes End-to-End (Playwright) e Testes de Componentes**:
   - Adicionar `@testing-library/react` e `jsdom`/`happy-dom` para testar interações de formulário e criar uma suíte E2E simulando o fluxo completo: *Coordenador cria agricultor e gera código → Agricultor ativa celular → Agricultor registra plantio → Coordenador audita registro*.

---

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
# 1. Subir banco de dados e infraestrutura (PostgreSQL + PostGIS + MinIO + Mailpit)
docker compose -f infra/docker-compose.yml up -d

# 2. Subir a API Backend (porta 8080)
cd api && ./mvnw spring-boot:run

# 3. Subir o Painel da Coordenação (porta 5173)
pnpm --filter @agrofacil/web dev

# 4. Subir o Aplicativo Mobile do Agricultor (porta 5174)
pnpm --filter @agrofacil/mobile dev
```

---

## 5. Convenções de Código para o Próximo Agente de IA

- **Idioma**: Todas as respostas ao usuário, comentários de código, mensagens de erro e rótulos de interface devem estar em **Português (pt-BR)**.
- **Tipagem Estrita**: Nunca crie interfaces manuais duplicando o contrato do backend; importe sempre de `@agrofacil/api-types`.
- **Regras Compartilhadas**: Qualquer regra de cálculo, formatação ou validação usada tanto pelo coordenador quanto pelo agricultor deve residir em `@agrofacil/domain` com testes unitários correspondentes.
- **Serialização de Query Params**: O Spring Boot espera parâmetros de filtro e paginação achatados na query string (`?page=0&size=20&type=PLANTING`), o que já é tratado automaticamente pelo `serializeSpringQuery` em `src/api/client.ts` de ambos os apps.
- **Verificação Obrigatória**: Antes de concluir qualquer tarefa, execute sempre `pnpm -r typecheck && pnpm -r lint && pnpm -r test`.
