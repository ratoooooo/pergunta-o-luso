# Arquivo — histórico fase a fase

← [índice](../00-indice.md)

O registo completo, por ordem cronológica, está em **[`GAME_DESIGN.md`](../../../GAME_DESIGN.md)**
na raiz do repositório (~3000 linhas, 33 fases). Não foi copiado para aqui de propósito:
**duas cópias divergem**, e este projeto já perdeu tempo com documentação desencontrada do código
mais do que uma vez.

**Quando vir aqui:** só para perceber *como se chegou a uma decisão* ou o que já foi tentado e
falhou. Para o estado atual, usar o [índice](../00-indice.md).

## Aviso sobre a primeira parte do ficheiro

As secções **antes** da "Phase 11" (linhas ~1–546) são o desenho **original** e foram
**superadas** pelas fases seguintes, sem serem reescritas. Não são o estado atual. Em concreto:

| Secção antiga | Estado real |
|---|---|
| "Multiplayer — Duelo 1x1" (`game/onevone/`, `/salas1x1`) | **removido** — dobrado no MultiMatch generalizado |
| `/matchmaking`, `/salas1x1` nas tabelas de rules | **removidos** das rules |
| "Answer feedback" (opções coloridas em repouso) | mudado — cartões neutros com emblema A/B/C/D |
| "Podium" (top 5) | mudado — top 3 no pódio, quadro completo no Ranking |
| "Multiplayer — 2x2 & Grupo" a dizer 4 jogadores no Grupo | é **4 a 10** (nota de correção já lá está) |
| Categoria/modo fixos no multijogador | todos os formatos escolhem categoria e modo |

A lista de "Honest divergences" da Fase 11 **foi revista e anotada** — cada entrada diz agora se
está resolvida, de pé, ou se o próprio texto estava errado.

## Mapa das fases

| Fases | Tema |
|---|---|
| (topo) | desenho original: Firebase, categorias, dificuldade, pontuação, modos |
| 11–12b | alinhamento ao mockup, navegação inferior, salas de espera, XP/níveis, presença |
| 13 | agregação do multijogador no perfil, avatares e conquistas |
| 14–15 | amigos, desafio direto |
| 16–17 | Verdadeiro/Falso, perguntas novas, validação e **43 correções** de conteúdo |
| 18–19 | hierarquia de cor em todos os ecrãs, animações por ecrã |
| 20 | auditoria de rules, **remoção da Roda da Sorte** |
| 21 | mudança de `applicationId` |
| 22 | **backdoor de auto-login**, contas de teste, endurecimento das rules |
| 23 | eliminação de conta self-service |
| 24 | regra de `/scores`, assinatura de release, páginas legais |
| 25 | salas privadas repostas a funcionar, moderação dos quizzes |
| 26 | primeiro release assinado, **R8 verificado em runtime** |
| 27 | buraco no `hostUid`, e a regressão que a correção causou |
| 28 | três defeitos apanhados a jogar à mão |
| 29 | ícone próprio, primeiras correções de responsividade |
| 30 | Grupo injogável: sala sem scroll + portão de arranque errado |
| 31 | patentes, contagem de perguntas, som/háptico, revisão das divergências |
| 32 | ranking por formato, Grupo de 4 a 10, VIBRATE na política |
| 33 | porque é que os sons não se ouviam (duas causas independentes) |

## Fases que vale mesmo a pena ler

- **27** — como uma correção de rules correta em isolamento partiu a app em produção. É a origem
  da regra "testar com lixo pré-existente".
- **33** — como uma verificação pode provar o caminho de código e à mesma não provar nada
  (`-no-audio` no emulador). Erro de método assumido.
- **20** — porque é que uma funcionalidade completa foi deitada fora.
