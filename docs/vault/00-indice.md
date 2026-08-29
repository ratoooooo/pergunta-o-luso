# Pergunta ó Luso — índice

**Começa sempre por aqui.** Esta nota é o estado do projeto num ecrã. Tudo o resto está
ligado abaixo; não é preciso ler o histórico completo para trabalhar.

## O que é

Jogo de trivia de cultura portuguesa para Android. Solo e multijogador em tempo real.
1630 perguntas em 5 categorias visíveis — 326 em cada, niveladas a 9 ago 2026 — mais 55 em duas
categorias escondidas. Escolha múltipla e Verdadeiro/Falso.

- **Stack:** Kotlin 2.2 · Jetpack Compose (Material 3) · MVVM (`ViewModel` + `StateFlow`)
- **Backend:** Firebase Auth + Realtime Database **+ servidor de partida próprio** (VPS, Node)
  para o multijogador ao vivo. Sem Cloud Functions.
- **Build:** Gradle 9.4.1 · AGP 9.2.1 · compileSdk 36 · minSdk 26
- **Pacote:** `com.ratoooooo.perguntaoluso` (permanente — ver [firebase](arquitetura/firebase.md))
- **Estado:** funcional ponta a ponta. **Não publicado** na Play Store.

## Estado por área

| Área | Estado |
|---|---|
| Solo (3 modos), pontuação, dificuldade | feito |
| Multijogador 1x1 / 2x2 / Grupo, salas privadas por código | feito |
| Contas, perfil agregado, eliminação de conta | feito |
| XP, níveis, patentes, conquistas, avatares | feito |
| Sequência diária + protecção, "quase lá" no pódio | feito |
| Amigos, desafio direto, presença online | feito |
| Quizzes da Comunidade + moderação mínima | feito |
| Ranking (por modo **e** por formato), histórico | feito |
| Som e retorno háptico | feito |
| Rules auditadas e endurecidas | feito |
| Assinatura de release, ícone próprio | feito |
| Submissão à Play Store | **por fazer** — ver [por-fazer](por-fazer.md) |

## Onde ir a seguir

**Como o jogo funciona**
- [Modos de jogo, pontuação e dificuldade](funcionalidades/modos-de-jogo.md)
- [Multijogador e matchmaking](funcionalidades/multiplayer.md)
- [XP, níveis e patentes](funcionalidades/xp-niveis-patentes.md)
- [Conquistas e avatares](funcionalidades/conquistas-avatares.md)
- [Amigos, desafios e presença](funcionalidades/amigos-desafios.md)
- [Quizzes da Comunidade e moderação](funcionalidades/quizzes-comunidade.md)
- [Ranking, histórico e perfil](funcionalidades/ranking-historico-perfil.md)
- [Som e retorno háptico](funcionalidades/som-haptico.md)
- [Sequência diária e protecção](funcionalidades/streak-diario.md)
- [Eliminação de conta](funcionalidades/eliminacao-conta.md)
- [Conteúdo das perguntas](funcionalidades/conteudo-perguntas.md)

**Como está construído**
- [Firebase e identidade da app](arquitetura/firebase.md)
- [Schema da Realtime Database](arquitetura/rtdb-schema.md)
- [Regras de segurança](arquitetura/rules.md)
- [Servidor da partida ao vivo](arquitetura/servidor-partida.md)
- [Autenticação](arquitetura/autenticacao.md)
- [Sistema visual e componentes](arquitetura/sistema-visual.md)

**Manutenção**
- [Limpeza de contas e dados de teste](manutencao/limpeza-dados-teste.md) — 9 ago 2026

**Segurança — ler antes de mexer em rules ou dados**
- [Histórico de vulnerabilidades](seguranca/historico-vulnerabilidades.md) ← os erros já cometidos
- [Limitações conhecidas do modelo](seguranca/limitacoes-conhecidas.md)
- [Segredos e assinatura](seguranca/segredos-e-assinatura.md)

**Porque é que as coisas são como são**
- [Critério de vitória por modo](decisoes/criterio-vitoria.md)
- [Grupo joga com 4 a 10](decisoes/grupo-4-a-10.md)
- [Os três eixos do ranking](decisoes/eixos-ranking.md)
- [Faixas das patentes](decisoes/patentes-faixas.md)
- [Hierarquia de cor](decisoes/hierarquia-de-cor.md)
- [Decisões revertidas e recusadas](decisoes/recusadas-e-revertidas.md)

**Arquivo**
- [Histórico fase a fase](historico-fases/README.md) — 33 fases, ~3000 linhas. Só quando
  precisas do *como se chegou ali*, não do *como está*.

## Regras da casa

1. **Não inventar dados.** Se um número não existe no schema, não se fabrica um campo para o
   mostrar — documenta-se a limitação. Ver [eixos-ranking](decisoes/eixos-ranking.md).
2. **Testar no dispositivo, não deduzir.** Várias fases foram gastas a corrigir coisas que
   "deviam funcionar". Ver [limitacoes-conhecidas](seguranca/limitacoes-conhecidas.md).
3. **Rules revalidadas por transação de ascendente** precisam de ser testadas **com lixo
   pré-existente na base de dados**, não com um nó limpo — ver
   [historico-vulnerabilidades](seguranca/historico-vulnerabilidades.md).
4. Documentação e código desencontram-se depressa. Quando um número aparece em texto, deve vir
   do código (ex.: `MatchFormat.sizeLabel`), não estar escrito à mão.
