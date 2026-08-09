# Amigos, desafios diretos e presença

← [índice](../00-indice.md)

## Pesquisa de jogadores

Sem índice novo: `/jogadores/{uid}/nomeBusca` é o nome **trimmed + minúsculas**, escrito na
**mesma `updateChildren`** que o `nome` (`ProfileRepository.setNome`), por isso nunca divergem —
registo e edição de perfil passam ambos por lá, não há terceiro caminho de escrita.

Query: `orderByChild("nomeBusca").startAt(q).endAt(q + "").limitToFirst(20)`, com
`.indexOn: "nomeBusca"`. Filtra o próprio uid e perfis sem nome — **jogadores anónimos nunca têm
`nomeBusca`, logo são invisíveis à pesquisa por construção**. Os resultados mostram avatar + nome
+ nível, para distinguir nomes repetidos.

`nomeBusca` guarda acentos (só faz lowercase), por isso um prefixo com acento tem de ser escrito
com acento.

> **Dados legados:** perfis criados antes desta funcionalidade têm `nome` mas não `nomeBusca` e
> **não aparecem na pesquisa** até o dono reeditar o nome. Não houve backfill — exigiria escrever
> no nó de outros uids, o que as rules (bem) proíbem; teria de ser um script admin.

## Modelo de amizade (`/amigos`)

```
/amigos/{uid}/pedidosEnviados/{outro}   { nome, ts }
/amigos/{uid}/pedidosRecebidos/{outro}  { nome, ts }
/amigos/{uid}/lista/{outro}             { nome, ts }   // escrito nos DOIS lados
```

Um pedido pendente existe em dois sítios; aceitar move-o para `lista` em ambos. **Cada transição
é um único `updateChildren` multi-caminho a partir da raiz**, por isso os dois lados nunca podem
divergir.

Escolhido em vez de um nó partilhado por par de uids porque assim cada jogador lê **só o seu
próprio nó** — um único listener alimenta as três zonas do ecrã, sem fan-out nem índices extra.

A regra que impede alguém de se auto-adicionar à lista de outra pessoa: só se pode **completar**
uma amizade que o dono iniciou (tem de existir o `pedidosEnviados` correspondente).

## Desafio direto (`/convites`)

Complementa o matchmaking aleatório: escolhe-se **quem** enfrentar.

- **Só 1x1.** Um convite 1-para-1 mapeia exactamente numa sala de 2. 2x2/Grupo exigiriam um lobby
  com vários convites em paralelo. O formato viaja no convite e o pipeline aceita qualquer
  `MatchFormat`, por isso alargar depois é só permitir mais convites por sala.
- **Só amigos online** podem ser desafiados (reutiliza `/presenca`). Um convite só é entregue com
  a app aberta, por isso mostrá-lo para offline seria enganador.
- **Expira em 45 s** (`CONVITE_TTL_MS`) — curto para não deixar o desafiante à espera, longo para
  reagir a um overlay que aparece sem aviso.
- **30 s entre desafios ao mesmo amigo**, para não dar para bombardear.

**A sala é criada primeiro** e o `salaId` vai dentro do convite. Aceitar é literalmente "entra
nesta sala": daí para a frente é o código de multijogador de sempre — lockstep, desistência,
pódio, agregação. Zero duplicação de lógica de jogo.

O destinatário filtra convites com mais de 45 s pelo relógio do servidor e tem um ticker de 1 s,
para o caso de o desafiante morrer sem limpar.

**Nota:** "NOVO JOGO" no pódio de uma partida por convite volta ao **matchmaking aleatório** — a
sala do desafio é de uso único. Convites que cheguem durante uma partida não são mostrados; ficam
no inbox e aparecem no fim, se ainda não tiverem expirado.

## Presença (`/presenca`)

Cada cliente escreve `/presenca/{uid} = true` com `onDisconnect().removeValue()`, e um listener
de `.info/connected` **re-arma** o onDisconnect a cada reconexão.

**"A jogar agora" significa: qualquer jogador com a app aberta** — não só quem está numa partida.
Distinguir "em jogo" exigiria estado partilhado extra; "app aberta" é a definição honesta possível.

Limitação: depois de um período longo parado, o socket da RTDB pode cair e o contador lê a menos
por instantes, corrigindo-se sozinho na mudança seguinte.

Ver também: [multiplayer](multiplayer.md) · [rules](../arquitetura/rules.md)
