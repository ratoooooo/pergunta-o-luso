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
5. **Eliminar as 4 contas `teste*@starforge.test`** — estão desativadas, não apagadas. São 4
   cliques na consola do Firebase.

## Higiene técnica

6. **Apagar código morto:** `MultiMatchRepository.createRoom` e a fila `/matchmakingN` (mais as
   rules correspondentes). Confirmado sem chamadores.
7. **Limpeza de estado velho na RTDB:** lobbies, multisalas, `/salas_privadas` e contas anónimas
   de QA. Não há TTL nem job de limpeza — ver
   [limitacoes-conhecidas](seguranca/limitacoes-conhecidas.md).
8. **Índices e queries no servidor:** `loadAllProfiles` e `loadMyScores` descarregam tudo para
   filtrar no cliente. Falta `.indexOn` em `pontos`/`uid`.
9. **Guardar o `mapping.txt`** de cada release publicado, fora do repositório.

## Verificação em falta

10. **Grupo com 5 a 10 jogadores** — nunca observado (só há 4 emuladores).
11. **Saída a meio em 2x2 e Grupo** — verificada só em 1x1.
12. **Ramo de reautenticação** da eliminação de conta.
13. **Áudio em runtime num APK de release** — verificado só ao nível do recurso.

## Produto / desenho

14. **Reflow do cartão de perfil do Início** — em ecrãs estreitos os chips partem o texto letra a
    letra. É layout adaptativo (`BoxWithConstraints`), não um remendo.
15. **"Mais pontos" por formato** no ranking — exige contador novo e só contaria daí para a
    frente. Ver [eixos-ranking](decisoes/eixos-ranking.md).
16. **~11 perguntas por confirmar** factualmente (lista nominal na Fase 17 do arquivo).
17. **Temporizador de auto-arranque** vive na composição e só corre para o anfitrião: se ele puser
    a app em segundo plano, o relógio pára.
18. **Tablet nunca foi testado.** Landscape não se aplica (o manifesto força portrait).
