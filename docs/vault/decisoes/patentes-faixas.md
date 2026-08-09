# Faixas das patentes

← [índice](../00-indice.md)

## O tema

**A hierarquia de bordo de uma nau portuguesa, seguida da progressão para o mar aberto.** As
quatro primeiras são postos reais e pela ordem certa (grumete → marinheiro → piloto → capitão);
as duas últimas vêm dos Descobrimentos.

Assenta no resto do jogo: o ícone da app é uma caravela, e Caravela e Farol já são dois dos dez
símbolos de avatar. Seis patentes chegam para haver degraus visíveis sem virar tabela de patentes
militares.

## As fronteiras

| Patente | Níveis | XP acumulado | ≈ partidas (a 120 XP) |
|---|---|---|---|
| Grumete | 1–4 | 0 | 0 |
| Marinheiro | 5–9 | 2 100 | 18 |
| Piloto | 10–14 | 8 100 | 68 |
| Capitão | 15–19 | 17 850 | 149 |
| Navegador | 20–24 | 31 350 | 261 |
| Descobridor | 25+ | 48 600 | 405 |

Escolhidas **contra a curva de XP real** (`75*(n-1)*(n+2)` acumulado para chegar ao nível n) e
contra os ~50–150 XP de uma partida — não por serem números redondos.

- A **primeira subida (nível 5)** chega em menos de vinte partidas, para a mecânica se dar a
  conhecer cedo.
- E **coincide de propósito** com a conquista "Nível 5", que já existia: o mesmo momento passa a
  valer duas coisas.
- **Descobridor fica deliberadamente longe** — é a patente que quase ninguém tem.

## Derivadas, nunca guardadas

A patente sai do nível, que sai do `xpTotal`. Nada é persistido, por isso não pode dessincronizar.

`PatenteTest` verifica as fronteiras **e** que os números da tabela acima batem certo com a
`Progressao` real. Foi esse teste que apanhou dois valores mal escritos na documentação (Capitão
dizia 18 900 XP e 158 partidas; é 17 850 e 149) — a tabela deste ficheiro está corrigida.

## Onde ficou colocada, e porquê ali

No Início e no Perfil a patente entra na **linha de rótulos da barra de XP**, não ao lado do nome
do jogador. O cartão de perfil do Início já espremia os três chips em ecrãs estreitos; um
elemento novo a disputar essa largura reproduzia o mesmo defeito. Na linha da barra o canto
esquerdo estava vazio (o `x / y XP` está encostado à direita).

No Ranking vai dentro da pastilha existente: `Nv 5 · Marinheiro`.

Ver também: [xp-niveis-patentes](../funcionalidades/xp-niveis-patentes.md)
