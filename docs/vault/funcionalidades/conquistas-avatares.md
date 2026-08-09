# Conquistas e avatares

← [índice](../00-indice.md)

## Conquistas (15)

`game/Achievement.kt`. **Todas ligadas a campos que já existem** em `/jogadores/{uid}` — nenhuma
exigiu inventar telemetria nova.

| Conquista | Ligada a |
|---|---|
| Primeira Vitória | `vitorias ≥ 1` |
| Em Chamas | `maxStreak ≥ 5` |
| Partida Perfeita | `partidasPerfeitas ≥ 1` |
| Mestre de {Cultura Geral, Geografia, História, Desporto, Gentílicos} | `categorias.{slug}.vitorias ≥ 3` |
| Veterano / Dedicado / Lendário | `jogos ≥ 10 / 50 / 100` |
| Duelista / Companheiro / Rei do Grupo | `multiVitorias.{1x1,2x2,grupo} ≥ 1` |
| Nível 5 | `nivel ≥ 5` (derivado) |

Cada uma tem um símbolo português distinto. Bloqueada = silhueta cinzenta + cadeado + progresso
`x / y`; desbloqueada = símbolo a cores + anel dourado com halo pulsante. O ecrã tem separadores
Todas / Feitas / Por fazer — 15 conquistas empilhadas eram densas de mais.

**Ressalva honesta:** "Em Chamas" está ligada a `maxStreak`, que é a melhor sequência de
**respostas certas**, não de vitórias seguidas. Uma sequência de vitórias exigiria um campo novo,
evitado de propósito.

Uma conquista desbloqueada é detectada por **comparação do perfil antes e depois** de agregar a
partida, e celebrada no pódio com som — ver [som-haptico](som-haptico.md).

## Avatares (10 símbolos)

`game/avatar/`. Desenhados como **line art em Compose `Canvas`** (`SymbolIcon.kt`), todos com a
mesma espessura de traço e um só tom — por isso o mesmo desenho serve de avatar (creme sobre
cor), de conquista bloqueada (cinzento) e desbloqueada (cor cheia).

Azulejo · Pastel de Nata · Caravela · Farol · Sardinha · Galo de Barcelos · Os Lusíadas ·
Guitarra · Calçada · Coração de Viana.

**Porquê desenhados à mão e não gerados:** nenhuma ferramenta de geração de imagem disponível no
ambiente escrevia PNGs recortáveis para disco. Três símbolos foram redesenhados depois de uma
primeira versão ler mal (a caravela parecia um windsurfista; a guitarra, um corta-pizza; o galo,
uma mancha).

Guardado em `/jogadores/{uid}/avatar`; sem avatar escolhido mostra as iniciais do nome. No
selector o escolhido marca-se com um **emblema de visto** no canto, não com um anel dourado — o
anel desaparecia por cima dos símbolos que já são dourados.

Ver também: [xp-niveis-patentes](xp-niveis-patentes.md) ·
[rtdb-schema](../arquitetura/rtdb-schema.md)
