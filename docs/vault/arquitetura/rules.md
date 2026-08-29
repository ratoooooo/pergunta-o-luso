# Regras de segurança (`database.rules.json`)

← [índice](../00-indice.md)

**Para tudo o que não é a partida ao vivo, as rules são o único chão de validação.** Não há Cloud
Functions, por isso o que elas não travarem, não é travado. Ler
[limitacoes-conhecidas](../seguranca/limitacoes-conhecidas.md) antes de assumir que algo está
protegido.

A excepção é o multijogador: desde 29 ago 2026 a partida corre no
[servidor próprio](servidor-partida.md), que se autentica na RTDB como `pol-servidor` — um
utilizador **sujeito a estas rules**, não um admin que as ignora. `/lobbies`, `/multisalas`,
`/salas_privadas` e `/matchmakingN` saíram do ficheiro nessa altura.

## Princípios em vigor

1. **Nada é público.** `/categorias` e `/jogadores` exigem `auth != null` (anónimo conta). Antes eram `.read: true` e qualquer pessoa com o URL despejava as perguntas **com a resposta certa** ou a tabela inteira de jogadores.
2. **Cada um escreve só o seu.** Todo o nó por jogador valida `auth.uid === $uid`.
3. **Schemas fechados.** `$other: { ".validate": false }` em quase todos os nós — um campo desconhecido é rejeitado em vez de guardado.
4. **Tectos numéricos** onde o cliente calcula o valor — hoje só o solo, que continua a pontuar
   no dispositivo.
5. **Create-once** onde o dado não deve mudar depois de escrito.
6. **O multijogador não passa pelo cliente.** `/scores` só aceita `formato` diferente de `solo`
   vindo de `auth.uid === 'pol-servidor'`, e só esse uid pode gravar um registo com o uid de
   outra pessoa — é ele que apura o resultado de todos os jogadores da partida.

## Armadilhas do motor de rules (aprendidas à força)

- **`.validate` não corre em apagamentos.** Um `.write` permissivo com `.validate` apertado
  deixa apagar à vontade. Foi assim que qualquer autenticado podia apagar o `/scores` de outro.
- **`.write` cascateia para descendentes; `.validate` corre em todos.** Era por isso que o
  controlo de dono em `/lobbies` se fazia por `.validate` e não por `.write`: a transação do
  matchmaking entrava pelo nó do formato inteiro e as regras `.write` por sala nunca chegavam a
  ser avaliadas. O nó já não existe, mas a armadilha continua a valer para qualquer transação
  sobre um ascendente.
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
