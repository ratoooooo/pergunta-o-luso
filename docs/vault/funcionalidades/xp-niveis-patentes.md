# XP, níveis e patentes

← [índice](../00-indice.md)

## Só se guarda `xpTotal`

`data/Progressao.kt`. **O nível e a divisão dentro do nível são sempre derivados** de `xpTotal`,
drenando o custo de cada nível. Nunca são persistidos, por isso os dados guardados não podem
divergir da curva — se a curva mudar, os níveis recalculam-se sozinhos.

**Curva:** `xpNecessarioParaProximoNivel(n) = 300 + (n-1)*150` → 300, 450, 600, …
XP acumulado para *chegar* ao nível n: `75*(n-1)*(n+2)`.

**XP por partida terminada** = `base + desempenho + vitória`:

| Parcela | Valor |
|---|---|
| base | 50 (Clássico/Caótico) · 40 (Eliminatórias) |
| desempenho | `respostasCertas * 10` |
| vitória | 100 (ou 80 em Eliminatórias), 0 se não venceu |

Quizzes da comunidade dão **XP reduzido** (5 por acerto) e não entram em `/scores` nem nos
agregados normais — as perguntas não têm curadoria.

Curva e forma da recompensa vêm do BrainBrawl (comportamento como referência, sem reutilizar
código). Divergência assumida: o BrainBrawl anula o bónus de vitória no modo solo; aqui é dado a
**qualquer** partida ganha, porque uma vitória solo é o sinal de mérito que existe.

Nota: **pontos não são XP.** Um veterano com muitos pontos continua no nível 1 até jogar sob este
sistema — os perfis existentes começaram em `xpTotal = 0`.

## Patentes

`data/Patente.kt`. Seis nomes derivados do nível — outra vez, nada persistido.

| Patente | Níveis | XP acumulado | ≈ partidas (a 120 XP) |
|---|---|---|---|
| Grumete | 1–4 | 0 | 0 |
| Marinheiro | 5–9 | 2 100 | 18 |
| Piloto | 10–14 | 8 100 | 68 |
| Capitão | 15–19 | 17 850 | 149 |
| Navegador | 20–24 | 31 350 | 261 |
| Descobridor | 25+ | 48 600 | 405 |

O tema e as fronteiras estão justificados em [patentes-faixas](../decisoes/patentes-faixas.md).
`PatenteTest` (5 testes) prende as fronteiras e verifica que os números da tabela batem certo com
a `Progressao` real — foi esse teste que apanhou dois valores mal escritos na documentação.

## Onde aparece

| Ecrã | O quê |
|---|---|
| Início | emblema de nível no cartão de perfil + barra de XP com a patente |
| Perfil | emblema + barra detalhada `x / y XP` com a patente |
| Ranking | pastilha `Nv 5 · Marinheiro` por jogador |

A barra de XP usa um **gradiente frio** (Teal → Azul → Roxo), não as cores de estado: a barra do
tempo, no ecrã da pergunta, vai de Teal a Dourado a Coral, e as duas não se podem confundir.
Acima de 85 % ganha um brilho dourado pulsante — a única cor quente da barra, que se lê como
"estás quase a subir".

## Limitação conhecida

Num ecrã de 720 px com `font_scale` a 1.3, as patentes mais longas ainda podem reticenciar no
Início (o corpo já desce para 13 sp). Degrada com reticências, não parte o layout. A correção a
sério é o *reflow* do cartão de perfil, que continua por fazer.

Ver também: [conquistas-avatares](conquistas-avatares.md) · [ranking-historico-perfil](ranking-historico-perfil.md)
