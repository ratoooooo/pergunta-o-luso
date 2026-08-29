# Quizzes da Comunidade e moderação

← [índice](../00-indice.md)

> **Escondido da UI desde 9 ago 2026.** `FeatureFlags.QUIZZES_COMUNIDADE_VISIVEIS = false` tira o
> botão do Início, que era o único ponto de entrada. Tudo o que está descrito abaixo continua a
> existir e a funcionar — ecrã, repositório, salas privadas por código, moderação e rules. Pôr a
> flag a `true` devolve o acesso; não é preciso mexer em mais nada.

Perguntas criadas pelos próprios jogadores. Alcançável por "COMUNIDADE" no picker de categoria
ou por atalho no Início.

## O que faz

- **Criar um quiz**: título, descrição, dificuldade, perguntas de escolha múltipla ou
  Verdadeiro/Falso. O selector da resposta certa usa o **emblema A/B/C/D** do ecrã de jogo, não
  `RadioButton`.
- **Publicar / despublicar** (`publica`), **explorar as públicas**, **avaliar por estrelas**.
- **Jogar a solo** ou **criar sala privada por código** — ver [multiplayer](multiplayer.md).
  A sala privada passou a ser criada **pelo servidor da partida** (29 ago 2026): o cliente manda
  o id do quiz e o servidor é que lê as perguntas de `/categorias_comunitarias` e gera o código.
  O anfitrião deixou de as enviar, e portanto de as poder inventar. O caminho está ligado e
  testado, mas **continua sem porta de entrada na UI** enquanto
  `QUIZZES_COMUNIDADE_VISIVEIS` for `false`.
- **Denunciar** um quiz.

Pontuação de um quiz da comunidade **não entra** em `/scores` nem nos agregados normais: dá
apenas XP reduzido (5 por acerto), para não inflacionar o ranking com perguntas sem curadoria.

Um quiz jogado em sala privada está limitado a **10 perguntas** (`MAX_PERGUNTAS_SALA`) porque as
rules travam `respostasCertas <= 10`: um quiz maior fazia a gravação do resultado ser recusada
**depois** de já se ter jogado.

## Moderação — o que é e o que não é

> **Isto não é um sistema de moderação.** Não há painel de administração, fila de revisão nem
> moderadores. A revisão é manual, na consola do Firebase. O que existe é o mínimo que a Play
> Store exige por haver conteúdo público gerado por utilizadores, e serve para tirar spam
> evidente de circulação depressa — não para arbitrar disputas.

**Denúncias** (`/denuncias/{quizId}/{uid}`): uma por pessoa por quiz, **irreversível**. O botão
aparece a toda a gente **excepto ao autor**. Não há `.read` na colecção nem em `$quizId` — cada
pessoa só lê a **sua** denúncia (o suficiente para a app mostrar "já denunciaste"). Ninguém
consegue ver quem denunciou o quê.

**Auto-ocultação às 3 denúncias** (`DENUNCIAS_PARA_OCULTAR`). Três é um compromisso: mais baixo
deixa uma ou duas pessoas silenciarem conteúdo legítimo; mais alto deixa spam à vista tempo de
mais numa app sem moderadores. O quiz **não é apagado** — passa a `publica = false`, o autor
continua a vê-lo em "As Minhas" e pode republicá-lo.

**Filtro de linguagem** (`ProfanityFilter`): lista estática verificada **no repositório**, não só
no ecrã, para nenhum caminho a contornar por engano. Comparação com **fronteiras de palavra**
sobre texto normalizado (minúsculas, sem acentos) — nunca `contains`.

Palavras deliberadamente **removidas** da lista por causarem falsos positivos numa app de cultura
portuguesa: `cabra` (o animal, e "cabra-cega" o jogo), `burro` (o animal), `puto` (em Portugal é
miúdo), `broche` (a peça de joalharia), `corno` (do animal), `idiota` ("O Idiota" de
Dostoiévski), `bicha` (também é fila). Recusar uma pergunta legítima sem o autor perceber porquê
é pior do que deixar passar um palavrão — para esse ainda há a denúncia. Um teste unitário
apanhou exactamente este problema antes de chegar a produção.

## Limitações conhecidas

- **O contador de denúncias é falsificável.** As rules da RTDB não têm `numChildren()`, por isso
  é impossível validar que `totalDenuncias` bate certo com o número real. A mitigação é o
  incremento estar limitado a `+1` e exigir uma denúncia registada de quem escreve; ainda assim,
  alguém determinado consegue forçar a ocultação de um quiz. Impacto é de **disponibilidade**,
  não de perda de dados — o autor vê-o e republica. Fechar isto a sério exige Cloud Functions.
- **Revisão é manual na consola.** Sem painel, sem notificação, sem histórico de decisões.
- **O filtro é uma lista estática.** Contorna-se trivialmente e não apanha ofensas escritas sem
  palavrões.
- **Quizzes de contas eliminadas são apagados, não anonimizados** — ver
  [eliminacao-conta](eliminacao-conta.md) para o porquê.

Uma vulnerabilidade séria neste caminho (qualquer pessoa reescrever o quiz de outra, incluindo a
autoria) foi encontrada e corrigida — ver
[historico-vulnerabilidades](../seguranca/historico-vulnerabilidades.md).
