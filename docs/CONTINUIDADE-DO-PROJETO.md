# Continuidade do Agrofácil

O Agrofácil é um caderno de campo digital e painel de gestão do Sistema Participativo de Garantia (SPG), desenvolvido no IFBA. As instruções para subir a aplicação estão no [README](../README.md).

## Implementação atual

| Caminho                                         | Responsabilidade                                                                       |
| ----------------------------------------------- | -------------------------------------------------------------------------------------- |
| [`api/`](../api/)                               | Java 21, Spring Boot 4, PostgreSQL, Flyway e auditoria com Envers.                     |
| [`apps/web/`](../apps/web/)                     | Painel da coordenação em React, Vite, React Router e TanStack Query.                   |
| [`apps/mobile/`](../apps/mobile/)               | PWA do agricultor em React e Vite, instalável pelo navegador.                          |
| [`packages/api-types/`](../packages/api-types/) | Contrato OpenAPI versionado e tipos TypeScript gerados.                                |
| [`packages/domain/`](../packages/domain/)       | Regras de domínio e formatação compartilhadas.                                         |
| [`infra/`](../infra/)                           | PostgreSQL e Mailpit no desenvolvimento. Scripts de backup, restauração e conferência. |

A primeira ativação do agricultor exige conexão e um código gerado pelo coordenador. Depois da carga inicial, o service worker serve as telas e arquivos estáticos do build sem sinal. O app persiste o espelho e as mutações no armazenamento do navegador, por conta. As fotos pendentes ficam no IndexedDB. Evite limpar os dados do site ou usar uma sessão privada enquanto houver envios pendentes.

O app busca mudanças com cursor, aplica exclusões e repete as páginas até terminar. No envio, processa lotes de até 100 mutações e confirma cada item pelo ID. A aba **Registros** e o botão **Sincronizar** permitem acompanhar recusas e fotos pendentes. As unidades de quantidade vêm do catálogo da API. Quando uma saída possui vínculo com plantio, a API deriva o produto a partir desse plantio.

O painel cadastra grupos, agricultores e propriedades, gera códigos de acesso e consulta os manejos enviados. O app também lista as visitas do SPG e permite ao responsável preencher e enviar o resultado. O painel registra presença e respostas explícitas para visitas preenchidas no papel.

## Validação e operação

- Rode `pnpm format:check`, `pnpm lint`, `pnpm typecheck`, `pnpm test` e `pnpm -r build` na raiz.
- Rode `./mvnw verify` em `api/` com Docker acessível para os testes com PostgreSQL real.
- O [CI](../.github/workflows/ci.yml) usa o navegador com a aplicação e o Mailpit para exercitar grupo, agricultor, propriedade e manejos offline. O mesmo job compara o OpenAPI e restaura o banco e os arquivos em destinos isolados.
- Em **Configurações → Retenção de dados**, a coordenação pode simular e confirmar a anonimização por agricultor. A rotina usa a data de desligamento registrada depois da migração V8. Artefatos compartilhados e cópias em backups seguem a revisão operacional descrita na [seção de retenção](1-arquitetura-e-contrato.md#57-retenção-eliminação-e-anonimização).
- Em uma implantação, sirva cada frontend em HTTPS e encaminhe `/api/v1` de cada origem para a mesma API. Configure o perfil de produção, SMTP e backups seguindo o [guia da API](2-guia-api.md#parte-3-ambiente-e-implantação).

Os documentos de arquitetura e UX ainda guardam referências ao projeto original em Expo. Para implementar mudanças no app atual, use este resumo e a [seção inicial do guia de front-end](3-guia-front-end.md#implementação-atual-pwa).
