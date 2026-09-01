# Por fazer

← [índice](00-indice.md)

Consolidado das listas "Por fazer" espalhadas pelas fases. Ordenado por o que bloqueia o quê.

## Bloqueia a submissão à Play Store

1. **Gerar o `.aab`** (`./gradlew bundleRelease`). O keystore já existe e o release já assina.
2. **Gráfico de destaque 1024×500** para a ficha da loja — não se deriva do ícone.
3. **URL web de pedido de eliminação de conta**, exigido pelo formulário Data Safety **além** do
   fluxo já implementado na app.
4. **Publicar a `gh-pages`**: o commit `0a14609` (declarar `VIBRATE` na política de privacidade)
   **está só local**. `git push origin gh-pages`.
5. ~~**Apagar 67 contas de teste do Firebase Auth.**~~ Feito a 20 ago 2026, mas **não por este
   script** — o dono apagou-as na consola, e de caminho apagou também `rato@gmail.com` e
   `inis.teste@example.com`, que a decisão de 9 ago tinha marcado para sobreviver. Restam 8
   contas: as 4 dos emuladores e 4 anónimas. Ver
   [limpeza-dados-teste](manutencao/limpeza-dados-teste.md).
   `KEEP_EMAILS` em [`qa/apagar-contas-teste.js`](../../qa/apagar-contas-teste.js) passou a ter só
   as 4 dos emuladores; o script continua útil para as anónimas que a QA vai deixando ficar.

## Higiene técnica

6. ~~**Apagar código morto:** `MultiMatchRepository.createRoom` e a fila `/matchmakingN`.~~
   Feito a 29 ago 2026, na fase 6 do [servidor da partida](arquitetura/servidor-partida.md).
   `MultiMatchRepository` desapareceu inteiro, e não só o `createRoom`: com a partida a correr no
   servidor, todo o caminho da RTDB — lobbies, salas, salas privadas — deixou de ter chamadores.
7. ~~**Limpeza de estado velho na RTDB.**~~ Feita a 9 ago 2026 — ver
   [limpeza-dados-teste](manutencao/limpeza-dados-teste.md). `/lobbies`, `/multisalas` e
   `/salas_privadas` a `null`; `/matchmakingN` e `/categorias_comunitarias` não existem sequer;
   `/jogadores` passou de 23 para 6 perfis e `/scores` de 111 para 71 registos. Continua sem
   haver TTL nem job de limpeza, por isso volta a acumular — ver
   [limitacoes-conhecidas](seguranca/limitacoes-conhecidas.md).
8. **Índices e queries no servidor:** `loadAllProfiles` e `loadMyScores` descarregam tudo para
   filtrar no cliente. Falta `.indexOn` em `pontos`/`uid`.
9. **Guardar o `mapping.txt`** de cada release publicado, fora do repositório.
10. ~~**Fase 5 do servidor da partida: o `saveScore` do multijogador já foi removido.**~~
    Resolvido. O `ScoreRepository` continua intocado e o solo continua a usá-lo; no multijogador
    quem grava `/scores` é o servidor.
11. ~~**Contagem decrescente do desafio no `FriendsScreen` ficou inalcançável.**~~ Removido
    o cartão inalcançável do `FriendsScreen.kt` a 31 ago 2026.

## Defeitos abertos

> **B3 (`switchLobby`) — resolvido.** `joinLobbyById` e `findOrCreateLobby` corriam a descoberto
> dentro do `viewModelScope.launch`. Não é um listener, por isso o `coletarListener` não servia:
> passam pelo `executarAcao`, irmão dele para acções do jogador — mesma re-lançada de
> `CancellationException`, mesmo ecrã de erro. O `runCatching` do `leaveLobby` ficou como estava.
>
> O que isto obrigou a decidir foi o **estado a meio**: o `switchLobby` é optimista e escreve o
> lobby de destino em `currentLobbyId`/`isHost` antes de saber se a entrada resulta. A falhar,
> o jogador já saiu do lobby antigo e não entrou em nenhum — e o VOLTAR do ecrã de erro chama
> `leave()`, que ia tentar sair de um lobby onde nunca esteve. `estadoAposFalhaAoTrocarDeSala`
> repõe `currentLobbyId = null` e `isHost = false`; `categoria`/`modo` sobrevivem por serem
> escolha do jogador. `ExecutarAcaoTest` reproduz o crash nos **dois** pontos como controlo e
> prende também esta transição de estado.

