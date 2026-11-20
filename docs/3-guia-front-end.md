# Agrofácil: guia do front-end

O front tem dois apps no mesmo monorepo. Este guia está dividido assim:

- **[Parte A](#parte-a-experiência-do-agricultor-ux): a experiência do agricultor (UX).** Telas, textos, acessibilidade e testes com agricultores. Define *o que* o app mostra.
- **[Parte B](#parte-b-comum): o que é comum.** O monorepo, os tipos gerados da API e o pacote `domain`, com as regras compartilhadas.
- **[Parte C](#parte-c-app-do-agricultor-expo): o app do agricultor.** Expo / React Native, offline-first.
- **[Parte D](#parte-d-painel-do-coordenador-vite--react): o painel do coordenador.** Vite / React, desktop-first.
- **[Parte E](#parte-e-ordem-de-implementação-do-front): a ordem de implementação.**

> **Leia antes:** o contrato ([doc 1, Parte 3](1-arquitetura-e-contrato.md#parte-3-contrato-api-front)) e a sincronização ([doc 1, Parte 4](1-arquitetura-e-contrato.md#parte-4-sincronização-offline), para o mobile). Depois, a [Parte A](#parte-a-experiência-do-agricultor-ux) deste guia: ela define as telas do app, e as Partes B a D mostram como construí-las.

**Os três documentos:** [doc 1: arquitetura, domínio e contrato](1-arquitetura-e-contrato.md) · [doc 2: guia da API](2-guia-api.md) · **doc 3: guia do front-end (este)**

## Sumário

- **[Parte A: experiência do agricultor (UX)](#parte-a-experiência-do-agricultor-ux)**: [A.1 Princípios](#a1-princípios) · [A.2 Navegação](#a2-navegação) · [A.3 Primeiro acesso (ativação)](#a3-primeiro-acesso-ativação) · [A.4 Tela inicial](#a4-tela-inicial) · [A.5 Fluxos de registro](#a5-fluxos-de-registro) · [A.6 Propriedades (RF04–RF09)](#a6-propriedades-rf04rf09) · [A.7 Avisos e lembretes](#a7-avisos-e-lembretes) · [A.8 Meus dados e ajuda](#a8-meus-dados-e-ajuda) · [A.9 Vocabulário](#a9-vocabulário) · [A.10 Acessibilidade e visual](#a10-acessibilidade-e-visual) · [A.11 Painel do coordenador: princípios de UX](#a11-painel-do-coordenador-princípios-de-ux) · [A.12 Testes com agricultores](#a12-testes-com-agricultores) · [A.13 Visitas do grupo e da comissão (SPG)](#a13-visitas-do-grupo-e-da-comissão-spg)
- **[Parte B: comum](#parte-b-comum)**: [B.1 Monorepo com pnpm](#b1-monorepo-com-pnpm) · [B.2 Qualidade de código](#b2-qualidade-de-código) · [B.3 Tipos gerados da API (`@agrofacil/api-types`)](#b3-tipos-gerados-da-api-agrofacilapi-types) · [B.4 Pacote `@agrofacil/domain`](#b4-pacote-agrofacildomain)
- **[Parte C: app do agricultor (Expo)](#parte-c-app-do-agricultor-expo)**: [C.1 Criando o projeto](#c1-criando-o-projeto) · [C.2 Estrutura de pastas](#c2-estrutura-de-pastas) · [C.3 Tema e componentes base](#c3-tema-e-componentes-base) · [C.4 Banco local (expo-sqlite + Drizzle)](#c4-banco-local-expo-sqlite--drizzle) · [C.5 Autenticação e sessão](#c5-autenticação-e-sessão) · [C.6 Motor de sincronização](#c6-motor-de-sincronização) · [C.7 GPS, foto, áudio e voz](#c7-gps-foto-áudio-e-voz) · [C.8 O assistente de registro](#c8-o-assistente-de-registro) · [C.9 Notificações](#c9-notificações) · [C.10 Telas principais](#c10-telas-principais) · [C.11 Testes do mobile](#c11-testes-do-mobile) · [C.12 Build e distribuição](#c12-build-e-distribuição)
- **[Parte D: painel do coordenador (Vite + React)](#parte-d-painel-do-coordenador-vite--react)**: [D.1 Criando o projeto](#d1-criando-o-projeto) · [D.2 Estrutura](#d2-estrutura) · [D.3 Autenticação no painel](#d3-autenticação-no-painel) · [D.4 Dados: TanStack Query](#d4-dados-tanstack-query) · [D.5 Rotas e menu](#d5-rotas-e-menu) · [D.6 Telas](#d6-telas) · [D.7 Padrões de UI do painel](#d7-padrões-de-ui-do-painel) · [D.8 Testes do painel](#d8-testes-do-painel) · [D.9 Build](#d9-build)
- **[Parte E: ordem de implementação do front](#parte-e-ordem-de-implementação-do-front)**

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
# .npmrc: o Metro (bundler do React Native) funciona melhor com dependências "achatadas".
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

Os pacotes `domain` e `api-types` são consumidos **direto do código-fonte TypeScript** (`"main": "src/index.ts"`), sem etapa de build. O Vite e o Metro do Expo entendem workspaces sozinhos nas versões atuais. Se o Metro não achar um pacote, confira a seção "Work with monorepos" da documentação do Expo.

### B.2 Qualidade de código

- **TypeScript** com `"strict": true` e `"noUncheckedIndexedAccess": true` em todos os pacotes.
- **Lint** por pacote (`pnpm lint` roda todos):
  - painel e pacotes: **oxlint**, o padrão do template atual do Vite, com as regras de React Hooks;
  - app: `expo lint` (ESLint com `eslint-config-expo`).

  O **Prettier** cuida da formatação (`pnpm format`); o lint não opina sobre estilo.
- **pnpm 12 via Corepack:** a versão está no `packageManager`. O pnpm bloqueia versões publicadas há pouco tempo (`minimumReleaseAge`); as exceções que ele precisou acrescentar ficam comentadas no `pnpm-workspace.yaml`.
- `.editorconfig`: 2 espaços, LF, UTF-8, nova linha no fim. O `.java` usa o que o google-java-format define.
- **Todo arquivo começa com um comentário de uma frase** dizendo o que faz. Funções exportadas têm JSDoc.
- **Nomes:**
  - código em inglês;
  - textos da interface em português, **nunca espalhados no JSX** quando se repetem: ficam em `src/i18n/pt-BR.ts` de cada app, ou nos rótulos do `domain`.
- **Nada de números mágicos:** limites e tempos viram constantes nomeadas (`PUSH_BATCH_SIZE = 100`, `GPS_TIMEOUT_MS = 10_000`).
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

## Parte C: app do agricultor (Expo)

### C.1 Criando o projeto

```bash
cd apps
pnpm create expo-app mobile --template default   # TypeScript + expo-router
cd mobile
npx expo install expo-sqlite expo-secure-store expo-local-authentication expo-location \
  expo-image-picker expo-image-manipulator expo-file-system expo-crypto expo-audio expo-speech \
  expo-notifications expo-background-task expo-task-manager expo-haptics expo-dev-client \
  expo-device expo-application expo-linking @react-native-community/netinfo \
  @shopify/flash-list react-native-svg
pnpm add drizzle-orm openapi-fetch zod react-hook-form @hookform/resolvers zustand date-fns
pnpm add -D drizzle-kit jest-expo @testing-library/react-native
```

- **Use sempre `npx expo install`** para pacotes nativos: ele escolhe a versão compatível com o SDK.
- **Development build, não Expo Go:** push e alguns módulos nativos não funcionam no Expo Go. Rode com `npx expo run:android` (precisa do Android Studio) ou gere com o EAS ([§C.12](#c12-build-e-distribuição)).

`app.config.ts` (trechos):

```ts
// Configuração do app Expo; a URL da API vem do ambiente para trocar entre dev, túnel e produção.
export default {
  name: "Agrofácil",
  slug: "agrofacil",
  scheme: "agrofacil",
  orientation: "portrait",
  android: {
    package: "br.edu.ifba.agrofacil",
    permissions: ["ACCESS_FINE_LOCATION", "CAMERA", "RECORD_AUDIO", "USE_BIOMETRIC"],
  },
  plugins: [
    "expo-router",
    "expo-sqlite",
    "expo-secure-store",
    ["expo-local-authentication", { faceIDPermission: "Usar seu rosto para abrir o Agrofácil." }],
    ["expo-location", { locationWhenInUsePermission: "Para marcar onde o registro foi feito." }],
    ["expo-image-picker", { cameraPermission: "Para tirar fotos dos produtos e da plantação." }],
    ["expo-audio", { microphonePermission: "Para gravar recados nos registros." }],
    "expo-notifications",
    "expo-background-task",
  ],
  extra: { apiUrl: process.env.EXPO_PUBLIC_API_URL },
};
```

### C.2 Estrutura de pastas

```text
apps/mobile/
├── app/                              # rotas (expo-router)
│   ├── _layout.tsx                   # providers, migrações do banco, portão de autenticação
│   ├── (onboarding)/                 # boas-vindas, cpf, codigo, termo, protecao, pronto
│   ├── (app)/
│   │   ├── _layout.tsx               # trava por digital + abas
│   │   ├── (tabs)/index.tsx          # Início
│   │   ├── (tabs)/registros.tsx
│   │   ├── (tabs)/propriedades.tsx
│   │   ├── registrar/[type].tsx      # assistente genérico (PLANTING, HARVEST…)
│   │   ├── registro/[id].tsx         # detalhe
│   │   ├── propriedade/[id].tsx
│   │   ├── propriedade/nova.tsx
│   │   ├── visita/[id].tsx           # detalhe (quem recebe ou quem visita)
│   │   ├── visita/[id]/fazer.tsx     # modo visita (assistente)
│   │   ├── avisos.tsx
│   │   └── meus-dados.tsx
│   └── sessao-expirada.tsx
├── src/
│   ├── api/          # client.ts (openapi-fetch + token), auth.ts
│   ├── db/           # schema.ts (drizzle), client.ts, migrations/, repositories/
│   ├── sync/         # engine.ts, push.ts, pull.ts, files.ts, triggers.ts
│   ├── auth/         # session.ts (SecureStore), biometrics.ts
│   ├── wizard/       # Wizard.tsx, definitions/*.ts, steps/*.tsx, drafts.ts
│   ├── device/       # location.ts, camera.ts, audio.ts, speech.ts, notifications.ts
│   ├── ui/           # componentes base (§C.3)
│   ├── theme/        # cores, tamanhos, tipografia
│   ├── i18n/         # pt-BR.ts
│   └── config.ts     # constantes e leitura de Constants.expoConfig.extra
└── drizzle.config.ts
```

### C.3 Tema e componentes base

Construa um conjunto **pequeno** de componentes próprios, sem biblioteca de UI pesada, para controlar tamanho, contraste e acessibilidade ([§A.10](#a10-acessibilidade-e-visual)).

| Componente | Uso |
|---|---|
| `BigActionButton` | Os 5 botões da tela inicial (ícone + texto, ≥ 56 dp) |
| `ChoiceGrid` | Grade de opções com ícone (culturas, produtos, destinos), com "Mais usados" no topo e "Outro" |
| `ChoiceList` | Lista de cartões grandes (plantios, áreas, propriedades) |
| `NumberStepper` | `[−] 30 [+]` com passo inteligente (1 até 20, depois 5) e toque no número para abrir o teclado |
| `UnitChips` | Botões de unidade, filtrados pelo tipo de medida (área ou quantidade) |
| `QuickDate` | Hoje / Ontem / Outro dia |
| `WizardScaffold` | Cabeçalho com voltar, título e bolinhas de progresso; pergunta grande + 🔊; rodapé com Pular / Próximo |
| `SyncBadge` | ✓ enviado / ⏱ esperando internet / ! precisa de atenção |
| `CertificationBanner` | O aviso fixo da RN21 |
| `Callout` | Caixa amarela de pedido de correção ou aviso |
| `SpeakButton` | Lê um texto com `expo-speech` |
| `ConfirmDialog` | Diálogo com frase clara e botões "Não" / "Sim, apagar" |

```ts
// Tokens visuais do app; tamanhos pensados para leitura no sol e dedos grandes (§A.10).
export const theme = {
  font: { body: 18, question: 24, title: 28, small: 15 },
  touch: { primary: 56, secondary: 48 },
  space: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 },
  radius: { md: 12, lg: 16 },
  color: {
    // Copie as cores da "Identidade Visual" do Figma e confira o contraste (7:1 no texto principal).
    text: "#1B1B1B",
    background: "#FFFFFF",
    primary: "<cor da marca>",
    success: "#1E7B34",
    warning: "#8A5A00",
    danger: "#B3261E",
    muted: "#5F5F5F",
  },
} as const;
```

- A preferência "Tamanho da letra" (Normal / Grande / Muito grande) multiplica `font.*` por 1 / 1,15 / 1,3, através de um contexto `useFontScale()`.
- **Ícones de culturas:** SVGs em `src/ui/crop-icons/`, mapeados pela chave `crop.icon` que vem do servidor. Ícone desconhecido → ícone genérico de planta.

### C.4 Banco local (expo-sqlite + Drizzle)

```ts
// Esquema do banco local: espelho dos dados do servidor + tabelas de controle da sincronização (doc 1 §4.2).
import { sqliteTable, text, integer, real } from "drizzle-orm/sqlite-core";

const localSyncColumns = {
  localStatus: text("local_status", { enum: ["SYNCED", "PENDING", "ERROR"] }).notNull().default("SYNCED"),
  localError: text("local_error"),            // JSON
  serverVersion: integer("server_version"),
  deletedLocally: integer("deleted_locally", { mode: "boolean" }).notNull().default(false),
};

export const records = sqliteTable("records", {
  id: text("id").primaryKey(),
  type: text("type").notNull(),
  number: integer("number"),
  propertyId: text("property_id").notNull(),
  plotId: text("plot_id"),
  plantingId: text("planting_id"),             // copiado do payload para facilitar consultas
  performedOn: text("performed_on").notNull(), // "2026-10-24"
  completeness: text("completeness").notNull(),
  missingFields: text("missing_fields").notNull(), // JSON array
  cycleStatus: text("cycle_status"),           // só plantios
  expectedHarvestOn: text("expected_harvest_on"),
  subjectKey: text("subject_key"),             // cropId / inputId / texto "Outro", para "mais usados"
  createdById: text("created_by_id"),
  payload: text("payload").notNull(),          // JSON com o Record completo do contrato
  updatedAt: text("updated_at"),
  ...localSyncColumns,
});

export const outbox = sqliteTable("outbox", {
  mutationId: text("mutation_id").primaryKey(),
  entity: text("entity").notNull(),
  entityId: text("entity_id").notNull(),
  op: text("op", { enum: ["UPSERT", "DELETE"] }).notNull(),
  data: text("data"),
  baseVersion: integer("base_version"),
  createdAt: text("created_at").notNull(),
  attempts: integer("attempts").notNull().default(0),
  lastError: text("last_error"),
});
// properties, plots, workers, certifications, attachments, correctionRequests, announcements,
// notifications, crops, farmInputs, units, people, me, visits, visitChecklistItems, fileUploads, syncState, drafts…
```

- **Migrações locais:** `drizzle-kit generate` (com `dialect: "sqlite"` e `driver: "expo"`) gera os arquivos. O `useMigrations(db, migrations)` roda as migrações no `_layout.tsx` raiz, antes de qualquer tela.
- **Abra o banco com `openDatabaseSync("agrofacil.db", { enableChangeListener: true })`.** Isso faz o `useLiveQuery` do Drizzle atualizar as telas sozinho quando o sync grava algo.
- **Repositórios (`src/db/repositories/`):** cada um junta "gravar dado + gravar mutação" numa transação ([doc 1 §4.3](1-arquitetura-e-contrato.md#43-escritas-locais)). As telas **nunca** escrevem em tabela diretamente:

```ts
// Grava um registro localmente e enfileira a mutação para a sincronização, na mesma transação.
export async function saveRecord(record: RecordWrite, ctx: SaveContext): Promise<void> {
  const missing = missingFields({ ...record, hasPhoto: ctx.hasPhoto });
  await db.transaction(async (tx) => {
    await tx.insert(records).values(toRow(record, missing)).onConflictDoUpdate({
      target: records.id,
      set: toRow(record, missing),
    });
    await enqueueMutation(tx, { entity: "record", entityId: record.id, op: "UPSERT", data: record });
  });
  scheduleSync(); // gatilho com 2 s de espera (doc 1 §4.4.1)
}
```

> Confira na documentação do Drizzle para `expo-sqlite`, na versão que vocês usarem, se a transação é síncrona ou assíncrona, e ajuste o padrão acima. O importante é **dado + mutação na mesma transação**.

### C.5 Autenticação e sessão

```ts
// Cliente HTTP tipado com token de acesso em memória e renovação automática (uma de cada vez).
import createClient, { type Middleware } from "openapi-fetch";
import type { paths } from "@agrofacil/api-types";

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;

export const api = createClient<paths>({ baseUrl: `${config.apiUrl}/api/v1` });

const auth: Middleware = {
  async onRequest({ request }) {
    request.headers.set("X-Device-Id", await session.deviceId());
    request.headers.set("X-App-Version", config.appVersion);
    if (accessToken) request.headers.set("Authorization", `Bearer ${accessToken}`);
    return request;
  },
};
api.use(auth);

/** Renova o token de acesso. Chamadas simultâneas compartilham a mesma renovação. */
export function refreshSession(): Promise<boolean> {
  refreshing ??= doRefresh().finally(() => (refreshing = null));
  return refreshing;
}
```

- O `deviceId` é gerado (`Crypto.randomUUID()`) na primeira execução e fica no **SecureStore**, assim como o `refreshToken`. O **token de acesso fica só em memória**.
- Ao receber 401 `TOKEN_EXPIRED`, chame `refreshSession()` e repita a requisição **uma** vez. Se o refresh falhar com `REFRESH_INVALID` ou `DEVICE_REVOKED`, apague o refresh token (mas **não** o banco) e navegue para `sessao-expirada` ([doc 1 §4.6](1-arquitetura-e-contrato.md#46-sessão-expirada-ou-aparelho-revogado)).
- **Portão de entrada** (`app/_layout.tsx`): sem refresh token → `(onboarding)`; com refresh token → `(app)`. O app **abre sem internet**, porque não depende de validar o token para mostrar dados locais.
- **Trava por digital** (`src/auth/biometrics.ts`): se o usuário ativou, ao abrir o app ou voltar após mais de 5 min em segundo plano, chame `LocalAuthentication.authenticateAsync({ promptMessage: "Abrir o Agrofácil", fallbackLabel: "Usar a senha do celular" })`. Se falhar, tente de novo; não há outra saída, porque a digital protege os dados de quem pega o celular. Se o celular não tiver biometria nem bloqueio de tela, a opção nem aparece (`hasHardwareAsync`, `isEnrolledAsync`, `getEnrolledLevelAsync`).

### C.6 Motor de sincronização

```ts
// Orquestra o ciclo de sincronização: push da fila, upload de arquivos e pull de mudanças (doc 1 §4.4).
const PUSH_BATCH_SIZE = 100;
const DEBOUNCE_MS = 2_000;

let running = false;
let rerunRequested = false;
let debounce: ReturnType<typeof setTimeout> | undefined;

/** Pede uma sincronização logo após uma gravação local; agrupa vários pedidos seguidos. */
export function scheduleSync(): void {
  clearTimeout(debounce);
  debounce = setTimeout(() => void requestSync(), DEBOUNCE_MS);
}

/** Roda um ciclo agora; se já houver um rodando, agenda outro para quando ele terminar. */
export async function requestSync(): Promise<void> {
  if (running) {
    rerunRequested = true;
    return;
  }
  running = true;
  try {
    do {
      rerunRequested = false;
      await syncOnce();
    } while (rerunRequested);
  } catch (e) {
    await recordSyncFailure(e);   // guarda em sync_state; a tela inicial mostra "esperando internet"
  } finally {
    running = false;
  }
}

async function syncOnce(): Promise<void> {
  const net = await NetInfo.fetch();
  if (!net.isInternetReachable) return;
  await pushAll();
  await uploadPendingFiles();
  await pullAll();
}

async function pushAll(): Promise<void> {
  for (;;) {
    const batch = await outboxRepository.nextBatch(PUSH_BATCH_SIZE);
    if (batch.length === 0) return;
    const { data, error, response } = await api.POST("/sync/push", {
      body: { deviceId: await session.deviceId(), mutations: batch.map(toMutation) },
    });
    if (error) throw new SyncHttpError(response.status, error);   // rede/5xx/401 tratados acima
    await outboxRepository.applyResults(data.results);            // APPLIED → remove; REJECTED → marca ERROR
  }
}

async function pullAll(): Promise<void> {
  let hasMore = true;
  while (hasMore) {
    const cursor = await syncStateRepository.cursor();
    const { data, error, response } = await api.GET("/sync/pull", { params: { query: { cursor } } });
    if (error) throw new SyncHttpError(response.status, error);
    await mirrorRepository.applyPull(data);   // transação: upserts (pulando quem tem pendência), lápides, cursor
    await visitsRepository.pruneOthersDoneBefore(daysAgo(30)); // RN36: resumo de outra roça sai do celular
    hasMore = data.hasMore;
  }
}
```

**Gatilhos** (`src/sync/triggers.ts`), registrados uma vez no layout do `(app)`:
- `NetInfo.addEventListener`, quando `isInternetReachable` passa de `false` para `true`;
- `AppState.addEventListener("change")` para `active`;
- **tarefa em segundo plano:**
  1. `TaskManager.defineTask(SYNC_TASK, async () => { await requestSync(); return BackgroundTask.BackgroundTaskResult.Success; })`, **no escopo do módulo**, fora de componentes;
  2. `BackgroundTask.registerTaskAsync(SYNC_TASK, { minimumInterval: 15 })`.
- puxar para atualizar, na lista de registros.

**Espera exponencial:** guarde `nextAttemptAt` no `sync_state` depois de uma falha, e os gatilhos automáticos respeitam esse horário. O gatilho manual ignora.

### C.7 GPS, foto, áudio e voz

```ts
// Captura a localização sem nunca bloquear o registro (RN26): até 10 s, senão segue sem GPS.
const GPS_TIMEOUT_MS = 10_000;

export async function captureLocation(): Promise<GeoPoint | null> {
  const { status } = await Location.getForegroundPermissionsAsync();
  if (status !== "granted") return null;
  const position = await Promise.race([
    Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }),
    sleep(GPS_TIMEOUT_MS).then(() => null),
  ]);
  const fix = position ?? (await Location.getLastKnownPositionAsync());
  if (!fix) return null;
  return {
    lat: round(fix.coords.latitude, 6),
    lng: round(fix.coords.longitude, 6),
    accuracyM: fix.coords.accuracy ?? undefined,
    capturedAt: new Date(fix.timestamp).toISOString(),
  };
}
```

- **Quando chamar:** ao **abrir** o assistente (RF10). A promessa fica guardada no estado do assistente e é lida na hora de salvar.
- **Permissão de localização:** peça na primeira vez que o agricultor abrir um registro, com uma tela explicativa antes do pedido do sistema ("Para mostrar à associação onde a atividade foi feita").
- **Foto:**
  1. `ImagePicker.launchCameraAsync({ quality: 1 })`;
  2. `ImageManipulator`, que redimensiona o lado maior para 1600 px e salva em JPEG com compressão 0,7;
  3. copie para `Paths.document/attachments/<uuid>.jpg` (API de arquivos do `expo-file-system`);
  4. calcule o SHA-256 com `expo-crypto` a partir dos bytes do arquivo.
- **Áudio:**
  - `expo-audio` (`useAudioRecorder` com preset de alta qualidade, gera `.m4a` / `audio/mp4`);
  - limite de **3 min** com contagem na tela e botão grande de parar;
  - depois de gravar, o agricultor pode ouvir e regravar.
- **Voz:** `Speech.speak(texto, { language: "pt-BR", rate: 0.95 })`. Antes de falar, pare qualquer fala anterior (`Speech.stop()`).

### C.8 O assistente de registro

Um componente genérico `Wizard` com uma **definição declarativa** por tipo. Isso evita 5 telas copiadas.

```ts
// Definição declarativa de um passo do assistente de registro.
export type WizardStep<D> = {
  id: string;
  question: (draft: D, ctx: WizardContext) => string;        // texto grande e o que o 🔊 lê
  isVisible?: (draft: D, ctx: WizardContext) => boolean;     // ex.: esconder "Onde?" se há uma área só
  isOptional?: boolean;                                      // mostra "Pular"
  Component: React.FC<StepProps<D>>;                          // ChoiceGrid, NumberStepper…
};

export type WizardDefinition<D, Out = RecordWrite> = {
  type?: RecordWrite["type"];                                // só nos registros
  title: string;                                             // "Colheita"
  initialDraft: (ctx: WizardContext, preset?: Partial<D>) => D; // hoje, última unidade, plantio do lembrete…
  steps: WizardStep<D>[];
  toWrite: (draft: D, ctx: WizardContext) => Out;            // valida com o esquema zod do domain
};
```

```ts
// Assistente de colheita (§A.5.3).
export const harvestWizard: WizardDefinition<HarvestDraft> = {
  type: "HARVEST",
  title: "Colheita",
  initialDraft: (ctx, preset) => ({ performedOn: ctx.today, isFinal: false, ...preset }),
  steps: [
    { id: "planting", question: () => "Colheu qual plantação?", Component: PlantingPickerStep },
    { id: "date", question: () => "Quando você colheu?", Component: QuickDateStep },
    {
      id: "quantity",
      question: () => "Quanto você colheu?",
      isOptional: true,
      Component: QuantityStep,
    },
    { id: "final", question: (d, ctx) => `Terminou de colher ${ctx.plantingLabel(d.plantingId)}?`, Component: YesNoStep },
    { id: "extras", question: () => "Quer acrescentar algo?", isOptional: true, Component: ExtrasStep },
  ],
  toWrite: (d, ctx) => harvestWriteSchema.parse({ ...d, id: ctx.recordId, type: "HARVEST", propertyId: ctx.propertyOf(d.plantingId) }),
};
```

O `Wizard`:
- calcula os passos visíveis;
- mostra o progresso;
- salva o rascunho na tabela `drafts` a cada mudança;
- no fim, mostra a **confirmação** com `describeRecord` (pacote `domain`);
- chama `saveRecord` e vai para a tela de sucesso, com `Haptics.notificationAsync(Success)`.

**Rotas:**
- `registrar/[type]?preset=<json>` permite abrir já preenchido. É assim que funcionam o lembrete "Já colheu?", o "Registrar de novo" e o "+ Colheita" no detalhe do plantio.
- **Editar** usa o mesmo assistente, começando na lista de respostas da confirmação.
- **Completar** mostra só os passos dos `missingFields`.

**Modo visita:** reaproveita o `Wizard` com passos **montados a partir do roteiro** que veio do servidor (`visit_checklist_items`). Generalize a definição para que a saída não seja só um `RecordWrite`:

```ts
// Monta o assistente da visita: um passo por pergunta ativa do roteiro, mais os passos fixos (doc 3 §A.13.3).
export function buildVisitWizard(visit: Visit, items: VisitChecklistItem[]): WizardDefinition<VisitDraft, VisitResultWrite> {
  const questions = items
    .filter((i) => i.active && (i.appliesTo === "BOTH" || i.appliesTo === visit.type))
    .sort((a, b) => a.sortOrder - b.sortOrder);
  return {
    title: `Visita · ${visit.property.name}`,
    initialDraft: (ctx) => ({ doneOn: ctx.today, attended: visitorIds(visit), answers: {} }),
    steps: [
      { id: "attended", question: () => "Quem foi na visita?", Component: PeoplePickerStep },
      ...questions.map((q) => ({ id: q.code, question: () => q.question, Component: ChecklistAnswerStep })),
      { id: "issues", question: () => "Encontraram algo que precisa melhorar?", Component: IssuesStep },
      { id: "advice", question: () => "Quer deixar alguma recomendação?", isOptional: true, Component: TextOrAudioStep },
      { id: "photos", question: () => "Quer tirar fotos da visita?", isOptional: true, Component: PhotosStep },
    ],
    toWrite: (d) => visitResultSchema.parse(toVisitResult(d, questions)),
  };
}
```

- **Gravar:** `saveVisitResult` grava na tabela `visits` e enfileira a mutação `visit` **na mesma transação**, como o `saveRecord`.
- **Rascunho:** fica na mesma tabela `drafts`, com a chave `visit:<id>`.
- **Quem pode abrir:** a rota `visita/[id]/fazer` só abre se o usuário for o `recorderId`. Os outros veem o aviso da [§A.13.2](#a132-quem-vai-visitar).

**"Mais usados":** uma consulta local agrupa `records.subjectKey` dos últimos 180 dias por tipo, ordena pela contagem e pega os 6 primeiros.

### C.9 Notificações

- **Push:**
  1. ao entrar no `(app)`, peça permissão com uma explicação antes;
  2. obtenha `Notifications.getExpoPushTokenAsync({ projectId })`;
  3. envie em `PUT /me/device`.

  Repita a cada abertura, porque o token pode mudar.
- **Ao tocar numa notificação** (`addNotificationResponseReceivedListener`), leia o `data.type` e navegue: registro → `registro/[id]`, certificação → `propriedade/[id]`, visita → `visita/[id]`, comunicado → `avisos`.
- **Lembretes locais** ([§A.7](#a7-avisos-e-lembretes)), com `Notifications.scheduleNotificationAsync`:
  - **colheita prevista:** gatilho de data às 7h do `expectedHarvestOn`. O identificador é o id do plantio, para reagendar ou cancelar ao editar, ao encerrar o plantio ou ao apagar;
  - **semanal:** gatilho semanal; ao disparar, o app não tem como verificar antes, então o texto é genérico. Ao abrir, se já houver registro na semana, nada acontece;
  - **categorias com botões** (`setNotificationCategoryAsync`): "Sim, registrar" e "Ainda não".
- Crie o canal Android `default` com importância alta na inicialização.

### C.10 Telas principais

| Tela | Fonte de dados (sempre local) | Observações |
|---|---|---|
| Início | `useLiveQuery` de lembretes, últimos registros, status da fila e certificações | [§A.4](#a4-tela-inicial) |
| Registros | `records` com filtros por tipo, agrupados por mês, em `FlashList` | Puxar para atualizar dispara o sync |
| Detalhe do registro | `records` + `attachments` + `correctionRequests` + `people` | Fotos lidas do arquivo local; se ainda não existir, baixe com `/files/{id}/content` |
| Propriedades | `properties` + `certifications` (status calculado com o `domain`) | |
| Detalhe da propriedade | `plots`, `workers`, `certifications`, plantios ativos | Mapa estático: `react-native-maps` só se a equipe quiser; para o MVP basta o botão "Abrir no mapa" com `geo:lat,lng` |
| Avisos | `notifications` + `announcements` | Ao abrir, marca como lido (mutação `notification`) |
| Meus dados | `me` | A edição exige internet, com aviso claro |
| Detalhe da visita | `visits` (com o `brief`) + `people` | [§A.13](#a13-visitas-do-grupo-e-da-comissão-spg). "Ligar" usa `tel:`; "Abrir no mapa" usa `geo:lat,lng` |
| Modo visita | `visits` + `visitChecklistItems` | Assistente gerado pelo `buildVisitWizard` (§C.8) |

### C.11 Testes do mobile

| Tipo | Ferramenta | O que testar |
|---|---|---|
| Unitário | Jest (`jest-expo`) | Repositórios (com SQLite em memória ou mock), compactação da fila, aplicação do pull, definições do assistente |
| Componentes | React Native Testing Library | `NumberStepper`, `ChoiceGrid`, `Wizard` (navegar, pular, voltar, confirmar) |
| Ponta a ponta | **Maestro** (fluxos YAML) | Os 9 roteiros da [§A.12](#a12-testes-com-agricultores), incluindo o modo avião (a visita feita sem internet) |

### C.12 Build e distribuição
- **EAS Build** com o perfil `preview` (`"android": { "buildType": "apk" }`) gera um APK para instalar direto nos celulares, sem Play Store.
- **Variáveis:** `EXPO_PUBLIC_API_URL` por perfil (`development` → IP da máquina na rede local; `preview` → URL do túnel HTTPS, [doc 2, Parte 3](2-guia-api.md#parte-3-ambiente-e-implantação)).
- **Versão:** `version` no `app.config.ts`. Suba a `app.min-mobile-version` da API só quando uma mudança quebrar a compatibilidade.
- **Atualizações sem reinstalar:** o `expo-updates` (OTA) é opcional e pode vir depois do MVP.

---

## Parte D: painel do coordenador (Vite + React)

### D.1 Criando o projeto

```bash
cd apps
pnpm create vite web --template react-ts
cd web
pnpm add @mantine/core @mantine/hooks @mantine/dates @mantine/charts @mantine/notifications @mantine/modals \
  mantine-datatable recharts dayjs @tanstack/react-query react-router openapi-fetch \
  react-hook-form @hookform/resolvers zod leaflet react-leaflet @tabler/icons-react
pnpm add -D vitest @testing-library/react @testing-library/user-event jsdom msw @playwright/test @types/leaflet
```

`vite.config.ts`: o proxy evita CORS no desenvolvimento.

```ts
// Configuração do Vite: repassa /api para a API local, para que painel e API fiquem na mesma origem.
export default defineConfig({
  plugins: [react()],
  server: { proxy: { "/api": "http://localhost:8080" } },
});
```

### D.2 Estrutura

```text
apps/web/src/
├── main.tsx                 # MantineProvider (locale pt-BR do dayjs), QueryClientProvider, RouterProvider
├── router.tsx               # tabela de rotas (§D.5)
├── api/
│   ├── client.ts            # openapi-fetch + token em memória + refresh por cookie
│   └── queries/             # hooks por recurso: useFarmers, useFarmer, useCreateFarmer…
├── auth/                    # AuthProvider, RequireAuth, telas de login/2FA/convite/senha
├── layout/                  # AppShell com menu lateral, cabeçalho com sino de notificações
├── features/
│   ├── dashboard/
│   ├── farmers/
│   ├── properties/
│   ├── records/
│   ├── certifications/
│   ├── groups/
│   ├── announcements/
│   ├── catalog/
│   ├── reports/
│   ├── visits/              # planejamento, agenda, marcar visita (sugestão), detalhe, resultado
│   ├── documents/           # gerar, listar, cópia assinada, conferir código
│   ├── spg-people/          # colaboradores, Comissão, impedimentos
│   └── settings/
├── components/              # DataTableWithFilters, StatusBadge, ConfirmDelete, MapPicker, FileDownloadButton
├── i18n/pt-BR.ts
└── test/                    # handlers do MSW e utilitários
```

### D.3 Autenticação no painel

- **Token de acesso em memória** (variável de módulo), nunca no `localStorage`. O refresh token está no cookie `HttpOnly`, que o JavaScript não lê.
- **Ao carregar a página:** `POST /api/v1/auth/refresh` com `credentials: "include"` e `X-Requested-With: agrofacil-web`.
  - Deu certo: o usuário está logado (busca `/me`).
  - Deu 401: vai para `/entrar`.
- O middleware do `openapi-fetch`:
  - põe o `Authorization`;
  - em `TOKEN_EXPIRED`, faz um refresh único (mesmo padrão do mobile, [§C.5](#c5-autenticação-e-sessão)) e repete a requisição;
  - se o refresh falhar, limpa o cache do TanStack Query e vai para `/entrar?voltar=<rota atual>`.
- **Telas de autenticação:**

| Rota | Tela |
|---|---|
| `/entrar` | E-mail e senha |
| `/entrar/codigo` | 6 caixas para o código, "Reenviar" com contador de 60 s e a dica `emailHint` |
| `/esqueci-senha` | Pede o e-mail |
| `/redefinir-senha?token=` | Nova senha |
| `/convite?token=` | Nome e senha |

- **Logout:** `POST /auth/logout`, depois limpe o cache e vá para `/entrar`.

### D.4 Dados: TanStack Query

```ts
// Hooks de consulta e mutação de agricultores; as chaves seguem [recurso, filtros].
export function useFarmers(filters: FarmerFilters) {
  return useQuery({
    queryKey: ["farmers", filters],
    queryFn: () => unwrap(api.GET("/farmers", { params: { query: filters } })),
    placeholderData: keepPreviousData,      // a tabela não "pisca" ao trocar de página
  });
}

export function useCreateAccessCode(farmerId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => unwrap(api.POST("/farmers/{id}/access-codes", { params: { path: { id: farmerId } } })),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["farmers", farmerId] }),
  });
}
```

- `unwrap` transforma a resposta de erro do `openapi-fetch` numa exceção `ApiError` com `code`, `detail` e `errors[]`.
- **Formulários:** um utilitário `applyServerErrors(form, apiError)` faz o `setError` de cada `errors[].field` no react-hook-form. Erros sem campo viram notificação (`@mantine/notifications`).
- **`VERSION_CONFLICT`:** mostre um modal "Alguém alterou este item. Recarregar?", que invalida a consulta.
- **Filtros na URL:** os filtros das listas são lidos e escritos com `useSearchParams`. Eles são a chave da consulta.

### D.5 Rotas e menu

| Menu | Rota | Tela |
|---|---|---|
| Painel | `/` | Dashboard |
| Agricultores | `/agricultores`, `/agricultores/novo`, `/agricultores/:id` | Lista, cadastro, detalhe com abas |
| Propriedades | `/propriedades`, `/propriedades/:id` | Lista com alternância Tabela/Mapa; detalhe |
| Registros | `/registros`, `/registros/novo`, `/registros/:id` | Lista com filtros, registrar pelo agricultor, detalhe + histórico |
| Certificações | `/certificacoes`, `/certificacoes/nova`, `/certificacoes/:id` | |
| Grupos | `/grupos`, `/grupos/:id` | |
| Visitas | `/visitas`, `/visitas/agenda`, `/visitas/:id` | Planejamento, agenda e detalhe |
| Documentos | `/documentos` | Gerar, listar, cópia assinada, conferir código |
| Pessoas do SPG | `/spg/colaboradores`, `/spg/comissao`, `/spg/impedimentos` | |
| Comunicados | `/comunicados` | Lista + modal de novo comunicado |
| Catálogo | `/catalogo/culturas`, `/catalogo/insumos`, `/catalogo/unidades`, `/catalogo/roteiro`, `/catalogo/sem-catalogo` | |
| Relatórios | `/relatorios` | |
| Configurações | `/configuracoes` (associação), `/configuracoes/coordenadores` | |

### D.6 Telas

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
- **Tabelas:** use o `mantine-datatable` com paginação do servidor (`page`, `size`, `sort` vão para a URL e para a consulta). A ordenação só vale em colunas que a API ordena.
- **Badges de status:** **as mesmas cores e frases do app** ([§A.6](#a6-propriedades-rf04rf09)), tiradas de `certificationPhrase` e `labels` do `domain`.
- **Datas:** dayjs com o locale `pt-br`. Exiba `20/11/2026` e use datas relativas só em "último envio" ("há 2 dias").
- **Confirmações:** `modals.openConfirmModal` com o texto do que vai acontecer e o botão de perigo em vermelho.
- **Carregamento:** `Skeleton` nas tabelas e cartões. Nunca uma tela branca.
- **Segurança:** nunca use `dangerouslySetInnerHTML`. O texto dos comunicados é exibido como texto puro, com `white-space: pre-line`. Links externos levam `rel="noopener noreferrer"`.

### D.8 Testes do painel

| Tipo | Ferramenta | O que testar |
|---|---|---|
| Unitário / componentes | Vitest + Testing Library + **MSW** (respostas falsas da API) | Formulários (validação + erros do servidor), filtros na URL, modal do código |
| Ponta a ponta | Playwright, com a API rodando de verdade via Docker Compose | Login com 2FA (o código é lido da API do Mailpit), cadastrar agricultor, gerar código, criar certificação, registrar pelo agricultor, marcar visita com a sugestão, digitar o resultado e gerar o documento de aprovação |

Os handlers do MSW também permitem **desenvolver o painel antes da API ficar pronta**. Escreva-os a partir dos exemplos do [doc 1, Parte 3](1-arquitetura-e-contrato.md#parte-3-contrato-api-front).

### D.9 Build
`pnpm --filter @agrofacil/web build` gera `apps/web/dist/`, que o nginx serve ([doc 2, Parte 3](2-guia-api.md#parte-3-ambiente-e-implantação)). A URL da API é relativa (`/api/v1`), então o mesmo build funciona em qualquer endereço.

---

## Parte E: ordem de implementação do front

| Marco | Mobile | Web |
|---|---|---|
| F0 | Monorepo, lint, `domain` com testes, `api-types` gerado | idem |
| F1 | Tema, componentes base, navegação com dados falsos | Layout, rotas, login + 2FA (contra MSW ou a API M2) |
| F2 | Banco local, repositórios, assistentes dos 5 tipos **offline** (sem API) | Agricultores, código de acesso, grupos |
| F3 | Ativação, sessão, digital | Propriedades, áreas, trabalhadores, mapa |
| F4 | Motor de sync (push, pull, arquivos) contra a API M6 | Registros: lista, detalhe, histórico, registrar pelo agricultor, pedidos de correção |
| F5 | Lembretes locais, push, avisos, certificação na tela inicial | Certificações, comunicados, notificações |
| F6 | Propriedades (cadastro e detalhe), Meus dados, voz | Painel inicial, catálogo, relatórios, configurações |
| F7 | Testes com agricultores ([§A.12](#a12-testes-com-agricultores)) e ajustes | Testes com o coordenador e ajustes |
| F8 | Visitas: cartões na tela inicial, detalhe com o resumo e modo visita offline (contra a API M10) | Planejamento, marcar visita com sugestão, agenda, resultado, documentos, pessoas do SPG, roteiro |

O F8 pode começar antes da API M10 com o MSW e um `brief` de exemplo. Teste o modo visita com um grupo de verdade antes de fechar o roteiro.

O F2 do mobile não depende da API: com o banco local e os assistentes prontos, já dá para **testar a usabilidade com agricultores** cedo, que é o maior risco do projeto.
