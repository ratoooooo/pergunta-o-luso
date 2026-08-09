# Hierarquia de cor

← [índice](../00-indice.md)

A regra mais estruturante do sistema visual. Se houver dúvida sobre que cor dar a alguma coisa
nova, é aqui.

## A regra

> **Em cada ecrã, o dourado quer dizer uma coisa só.**
>
> Nos ecrãs que celebram um resultado (Pódio solo e multijogador, Ranking, Conquistas) o dourado
> é o **vencedor/mérito**, e a ação primária passa a **Roxo**. Em todos os outros ecrãs o dourado
> é a **ação primária**.

O dourado tem dois significados legítimos — ação primária **e** primeiro lugar/mérito — e nos
ecrãs de resultado colidiam: o pódio tinha cartão dourado, linha #1 dourada e botão dourado ao
mesmo tempo.

**Os separadores nunca são dourados**: não são ação nem mérito.

## Consequências práticas

| Elemento | Cor | Porquê |
|---|---|---|
| Ação primária (ecrã normal) | Dourado | uma só por ecrã |
| Ação primária (ecrã de resultado) | Roxo | o dourado está reservado ao mérito |
| Ação secundária | Lavanda | útil sem competir |
| Cancelar / destrutivo | Coral | mesmo significado em toda a app |
| Confirmar / certo | Teal | idem |
| Separador activo | Roxo | filtro não é ação nem mérito |
| "Sou eu" | contorno roxo | **nunca** fundo Teal — "sou eu" e "ganhei" não podem ser o mesmo sinal |

## Dois casos que valem por si

**As opções de resposta.** Nasciam pintadas de Roxo/Coral/Teal/Dourado — exactamente as cores que
a revelação usa para dizer "certa", "era esta" e "erraste". Uma opção **ainda por responder**
aparecia verde ou vermelha e parecia já corrigida. Em Verdadeiro/Falso era pior: "Verdadeiro"
nascia verde e "Falso" vermelho, o que **insinuava a resposta antes de o jogador escolher**.

Solução: em repouso, cartão lavanda neutro com **emblema roxo A/B/C/D** — a letra substitui a cor
como forma de distinguir. A cor só entra na revelação.

**A barra de XP.** É a única barra da app que **não** usa cores de estado, por isso leva um
gradiente frio (Teal → Azul → Roxo). A barra do tempo, na pergunta, é lisa e vai de Teal a
Dourado a Coral conforme o tempo acaba. Assim nunca se confunde "quanto tempo falta" com "quanto
XP falta". O brilho dourado acima dos 85 % é a única cor quente da barra e lê-se
inequivocamente como "estás quase a subir de nível".

## A cor de categoria é excepção

Nos cartões de categoria a cor é **identidade**, não hierarquia: reaparece no chip do Modo, no
cabeçalho da pergunta e no Histórico. Esses ecrãs não têm botão com que possa competir.

## Divergência assumida do mockup

O mockup pinta o ecrã de jogo de **fundo escuro**. Não foi seguido: o sistema é creme em todos os
ecrãs e um único ecrã escuro leria como outra aplicação. A legibilidade que o fundo escuro dava
às opções coloridas foi obtida de outra maneira — cartões neutros com emblema de letra.

Ver também: [sistema-visual](../arquitetura/sistema-visual.md) ·
[modos-de-jogo](../funcionalidades/modos-de-jogo.md)
