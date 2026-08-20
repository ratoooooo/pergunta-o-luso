# Sequência diária e protecção

← [índice](../00-indice.md)

Sequência de **dias seguidos a jogar**, com uma protecção que tapa um dia falhado. Fase 33.

Nasceu da revisão de retenção da Fase 32: o jogo estava bem construído para a sessão e não tinha
**nada** para o dia seguinte. Não havia uma única data guardada em lado nenhum.

## Não confundir com a outra sequência

São duas coisas com o mesmo nome e não se misturam:

| | Onde vive | O que conta | Onde aparece |
|---|---|---|---|
| `maxStreak` | já existia | respostas certas seguidas **dentro** de uma partida | só na pergunta, número solto ao lado dos pontos |
| `diasSeguidos` | novo | dias civis seguidos com pelo menos uma partida | só no Início, pastilha que diz "N DIAS SEGUIDOS" |

O **vocabulário visual é de propósito o mesmo** — chama, Dourado a partir de 2, Coral a partir de
5 — porque a ideia que comunicam é a mesma ("não quebres isto") e não vale a pena obrigar o
jogador a aprender dois sinais. O que os distingue é o rótulo e o sítio: os dois **nunca
partilham ecrã**.

## Campos em `/jogadores/{uid}`

| Campo | Tipo | Notas |
|---|---|---|
| `diasSeguidos` | número | 0–3660 nas rules |
| `ultimoDiaJogado` | string | `"AAAA-MM-DD"`, dia civil de Lisboa |
| `maiorSequenciaDias` | número | recorde histórico, **nunca desce** |
| `protecoesStreak` | número | 0 ou 1 |
| `protecaoUsadaEm` | string | dia que a protecção tapou, para se poder avisar o jogador |

Foi preciso mexer nas rules: `/jogadores/$uid` tem `$other: {".validate": false}`, por isso **um
campo novo é rejeitado até ser declarado**. Os tectos seguem a convenção da casa — bloqueiam o
impossível, não o implausível.

## Decisões

### O dia é o de Lisboa, não o do dispositivo

`ultimoDiaJogado` guarda **data**, não timestamp, e a data é a de `Europe/Lisbon` para toda a
gente. Porquê:

1. O conteúdo, a língua e o público são de Portugal — o dia que o jogador tem em mente é o de
   Lisboa.
2. Com o fuso do dispositivo, a fronteira do dia mudava de jogador para jogador, e o mesmo
   jogador via a sequência mexer só por atravessar um fuso.
3. Guardar data e não instante torna "foi ontem?" uma subtracção trivial.

**Custo assumido:** quem jogar dos Açores ou do Brasil vê o dia virar à meia-noite de Lisboa e não
à sua. É consciente; se algum dia houver público fora de Portugal, é isto que se muda.

Uma data guardada **no futuro** (relógio do telemóvel para trás) é tratada como "já jogou hoje":
não incrementa e não reinicia. Estragar a sequência de alguém por causa do relógio seria pior do
que não fazer nada.

### Uma protecção, reposta aos 7 dias

Começa-se com 1. Falhar **um** dia gasta-a e a sequência continua; falhar **dois** reinicia à
mesma, escudo intacto (só tapa um dia). Volta a 1 quando a sequência chega a um múltiplo de 7.

Considerou-se fixá-la em 1 para sempre, que era a opção mais simples. Rejeitada: gasta uma vez,
o jogador ficava sem rede **para sempre** e a mecânica morria à primeira falha. Repor a cada 7
dias mantém o estado binário (0 ou 1) e continua a ser fácil de evoluir para um stock maior.

A protecção é **visível**: o Início diz "A tua sequência foi protegida pelo dia que falhaste", no
próprio dia e no seguinte. Uma rede de segurança que ninguém vê não tranquiliza ninguém — e sem
o aviso o jogador não percebia porque é que a sequência não tinha zerado.

### XP: +5 fixos por dia, nunca escalável

`StreakDiario.XP_POR_DIA = 5`, somado **uma vez por dia**, no primeiro jogo do dia. Não cresce
com o tamanho da sequência e não tem multiplicador.

Deliberadamente pequeno por causa da Roda da Sorte (Fase 20), em que XP sem tecto desequilibrou a
curva de progressão. Um jogador diário perfeito ganha 1825 XP por ano com isto — menos do que
oito partidas. Serve de empurrão, não de atalho.

## Onde corre

Dentro de `ProfileRepository.accumulate`, ou seja **na mesma transação** que agrega a partida.
Solo e multijogador passam os dois por `updateAfterGame`, por isso qualquer partida conta sem ser
preciso duplicar nada.

O dia é calculado **fora** da transação e passado em `GameResult.hoje`: o handler pode correr
várias vezes, e recalcular a data numa retentativa que atravessasse a meia-noite dava dois
resultados para a mesma partida.

`ultimoDiaJogado` e `protecaoUsadaEm` **só são escritos quando têm valor**. As rules exigem
`AAAA-MM-DD` nos dois, e escrever `""` faria falhar a validação — que é sobre o nó inteiro, por
isso não perdia só a sequência: rejeitava a transação toda e o jogador perdia os pontos e o XP
da partida.

## Verificação (10 ago 2026)

A lógica pura tem **13 testes** (`StreakDiarioTest`), incluindo relógio para trás, data guardada
inválida e o recorde a não descer.

No dispositivo, os emuladores desta máquina **não são rootáveis** (`adbd cannot run as root`), por
isso não deu para avançar o relógio. Em vez disso semeou-se `ultimoDiaJogado` e jogou-se a sério
— o que corre a seguir é o código verdadeiro, com as rules publicadas:

| Cenário | Resultado |
|---|---|
| Primeira partida de sempre | `diasSeguidos = 1` ✓ |
| Jogou ontem | 4 → 5 ✓ |
| Segunda partida no mesmo dia | continua 5, sem XP extra ✓ |
| Falhou 1 dia, com escudo | 5 → 6, escudo a 0, dia tapado registado ✓ |
| Falhou 1 dia, sem escudo | reinicia a 1, recorde 9 preservado ✓ |
| Falhou 2 dias, com escudo | reinicia a 1, escudo **não** se gasta ✓ |
| Chegou a 7 dias | escudo reposto ✓ |
| 3 dias / 6 dias | pastilha Dourada / Coral, medida ao pixel ✓ |

Rules testadas por REST nos dois sentidos com token real: 6 escritas legítimas a 200, 8 inválidas
(negativo, absurdo, formato de data errado, data vazia, campo inventado) a 401, e outro jogador a
escrever na sequência alheia a 401.

**Por observar:** a contagem numa partida **multijogador**. Corre pelo mesmo
`ProfileRepository.updateAfterGame`, e isso está lido no código, mas não foi exercitado com dois
dispositivos.

Ver também: [xp-niveis-patentes](xp-niveis-patentes.md) ·
[conquistas-avatares](conquistas-avatares.md) · [rules](../arquitetura/rules.md)
