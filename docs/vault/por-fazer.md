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
5. **Apagar 67 contas de teste do Firebase Auth.** A limpeza de 9 ago 2026 purgou-lhes **todos**
   os dados da RTDB, mas as contas do Auth continuam de pé: a CLI do Firebase só tem
   `auth:export` / `auth:import`, e não há service account nem `gcloud` nesta máquina. Está
   pronto o script [`qa/apagar-contas-teste.js`](../../qa/apagar-contas-teste.js) — só precisa de
   uma chave de conta de serviço e `npm install firebase-admin`; corre primeiro com `--dry-run`.
   Sobrevivem seis contas, decididas contigo: as 4 dos emuladores
   (`teste_{um,dois,tres,quatro}_2026@starforge.test`), `rato@gmail.com` e
   `inis.teste@example.com`.
   *Nota:* as 4 `teste*@starforge.test` estavam documentadas como "desativadas" — o export mostra
   `disabled: false` nas quatro. Ou foram reactivadas, ou nunca chegaram a ser desactivadas.

## Higiene técnica

6. **Apagar código morto:** `MultiMatchRepository.createRoom` e a fila `/matchmakingN` (mais as
   rules correspondentes). Confirmado sem chamadores.
7. ~~**Limpeza de estado velho na RTDB.**~~ Feita a 9 ago 2026 — ver
   [limpeza-dados-teste](manutencao/limpeza-dados-teste.md). `/lobbies`, `/multisalas` e
   `/salas_privadas` a `null`; `/matchmakingN` e `/categorias_comunitarias` não existem sequer;
   `/jogadores` passou de 23 para 6 perfis e `/scores` de 111 para 71 registos. Continua sem
   haver TTL nem job de limpeza, por isso volta a acumular — ver
   [limitacoes-conhecidas](seguranca/limitacoes-conhecidas.md).
8. **Índices e queries no servidor:** `loadAllProfiles` e `loadMyScores` descarregam tudo para
   filtrar no cliente. Falta `.indexOn` em `pontos`/`uid`.
9. **Guardar o `mapping.txt`** de cada release publicado, fora do repositório.

## Defeitos abertos

**B. `observeRoom` mata a app quando a RTDB cancela o listener.**
`MultiMatchViewModel.observeRoom` (linha ~300) faz `collect` sem `runCatching`, e
`MultiMatchRepository.kt:374` fecha o `callbackFlow` com a exceção — sai por `viewModelScope`
como `FATAL EXCEPTION: main` (`DatabaseException: This client does not have permission to
perform this operation`). Provocado por apagar `/multisalas` com clientes dentro da sala, o que
não é uso normal; mas qualquer negação de leitura na sala (sair de `meta.membrosNomes`, limpeza
de estado) segue o mesmo caminho. Todas as outras chamadas do ficheiro estão dentro de
try/catch — esta é a excepção.

**C. A lista do top do pódio nunca se actualiza em directo.** `sessionOnly()` limpa `topScores` e
`loadTopScores()` corre uma vez por pódio, por isso uma pontuação nova só aparece no pódio
seguinte. As chaves estáveis e o `animateItemPlacement` já lá estão (Fase 34) — falta a lista
passar a observar `/scores`, se algum dia isso interessar.

**D. Erro de transporte do Auth chega ao utilizador em inglês.**
*"An internal error has occurred. [ unexpected end of stream on com.android.okhttp.Address@… ]"*
apareceu no diálogo de eliminação. `friendlyAuthError` não o mapeia.

**E. `StatChip` corta o último dígito de números grandes, sem aviso.** O `Text` do valor (Início,
cartão de perfil) não tem `overflow` definido; com `maxLines = 1` e um número mais largo do que o
chip, o Compose recorta em vez de mostrar tudo — sem reticências, sem sinal de que falta algo.
Visto com `pontos = 3894610` a mostrar `389461`. `uiautomator` confirma que o valor semântico
está completo (é só o desenho que falha). Nunca deveria acontecer em uso normal — a pontuação por
jogo tem tecto de 4000 nas rules — mas um jogador de longo prazo pode legitimamente acumular
7 dígitos. Descoberto a 10 ago 2026, ver
[ranking-historico-perfil](funcionalidades/ranking-historico-perfil.md).

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
