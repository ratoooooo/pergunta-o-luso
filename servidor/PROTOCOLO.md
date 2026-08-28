# Protocolo da partida ao vivo

Fonte única do que passa entre a app e o servidor. Se o Kotlin e este ficheiro discordarem, é
este que está certo — ou então o ficheiro está por actualizar, e isso é um defeito.

- **Transporte:** WebSocket sobre TLS (`wss://`). O Caddy termina o TLS e faz proxy para o Node
  em `127.0.0.1:2567`.
- **Formato:** JSON, uma mensagem por frame, sempre com o campo `t` (tipo).
- **Identidade:** ID token do Firebase no cabeçalho `Authorization: Bearer <token>` do handshake.
  Nunca na query string — o URL fica em logs de proxy e em histórico. Handshake sem token
  válido leva `401` e **não** chega a ser um WebSocket.
- **`uid` nunca viaja nas mensagens.** O servidor usa sempre o da ligação. É isto que substitui
  o `auth.uid === $uid` que as rules da RTDB faziam.

## Cliente → servidor

| `t` | Campos | O que faz |
|---|---|---|
| `procurar` | `formato`, `categoria`, `modo` | Entra no primeiro lobby compatível ou cria um. |
| `trocar_sala` | `lobbyId` | "VER OUTRAS SALAS ABERTAS". Se a sala já não der, cai no `procurar`. |
| `iniciar` | — | INICIAR JOGO. Só o anfitrião, e só a partir do mínimo do formato. |
| `sair` | — | Sai do lobby, ou desiste da partida. |
| `responder` | `indice`, `opcao`, `tCliente` | A opção que o jogador tocou. **Não** manda pontos. |
| `ping` | `t0` | Aferição do relógio do cliente. |
| `sonda_ok` | `s` | Resposta à sonda do servidor (é assim que ele mede o rtt). |
| `privada_criar` | `formato`, `quizId` | Sala privada com um quiz da comunidade. Devolve `sala` com `codigo`. |
| `privada_entrar` | `codigo` | Entra por código de 4 dígitos. |
| `desafio_criar` | `formato`, `categoria`, `modo`, `paraUid` | Cria a sala do desafio; o `lobbyId` vai no convite em `/convites`. |
| `desafio_entrar` | `salaId` | Aceitar o desafio. Só o convidado entra. |

## Servidor → cliente

| `t` | Campos | Quando |
|---|---|---|
| `sessao` | `uid`, `nome`, `versao`, `agora` | Ao ligar. O `nome` vem de `/jogadores/{uid}/nome`. |
| `salas` | `formato`, `salas[]` | Lista de salas abertas (só as não privadas). |
| `sala` | `lobbyId`, `membros`, `souAnfitriao`, `minimo`, `capacidade`, `podeComecar`, `autoEm`, `codigo` | Sempre que o lobby muda. |
| `partida` | `salaId`, `formato`, `membros[{uid,nome,equipa}]`, `totalPerguntas` | A partida vai começar (ecrã de revelação, 2,5 s). |
| `pergunta` | `indice`, `total`, `opcoes`, `dificuldade`, `evento`, `duracao`, `fimEm`, `agora` | Abre uma pergunta. **Sem `respostaCorreta`.** |
| `resposta` | `indice`, `certa`, `respostaCorreta`, `total`, `certas` | Só a quem respondeu, e só depois de responder. |
| `placar` | `indice`, `pontos{uid:n}` | Depois de cada resposta e ao fechar a pergunta. |
| `ausente` / `voltou` / `saiu` | `uid`, `ateEm` | Ligação perdida, recuperada, ou desistência. |
| `podio` | `walkover`, `ranking[]` ou `equipas[]`, `ganhei`, `meuScore`, `minhasCertas`, `maxSequencia`, `totalPerguntas` | Fim. |
| `sonda` | `s` | Medição de rtt; responder já com `sonda_ok`. |
| `pong` | `t0`, `tS` | Resposta ao `ping`. |
| `aviso` / `erro` | `codigo`, `msg` | Ver a tabela abaixo. |

### Códigos de erro

`em_manutencao` (a actualizar, tentar daqui a pouco) · `nao_pode_comecar` · `sem_partida` ·
`pergunta_errada` · `ja_respondeu` · `tarde_demais` · `opcao_invalida` · `fora_da_partida` ·
`codigo_invalido` · `sala_cheia` · `quiz_invalido` · `desafio_expirado` · `convidado_invalido` ·
`arranque_falhou` · `json_invalido` · `accao_desconhecida` · `falha_interna`

Avisos (não são falhas): `sala_indisponivel` (a sala escolhida encheu, ficaste noutra) e
`reentraste` (voltaste a uma partida a decorrer).

## Relógio

Duas medições, com objectivos diferentes:

- **`ping`/`pong`** dá ao **cliente** o desvio do relógio: `desvio = tS − (t0 + t1) / 2`. O
  `fimEm` de cada pergunta vem em tempo de servidor, e é para lá que o cliente conta. Mantém-se
  a disciplina de reler o desvio a cada pergunta, e não uma vez por partida — um desvio velho
  era a principal fonte de dessincronia na versão RTDB.
- **`sonda`/`sonda_ok`** dá ao **servidor** o rtt. É medido por ele, não declarado pelo cliente,
  porque serve para limitar o crédito de tempo de uma resposta.

**Crédito de tempo.** Os pontos dependem dos segundos que sobram, por isso carimbar a resposta à
chegada faria a latência custar pontos. O cliente envia em `tCliente` o instante em que
respondeu (em tempo de servidor) e o servidor credita
`clamp(tCliente, chegada − rtt, chegada)` — mentir só devolve o rtt real do próprio jogador,
que é exactamente o que se lhe queria devolver.

## O que o servidor não aceita

Verificado em cada `responder`: índice diferente do corrente, segunda resposta à mesma pergunta,
resposta depois de `fimEm`, opção que não consta da pergunta, jogador fora da partida. E fora
dela: `iniciar` de quem não é anfitrião ou abaixo do mínimo, entrar numa sala cheia, já começada
ou privada sem convite.

**O cliente não tem pontuação própria.** O total é o que a última mensagem `resposta` disser.