> **B2 (listeners do lobby) — resolvido.** `openLobbiesJob` (`observeOpenLobbies`) e `lobbyJob`
> (`observeLobby`) faziam o mesmo `collect` cru do B. *Pareciam* cobertos pelo `try` do `start()`,
> mas cada um lança a sua `viewModelScope.launch` — corrotina filha, o `catch` do pai não a
> apanha. Passam os dois pelo `coletarListener`, com `falhaDoListenerDoLobby()` a mandá-los para o
> mesmo ecrã de erro do B. A guarda `deveAvisarDeFalhaNoLobby` impede o efeito colateral que isto
> abria: com o jogo já a decorrer em `/multisalas`, um listener de `/lobbies` a morrer não pode
> trocar a partida pelo ecrã de erro — o lobby até é apagado no arranque normal. De caminho ficou
> coberto o **corpo** do `collect` do `listenToLobby`, que chama a RTDB lá dentro (`joinRoom`,
> `loadGameQuestions`, `startLobbyRoom`) e estoirava pela mesma porta.

> **B (`observeRoom`) — resolvido.** `MultiMatchViewModel.observeRoom` fazia `collect` sem
> protecção e a excepção saía por `viewModelScope` como `FATAL EXCEPTION: main`
> (`DatabaseException: This client does not have permission to perform this operation`). Passa
> pelo `coletarListener`, que encaminha a falha para o ecrã de erro — o jogador sai pelo VOLTAR,
> que é o `leave()` de sempre. `CancellationException` é re-lançada, para o cancelamento normal
> (`leave()`, `onCleared()`) não passar por falha. `ColetarListenerTest` reproduz o crash antigo
> como caso de controlo e prende os dois lados.

> **C (`topScores` ao vivo) — resolvido.** `ScoreRepository.observeTopScores()` observa
> `/scores` via `callbackFlow` e `ValueEventListener`, ligado a `topScores` no `GameViewModel`
> e religado nas mudanças de autenticação (arranque, login, registo, sign-out, eliminação).
> `topScores` sobrevive em `sessionOnly()` e a leitura pontual `loadTopScores()` foi removida
> do `finishGame()`.

> **D (erro de transporte no Auth) — resolvido.** `friendlyAuthError` em `GameViewModel.kt`
> passou a mapear erros de transporte/rede ("unexpected end of stream", "failed to connect",
> "timeout", etc.) para "Sem ligação à internet — tenta outra vez", impedindo mensagens técnicas
> do OkHttp de escaparem para o utilizador.

> **E (`StatChip` com overflow de números grandes) — resolvido.** `StatChip` no `StartScreen.kt`
> passou a ter `overflow = TextOverflow.Ellipsis` no `Text` do valor e do rótulo, exibindo
> reticências em vez de recortar silenciosamente dígitos que excedam a largura do chip.

## Verificação em falta

10. **Grupo com 7 a 10 jogadores.** ~~5 a 10~~ — 5 e 6 em simultâneo verificados a 9 ago 2026
    (ver [multiplayer](funcionalidades/multiplayer.md)); acima de 6 não há emuladores.
11. **Saída a meio em 2x2 e Grupo** — verificada só em 1x1.
12. ~~**Ramo de reautenticação** da eliminação de conta.~~ Feito a 9 ago 2026 — ver
    [eliminacao-conta](funcionalidades/eliminacao-conta.md). Falta só o caminho da
    **palavra-passe errada**, deixado de fora para não arriscar o rate limit a meio da purga.
13. **Áudio em runtime num APK de release** — verificado só ao nível do recurso.

## Produto / desenho

14. **Reflow do cartão de perfil do Início.** Parcialmente resolvido a 9 ago 2026: a legenda dos
    chips desceu para 13 sp e deixou de partir em duas linhas no caso normal. Para letra de
    sistema muito grande continua a faltar layout adaptativo (`BoxWithConstraints`) — isso é
    trabalho a sério, não um remendo.
15. **"Mais pontos" por formato** no ranking — exige contador novo e só contaria daí para a
    frente. Ver [eixos-ranking](decisoes/eixos-ranking.md).
16. **~11 perguntas por confirmar** factualmente (lista nominal na Fase 17 do arquivo).
17. **Temporizador de auto-arranque** vive na composição e só corre para o anfitrião: se ele puser
    a app em segundo plano, o relógio pára.
18. **Tablet nunca foi testado.** Landscape não se aplica (o manifesto força portrait).
19. **Ouvir os sons num dispositivo real.** A correção da interferência (9 ago 2026) foi
    verificada por medição e por registo, não por audição — os emuladores desta máquina correm
    com `-no-audio`. Ver [som-haptico](funcionalidades/som-haptico.md).
20. **Rever as 595 perguntas novas** factualmente. Ver
    [conteudo-perguntas](funcionalidades/conteudo-perguntas.md).
