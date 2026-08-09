# Limitações conhecidas do modelo

← [índice](../00-indice.md)

Não são bugs por corrigir — são **consequências assumidas** da arquitetura. Estão aqui para
ninguém as descobrir a meio de uma auditoria e achar que são novidade.

## 1. A pontuação é validada no cliente

**Não há servidor nem Cloud Functions.** O dispositivo decide se a resposta está certa
(`writeAnswer` recebe `correct: Boolean` já decidido) e calcula os pontos.

Esconder a `respostaCorreta` dos próprios jogadores é **impossível nesta arquitetura**: o cliente
tem de a ler para corrigir. O que se pôde fazer com rules foi fechar a leitura de `meta.perguntas`
aos **não-membros** da sala.

**O único travão a batota são os tectos numéricos nas rules** — `pontuacao ≤ 4000` (10 perguntas
× 400, o máximo matemático: 15 s × 10 × 2.0 de dificuldade + 100 de sequência),
`respostasCertas ≤ 10`, e limites equivalentes em `/scores`. Isto bloqueia um valor impossível;
**não bloqueia um valor plausível mas não merecido**.

Validação a sério exigiria Cloud Functions: o cliente enviaria a escolha e a função devolvia
certo/errado e escrevia a pontuação. Está fora de âmbito e é conhecido.

## 2. O contador de denúncias não é verificável

As rules da RTDB não têm `numChildren()`. Detalhe em
[quizzes-comunidade](../funcionalidades/quizzes-comunidade.md).

## 3. Sem TTL, sem limpeza

A RTDB não expira nada sozinha e não há job de limpeza:

- **Lobbies e multisalas** de sessões de QA acumulam-se e já causaram emparelhamentos fantasma
  mais do que uma vez. Convém limpar `/lobbies` e `/multisalas` antes de testar multijogador.
- **`/salas_privadas`** nunca expira. Com 9000 códigos de 4 dígitos, a probabilidade de colisão
  cresce com o tempo. Há retentativa, mas sem limpeza acaba por esgotar.
- **Contas anónimas** criadas em testes ficam no Auth sem dados associados.

## 4. O código de sala não é um segredo forte

`/lobbies` tem `.read: auth != null` (o matchmaking aleatório precisa) e o `codigo` está lá
dentro. Quem enumerar lobbies vê códigos. **A protecção real do conteúdo é a pertença ao lobby,
não o segredo do código.** Fechar isto implicaria tirar o `codigo` do lobby e repensar a listagem
de salas abertas.

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
