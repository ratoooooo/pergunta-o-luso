# Limpeza de contas e dados de teste — 9 ago 2026

← [índice](../00-indice.md)

Antes desta limpeza o projeto tinha **73 contas** no Firebase Auth, quase todas resíduo de QA em
emuladores. Ficaram **6**; as outras 67 tiveram os dados da RTDB purgados e esperam só pela
extinção no Auth.

## O que sobreviveu, e porquê

| Conta | Porquê |
|---|---|
| `teste_um_2026@starforge.test` | as 4 dos emuladores, ainda em uso para QA de multijogador |
| `teste_dois_2026@starforge.test` | idem |
| `teste_tres_2026@starforge.test` | idem |
| `teste_quatro_2026@starforge.test` | idem |
| `rato@gmail.com` | conta pessoal do dono do projeto, não é de teste |
| `inis.teste@example.com` | e-mail tem padrão de teste mas o nome é do dono — preservada por decisão explícita |

As quatro dos emuladores continuam amigas umas das outras (3 arestas cada); o grafo delas não foi
tocado.

## A verificação que veio antes de apagar

Na Fase 22 uma conta de teste (`teste1`) era dona do único quiz da comunidade do projeto e o quiz
foi apagado junto com ela. Por isso, desta vez, o levantamento correu **antes** de qualquer
escrita:

- **`/categorias_comunitarias` não existe** na base de dados — zero quizzes. O caso da Fase 22 não
  se podia repetir porque já não há conteúdo partilhado nenhum.
- **Grafo de amizades fechado dentro das coortes de teste**: Ana Costa ↔ Bruno Dias, e cada um dos
  dois grupos `Teste Um–Quatro` só entre si. Nenhuma aresta a apontar para uma conta preservada.
- **12 registos em `/scores` sem campo `uid`** — órfãos de um formato antigo, não reclamáveis por
  ninguém. Apagados também.

## Como a purga foi feita

Espelha `AccountDeletionRepository.purge()` caminho a caminho, em vez de inventar lógica nova:
mesmos nós, mesmos *buckets*, mesma limpeza cega do espelho na contraparte, e **uma única
`updateChildren` a partir da raiz** — 216 caminhos de uma vez, para não existir um estado
intermédio com o perfil apagado e as arestas ainda a apontar para ele.

O plano tinha uma asserção a recusar correr se algum caminho contivesse o uid de um sobrevivente.

Resultado, conferido depois da escrita:

| Nó | Restos das 67 contas |
|---|---|
| `/jogadores` | 0 |
| `/scores` | 0 (+ 0 órfãos sem uid) |
| `/amigos`, nó próprio | 0 |
| `/amigos`, espelho noutras contas | 0 |
| `/presenca` | 0 |

`/jogadores` passou de 23 para 6 perfis, `/scores` de 111 para 71 registos. `/lobbies`,
`/multisalas` e `/salas_privadas` ficaram a `null`; `/matchmakingN` (as três variantes) e
`/notify` e `/denuncias` não existem na base de dados.

## O que ficou por fazer: extinguir as contas no Auth

**A CLI do Firebase não tem `auth:delete`** — só `auth:export` e `auth:import`. Continua a ser o
mesmo bloqueio da Fase 22. Nesta máquina não há service account nem `gcloud`, e extrair o refresh
token do config store da CLI é indistinguível de exfiltração de credenciais.

Está escrito [`qa/apagar-contas-teste.js`](../../qa/apagar-contas-teste.js), pronto a correr, com
a mesma lista de seis sobreviventes embutida e uma guarda que aborta se não encontrar exactamente
essas seis. Precisa de:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/caminho/para/chave.json
npm install firebase-admin
node qa/apagar-contas-teste.js --dry-run
```

Até lá as 67 contas existem mas não têm dado nenhum associado. As anónimas são inalcançáveis —
ninguém volta a entrar numa sessão anónima de outro dispositivo.

## Actualização de 20 ago 2026 — as contas do Auth foram mesmo apagadas

A secção acima ficou por cumprir tal como estava escrita: **não foi o script que as apagou.** O
dono extinguiu-as directamente na consola do Firebase.

E não foram só as 67. `rato@gmail.com` e `inis.teste@example.com` — as duas que a tabela de
sobreviventes acima marcava para ficar — **também foram apagadas, de propósito**. A decisão de
9 ago 2026 deixou de valer; a tabela fica como está por ser o registo do que se decidiu na altura.

Estado verificado nesse dia, com uma chave de conta de serviço nova:

| | |
|---|---|
| contas no Auth | **8** |
| com e-mail | 4 — `teste_{um,dois,tres,quatro}_2026@starforge.test` |
| anónimas | 4 |

`KEEP_EMAILS` em [`qa/apagar-contas-teste.js`](../../qa/apagar-contas-teste.js) passou a listar só
as 4 dos emuladores. Enquanto lá estiveram as seis, a guarda do script abortava sempre — exigia
encontrar todas as de KEEP_EMAILS, e duas já não existiam. O script continua a servir: os alvos
que sobram são as 4 sessões anónimas, e a QA vai deixando ficar mais.

Na mesma altura o script foi migrado para a API modular do `firebase-admin` (v13/v14 removeu
`admin.credential` e `admin.auth()`), e `qa/` ganhou `package.json` próprio — o
`npm install firebase-admin` do bloco acima corria a partir de `qa/` mas ia instalar à pasta
pessoal, por não haver lá `package.json` nenhum. Agora é `npm install`, a partir de `qa/`.

## Backups

Snapshots pré-limpeza de `/jogadores`, `/scores`, `/amigos`, `/convites`, `/presenca`,
`/lobbies`, `/multisalas`, `/categorias` e do `auth:export` completo ficaram no scratchpad da
sessão, **fora do repositório**. Não sobrevivem à máquina — se a reversão importar, copiar para
um sítio permanente.

Ver também: [eliminacao-conta](../funcionalidades/eliminacao-conta.md) ·
[limitacoes-conhecidas](../seguranca/limitacoes-conhecidas.md)
