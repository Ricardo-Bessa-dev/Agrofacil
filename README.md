# Agrofácil

Caderno de campo digital para agricultores orgânicos de uma cooperativa certificada pelo Sistema Participativo de Garantia (SPG). Projeto acadêmico do IFBA.

- **App do agricultor** (`apps/mobile`): React 19 + Vite (PWA Offline-First), funciona mesmo sem internet no campo.
- **Painel do coordenador** (`apps/web`): React 19 + Vite + TanStack Query.
- **API** (`api/`): Spring Boot 4 + PostgreSQL.

A documentação completa está em [`docs/`](docs/). Comece pelo [doc 1](docs/1-arquitetura-e-contrato.md) e veja o resumo de implementação em [`docs/CONTINUIDADE-DO-PROJETO.md`](docs/CONTINUIDADE-DO-PROJETO.md).

## Estrutura

```text
api/                  # API Spring Boot (Maven), fora do workspace pnpm
apps/mobile/          # @agrofacil/mobile (App Web/PWA do agricultor na porta 5174)
apps/web/             # @agrofacil/web (Painel da coordenação na porta 5173)
packages/api-types/   # tipos gerados do OpenAPI da API (pnpm gen:api)
packages/domain/      # regras puras compartilhadas pelo app e pelo painel
infra/                # Docker Compose de desenvolvimento
docs/                 # arquitetura, contrato API-front, guias da API e do front
```

## Pré-requisitos

- JDK 21, Docker e Node 22.
- pnpm pelo Corepack: rode `corepack enable` uma vez (a versão vem do `package.json`).

## Rodar no desenvolvimento

1. **API:**
   ```bash
   cp api/.env.example api/.env        # preencha JWT_SECRET (openssl rand -base64 48)
   cd api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```
   O Spring Boot sobe o Postgres e o Mailpit de `infra/docker-compose.dev.yml` sozinho.
   - Health check: http://localhost:8080/api/v1/actuator/health
   - Swagger: http://localhost:8080/api/v1/docs
   - E-mails de teste: http://localhost:8025
2. **Front:**
   ```bash
   pnpm install
   pnpm gen:api                         # com a API rodando: atualiza packages/api-types
   pnpm --filter @agrofacil/web dev     # painel em http://localhost:5173
   pnpm --filter @agrofacil/mobile start
   ```

## Estado da API

