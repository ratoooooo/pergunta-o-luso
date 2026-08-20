# Ranking, histórico e perfil

← [índice](../00-indice.md)

Tudo isto lê de `/jogadores` (agregado), **nunca de `/scores`** — o log em bruto teria o mesmo
jogador várias vezes na mesma tabela. Excepção: o Histórico, que é por definição a lista de
partidas.

## Ranking — duas dimensões, três níveis

| Nível | Componente | Conteúdo |
|---|---|---|
| Dimensão | `UnderlineTabs` | Por modo · Por formato |
| Valor | `SegmentedTabs` (pastilha roxa) | Clássico/Caótico/Eliminatórias **ou** 1x1/2x2/Grupo |
| Tabela | `UnderlineTabs` | as três listas da dimensão |

A pastilha — o elemento mais pesado — fica **onde já estava**, no meio. A dimensão entra por cima
em sublinhado porque é uma troca rara de duas opções e não deve pesar mais do que a escolha
seguinte. Trocar de dimensão repõe os separadores de baixo no primeiro: "Eliminatórias" e "Grupo"
não são a mesma coisa e herdar o índice dava um quadro que o jogador não pediu.

**Por modo** (de `modos/{modo}`): Mais vitórias · Mais pontos · Melhor recorde.
**Por formato** (de `multiVitorias`/`multiJogos`): Mais vitórias · Mais jogos · % vitórias.

Porque é que por formato não há "Mais pontos": [eixos-ranking](../decisoes/eixos-ranking.md).

A tabela de percentagem exige **3 jogos** (`MIN_JOGOS_PARA_PERCENTAGEM`) — sem mínimo, quem
ganhou o único jogo que fez aparecia em 1.º com 100 %. O ecrã explica o corte numa linha por cima
da tabela, **também quando a tabela está cheia**: cinco nomes parecem uma lista completa e quem
não se encontra nela não teria como saber porquê.

Cada linha mostra posição, avatar, nome, pastilha `Nv 5 · Marinheiro` e o valor. O próprio
jogador aparece com **contorno roxo e "(tu)"** — sem isso era preciso ler todos os nomes para se
encontrar. O 1.º lugar é dourado, com um encaixe creme por trás do avatar (um avatar dourado
sobre linha dourada desaparecia). Top 5 por tabela.

## Histórico

As partidas do próprio jogador, mais recentes primeiro: categoria · modo, certas/total, data,
pontos. Lido de `/scores` filtrado por uid **no cliente** (não há índice por `uid`; o histórico é
pequeno).

Separadores por **formato**: Todos / Solo / 1x1 / 2x2 / Grupo, a partir do campo `formato` do
`ScoreEntry`.

## Perfil

Grelha de estatísticas globais + detalhe por modo, de `/jogadores/{uid}`. Edição de nome inline
(passa pelo mesmo `setNome` do registo, por isso `nomeBusca` acompanha).

"Por modo" usa **separadores**, não três cartões empilhados — as mesmas cinco métricas repetidas
três vezes inflacionavam a densidade sem acrescentar informação.

Terminar sessão fica no fim, pequeno e discreto. **Eliminar conta** fica ainda mais abaixo,
separado por uma linha, e é o único elemento do ecrã em Coral cheio — ver
[eliminacao-conta](eliminacao-conta.md).

## O Início e o Perfil ficam ao vivo (10 ago 2026)

Reportado: a barra de XP e as estatísticas do Início não actualizavam sozinhas depois de uma
partida — só se o jogador passasse pelo Perfil primeiro (isso força uma releitura). Mesmo padrão
do bug do `friendsJob` da Fase 14: estado preso a um momento antigo em vez de reagir à mudança
real.

**Causa.** `GameViewModel` só tinha leitura pontual de `/jogadores/{uid}`
(`ProfileRepository.loadProfile`), chamada em três pontos: `refreshProfile()`, `goToProfile()` e
dentro de `finishGame()` a seguir a uma partida **solo**. Isso escondia o problema em solo — o
perfil ficava mesmo actualizado antes de o jogador sair do pódio — mas expunha-o por completo em
**multijogador**: `MultiMatchViewModel` é um ViewModel separado, com o seu próprio estado, e
agrega o perfil (`aggregateProfile` → `profileRepository.updateAfterGame`) escrevendo
directamente na RTDB. Nunca existiu qualquer forma de avisar o `GameViewModel` de que os dados
tinham mudado — o Início ficava sempre com o valor de antes da partida.

**Correcção — a mesma que `/amigos/{uid}` e `/presenca` já usam.** `ProfileRepository.observe(uid)`
devolve um `Flow<Profile>` (`callbackFlow` + `ValueEventListener`, sem novidade nenhuma no
padrão), e `GameViewModel` liga-o a `_uiState.profile` assim que o uid fica disponível —
exactamente onde `observeFriends(uid)` já corria. Passa a ser a **fonte de verdade contínua**,
qualquer que seja quem escreveu: solo, multijogador, ou o que vier a seguir. O Início deixou de
depender de alguém se lembrar de avisar.

A leitura pontual de `finishGame()` **manteve-se** — não é redundante, é o único sítio onde se
compara "perfil antes" com "perfil depois" da mesma partida para detectar subida de nível e
conquistas novas, e isso continua a exigir um valor determinístico logo a seguir à escrita.

**Uid a mudar exige religar o listener**, tal como já valia para `friendsJob`: religado em
`register()`, `login()`, `signOut()` e `confirmDeleteAccount()`, nos mesmos pontos onde
`observeFriends()` já era religado.

**Verificado no dispositivo, dois emuladores**, sem passar pelo Perfil em nenhum caso:

| Cenário | Antes → depois |
|---|---|
| Solo, voltar direito ao Início | 72060 pts, 62→63 jogos, 660→740 XP |
| Multijogador (1x1), voltar direito ao Início | 63→64 jogos, 830→1010 XP |
| Escrita directa na BD com a app aberta no Início (sem jogar) | XP reflectido em ~2 s, sem qualquer acção do jogador |
| Subida de nível numa partida solo | Nível 11→**12** no emblema, XP reiniciado para o novo nível (30/1950), imediato ao voltar |

**Descoberta lateral, não corrigida — fora do âmbito deste bug.** Durante o teste de multijogador
o script de automação bateu nas respostas mais depressa do que um jogador faria, inflacionando os
pontos de uma conta de teste para `3 894 610`. Isso expôs um bug de **UI separado**: o `Text` do
valor em `StatChip` (Início) não tem `overflow` definido, e corta o último dígito quando o número
é demasiado largo para o chip — mostra `389461`, sem reticências, sem aviso. Confirmado com
`uiautomator`, que lê o valor semântico completo (`3894610`) mesmo com o dígito cortado no
ecrã — não é um bug de dados, é puramente de layout. Nunca visto em uso normal (a pontuação
por jogo tem tecto de 4000 nas rules), mas um jogador de longo prazo pode legitimamente lá
chegar. Ver [por-fazer](../por-fazer.md).

## Limitação conhecida

`loadAllProfiles` e `loadMyScores` descarregam `/jogadores` e `/scores` **inteiros** para filtrar
no cliente. Não é problema de segurança, é de escala e custo: falta `.indexOn` em `pontos`/`uid`
e queries do lado do servidor. Está identificado desde a auditoria de pré-lançamento.

Ver também: [xp-niveis-patentes](xp-niveis-patentes.md) ·
[rtdb-schema](../arquitetura/rtdb-schema.md)
