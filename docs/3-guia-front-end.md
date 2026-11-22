# Agrofácil: guia do front-end

> **Estado (nov/2026):** a [Parte A](#parte-a-experiência-do-agricultor-ux) é a especificação de UX do app. As Partes [C](#parte-c-app-do-agricultor-pwa) e [D](#parte-d-painel-do-coordenador-vite--react) descrevem os dois apps como estão construídos: o do agricultor é um **PWA** (React + Vite), e não um app Expo como na proposta original. A [Parte E](#parte-e-estado-da-implementação-do-front) diz o que já existe e o que falta.

O front tem dois apps no mesmo monorepo. Este guia está dividido assim:

- **[Parte A](#parte-a-experiência-do-agricultor-ux): a experiência do agricultor (UX).** Telas, textos, acessibilidade e testes com agricultores. Define *o que* o app mostra.
- **[Parte B](#parte-b-comum): o que é comum.** O monorepo, os tipos gerados da API e o pacote `domain`, com as regras compartilhadas.
- **[Parte C](#parte-c-app-do-agricultor-pwa): o app do agricultor.** PWA (React + Vite), offline-first.
- **[Parte D](#parte-d-painel-do-coordenador-vite--react): o painel do coordenador.** Vite / React, desktop-first.
- **[Parte E](#parte-e-estado-da-implementação-do-front): o estado da implementação.**

> **Leia antes:** o contrato ([doc 1, Parte 3](1-arquitetura-e-contrato.md#parte-3-contrato-api-front)) e a sincronização ([doc 1, Parte 4](1-arquitetura-e-contrato.md#parte-4-sincronização-offline), para o mobile). Depois, a [Parte A](#parte-a-experiência-do-agricultor-ux) deste guia: ela define as telas do app, e as Partes B a D mostram como construí-las.

**Os três documentos:** [doc 1: arquitetura, domínio e contrato](1-arquitetura-e-contrato.md) · [doc 2: guia da API](2-guia-api.md) · **doc 3: guia do front-end (este)**

## Sumário

- **[Parte A: experiência do agricultor (UX)](#parte-a-experiência-do-agricultor-ux)**: [A.1 Princípios](#a1-princípios) · [A.2 Navegação](#a2-navegação) · [A.3 Primeiro acesso (ativação)](#a3-primeiro-acesso-ativação) · [A.4 Tela inicial](#a4-tela-inicial) · [A.5 Fluxos de registro](#a5-fluxos-de-registro) · [A.6 Propriedades (RF04–RF09)](#a6-propriedades-rf04rf09) · [A.7 Avisos e lembretes](#a7-avisos-e-lembretes) · [A.8 Meus dados e ajuda](#a8-meus-dados-e-ajuda) · [A.9 Vocabulário](#a9-vocabulário) · [A.10 Acessibilidade e visual](#a10-acessibilidade-e-visual) · [A.11 Painel do coordenador: princípios de UX](#a11-painel-do-coordenador-princípios-de-ux) · [A.12 Testes com agricultores](#a12-testes-com-agricultores) · [A.13 Visitas do grupo e da comissão (SPG)](#a13-visitas-do-grupo-e-da-comissão-spg)
- **[Parte B: comum](#parte-b-comum)**: [B.1 Monorepo com pnpm](#b1-monorepo-com-pnpm) · [B.2 Qualidade de código](#b2-qualidade-de-código) · [B.3 Tipos gerados da API (`@agrofacil/api-types`)](#b3-tipos-gerados-da-api-agrofacilapi-types) · [B.4 Pacote `@agrofacil/domain`](#b4-pacote-agrofacildomain)
- **[Parte C: app do agricultor (PWA)](#parte-c-app-do-agricultor-pwa)**: [C.1 Stack e comandos](#c1-stack-e-comandos) · [C.2 Estrutura de pastas](#c2-estrutura-de-pastas) · [C.3 Tema e componentes base](#c3-tema-e-componentes-base) · [C.4 Dados guardados no aparelho](#c4-dados-guardados-no-aparelho) · [C.5 Autenticação e sessão](#c5-autenticação-e-sessão) · [C.6 Motor de sincronização](#c6-motor-de-sincronização) · [C.7 Fotos](#c7-fotos) · [C.8 Formulário de registro](#c8-formulário-de-registro) · [C.9 Avisos](#c9-avisos) · [C.10 Telas e cobertura da Parte A](#c10-telas-e-cobertura-da-parte-a) · [C.11 Testes do mobile](#c11-testes-do-mobile) · [C.12 Build e distribuição](#c12-build-e-distribuição)
- **[Parte D: painel do coordenador (Vite + React)](#parte-d-painel-do-coordenador-vite--react)**: [D.1 Stack e comandos](#d1-stack-e-comandos) · [D.2 Estrutura](#d2-estrutura) · [D.3 Autenticação no painel](#d3-autenticação-no-painel) · [D.4 Dados: TanStack Query](#d4-dados-tanstack-query) · [D.5 Rotas e menu](#d5-rotas-e-menu) · [D.6 Telas](#d6-telas) · [D.7 Padrões de UI do painel](#d7-padrões-de-ui-do-painel) · [D.8 Testes do painel](#d8-testes-do-painel) · [D.9 Build](#d9-build)
- **[Parte E: estado da implementação do front](#parte-e-estado-da-implementação-do-front)**

## Parte A: experiência do agricultor (UX)

Esta parte substitui o que mudou no protótipo do Figma: login, cadastro e formulários longos. O resto do protótipo continua valendo, inclusive a identidade visual: cores, logo e tipografia.

### A.1 Princípios

1. **Registrar uma colheita leva menos de 30 segundos.** É a meta que guia cada tela. Se não der, a tela está errada.
2. **Tocar em vez de digitar.** Teclado só no "Outro" e nas observações. Para essas, há sempre a alternativa de foto ou áudio.
3. **Uma pergunta por tela.** A pergunta fica escrita em letras grandes, com botões grandes. O progresso aparece em bolinhas no topo.
4. **O app já sabe o que dá para saber.** Data de hoje, a única área, a última unidade usada, o produto que ele sempre usa, a cultura do plantio.
5. **Só o essencial é obrigatório.** Quase tudo tem **Pular**. O registro incompleto é salvo e o app lembra depois (selo "Falta completar").
6. **Nunca perder nada.** Salvar funciona sempre, com ou sem internet. Se o app fechar no meio, o rascunho volta.
7. **O app pergunta, o agricultor responde.** Lembretes no dia previsto da colheita trazem o formulário já preenchido.
8. **Palavras da roça.** Nada de "insumo", "manejo", "sincronizar", "erro", "inválido" (ver [§A.9](#a9-vocabulário)).
9. **Ícone sempre acompanhado de texto.** Nunca use cor como único sinal.
10. **Testar com agricultores de verdade**, no sol, com celular barato ([§A.12](#a12-testes-com-agricultores)).

### A.2 Navegação

Segue a estrutura do protótipo:

```text
┌──────────────────────────────────────┐
│  Bom dia, José            🔔 2   👤   │  ← sino = Avisos; boneco = Meus dados
├──────────────────────────────────────┤
│              (conteúdo)              │
├──────────────────────────────────────┤
│  🏠 Início  📋 Registros  🏡 Propriedades │  ← barra de abas
└──────────────────────────────────────┘
```

- **3 abas:** Início, Registros e Propriedades. "Meus dados" e "Avisos" ficam no topo, como no protótipo, mas fora da barra de abas para reduzir as opções.
- O botão **Voltar** fica sempre visível no topo, além do botão do Android.
- Não existem gestos escondidos (arrastar para apagar, pressionar e segurar). Toda ação tem um botão visível.
- As **visitas do SPG** não ganham aba: aparecem como cartão na tela inicial e nos Avisos ([§A.13](#a13-visitas-do-grupo-e-da-comissão-spg)).

### A.3 Primeiro acesso (ativação)

Substitui login, cadastro e "esqueci a senha" do protótipo.

```text
Tela 1 – Boas-vindas
  [logo]
  "Bem-vindo ao Agrofácil"
  "Para começar, você precisa do código que a associação te deu."
  [ Tenho o código ]          (botão grande)
  [ Não tenho código ]        → abre o WhatsApp da associação com mensagem pronta

Tela 2 – CPF
  "Digite seu CPF"
  [ ___.___.___-__ ]          (teclado numérico, máscara automática)
  [ Continuar ]

Tela 3 – Código
  "Digite o código de 6 números"
  [ _ ][ _ ][ _ ][ _ ][ _ ][ _ ]   (caixas grandes, teclado numérico)
  Ao completar o 6º número → envia sozinho
  Erro: "O código não confere. Confira os números ou peça um novo à associação."
        [ Pedir novo código pelo WhatsApp ]

Tela 4 – Termo (resumido)
  "Seus dados e a associação"
  3 frases curtas: o que é guardado, quem vê, para quê. [ Ler tudo ] abre o texto completo.
  [ Entendi, continuar ]

Tela 5 – Proteção (opcional)
  "Quer usar sua digital para abrir o app?"
  [ Sim, usar digital ]   [ Agora não ]
  (sem digital cadastrada no celular: "Quer usar a senha do seu celular?" Sim / Agora não)

Tela 6 – Pronto
  "Tudo certo, José!"
  [ Ver como registrar ]  → tour de 3 telas (pode pular)
  [ Começar ]
```

Notas:
- **Sem internet na ativação** é o único momento em que o app precisa de rede. Mostre "Você precisa de internet só agora, para entrar pela primeira vez. Depois o app funciona sem internet."
- O coordenador costuma **fazer a ativação junto com o agricultor**, entregando o código pessoalmente. O tour de 3 telas é para ele mostrar.

### A.4 Tela inicial

```text
┌──────────────────────────────────────┐
│ Bom dia, José                  🔔 2 👤│
│ ✓ Tudo enviado para a associação      │  ← ou "⏱ 3 registros esperando internet"
├──────────────────────────────────────┤
│ ⚠ Sua certificação do Sítio Boa Vista │  ← só se EXPIRING / EXPIRED / SUSPENDED (RN21)
│   vence em 20 dias. Fale com a        │
│   associação.          [ WhatsApp ]   │
├──────────────────────────────────────┤
│ Lembretes                             │
│ 🧺 Já colheu a alface da Horta de     │
│    baixo?        [ Sim ] [ Ainda não ]│
│ ✏ A associação pediu para corrigir    │
│    um registro.          [ Ver ]      │
├──────────────────────────────────────┤
│ O que você fez na roça?               │
│ ┌──────────┐ ┌──────────┐            │
│ │ 🌱        │ │ 💧        │            │
│ │ Plantei   │ │ Usei adubo│            │
│ │           │ │ ou remédio│            │
│ └──────────┘ └──────────┘            │
│ ┌──────────┐ ┌──────────┐            │
│ │ 🧺 Colhi  │ │ 🚚 Vendi ou│            │
│ │           │ │ entreguei │            │
│ └──────────┘ └──────────┘            │
│ ┌───────────────────────┐            │
│ │ 🛒 Comprei produto para │            │
│ │    a roça              │            │
│ └───────────────────────┘            │
│ ↻ Repetir: Biofertilizante na alface  │
├──────────────────────────────────────┤
│ Últimos registros            Ver todos│
│ 🧺 Colheita de alface · hoje      ✓   │
│ 💧 Biofertilizante · ontem        ⏱   │
├──────────────────────────────────────┤
│ 💬 Fale com a associação              │
└──────────────────────────────────────┘
```

- **Visitas:** se houver visita marcada na roça dele ou uma visita que ele vai fazer, um cartão aparece acima dos lembretes ([§A.13](#a13-visitas-do-grupo-e-da-comissão-spg)).
- **Lembretes:** colheita prevista para hoje ou já passada, pedidos de correção, registros incompletos ("2 registros estão faltando informações") e plantios ativos há mais de 120 dias sem colheita. Cada lembrete pode ser dispensado.
- **"Repetir"** mostra o último registro do tipo mais frequente. Ao tocar, abre direto a confirmação ([§A.5.6](#a56-confirmação-e-sucesso)) com a data de hoje.
- O menu "Registrar manejo" do protótipo vira **esta grade de 5 botões na própria tela inicial**: um toque a menos.

### A.5 Fluxos de registro

Todas as telas do assistente seguem o mesmo molde:

```text
┌──────────────────────────────────────┐
│ ←  Colheita                ● ● ○ ○    │  ← voltar, tipo, progresso
│                                      │
│  Quanto você colheu?          🔊      │  ← pergunta grande + "ouvir" (texto falado)
│                                      │
│        [ − ]    30    [ + ]          │  ← número grande; toque no número abre o teclado
│                                      │
│  ( molhos ) ( caixas ) ( kg ) ( mais…)│  ← unidades em botões; a última usada já marcada
│                                      │
│                                      │
│  [ Pular ]              [ Próximo → ] │  ← "Pular" só em campos opcionais
└──────────────────────────────────────┘
```

Regras comuns:
- **GPS:** começa a ser capturado em segundo plano ao abrir o fluxo (RN26). Não aparece na tela.
- **Data:** botões `Hoje` (marcado), `Ontem` e `Outro dia` (calendário).
- **Rascunho:** o fluxo é salvo a cada passo. Se o app fechar, ao abrir de novo aparece "Você estava registrando uma colheita. Quer continuar?" `[Continuar] [Descartar]`.
- **Extras (último passo antes da confirmação), todos opcionais:** `📷 Tirar foto`, `🎤 Gravar recado` (até 3 min) e `✏ Escrever`.
- **Listas grandes** (culturas e produtos) mostram primeiro os **"Mais usados por você"** (até 6, a partir do histórico local). Depois vêm as categorias, com a busca "Procurar" e o botão **"Não achei / Outro"**.

#### A.5.1 🌱 Plantei (`PLANTING`)

| # | Pergunta | Opções | Obrig.? |
|---|---|---|---|
| 1 | Onde você plantou? | Cartões "Propriedade · Área". **Pula** se só existir uma área. A última usada vem marcada | Sim |
| 2 | O que você plantou? | Grade de culturas com ícone. "Outro" abre um campo de texto | Sim |
| 3 | Quando você plantou? | Hoje / Ontem / Outro dia | Sim |
| 4 | Plantou semente ou muda? | Semente / Muda / Rama ou maniva / Outro. Depois: "Quantas?" com número e unidade | Não |
| 5 | De onde veio a semente (ou muda)? | 🏡 Guardei da minha roça / 🤝 Banco ou troca de sementes / 🌿 Comprei ou ganhei **orgânica** / 🏪 Comprei ou ganhei **comum** | Não (recomendado) |
| 6 | Qual o tamanho da área que você plantou? | Atalho "A área toda (2 tarefas)", se a área tiver tamanho. Senão, número + `Tarefa` / `m²` / `Hectare` | Não (recomendado) |
| 7 | Quando acha que vai colher? | Em 1 mês / 2 meses / 3 meses / Outra data / Não sei | Não (recomendado) |
| 8 | Quer acrescentar algo? | Foto / Recado / Escrever | Não |
| 9 | Confirmação | | |

Ao salvar com previsão de colheita, o app **agenda um lembrete local** para as 7h do dia previsto ([§A.7](#a7-avisos-e-lembretes)).

#### A.5.2 💧 Usei adubo ou remédio (`INPUT_APPLICATION`)

| # | Pergunta | Opções | Obrig.? |
|---|---|---|---|
| 1 | Usou em qual plantação? | Cartões dos plantios **ativos**: ícone da cultura, área e "plantado há 3 semanas". Depois, "Na área toda (sem plantação)", que pede a área. No fim, "Ver plantações terminadas" | Sim |
| 2 | O que você usou? | "Seus produtos" (histórico) primeiro. Depois as categorias: Adubo / Biofertilizante / Calda ou remédio natural / Corretivo do solo. "Outro" abre texto | Sim |
| 3 | Para quê? | Adubar / Combater praga ou doença / Corrigir o solo / Outro. **Vem marcado** pelo tipo do produto | Não (recomendado) |
| 4 | Quanto você usou? | Número + Litros / kg / Sacos / Latas / Carroças / mais… | Não (recomendado) |
| 5 | Quando? | Hoje / Ontem / Outro dia | Sim |
| 6–7 | Extras e confirmação | | |

Se o produto do catálogo tiver `needsAuthorization`, **não bloqueie nem assuste**. Na confirmação, mostre em tom neutro: "A associação pode pedir mais informações sobre este produto." O coordenador vê o registro destacado no painel.

#### A.5.3 🧺 Colhi (`HARVEST`)

| # | Pergunta | Opções | Obrig.? |
|---|---|---|---|
| 1 | Colheu qual plantação? | Plantios ativos, **ordenados pela previsão de colheita mais próxima**. "Ver plantações terminadas" no fim | Sim |
| 2 | Quando? | Hoje / Ontem / Outro dia | Sim |
| 3 | Quanto você colheu? | Número + Molhos / Caixas / kg / Sacos / Dúzias / Unidades. A unidade da última colheita **desta cultura** já vem marcada | Não (recomendado) |
| 4 | Terminou de colher esta plantação? | "Não, ainda vou colher mais" / "Sim, terminei". O "Sim" encerra o plantio (RN12) | Sim (padrão: Não) |
| 5–6 | Extras e confirmação | | |

Pelo lembrete "Já colheu a alface?" → **Sim**, o fluxo abre direto no passo 3, com o plantio e a data de hoje preenchidos.

#### A.5.4 🚚 Vendi ou entreguei (`OUTFLOW`)

| # | Pergunta | Opções | Obrig.? |
|---|---|---|---|
| 1 | Para qual propriedade? | **Pula** se houver só uma | Sim |
| 2 | O que saiu da roça? | Culturas **colhidas recentemente** primeiro; depois a grade completa | Sim |
| 3 | Para onde foi? | Feira / Cooperativa / Merenda escolar (PNAE) / Compra do governo (PAA) / Atravessador / Vendi direto / Consumo da família / Doação / Outro | Não (recomendado) |
| 4 | Quanto? | Número + unidade | Não (recomendado) |
| 5 | Quando? | Hoje / Ontem / Outro dia | Sim |
| 6 | Extras | + "Para quem vendeu? (opcional)" | Não |
| 7 | Confirmação | | |

#### A.5.5 🛒 Comprei produto para a roça (`INPUT_PURCHASE`)

| # | Pergunta | Opções | Obrig.? |
|---|---|---|---|
| 1 | Tire uma foto da nota ou da embalagem | Câmera grande / "Não tenho foto" | Não (recomendado) |
| 2 | Para qual propriedade? | Pula se houver só uma | Sim |
| 3 | O que você comprou? | Igual ao passo 2 da aplicação | Sim |
| 4 | Quanto? | Número + unidade | Não (recomendado) |
| 5 | De quem comprou? | Texto curto, com sugestões dos fornecedores já usados. **Pula sozinho** se houver foto | Não |
| 6 | Quando? | Hoje / Ontem / Outro dia | Sim |
| 7 | Confirmação | | |

#### A.5.6 Confirmação e sucesso

```text
┌──────────────────────────────────────┐
│ ←  Confirme                           │
│                                      │
│  🧺 Você colheu 30 molhos de alface   │
│     da Horta de baixo, hoje.          │
│     Ainda vai colher mais.            │
│                                      │
│  📷 1 foto                            │
│                                      │
│  Está certo?                    🔊    │
│  [ ✏ Corrigir ]   [ ✓ Sim, salvar ]   │
└──────────────────────────────────────┘
```

- **A frase é montada a partir das respostas.** As funções de frase ficam no pacote `domain` e têm testes.
- **"Corrigir"** mostra a lista de respostas. Ao tocar numa, volta àquele passo.
- **Sucesso:** um ✓ grande com vibração curta e o texto "Salvo!" + "Vai ser enviado para a associação quando tiver internet" (ou "Enviado para a associação"). Botões: `[Registrar outra coisa]` `[Voltar ao início]`.
- **Faltaram campos recomendados:** acrescente uma linha discreta "Faltou: tamanho da área. Você pode completar depois." **Não** pergunte de novo.

#### A.5.7 Ver, editar, repetir e apagar

- **Lista de registros (aba Registros):**
  - filtros em botões: Todos / Plantios / Adubo e remédio / Colheitas / Vendas / Compras;
  - agrupada por mês;
  - cada item mostra ícone, frase curta, data e status de envio ([doc 1 §4.5](1-arquitetura-e-contrato.md#45-estados-que-o-agricultor-vê));
  - selo laranja "Falta completar" e selo "Precisa de atenção".
- **Detalhe (substitui os detalhes do protótipo):**
  - a frase do registro e os campos em lista;
  - o número `Nº 000123`, ou "Aguardando envio";
  - "Registrado pela associação", quando foi o coordenador;
  - fotos e áudio (com botão de tocar).
  - No caso de um **plantio**, a linha do tempo das aplicações e colheitas dele, com botões `[+ Colheita]` e `[+ Adubo ou remédio]` já ligados ao plantio.
- **Botões do detalhe:** `Completar` (se incompleto; abre só os passos que faltam), `Editar`, `Registrar de novo` e `Apagar`.
- **Apagar:** confirmação com texto claro: "Apagar a colheita de 30 molhos de alface do dia 20/11? A associação vai ver que foi apagado." `[Não]` `[Sim, apagar]`. Num plantio com colheitas, explique em vez de bloquear sem motivo (RN13).
- **Pedido de correção:** no topo do detalhe, numa caixa amarela, fica a mensagem do coordenador, com o botão `Corrigir agora`.

### A.6 Propriedades (RF04–RF09)

**Lista:** um cartão por propriedade, com nome, município e a **frase da certificação**:

| Status | Frase | Cor + ícone |
|---|---|---|
| `VALID` | "Certificação em dia até 23/10/2027" | verde ✓ |
| `EXPIRING` | "Certificação vence em 20 dias (23/11)" | amarelo ⚠ |
| `EXPIRED` | "Certificação vencida desde 23/10" | vermelho ✕ |
| `SUSPENDED` | "Certificação suspensa. Fale com a associação" | vermelho ⏸ |
| sem certificação | "Sem certificação cadastrada" | cinza |

**Detalhe** (o "Propriedade - Exemplo" do protótipo):
- **Áreas:** nome, tamanho e status em linguagem simples: "Orgânica", "Em conversão até 03/2027", "Não orgânica", "A definir pela associação".
- **Plantações ativas**, com atalho para registrar.
- **Certificação:** validade, produtos e pessoas vinculadas. O histórico fica em "Ver anteriores".
- **Pessoas que trabalham aqui.**
- **Localização:** mapa estático e "Abrir no mapa", que abre o Google Maps.
- **Botões:** `Editar` e `Apagar`.

**Cadastrar propriedade** (tela que não existia no protótipo), em assistente:
1. "Qual o nome da propriedade?" (ex.: Sítio Boa Vista).
2. "Você está na propriedade agora?"
   - **Sim:** captura o GPS, mostra "Localização salva ✓ (precisão de 8 m)" e permite tentar de novo.
   - **Não:** "Sem problema. A associação pode marcar depois."
3. "Em qual município?" A lista vem com o município do agricultor marcado. Depois: "Qual o povoado ou comunidade?" (texto).
4. "Qual o tamanho da propriedade?" Número + Tarefa / Hectare / m². Pode pular.
5. "Como chegar lá?" (opcional, texto ou áudio) → `access_directions`.
6. Confirmação.

Depois de salvar, aparece a mensagem "A associação vai cadastrar as áreas da sua propriedade. Enquanto isso, você já pode registrar na Área principal."

**Apagar propriedade** (RF08/RF09):
- Confirmação com o nome digitado? **Não**, é difícil demais para o público. Use dois passos: um botão "Apagar" e depois um diálogo com "Apagar o Sítio Boa Vista? Os registros continuam guardados na associação." `[Não]` `[Sim, apagar]`.
- Com certificação válida: "Esta propriedade tem certificação válida. Para apagar, fale com a associação." `[WhatsApp]`.

### A.7 Avisos e lembretes

**Avisos (sino):** uma lista única, com ícone por tipo:

| Tipo | Ícone | Ao tocar |
|---|---|---|
| Certificação vencendo, vencida ou suspensa | ⚠ | Detalhe da propriedade |
| Comunicado da associação | 📢 | Texto completo, com foto |
| Dica de manejo | 💡 | Texto completo |
| Pedido de correção | ✏ | Detalhe do registro |
| Registro alterado pela associação | ℹ | Detalhe do registro |
| Visita (período de visitas, visita marcada, véspera, mudança, cancelamento) | 🤝 | Detalhe da visita ([§A.13](#a13-visitas-do-grupo-e-da-comissão-spg)) |

O aviso "Sincronização concluída" do protótipo foi **removido**: o status de envio já aparece no topo da tela inicial, e um aviso a mais só gera ruído.

**Notificações no celular:**
- **Push** (vindo do servidor): comunicados, dicas, certificação, pedidos de correção e visitas.
- **Lembretes locais**, que funcionam sem internet:
  - colheita prevista: "Já colheu a alface da Horta de baixo?", com as ações `Sim, registrar` e `Ainda não` (o "Ainda não" pergunta de novo em 3 dias);
  - semanal (padrão: segunda-feira às 18h, só se não houve registro nos últimos 7 dias): "Fez alguma coisa na roça esta semana? Leva menos de 1 minuto para registrar." Pode ser desligado em Meus dados.

### A.8 Meus dados e ajuda

- **Dados que o agricultor vê:** nome, CPF (mascarado) e associação, só para leitura ("Para mudar, fale com a associação"). Pode editar o telefone, o e-mail e o endereço; essas mudanças precisam de internet e o app avisa.
- **Configurações:**
  - "Usar digital para abrir o app" (liga e desliga);
  - "Lembrete semanal" (liga e desliga);
  - "Tamanho da letra" (Normal / Grande / Muito grande), que multiplica a escala do sistema.
- **Ajuda:**
  - "Como registrar" (o tour de novo);
  - "Fale com a associação" (WhatsApp);
  - "Termo de uso e privacidade".
- **Sair do app**, escondido no fim e com um alerta: "Se sair, vai precisar de um novo código da associação para entrar de novo. Seus registros não enviados continuam guardados neste celular." `[Cancelar]` `[Sair mesmo assim]`.
- **Versão do app e status:** "Último envio: hoje, 14:32". Toque longo copia as informações técnicas para ajudar o suporte.

### A.9 Vocabulário

| Evite | Use |
|---|---|
| Manejo, registrar manejo | "O que você fez na roça?" / Registrar |
| Aplicação de insumo | Usei adubo ou remédio |
| Insumo | Produto, adubo, remédio, semente |
| Aquisição de insumo | Comprei produto para a roça |
| Saída / comercialização | Vendi ou entreguei |
| Lote / talhão / gleba | Área (o agricultor pode usar o nome que quiser, ex.: "Roça de baixo") |
| Cultivar | Cultura / o que plantou |
| Material de propagação | Semente ou muda |
| Sincronizar / sincronização | Enviar para a associação |
| Offline | Sem internet |
| Erro / inválido / falha | "Não deu certo", "confira", "precisa de atenção" |
| Autenticar / login | Entrar |
| Status pendente | Esperando internet |
| Obrigatório | (não diga; só não mostre o "Pular") |
| Excluir / deletar | Apagar |
| Visita de pares | Visita do grupo |
| Visita de verificação / Comissão de Avaliação | Visita da comissão |
| Roteiro / checklist | Perguntas da visita |
| Não conformidade | Algo que precisa melhorar |
| Visitador | Quem vai visitar |

Outras regras de texto:
- Frases curtas, na segunda pessoa ("Você colheu…"), sem siglas. PNAE e PAA vêm com explicação: "Merenda escolar (PNAE)".
- Datas como "hoje", "ontem" e "20/11". Nunca "2026-11-20".
- Números com vírgula decimal ("2,5 tarefas").
- Botões dizem a ação ("Sim, salvar"), não "OK".

### A.10 Acessibilidade e visual

- **Toque:** áreas de toque com no mínimo **56 dp** de altura nos botões principais e 48 dp nos demais.
- **Texto:** letra base de **18 sp** (perguntas com 24 sp). O app respeita a escala de fonte do Android até 1,6x sem quebrar o layout. Teste com a escala máxima.
- **Contraste:** pelo menos **7:1** no texto principal, para ler no sol. Confira as cores da identidade visual do Figma e escureça o que precisar.
- **Ouvir (🔊):** cada pergunta e a confirmação podem ser lidas em voz alta (`expo-speech`, pt-BR, funciona sem internet na maioria dos Android). Ajuda quem lê com dificuldade.
- **Leitores de tela:** `accessibilityLabel` e `accessibilityRole` em todos os botões e ícones. O TalkBack precisa conseguir completar um registro.
- **Ícones de culturas:** ilustrações simples e reconhecíveis (alface, feijão, milho, mandioca, umbu, maracujá…), sempre com o nome embaixo.
- **Movimento:** animações curtas, respeitando "remover animações" do sistema.
- **Celular barato:** a lista de registros é virtualizada (FlashList). As fotos são reduzidas. O app deve abrir em menos de 2 s num Android de entrada (RNF03).
- **Modo escuro:** fora do MVP. O uso é no sol, e o tema claro com alto contraste é o melhor.

### A.11 Painel do coordenador: princípios de UX

O detalhamento das telas está na [§D](#parte-d-painel-do-coordenador-vite--react). Princípios:
- **Desktop-first** (1280 px ou mais), com menu lateral fixo. Deve funcionar até 1024 px. Celular não é prioridade.
- **Tudo é lista com filtro:** os filtros ficam na URL, para poder compartilhar ou voltar com o botão do navegador. Ordenação por coluna. Exportar CSV na própria lista.
- **O que precisa de ação aparece primeiro:** certificações vencendo, visitas urgentes ou sem resultado, pedidos de correção abertos, agricultores sem registrar há 30 dias, itens sem catálogo.
- **O sistema sugere, o coordenador decide:** a sugestão de visitadores mostra sempre o motivo de cada nome e permite trocar. Os documentos do SPG saem com a decisão em branco (doc 1, ADR-15).
- **O código de acesso** aparece numa janela com números grandes ("482 913"), com os botões "Copiar", "Enviar pelo WhatsApp" e "Imprimir".
- **"Registrar pelo agricultor"** usa um formulário normal, numa tela só e não em assistente, porque o coordenador sabe usar computador. O formulário avisa: "Este registro vai aparecer para o agricultor como registrado pela associação."
- **Toda exclusão** pede confirmação e diz o que acontece.

### A.12 Testes com agricultores

Faça os testes com pelo menos **5 agricultores** da associação a cada versão importante, seguindo o Guia de Estrutura do projeto.

**Condições:**
- no campo, ou num lugar parecido;
- em celular Android de entrada;
- com o modo avião ligado em parte do teste.

**Roteiro (tarefas):**
1. Entrar no app com o CPF e o código (com o coordenador junto).
2. Registrar que plantou alface hoje.
3. Registrar que colheu 20 molhos de alface ontem.
4. Registrar que usou biofertilizante na alface.
5. Registrar que vendeu na feira.
6. Encontrar quando vence a certificação.
7. Apagar um registro feito errado.
8. Encontrar quando é a próxima visita na sua roça e quem vem.
9. Fazer uma visita como responsável: responder as perguntas e salvar.

**Medidas:**

| Medida | Meta |
|---|---|
| Concluiu sem ajuda? | ≥ 4 de 5 |
| Tempo da tarefa 3 | < 30 s |
| Erros (toques errados, voltas) | Anotar |
| Frase do agricultor ("achei fácil", "não entendi…") | Anotar ao pé da letra |

**Técnica:** peça para a pessoa pensar em voz alta e **não ajude** durante a tarefa. Anote onde ela travou. Toda travada vira um ajuste de tela ou de texto antes do próximo teste.

### A.13 Visitas do grupo e da comissão (SPG)

As regras estão na [doc 1, Parte 6](1-arquitetura-e-contrato.md#parte-6-certificação-participativa-spg-visitas-e-documentos). No app, o agricultor encontra as visitas em dois papéis:
- **quem recebe** a visita na própria roça;
- **quem vai visitar** a roça de outro.

Não há aba nova. As visitas aparecem como **cartão na tela inicial**, acima dos lembretes, e nos **Avisos**.

**Palavras:** "visita do grupo" (de pares) e "visita da comissão" (de verificação). Nunca "verificação de conformidade", "pares" ou "checklist".

#### A.13.1 Quem recebe a visita

```text
┌──────────────────────────────────────┐
│ 🤝 Visita do grupo na sua roça        │
│    Quinta, 14/11 · com João e Ana     │
│                              [ Ver ]  │
└──────────────────────────────────────┘
```

O detalhe mostra:
- quando é e o tipo, em palavras simples;
- quem vem;
- o que deixar à mão: "Deixe à mão as notas de compra e mostre onde guarda os adubos e remédios. Os seus registros já estão no app.";
- o botão "Fale com a associação".

Depois da visita, o cartão vira "Visita feita em 14/11 ✓" e mostra **"O que o grupo recomendou"**, se houver. A **visita sem aviso** não aparece antes de acontecer (RN31).

#### A.13.2 Quem vai visitar

```text
┌──────────────────────────────────────┐
│ 🤝 Você vai visitar o Sítio Boa Vista │
│    de José · quinta, 14/11            │
│                              [ Ver ]  │
└──────────────────────────────────────┘
```

**Detalhe da visita** (funciona sem internet):
- **Quando** e **quem vai junto**.
- **Como chegar:** o roteiro de acesso, `Abrir no mapa` e `Ligar para José`.
- **O que José registrou**, em frases curtas montadas a partir do resumo (`brief`):
  - "3 áreas: Horta de baixo (orgânica), Roça do fundo (em conversão até março)…";
  - "Plantações agora: alface, coentro, feijão";
  - "Adubos e remédios no último ano: biofertilizante (12 vezes), calda bordalesa (2 vezes) ⚠ precisa de autorização";
  - "Compras: esterco de curral (Fazenda X), 10/10";
  - "Colheitas e vendas no último ano: alface, 320 molhos; feijão, 4 sacos";
  - "Na última visita, o grupo recomendou: …".
- **Botão principal:**
  - **se ele é o responsável pelo registro:** `[ Começar a visita ]`;
  - **se não é:** "Quem vai anotar a visita no celular é a Ana." Sem botão.
- **Fim do acesso:** o resumo some do celular 30 dias depois da visita (RN36). O app não explica isso; a tela só deixa de existir.

#### A.13.3 Modo visita (o responsável responde as perguntas)

Usa o **mesmo molde do assistente de registro** ([§A.5](#a5-fluxos-de-registro)): uma pergunta por tela, 🔊 para ouvir e rascunho salvo a cada resposta. Dá para responder andando pela roça, sem internet.

```text
┌──────────────────────────────────────┐
│ ←  Visita · Sítio Boa Vista   3 de 14 │
│                                      │
│  Os adubos e remédios usados          │
│  são permitidos?               🔊     │
│                                      │
│  [ ✓ Sim                         ]    │
│  [ ✗ Não                         ]    │
│  [ — Não se aplica               ]    │
│  [ ? Não deu para ver            ]    │
│                                      │
│  ℹ Usou no último ano: biofertilizante,│
│    calda bordalesa                    │  ← dica tirada do resumo, quando houver
└──────────────────────────────────────┘
```

| # | Passo | Como |
|---|---|---|
| 1 | "Quem foi na visita?" | Lista de nomes com caixas grandes, já marcados os visitadores da visita |
| 2…n | Uma tela por pergunta do roteiro ([doc 1 §6.6](1-arquitetura-e-contrato.md#66-roteiro-da-visita-modelo-in19-v1)) | Quatro botões grandes. Ao tocar, vai sozinho para a próxima. Se for **Não**, abre "Quer explicar?" com 🎤 / 📷 / ✏, e dá para pular |
| n+1 | "Encontraram algo que precisa melhorar?" | Sim / Não. Se Sim: "O que precisa melhorar?" (áudio ou texto, obrigatório) |
| n+2 | "Quer deixar alguma recomendação?" | Áudio ou texto. Pode pular |
| n+3 | Fotos da visita | `📷 Tirar foto` (opcional) |
| — | Confirmação | "Visita ao Sítio Boa Vista, hoje. 12 Sim, 1 Não, 1 Não se aplica. Precisa melhorar: separar a calda dos outros produtos." `[ ✏ Corrigir ]` `[ ✓ Salvar visita ]` |

**Sucesso:** "Visita salva! A associação vai imprimir o papel para todos assinarem na reunião do grupo."

O responsável pode **corrigir** a visita pelo app por 7 dias. Depois disso, aparece: "Para mudar, fale com a associação."

#### A.13.4 Textos dos avisos

| Aviso | Texto |
|---|---|
| Período de visitas | "Está chegando a época das visitas da certificação do Sítio Boa Vista. A associação vai combinar as datas com o grupo." |
| Você vai visitar | "Você vai visitar o Sítio Boa Vista, de José, na quinta, 14/11." |
| Visita na sua roça | "Visita do grupo na sua roça: quinta, 14/11, com João e Ana." |
| Véspera | "Amanhã: visita ao Sítio Boa Vista." / "Amanhã tem visita do grupo na sua roça." |
| Mudou | "A visita ao Sítio Boa Vista mudou para sábado, 16/11." |
| Cancelada | "A visita ao Sítio Boa Vista de 14/11 foi cancelada." |

## Parte B: comum

### B.1 Monorepo com pnpm

```text
agrofacil/
├── apps/mobile/        # @agrofacil/mobile
├── apps/web/           # @agrofacil/web
├── packages/api-types/ # @agrofacil/api-types
├── packages/domain/    # @agrofacil/domain
├── pnpm-workspace.yaml
├── package.json
├── .npmrc
├── .editorconfig
├── tsconfig.base.json  # strict + noUncheckedIndexedAccess, estendido pelos pacotes
├── .prettierrc
└── .prettierignore
```

```yaml
# pnpm-workspace.yaml: pacotes JavaScript do monorepo (a API Maven fica fora).
packages:
  - "apps/*"
  - "packages/*"
```

```ini
# .npmrc: dependências "achatadas" (herança da fase Expo; o Vite funciona com qualquer modo).
node-linker=hoisted
```

`package.json` da raiz:

```json
{
  "name": "agrofacil",
  "private": true,
  "packageManager": "pnpm@12.16.1",
  "scripts": {
    "gen:api": "pnpm --filter @agrofacil/api-types generate",
    "lint": "pnpm -r lint",
    "format": "prettier --write .",
    "typecheck": "pnpm -r typecheck",
    "test": "pnpm -r test"
  }
}
```

Os pacotes `domain` e `api-types` são consumidos **direto do código-fonte TypeScript** (`"main": "src/index.ts"`), sem etapa de build. O Vite entende workspaces sozinho.

### B.2 Qualidade de código

- **TypeScript** com `"strict": true` e `"noUncheckedIndexedAccess": true` em todos os pacotes.
- **Lint** por pacote (`pnpm lint` roda todos):
  - painel, app e pacotes: **oxlint**, o padrão do template atual do Vite, com as regras de React Hooks.

  O **Prettier** cuida da formatação (`pnpm format`); o lint não opina sobre estilo.
- **pnpm 12 via Corepack:** a versão está no `packageManager`. O pnpm bloqueia versões publicadas há pouco tempo (`minimumReleaseAge`); as exceções que ele precisou acrescentar ficam comentadas no `pnpm-workspace.yaml`.
- `.editorconfig`: 2 espaços, LF, UTF-8, nova linha no fim. O `.java` usa o que o google-java-format define.
- **Todo arquivo começa com um comentário de uma frase** dizendo o que faz. Funções exportadas têm JSDoc.
- **Nomes:**
  - código em inglês;
  - textos da interface em português. Os que se repetem nos dois apps (rótulos de status, tipos, unidades) ficam nos rótulos do `domain`.
- **Nada de números mágicos:** limites e tempos viram constantes nomeadas (`MAX_PAGE_SIZE = 100`, `PULL_PAGE_SIZE = 500`).
- **CI:** `pnpm lint && pnpm typecheck && pnpm test` em todo PR.

### B.3 Tipos gerados da API (`@agrofacil/api-types`)

```json
{
  "name": "@agrofacil/api-types",
  "main": "src/index.ts",
  "scripts": {
    "generate": "curl -sf http://localhost:8080/api/v1/openapi.json -o openapi.json && openapi-typescript openapi.json -o src/schema.ts"
  },
  "devDependencies": { "openapi-typescript": "<versão estável>" }
}
```

```ts
// Reexporta os tipos gerados do OpenAPI com nomes curtos para uso nos apps.
import type { components, paths } from "./schema";

export type { paths };
export type Schemas = components["schemas"];
export type Me = Schemas["Me"];
export type RecordView = Schemas["RecordView"];
export type RecordWrite = Schemas["RecordWrite"];
// … um alias por tipo usado com frequência
```

- `openapi.json` e `src/schema.ts` são **versionados**: qualquer pessoa compila o front sem a API rodando.
- **Nunca edite `schema.ts` à mão.**
- Os clientes HTTP usam **`openapi-fetch`** (`createClient<paths>()`): rotas, parâmetros e respostas ficam todos tipados.

### B.4 Pacote `@agrofacil/domain`

Regras puras (sem React, sem rede, sem banco), usadas pelo mobile **e** pelo web. Cada função tem testes (Vitest).

```text
packages/domain/src/
├── completeness.ts     # missingFields(record) — espelha o doc 1 §2.5 e o servidor
├── units.ts            # toSquareMeters, formatQuantity ("2,5 tarefas"), unidades por tipo de medida
├── certification.ts    # certificationStatus(cert, today, alertDays), certificationPhrase(...)
├── phrases.ts          # describeRecord(record, lookups) → "Você colheu 30 molhos de alface…"
├── visits.ts           # visitTypeLabel, describeBrief(brief) → frases do resumo, describeVisitResult(...)
├── labels.ts           # rótulos pt-BR de todos os enums (destino, origem da semente…)
├── dates.ts            # isFuture (com tolerância de 1 dia), formatRelativeDay ("hoje", "ontem", "20/11")
├── cpf.ts              # normalize, isValid, mask
├── schemas/            # esquemas zod de cada RecordWrite e do PropertyWrite
└── index.ts
```

Exemplo:

```ts
// Calcula quais campos recomendados de um registro ainda estão vazios (doc 1 §2.5; espelha o servidor).
import type { RecordWrite } from "@agrofacil/api-types";

const RECOMMENDED: Record<RecordWrite["type"], (r: any) => string[]> = {
  PLANTING: (r) => [
    !r.plantedArea && "plantedArea",
    !r.seedOrigin && "seedOrigin",
    !r.expectedHarvestOn && "expectedHarvestOn",
  ].filter(Boolean) as string[],
  INPUT_APPLICATION: (r) => [!r.quantity && "quantity", !r.purpose && "purpose"].filter(Boolean) as string[],
  HARVEST: (r) => [!r.quantity && "quantity"].filter(Boolean) as string[],
  INPUT_PURCHASE: (r) => [
    !r.quantity && "quantity",
    !r.supplier && !r.hasPhoto && "supplierOrReceipt",
  ].filter(Boolean) as string[],
  OUTFLOW: (r) => [!r.quantity && "quantity", !r.destination && "destination"].filter(Boolean) as string[],
};

/** Campos recomendados que faltam. A lista vazia significa registro "Completo". */
export function missingFields(record: RecordWrite & { hasPhoto?: boolean }): string[] {
  return RECOMMENDED[record.type](record);
}
```

> **Mesma regra em dois lugares:** a completude e o status da certificação existem no Java e aqui. Mantenha uma **tabela de casos de teste em JSON** (`packages/domain/test-cases/completeness.json`) e rode-a nos dois lados: no Vitest e num teste JUnit que lê o mesmo arquivo. Se alguém mudar a regra só de um lado, o teste quebra.

---

## Parte C: app do agricultor (PWA)

O app do agricultor é um **PWA**: uma aplicação web (React + Vite) feita para o celular, que funciona sem internet e pode ser instalada na tela inicial. A primeira proposta era um app nativo em Expo / React Native com SQLite; ela foi trocada pelo PWA ([doc 1, ADR-19](1-arquitetura-e-contrato.md#14-registro-de-decisões-adrs)). A [Parte A](#parte-a-experiência-do-agricultor-ux) continua valendo como especificação das telas; o que dela ainda não existe no app está em [§C.10](#c10-telas-e-cobertura-da-parte-a).

### C.1 Stack e comandos

| Item | Escolha |
|---|---|
| Base | React 19 + Vite + TypeScript (`apps/mobile`) |
| Rotas | React Router 7 |
| Dados | TanStack Query v5 sobre um cliente `openapi-fetch` que responde do espelho local ([§C.6](#c6-motor-de-sincronização)) |
| Tipos e regras | `@agrofacil/api-types` e `@agrofacil/domain` ([Parte B](#parte-b-comum)) |
| Offline | service worker próprio, `localStorage` (espelho e fila) e IndexedDB (fotos) |
| Lint e testes | oxlint e Vitest |

Na raiz do repositório:

- `pnpm --filter @agrofacil/mobile dev --host`: servidor em http://localhost:5174. O `--host` deixa abrir no celular pelo IP do computador. O Vite repassa `/api` para a API em `localhost:8080`.
- `pnpm --filter @agrofacil/mobile build`: gera `apps/mobile/dist/`, com o service worker.
- `pnpm --filter @agrofacil/mobile test`, `lint` e `typecheck`.

> **Contexto seguro:** `crypto.subtle` (hash das fotos) e o service worker só existem em HTTPS ou em `localhost`. Pelo IP da rede local o app abre e registra, mas não envia fotos nem funciona offline. Em produção, sirva sempre por HTTPS ([doc 2, Parte 3](2-guia-api.md#parte-3-ambiente-e-implantação)).

### C.2 Estrutura de pastas

```text
apps/mobile/
├── index.html, public/            # manifest.webmanifest, ícones do PWA (192 e 512 px)
├── sw/sw-template.js              # modelo do service worker (o build gera o sw.js)
├── vite.config.ts                 # proxy /api e o plugin que monta a lista de arquivos do sw
└── src/
    ├── main.tsx, App.tsx          # rotas, QueryClient, registro do service worker
    ├── a11y.ts                    # teclado em elementos com role de botão
    ├── api/
    │   ├── client.ts              # sessão, fila offline, sincronização, fotos e o fetch local
    │   ├── syncState.ts           # espelho do /sync/pull (aplica mudanças e exclusões)
    │   ├── pushResults.ts         # separa as mutações aceitas, pendentes e recusadas
    │   ├── photoQueue.ts          # fotos no IndexedDB até chegarem à API
    │   ├── recordPhotos.ts        # junta fotos da API e do aparelho
    │   ├── pendingRecords.ts      # registros que ainda estão na fila
    │   └── uuid.ts                # UUID v4 mesmo fora de contexto seguro
    ├── auth/                      # AuthContext, rota protegida, ativação
    ├── layout/MobileShell.tsx     # banner de sincronização e recusas, termos (LGPD), menu inferior
    ├── components/                # RecordPhotos, RecordTypeIcon
    ├── pages/                     # Home, Propriedades, Registros, Novo registro, Perfil, Avisos, Ativação
    └── styles/theme.css           # cores, tipografia e componentes base
```

### C.3 Tema e componentes base

- O tema fica em `styles/theme.css`, com variáveis CSS (`--color-primary`, `--color-surface`, `--radius-md`…) tiradas do protótipo do Figma, e classes reutilizáveis (`mobile-card`, `btn-primary`, `form-input`, `badge-status`).
- Fonte Poppins, textos grandes e botões com no mínimo 48 px de altura ([§A.10](#a10-acessibilidade-e-visual)).
- Imagens em WebP; os ícones dos tipos de manejo ficam no componente `RecordTypeIcon`.
- **Acessibilidade:** todo campo tem `<label htmlFor>`. Cartões clicáveis usam `role="button"` (ou `role="radio"` na escolha do tipo de manejo), `tabIndex={0}` e respondem a Enter e espaço (`a11y.ts`).

### C.4 Dados guardados no aparelho

| Onde | Chave | O quê |
|---|---|---|
| `localStorage` | `af_mobile_access_token`, `af_mobile_refresh_token`, `af_mobile_device_id`, `af_mobile_user` | Sessão e identificação do aparelho |
| `localStorage` | `af_mobile_sync_state` | Espelho do `/sync/pull`: cursor e coleções (`version: 3`) |
| `localStorage` | `af_mobile_offline_queue` | Mutações ainda não enviadas (`/sync/push`) |
| `localStorage` | `af_mobile_rejected_changes` | Mutações que a API recusou e o agricultor ainda não viu |
| IndexedDB `agrofacil-mobile` | `pending-photos` | Fotos (Blob) esperando envio, com hash e ids fixos |
| Cache Storage | `agrofacil-mobile-<versão>` | Arquivos do app para abrir sem internet |

- **Sair do app** (`clearSession`) apaga a sessão e o espelho (LGPD), mas **mantém** a fila e as fotos pendentes, para nada se perder se o agricultor sair sem internet.
- Nada disso é criptografado; o risco e as defesas estão no [doc 1, Parte 5](1-arquitetura-e-contrato.md#parte-5-segurança-e-lgpd).

### C.5 Autenticação e sessão

- **Ativação** ([§A.3](#a3-primeiro-acesso-ativação)): CPF + código de 6 dígitos em `POST /auth/farmer/activate`, com o `deviceId` (UUID gerado uma vez e guardado) e a plataforma `WEB`.
- **Tokens:** o de acesso dura 15 minutos. Toda chamada passa por `authFetch`, que põe o `Authorization` e, em 401, renova com o refresh token **uma vez** e repete a chamada. Renovações simultâneas compartilham a mesma promessa.
- **Sem internet** a renovação falha sem derrubar a sessão: o agricultor continua trabalhando offline. A sessão só é encerrada quando a API responde 401/403 ao refresh.
- **Troca de conta:** espelho, fila e fotos são separados por conta. Ao sair, o espelho é apagado (LGPD) e a fila e as fotos ficam guardadas para aquela conta. Se a conta muda (inclusive em outra aba), respostas da sessão anterior são ignoradas e o cache das telas é limpo.
- **Termos (LGPD):** se `termsAcceptedVersion` for diferente de `currentTermsVersion`, o `MobileShell` mostra o aceite (`POST /me/terms/accept`).

### C.6 Motor de sincronização

As telas usam o mesmo cliente `openapi-fetch` do painel, mas o `fetch` dele é trocado por `mobileSyncFetch`, que responde **a partir do espelho local**:

- **Leitura:** `GET /properties`, `/properties/{id}`, `/properties/{id}/plots`, `/records`, `/records/{id}`, `/announcements`, `/notifications`, `/catalog/crops`, `/catalog/farm-inputs` e `/catalog/units` são montadas do espelho, junto com os registros que ainda estão na fila. As demais rotas vão direto à API.
- **Pull incremental:** `fetchSyncSnapshot` chama `GET /sync/pull?cursor=` página a página (500 itens, até 50 páginas) até `hasMore=false`, aplica as mudanças com `applyPull` e grava o novo cursor. `reset=true` recomeça do zero. Itens sem `id` (unidades e itens do roteiro) usam `code` como chave. Chamadas seguidas em menos de 2,5 s reaproveitam o último resultado.
- **Escrita** (`submitMutation`): criar ou editar registro, propriedade ou resultado de visita gera uma mutação com `mutationId` gerado no aparelho, enviada por `/sync/push`. Sem rede, com a API fora do ar ou com itens na frente, ela entra na fila, para manter a ordem. Mutações para o mesmo item são unificadas (vale o último estado).
- **Envio da fila** (`flushOfflineQueue`): em lotes de até 100, ao abrir o app, ao voltar a internet (evento `online`) e no botão "Sincronizar". Resultado por mutação:
  - `APPLIED`: sai da fila;
  - `REJECTED`: fica na fila com o motivo e não é reenviada sozinha. O `MobileShell` mostra num aviso vermelho, com "Corrigir" (abre a tela do item) e "Descartar";
  - sem resposta (rede ou 5xx): fica na fila para a próxima tentativa.
- **Selo "Aguardando envio":** registros com mutação na fila (não recusada) aparecem assim na lista e no detalhe (`usePendingRecordIds`), porque a completude só é calculada pela API depois do envio.

### C.7 Fotos

Fluxo em duas etapas ([doc 1, §3.6](1-arquitetura-e-contrato.md#36-arquivos-fotos-áudios-pdfs)):

1. A foto é guardada no IndexedDB com `attachmentId`, `mutationId` e SHA-256 fixos.
2. Quando o registro dela já foi enviado, o app registra o anexo (mutação `attachment`) e depois manda o arquivo (`PUT /files/{id}/content`). Cada etapa concluída é gravada, então uma queda no meio não repete trabalho.
3. 401, 408, 429 e 5xx tentam de novo depois; outros 4xx (ex.: `CHECKSUM_MISMATCH`) ficam com o motivo, e a pessoa pode descartar a foto.

O detalhe do registro mostra as fotos enviadas (baixadas com o token) e as que ainda estão no aparelho, com o selo "Aguardando envio". As fotos baixadas ficam em cache só enquanto o app está aberto.

### C.8 Formulário de registro

`NewRecordPage` cria e edita os 5 tipos (plantio, aplicação, colheita, compra e saída) numa tela só: primeiro o tipo, depois os campos daquele tipo, com os catálogos da associação e "Outro" em texto livre.

- **Vínculos:** colheita e aplicação mostram os plantios em andamento, com a opção "Ver plantações terminadas" (RN11); a saída mostra também os encerrados e sugere o produto do plantio (RN42). A colheita se liga sozinha quando há exatamente um plantio em andamento, e a edição sempre mostra o plantio já vinculado ([`plantings.ts`](../packages/domain/src/plantings.ts)). Plantio pede a área (seleção automática só quando a propriedade tem uma área).
- **Unidades:** vêm do catálogo da API pelo espelho local, sem as de área ([`quantityUnits.ts`](../packages/domain/src/quantityUnits.ts)).
- **Foto opcional**, tirada pela câmera (`capture="environment"`).
- **Pedido de correção:** o registro com pedido aberto mostra a mensagem da coordenação; ao salvar, a API resolve o pedido.
- **Tamanho da propriedade** (em `PropertyDetailPage`): o app mostra quanto as áreas ocupam e não deixa salvar um tamanho menor (RN41).

### C.9 Avisos

Os avisos ficam **só dentro do app**: a tela de avisos lista as notificações do espelho e marca todas como lidas (`POST /notifications/read-all`). A tela inicial mostra os comunicados recentes. Não há push nem lembretes locais; o envio pelo Expo fica desligado na API (`PUSH_ENABLED=false`).

### C.10 Telas e cobertura da Parte A

| Rota | Tela |
|---|---|
| `/login` | Ativação (CPF + código) |
| `/` | Início: saudação, grupo, propriedade principal, últimos manejos e comunicados |
| `/propriedades`, `/propriedades/:id` | Propriedades, áreas, trabalhadores, edição de produção, tamanho e roteiro de acesso |
| `/registros` | Caderno de campo, do registro criado por último ao mais antigo, com filtro por tipo e detalhe (fotos, pendências e correções) |
| `/registros/novo`, `/registros/:id/editar` | Formulário de registro |
| `/visitas`, `/visitas/:id` | Visitas do SPG e resultado, só para o responsável pelo registro (funciona offline) |
| `/notificacoes` | Avisos |
| `/perfil` | Meus dados, WhatsApp da associação e sair |

**Ainda não implementado** em relação à [Parte A](#parte-a-experiência-do-agricultor-ux):

- GPS da propriedade e dos registros (RF10), áudio e registro por voz;
- assistente passo a passo (hoje é um formulário por tipo);
- lembretes e notificações fora do app (push);
- entrada por digital;
- visitas do SPG ([§A.13](#a13-visitas-do-grupo-e-da-comissão-spg)): cartões e resumo (`brief`) da propriedade visitada;
- cadastro e exclusão de propriedade pelo agricultor (o app só edita).

### C.11 Testes do mobile

Vitest, sem navegador, nas partes que têm regra: `syncState` (aplicação do pull), `pushResults` (separação dos resultados), `recordPhotos`, `pendingRecords`, `uuid`, ativação e o cliente HTTP. Os testes rodam com `pnpm --filter @agrofacil/mobile test` e no CI.

### C.12 Build e distribuição

- `pnpm --filter @agrofacil/mobile build` gera `apps/mobile/dist/`. Um plugin do `vite.config.ts` monta o `sw.js` a partir de `sw/sw-template.js`, com a lista de todos os arquivos do build e uma versão calculada pelo hash deles.
- **Service worker:** guarda os arquivos na instalação; navegação com rede primeiro (até 4 s) e o `index.html` guardado como reserva; demais arquivos do cache primeiro (com `ignoreVary`, porque o servidor manda `Vary: Origin`). Ao publicar uma versão nova, o cache antigo é apagado.
- **Publicação:** sirva `dist/` por HTTPS no mesmo domínio da API (o cliente usa `/api/v1` relativo). O agricultor abre o endereço no navegador e usa "Adicionar à tela inicial".

## Parte D: painel do coordenador (Vite + React)

### D.1 Stack e comandos

| Item | Escolha |
|---|---|
| Base | React 19 + Vite + TypeScript (`apps/web`) |
| Rotas | React Router 7 |
| Dados | TanStack Query v5 + `openapi-fetch` com os tipos de `@agrofacil/api-types` |
| Visual | CSS próprio com a identidade do Figma (`index.css` e `App.css`, classes `af-*`) |
| Lint e testes | oxlint e Vitest |

- `pnpm --filter @agrofacil/web dev`: painel em http://localhost:5173. O proxy do `vite.config.ts` repassa `/api` para `localhost:8080`, então painel e API ficam na mesma origem e não há CORS.
- `pnpm --filter @agrofacil/web build`, `test`, `lint` e `typecheck`.

### D.2 Estrutura

```text
apps/web/src/
├── main.tsx, App.tsx          # QueryClient (com avisos de erro), rotas e RequireCoordinator
├── index.css, App.css         # tema e classes af-* (cartões, tabelas, formulários, badges, alertas)
├── api/
│   ├── client.ts              # openapi-fetch, token em memória, refresh por cookie, unwrap e ApiError
│   ├── pagination.ts          # fetchAllPages: junta todas as páginas para menus de seleção
│   └── municipalities.ts      # busca na API com a lista local da Bahia como reserva
├── auth/                      # AuthContext e telas de login, 2FA, senha e convite
├── layout/AppLayout.tsx       # menu lateral em três grupos, cabeçalho com sino de notificações
├── components/                # Logo, StatusBadge, Pagination, AttachmentGallery, Toaster
└── features/
    ├── dashboard/             # painel inicial
    ├── farmers/               # lista, cadastro e ficha com abas
    ├── properties/            # lista (cartões, tabela, coordenadas), detalhe com áreas e trabalhadores
    ├── records/               # lista, registrar pelo agricultor, detalhe com fotos e pedidos de correção
    ├── certifications/        # lista, emitir, editar e renovar
    ├── groups/
    ├── spg/                   # visitas, documentos D1–D8, pessoas do SPG
    └── management/            # comunicados, catálogo, relatórios e configurações
```

### D.3 Autenticação no painel

- **Token de acesso em memória** (variável de módulo), nunca no `localStorage`. O refresh token está no cookie `HttpOnly` `af_refresh`, que o JavaScript não lê.
- **Ao carregar a página:** `POST /api/v1/auth/refresh` com `credentials: "include"` e `X-Requested-With: agrofacil-web`. Deu certo: busca `/me`. Deu 401: vai para o login.
- O middleware do `openapi-fetch` põe o `Authorization` e, em 401, faz **uma** renovação (compartilhada entre chamadas simultâneas) e repete a requisição.
- **Telas:**

| Rota | Tela |
|---|---|
| `/entrar` (ou `/login`) | E-mail e senha |
| `/entrar/codigo` (ou `/login/verificar`) | Código de 2 etapas enviado por e-mail |
| `/esqueci-senha` | Pede o e-mail |
| `/redefinir-senha?token=` | Nova senha |
| `/convite?token=` | Nome e senha do coordenador convidado |

### D.4 Dados: TanStack Query

- **Padrões do `QueryClient`:** `staleTime` de 15 s, uma nova tentativa e sem recarregar ao voltar para a aba.
- **`unwrap`** transforma a resposta de erro do `openapi-fetch` num `ApiError` com `status`, `code`, `detail` e `errors[]` (Problem Details, RFC 9457).
- **Erros de ações:** o `MutationCache` mostra um aviso no canto da tela (`Toaster`) com a mensagem da API sempre que uma mutação falha sem tratar o próprio erro. Para silenciar, use `onError` na mutação ou `meta: { silentError: true }`. Formulários com erro por campo (ex.: áreas da propriedade) mostram a mensagem junto do campo.
- **Listas** paginam no servidor (`page`, `size`, `sort`) e usam o componente `Pagination`. **Menus de seleção** que precisam da lista inteira usam `fetchAllPages` (páginas de 100, o máximo da API), com chaves de cache próprias (ex.: `["farmers", "all-select", "ACTIVE"]`).
- **Binários** (fotos, PDFs, CSV) são baixados com `fetch` + token, porque `<img src>` e links não enviam o `Authorization`.

### D.5 Rotas e menu

O menu lateral tem três grupos: **Operação**, **Certificação Participativa (SPG)** e **Gestão da Associação**.

| Menu | Rota | Tela |
|---|---|---|
| Início | `/` | Painel inicial |
| Agricultores | `/agricultores`, `/agricultores/novo`, `/agricultores/:id` | Lista, cadastro, ficha com abas |
| Propriedades | `/propriedades`, `/propriedades/:id` | Cartões, tabela ou coordenadas; detalhe |
| Registros de manejo | `/registros`, `/registros/novo`, `/registros/:id` | Lista com filtros, registrar pelo agricultor, detalhe |
| Certificações | `/certificacoes` | Lista, emitir, editar e renovar |
| Grupos do SPG | `/grupos` | |
| Visitas do SPG | `/visitas` | Situação do ciclo e visitas (abas) |
| Documentos | `/documentos` | Gerar, listar, cópia assinada, conferir código |
| Pessoas e impedimentos | `/spg` | Comissão, colaboradores e impedimentos (abas) |
| Comunicados | `/comunicados` | |
| Catálogos e roteiro | `/catalogo` | Culturas, insumos, unidades, roteiro e itens sem catálogo |
| Relatórios | `/relatorios` | PDF e CSV |
| Configurações | `/configuracoes` | Associação e coordenadores |

### D.6 Telas

> Especificação das telas. No painel atual: o painel inicial não tem gráficos (só indicadores e listas); visitas, pessoas do SPG, catálogo e configurações são abas numa rota só; as ações de exclusão ainda não pedem confirmação.

**Painel (`/`)**, a partir de `GET /dashboard/summary`, atualizado a cada 60 s com `refetchInterval` e `refetchIntervalInBackground: false`:
- **Linha de indicadores:**
  - agricultores ativos;
  - certificações em dia, vencendo e vencidas;
  - registros nos últimos 30 dias;
  - registros incompletos;
  - pedidos de correção abertos;
  - visitas urgentes e visitas sem resultado.

  Cada indicador é clicável e abre a lista já filtrada.
- **Gráfico de rosca:** certificações por status (RF19).
- **Gráfico de barras:** registros por semana (12 semanas).
- **Tabela "Vencendo em breve":** próximos vencimentos, com o botão "Renovar".
- **Lista "Precisam de atenção":** agricultores sem registro há 30 dias, sem aparelho ativo, e os itens sem catálogo.

**Agricultores:**
- **Lista:** nome, CPF mascarado, grupo, município, nº de propriedades, último envio, último registro, incompletos e pior status de certificação. Filtros: busca, grupo, status, município.
- **Novo:** formulário de uma tela com nome, CPF (máscara e validação com o `domain`), telefone, e-mail opcional, grupo, CAF opcional e endereço com município pesquisável.
  - Ao salvar, **abre direto o modal do código de acesso**.
  - Depois, sugere "Cadastrar a propriedade deste agricultor".
- **Detalhe (abas):**
  - **Dados:** edição;
  - **Propriedades:** cartões, com "Nova propriedade";
  - **Registros:** a lista de registros filtrada por ele;
  - **Aparelhos:** lista com "Revogar" e o botão **"Gerar código de acesso"**.

  Ações no topo: Desativar / Reativar.
- **Modal do código:**

```text
┌──────────────────────────────────────┐
│  Código de acesso de José da Silva    │
│                                      │
│            4 8 2   9 1 3             │   ← fonte bem grande, monoespaçada
│                                      │
│  Vale até 27/10 às 14:00 (72 h).      │
│  Ele só aparece agora: anote ou envie.│
│                                      │
│  [ Copiar ] [ Enviar pelo WhatsApp ] [ Imprimir ] │
└──────────────────────────────────────┘
```

**Propriedades:**
- **Lista e mapa.** No mapa (react-leaflet com tiles do OpenStreetMap e atribuição visível), um marcador colorido por status da certificação, com popup com link.
- **Detalhe (abas):**
  - **Dados:** edição. A localização usa um `MapPicker`: clique no mapa ou digite lat/lng;
  - **Áreas:** tabela editável com nome, tamanho, **status** e fim da conversão. É aqui que o coordenador cria as áreas;
  - **Trabalhadores;**
  - **Certificações:** histórico, com "Nova" e "Renovar";
  - **Registros;**
  - **Caderno de campo:** escolha do período e "Baixar PDF";
  - **Visitas e documentos:** situação do ciclo, visitas da propriedade e documentos gerados, com os atalhos para marcar visita e gerar documento.
- **Excluir:** confirmação. Mostra o erro RN07 com um texto claro.

**Registros:**
- **Lista:** colunas Nº, data, tipo (ícone + rótulo), agricultor, propriedade/área, assunto, quantidade, completude, correções e origem (📱 / 💻). Filtros: tipo, agricultor, propriedade, período, incompletos, com correção aberta e origem. Botão "Exportar CSV" com os filtros atuais.
- **Detalhe:**
  - campos formatados, fotos (miniaturas que abrem em tamanho grande), áudio (`<audio controls>` com o blob) e mapa pequeno com o ponto do GPS;
  - **pedidos de correção:** lista + "Pedir correção" (modal com mensagem);
  - **histórico:** linha do tempo de `GET /records/{id}/history` (quem, quando, de onde e o que mudou: "Quantidade: 20 molhos → 30 molhos");
  - ações: Editar e Excluir.
- **Registrar pelo agricultor (`/registros/novo`):**
  - formulário de uma tela que muda conforme o tipo escolhido;
  - selects dependentes: agricultor → propriedade → área / plantio;
  - usa os mesmos esquemas zod do `domain`;
  - mostra o aviso da [§A.11](#a11-painel-do-coordenador-princípios-de-ux).

**Certificações:**
- **Lista:** propriedade, agricultor, nº, emissão, validade, dias restantes, status (badge) e filtros (status, vencendo em N dias).
- **Formulário:**
  - propriedade (pesquisável);
  - nº, emissão;
  - validade, preenchida com +1 ano;
  - escopo;
  - **produtos** (multiselect de culturas + "adicionar texto");
  - **pessoas vinculadas** (checkbox dos trabalhadores da propriedade, com atalho "Cadastrar trabalhador");
  - observações;
  - upload do PDF.
- **Ações:**
  - **Renovar** (abre o formulário pré-preenchido, com emissão igual à validade anterior). Antes, o painel consulta `GET /properties/{id}/visit-status` e, se faltar a visita da comissão, pergunta: "Não há visita da comissão registrada neste ciclo. Renovar mesmo assim?" (RN39);
  - Suspender e Cancelar (motivo obrigatório);
  - Reativar;
  - **Gerar certificado (modelo)** (D8).

**Grupos:** lista com nº de agricultores e o alerta "menos de 3" (RN01). O detalhe mostra os agricultores, com "Mover para outro grupo".

**Visitas do SPG** ([doc 1, Parte 6](1-arquitetura-e-contrato.md#parte-6-certificação-participativa-spg-visitas-e-documentos)):
- **Planejamento (`/visitas`)**, a partir de `GET /visits/status`:
  - uma linha por propriedade: agricultor, grupo, vencimento, "Visitas do grupo 1 de 2", "Visita da comissão 0 de 1", visitas marcadas e a situação (badge verde, amarela ou vermelha);
  - filtros: grupo, situação, "só com período de visitas aberto";
  - as `URGENT` aparecem primeiro;
  - por grupo, um cabeçalho com o nº de agricultores e o "mínimo por amostragem: 3", só como informação;
  - ações por linha: `Marcar visita do grupo` e `Marcar visita da comissão`.
- **Marcar visita** (gaveta lateral):
  1. Tipo, data e, só na visita da comissão, a caixa **"Visita sem aviso ao agricultor"**, com a explicação "O agricultor não vai ser avisado. Só os visitadores".
  2. Ao escolher a data, o painel chama `POST /visits/suggest-visitors` e mostra:

```text
Sugestão de visitadores                        (a escolha final é sua, depois de combinar com o grupo)
 [x] Ana Lima        a 2,1 km · não visitou esta roça no último ano
 [x] João Souza      a 4,2 km · não visitou esta roça no último ano · 1 visita marcada
 [ ] Pedro Alves     a 3,0 km · já visitou esta roça no último ano
 [ ] Rita Gomes      a ~15 km (sem localização) · não visitou esta roça no último ano
 Não podem visitar: Maria da Silva (impedimento cadastrado) · Carlos Dias (outra visita no mesmo dia)
 Convidar (opcional): [ ] Téc. Lúcia (técnica)   [ ] Seu Antônio (consumidor)
 Quem vai anotar no celular: ( Ana Lima ▾ )     ← só agricultores marcados que têm aparelho ativo; "Ninguém (papel)"
                                             [ Cancelar ]  [ Marcar visita ]
```

  3. Ao salvar, o painel mostra o botão **"Avisar pelo WhatsApp"** para cada visitador, com um link `wa.me` montado no front e a mensagem pronta. Ele **não** aparece para o visitado numa visita sem aviso.
- **Agenda (`/visitas/agenda`):** lista por data (e calendário mensal opcional), com filtros de grupo, tipo e situação. Visitas com a data passada e sem resultado aparecem em vermelho.
- **Detalhe da visita (`/visitas/:id`):**
  - cabeçalho com tipo, data, situação, "sem aviso" e participantes (com os motivos da sugestão num tooltip);
  - ações:
    - `Editar` (data e participantes; só se `PLANNED`);
    - `Cancelar` (motivo obrigatório);
    - `Imprimir roteiro` (gera o D4);
    - `Registrar resultado`;
    - `Gerar registro` / `Gerar relatório` (D5/D6, depois de `DONE`);
  - aba **Resumo:** o `brief`, como o visitador vê;
  - aba **Resultado:** respostas, pontos a melhorar, recomendações, fotos, origem (📱 / 💻) e histórico.
- **Registrar resultado** (digitar a folha de papel): formulário de uma tela que segue a ordem da folha impressa.
  - Quem foi: caixas.
  - Uma linha por pergunta, com quatro botões de rádio e observação.
  - "Encontraram algo que precisa melhorar?", pontos a melhorar, recomendações e data.
  - **Anexar a foto da folha assinada**, obrigatório quando a visita foi no papel.
- **Documentos (`/documentos`):**
  - **Lista:** tipo, assunto, data, código, situação (Gerado / Assinado / Substituído / Cancelado) e ações `Baixar`, `Enviar cópia assinada` e `Cancelar`.
  - **Botão "Gerar documento":** escolhe o tipo e mostra só os campos daquele tipo (agricultor e propriedade; grupo e período; visita; certificação). Se a resposta trouxer `warnings`, eles aparecem antes do download: "O documento foi gerado com este aviso impresso: …".
  - **Campo "Conferir código":** chama `GET /documents/verify/{code}`.
  - Atalhos para gerar documentos também aparecem no detalhe da propriedade, da visita, do grupo e da certificação.
- **Pessoas do SPG (`/spg/...`):**
  - **colaboradores:** tabela editável (nome, tipo, organização, telefone, grupo, ativo);
  - **Comissão de Avaliação:** quem é titular ou suplente de cada grupo, com início e fim;
  - **impedimentos:** pares de agricultores e motivo, com o aviso "Isto só aparece para coordenadores".
- **Roteiro (`/catalogo/roteiro`):** as perguntas, com o texto do app e o do PDF, tipo, ordem e ativo. O `code` é só para leitura depois de criado.

**Comunicados:** lista com tipo, título, destino (todos ou grupo), data e autor. O modal "Novo" tem tipo (Aviso / Dica), título, texto (contador de caracteres), destino, imagem opcional e **pré-visualização de como aparece no celular**.

**Catálogo:**
- culturas e insumos em tabelas editáveis (nome, categoria/tipo, ícone, ativo, referência normativa, precisa de autorização, caseiro);
- unidades com ativar/desativar e ordem;
- **"Sem catálogo"**: textos livres agrupados com nº de ocorrências e um botão **"Associar a…"** com um select do catálogo, ou "Criar item com este nome".

**Relatórios:**
- cartões, um por relatório ([doc 1 §3.14](1-arquitetura-e-contrato.md#314-relatórios)), com os filtros e o botão "Baixar PDF" / "Baixar planilha";
- o download usa `fetch` com o token e transforma a resposta em blob, com o nome tirado do `Content-Disposition`.

**Configurações:**
- dados da associação, incluindo o **WhatsApp**, que o app usa no link "Fale com a associação";
- tamanho da tarefa (m²);
- dias de alerta;
- validade do código de acesso;
- anos de guarda dos dados;
- **SPG:** dias de antecedência do período de visitas, visitas do grupo por ano, visitadores por visita, dias do lembrete e "membro da comissão pode verificar o próprio grupo";
- coordenadores: lista, convidar e desativar.

### D.7 Padrões de UI do painel

- **Classes `af-*`** (em `index.css`): `af-card`, `af-table`, `af-btn`, `af-field` / `af-label` / `af-input`, `af-badge`, `af-alert`, `af-modal`, `af-pagination`. Novas telas usam essas classes antes de criar estilos novos.
- **Badges de status:** as mesmas cores e frases do app, tiradas dos rótulos do `domain` (`StatusBadge`).
- **Datas e números:** `formatDatePtBr`, `formatArea` e `formatQuantity` do `domain` (`20/11/2026`, `2,5 hectares`).
- **Formulários:** todo campo tem `<label htmlFor>` ligado ao `id`. Grupos de opções (botões ou caixas de seleção) usam `role="group"` com `aria-labelledby`.
- **Carregamento e vazio:** `af-empty-state` com o texto do que está acontecendo. Nunca uma tela branca.
- **Segurança:** nada de `dangerouslySetInnerHTML`. O texto dos comunicados é exibido como texto puro (`white-space: pre-wrap`).

### D.8 Testes do painel

| Tipo | Ferramenta | O que existe |
|---|---|---|
| Unitário | Vitest | Cliente HTTP (`ApiError`, telefone E.164, renovação da sessão), `fetchAllPages` |
| Regras | Vitest em `@agrofacil/domain` | CPF, áreas (RN41), unidades de quantidade, escolha do plantio (RN42), completude, certificação, datas, WhatsApp, municípios |

**Ponta a ponta (Playwright, no CI):** [`fluxo-principal.cjs`](../testes/e2e/fluxo-principal.cjs) cobre login com 2FA, grupo, agricultor, código, propriedade e manejos no app, com e sem internet.

**Ainda falta:** testes de componentes (Testing Library) e ponta a ponta de certificação, visitas e documentos.

### D.9 Build

`pnpm --filter @agrofacil/web build` gera `apps/web/dist/`, que o nginx serve ([doc 2, Parte 3](2-guia-api.md#parte-3-ambiente-e-implantação)). A URL da API é a mesma origem da página (`/api/v1`), então o mesmo build funciona em qualquer endereço.

---

## Parte E: estado da implementação do front

A ordem original (marcos F0 a F8) foi seguida com o app como PWA. Situação em novembro de 2026:

| Marco | App do agricultor (PWA) | Painel |
|---|---|---|
| F0 | Feito: monorepo, lint, `domain` com testes, `api-types` gerado | Feito |
| F1 | Feito: tema e navegação | Feito: layout, rotas, login com 2FA |
| F2 | Feito: formulários dos 5 tipos, funcionando offline | Feito: agricultores, código de acesso, grupos |
| F3 | Feito: ativação e sessão. **Falta:** digital | Feito: propriedades, áreas (RN41), trabalhadores, coordenadas |
| F4 | Feito: sincronização incremental, fila, recusas, fotos | Feito: registros, detalhe com fotos, pedidos de correção |
| F5 | Feito: avisos no app e comunicados. **Fora por decisão:** push e lembretes locais | Feito: certificações (emitir, editar, renovar com aviso da RN39), comunicados, notificações |
| F6 | Feito: propriedades (edição), Meus dados. **Falta:** voz e GPS | Feito: painel inicial, catálogo, relatórios, configurações |
| F7 | **Falta:** testes com agricultores ([§A.12](#a12-testes-com-agricultores)) | **Falta:** testes com o coordenador |
| F8 | Feito: lista de visitas e resultado pelo visitador. **Falta:** cartões e resumo | Feito: situação do ciclo, marcar visita com sugestão, resultado, documentos D1–D8, pessoas do SPG |

O maior risco continua sendo a usabilidade com os agricultores (F7): teste o app com eles antes de acrescentar funções.
