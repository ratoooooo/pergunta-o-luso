# Regras de segurança (`database.rules.json`)

← [índice](../00-indice.md)

**As rules são o único chão de validação que existe.** Não há servidor nem Cloud Functions, por
isso tudo o que elas não travarem, não é travado. Ler
[limitacoes-conhecidas](../seguranca/limitacoes-conhecidas.md) antes de assumir que algo está
protegido.

## Princípios em vigor

1. **Nada é público.** `/categorias` e `/jogadores` exigem `auth != null` (anónimo conta). Antes eram `.read: true` e qualquer pessoa com o URL despejava as perguntas **com a resposta certa** ou a tabela inteira de jogadores.
2. **Cada um escreve só o seu.** Todo o nó por jogador valida `auth.uid === $uid`.
3. **Schemas fechados.** `$other: { ".validate": false }` em quase todos os nós — um campo desconhecido é rejeitado em vez de guardado.
4. **Tectos numéricos** onde o cliente calcula o valor (pontuações), porque a pontuação é
   client-authoritative.
5. **Create-once** onde o dado não deve mudar depois de escrito (`meta` das salas, códigos de
   sala privada).

## Armadilhas do motor de rules (aprendidas à força)

- **`.validate` não corre em apagamentos.** Um `.write` permissivo com `.validate` apertado
  deixa apagar à vontade. Foi assim que qualquer autenticado podia apagar o `/scores` de outro.
- **`.write` cascateia para descendentes; `.validate` corre em todos.** Por isso o controlo de
  dono em `/lobbies` é feito por `.validate`, não por `.write` — a transação do matchmaking entra
  pelo nó do formato inteiro e regras `.write` por sala nunca chegariam a ser avaliadas.
- **Aritmética sobre `null` invalida a expressão inteira**, mesmo do outro lado de um `||`.
  Contadores incrementais precisam de ternário:
  `data.exists() ? newData.val() === data.val() + 1 : newData.val() === 1`
- **Não há `numChildren()`** — é impossível validar que um contador bate certo com o número real
  de filhos. Ver a limitação do contador de denúncias em
  [quizzes-comunidade](../funcionalidades/quizzes-comunidade.md).
- **Uma transação de ascendente revalida os dados dos outros.** Uma regra nova pode ser
  logicamente correta e à mesma partir a app, porque falha em **lixo pré-existente** que a
  transação arrasta. Aconteceu — ver
  [historico-vulnerabilidades](../seguranca/historico-vulnerabilidades.md).

## Método de teste

As rules são exercitadas por **REST com tokens reais de duas contas**, sempre dos dois lados:
o ataque tem de ser negado **e** o caminho legítimo tem de continuar a passar. Testar só o
ataque produz rules que protegem tudo, incluindo o utilizador. Nas fases de segurança isto deu
baterias de 8 a 43 verificações.

Ver também: [rtdb-schema](rtdb-schema.md) ·
[historico-vulnerabilidades](../seguranca/historico-vulnerabilidades.md)

## Fase 33 — campos da sequência diária

`/jogadores/$uid` ganhou `diasSeguidos`, `ultimoDiaJogado`, `maiorSequenciaDias`,
`protecoesStreak` e `protecaoUsadaEm`. **Tiveram de ser declarados**: o nó tem
`$other: {".validate": false}`, por isso um campo novo é rejeitado até constar do schema.

Tectos: 0–3660 nos contadores de dias (dez anos — o ponto onde o valor deixa de poder ser
verdade), 0–1 nas protecções, e `matches(/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/)` nas duas datas. É o
mesmo chão de validação do resto do ficheiro: bloqueia o impossível, não o implausível, porque
quem quiser bater a sequência muda a data do telemóvel e não há servidor que o impeça.

**A armadilha aqui:** a validação das datas é sobre o nó inteiro, revalidado pela transação de
agregação. Escrever `""` num destes campos não falharia só a sequência — rejeitava a transação
toda, e o jogador perdia os pontos e o XP da partida. Por isso o cliente só escreve estas duas
datas quando têm valor. Ver [streak-diario](../funcionalidades/streak-diario.md).

Testadas por REST nos dois sentidos: 6 escritas legítimas aceites, 8 inválidas negadas, e escrita
cruzada de outro uid negada.