A API cobre os marcos **M0 a M10** do [doc 2 §1.18](documentacao/2-guia-api.md#118-ordem-de-implementação-sugerida) e parte do M11:

- **M0–M6:** banco (migrações Flyway + auditoria com Envers), autenticação (2FA do coordenador, ativação do agricultor, refresh rotativo), associação, coordenadores, grupos, agricultores, aparelhos, `/me`, propriedades/áreas/trabalhadores, registros de manejo com completude e histórico, pedidos de correção, notificações, anexos e sincronização (`/sync/push` e `/sync/pull`).
- **M7:** cadastro, renovação, suspensão e cancelamento de certificações; rotina diária de alertas de vencimento (07:00) com notificação, push e e-mail.
- **M8:** comunicados (todos ou um grupo, também no pull), gestão de culturas, insumos e unidades, e "itens sem catálogo".
- **M9:** painel inicial (`/dashboard/summary`) e relatórios em PDF/CSV (caderno de campo, agricultores, certificações e exportação de registros).
- **M10:** colaboradores, Comissão de Avaliação e impedimentos; roteiro da visita; situação das visitas (RN29); sugestão de visitadores; visitas pelo painel e resultado pelo app (mutação `visit`); rotina de avisos (07:05); documentos D1–D8 com código de verificação e cópia assinada.
- **M11 (parcial):** rotina mensal de retenção (apaga códigos, tokens e mutações de sync vencidos), revisão dos logs e teste dos cabeçalhos de segurança.

Ainda falta no M11: a anonimização dos agricultores desligados (precisa da data de desligamento e de um relatório de simulação para o coordenador conferir, doc 1 §5.7) e o teste do backup (doc 2, Parte 3).

## Estado do front

O painel (`apps/web`) e o app mobile-first do agricultor (`apps/mobile`) estão completos e integrados à API:

- **Painel da Coordenação (`apps/web`)**: Login em duas etapas (2FA), Dashboard com KPIs, gestão de agricultores e geração de códigos de 6 dígitos, propriedades e talhões, caderno de campo e pedidos de correção, certificações, grupos, visitas do SPG, documentos D1–D8, comunicados, catálogos, relatórios PDF/CSV e configurações.
- **App do Agricultor (`apps/mobile`)**: Ativação por CPF + código de 6 dígitos, tela inicial, propriedades e edição de dados de produção/acesso, listagem e filtro do caderno de campo, criação e edição de manejos com vínculo de plantio ativo, atendimento a pedidos de correção, captura/upload de fotos da câmera (`SHA-256`), aceite de termos LGPD e suporte PWA offline-first.

Consulte o detalhamento em [`docs/CONTINUIDADE-DO-PROJETO.md`](docs/CONTINUIDADE-DO-PROJETO.md) e no [doc 3, Parte E](docs/3-guia-front-end.md#parte-e-ordem-de-implementação-do-front).

### Primeiro teste manual

1. Em `api/.env`, defina `JWT_SECRET` e `BOOTSTRAP_COORDINATOR_EMAIL` e suba a API (passo 1 acima). O convite chega no Mailpit (http://localhost:8025); no perfil `dev` o link também aparece no log.
2. Aceite o convite: `POST /api/v1/auth/invite/accept` com `{ "token", "name", "password" }` (senha com 10+ caracteres).
3. Entre: `POST /auth/coordinator/login` com `{ "email", "password" }` e depois `POST /auth/coordinator/verify` com o `challengeId` e o código do e-mail. Use o `accessToken` no botão **Authorize** do Swagger.
4. Cadastre um agricultor (`POST /farmers`), gere o código (`POST /farmers/{id}/access-codes`) e ative um "aparelho" com `POST /auth/farmer/activate`.
5. Com o token do agricultor, crie uma propriedade e um plantio por `POST /sync/push` e confira o resultado em `GET /sync/pull?cursor=0` (exemplo completo no [doc 1 §3.19](docs/1-arquitetura-e-contrato.md#319-exemplo-completo-colheita-registrada-no-campo)).
6. Com o token do coordenador, experimente os outros marcos (os campos de cada rota estão no Swagger):
   - **M7:** `POST /certifications`, depois `/renew`, `/suspend` e `/reactivate`.
   - **M8:** `POST /announcements` (aparece no pull do agricultor); um registro com `cropOtherName` aparece em `GET /catalog/unmapped?kind=CROP` e se resolve com `POST /catalog/unmapped/resolve`.
   - **M9:** `GET /dashboard/summary` e os relatórios em `/reports/*` (PDF e CSV).
   - **M10:** `POST /collaborators`, `POST /visits/suggest-visitors`, `POST /visits`, `PUT /visits/{id}/result`; documentos com `POST /documents`, `GET /documents/{id}/content` e `GET /documents/verify/{code}`.

As rotinas de alerta só rodam no horário (07:00 e 07:05, horário da Bahia); com a API no ar nesse horário, os avisos chegam no Mailpit. Fora disso, elas são cobertas pelos testes automáticos.

## Verificações (as mesmas do CI)

```bash
cd api && ./mvnw verify               # formatação (Spotless) + testes com Testcontainers
pnpm format:check && pnpm lint && pnpm typecheck && pnpm test
```

Para rodar só uma classe de teste: `./mvnw test -Dtest=CertificationTest` (aceita várias, separadas por vírgula). Os testes de integração precisam do Docker aberto.

`./mvnw spotless:apply` e `pnpm format` corrigem a formatação.

## Como trabalhar

- Siga a ordem de implementação: API no [doc 2 §1.18](docs/2-guia-api.md#118-ordem-de-implementação-sugerida), front no [doc 3, Parte E](docs/3-guia-front-end.md#parte-e-ordem-de-implementação-do-front).
- Mudou um campo, uma rota ou uma regra? Atualize o [doc 1](docs/1-arquitetura-e-contrato.md) **no mesmo pull request**.
- Nunca versione `.env`. Os modelos estão em `api/.env.example` e `apps/mobile/.env.example`.
