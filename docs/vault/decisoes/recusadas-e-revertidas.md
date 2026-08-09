# Decisões recusadas e trabalho revertido

← [índice](../00-indice.md)

O que **não** existe, e porquê. Poupa a próxima pessoa de reconstruir algo que já foi rejeitado
por boas razões.

## Roda da Sorte Diária — escrita e removida

Estava feita e a compilar, ligada a nada. Removida por inteiro. Por ordem de gravidade:

1. **Não funcionava.** Escrevia `ultimaRodaSpinTs` em `/jogadores/{uid}`, campo que não existe
   nas rules — e esse nó tem `$other: {".validate": false}`. A transação falhava com
   `Permission denied` logo à primeira rodada.
2. **Sem anti-cheat.** `xpTotal` só valida `isNumber() && >= 0`: sem tecto, sem verificação de
   que o incremento é um dos prémios possíveis, e sem confirmação das 24 h do lado do servidor —
   o cliente é que escreve o timestamp que depois se usa para o bloquear. Um cliente adulterado
   pedia o XP que quisesse, as vezes que quisesse.
3. **Economia furada.** Oito prémios equiprováveis de 100 a 1000 XP dão ≈ 406 XP por dia **só por
   abrir a app**, contra os ~50–150 XP de um jogo jogado. Isso inverte a curva de níveis: subia-se
   mais a rodar do que a jogar.
4. **Fora do sistema visual.** Trazia cinco cores próprias que não existem em `Color.kt`, e o
   texto dos gomos era preto fixo sobre roxo escuro (ilegível).

> Se voltar, tem de nascer com validação no servidor e prémios equilibrados contra a curva de XP.
> **Não é um trabalho de UI.**

## Salas por convite com "ESTOU PRONTO"

O mockup tinha código de sala, "Convidar…" e ready-up manual. O matchmaking é por lobby e a
partida **arranca sozinha** (ou pelo anfitrião). Não foram acrescentados controlos de ready-up
que sugerissem um passo que o jogador teria de dar.

Pela mesma razão, o "● Pronto" da sala de espera foi **removido** e passou a "Na sala": não
existe ready-up nenhum, dizer "Pronto" era enganador.

*(Salas privadas por código passaram entretanto a existir — o que não existe é o ready-up.)*

## Desafio direto em 2x2 e Grupo

Só 1x1. Um convite 1-para-1 mapeia exactamente numa sala de 2; 2x2/Grupo exigiriam um lobby com
vários convites em paralelo (convidar 3 amigos e esperar por todos). Preferiu-se não fingir
suporte — o formato já viaja no convite, por isso alargar depois é permitir mais convites por
sala.

## "Mais pontos" por formato no ranking

Recusado **por agora**, com razão explícita: o campo não existe e criá-lo faria a tabela nascer a
mentir sobre quem joga há mais tempo. Ver [eixos-ranking](eixos-ranking.md).

## Dados inventados para igualar o mockup

Não foram introduzidos: imagens nas perguntas, contagens fabricadas, filtros
Oficial/Personalizadas sem fonte de dados, nem requisitos de palavra-passe que a app não valida
(o mockup pedia maiúscula e número; pô-los no cartão de requisitos seria prometer uma validação
inexistente).

## Fila `/matchmakingN`

Substituída pelo matchmaking por lobby. `MultiMatchRepository.createRoom` — a única função que lá
escrevia — não é chamada de lado nenhum. **É código morto confirmado**; apagá-lo (e as rules
correspondentes) é limpeza segura, ainda por fazer.

O mesmo aconteceu antes com `/matchmaking` e `/salas1x1` (o 1x1 autónomo), esses já removidos.

Ver também: [multiplayer](../funcionalidades/multiplayer.md) ·
[limitacoes-conhecidas](../seguranca/limitacoes-conhecidas.md)
