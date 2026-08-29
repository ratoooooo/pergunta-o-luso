# Limitações conhecidas do modelo

← [índice](../00-indice.md)

Não são bugs por corrigir — são **consequências assumidas** da arquitetura. Estão aqui para
ninguém as descobrir a meio de uma auditoria e achar que são novidade.

## 1. A pontuação do **solo** é validada no cliente

No solo o dispositivo decide se a resposta está certa e calcula os pontos. O único travão são os
tectos numéricos das rules de `/scores` — bloqueiam um valor impossível, **não um valor plausível
mas não merecido**. Continua assim, e vai continuar: um servidor para o solo não se justifica.

**No multijogador já não é verdade.** Desde 29 ago 2026 a partida ao vivo corre no
[servidor próprio](../arquitetura/servidor-partida.md), que decide certo/errado, calcula a
pontuação e escreve `/scores` com a identidade `pol-servidor`. As rules recusam a um cliente
qualquer `formato` que não seja `solo`, por isso um dispositivo não consegue declarar que ganhou
um 1x1.

Caiu com isso a limitação que aqui estava escrita como impossível de fechar: **esconder a
`respostaCorreta` era impossível enquanto o cliente tinha de a ler para corrigir.** Agora ela
não sai do servidor antes de a pergunta fechar, e só vai para quem já respondeu.

O que **fica** por fechar no multijogador: o perfil agregado (`/jogadores/{uid}`) continua escrito
pelo dispositivo, com os números que o servidor lhe mandou. Falsificável, ao mesmo nível do solo —
e pela mesma razão de sempre, que é o solo passar por lá também.

## 2. O contador de denúncias não é verificável

As rules da RTDB não têm `numChildren()`. Detalhe em
[quizzes-comunidade](../funcionalidades/quizzes-comunidade.md).

## 3. Sem TTL, sem limpeza

A RTDB não expira nada sozinha e não há job de limpeza:

- **Contas anónimas** criadas em testes ficam no Auth sem dados associados.

O que aqui estava sobre lobbies, multisalas e códigos de sala **deixou de se aplicar**: esses nós
foram removidos a 29 ago 2026 e o estado da partida vive em memória no servidor, que o larga
quando a sala esvazia. Já não há nada nesses caminhos para acumular — nem emparelhamentos
fantasma, nem códigos de 4 dígitos a esgotar.

## 4. ~~O código de sala não é um segredo forte~~ — resolvido

Era: `/lobbies` tinha `.read: auth != null` e o `codigo` lá dentro, por isso quem enumerasse
lobbies via os códigos de toda a gente. Com `/lobbies` fora da RTDB não há o que enumerar: o
servidor manda a lista de salas abertas sem códigos, e o código só é conhecido por quem criou a
sala. A protecção do conteúdo continua a ser a pertença à sala, agora imposta pelo servidor.

## 5. Escala e custo

`loadAllProfiles` e `loadMyScores` descarregam `/jogadores` e `/scores` **inteiros** para filtrar
no cliente. Falta `.indexOn` em `pontos`/`uid` e queries do lado do servidor. Não é segurança, é
conta no fim do mês.

## 6. Responsividade

Não havia **nenhuma** lógica responsiva (zero ocorrências de `WindowSizeClass`,
`BoxWithConstraints` ou `LocalConfiguration`). Foi corrigido o pior — quatro ecrãs sem scroll em
que o botão JOGAR ficava inalcançável a 720×1280 com `font_scale` 1.3 — mas o cartão de perfil do
Início continua a espremer os chips (o texto parte-se letra a letra) e precisa de *reflow* a
sério. Tablet nunca foi testado; landscape não se aplica (o manifesto força portrait).

## 7. O que nunca foi observado

Distinguir **verificado** de **deduzido** é a razão desta secção existir:

- **Salas de Grupo com 5 a 10 jogadores** — só há quatro emuladores.
- **Saída a meio em 2x2 e Grupo** — verificada só em 1x1; nos outros é dedução sobre código
  partilhado.
- **O ramo de reautenticação da eliminação de conta.**
- **O áudio a tocar num APK de release** — verificado ao nível do recurso (os WAV sobrevivem ao
  `shrinkResources`), não em runtime.
- **Audibilidade do som por um humano** — tudo o que as ferramentas mostram diz que é
  renderizado; ouvir não dá.

Ver também: [historico-vulnerabilidades](historico-vulnerabilidades.md) ·
[rules](../arquitetura/rules.md)
