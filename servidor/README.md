# Servidor da partida ao vivo

Servidor autoritativo do multijogador do Pergunta ó Luso. **Decide certo/errado e a pontuação** —
o que até aqui era decidido no dispositivo.

Só isto sai do Firebase. Auth, perfil agregado, XP, conquistas, sequência diária, amigos,
ranking, histórico e o solo inteiro continuam onde estavam.

## Correr localmente

```bash
npm install
npm test                    # 64 testes, sem rede e sem Firebase
```

Para correr a sério é preciso Firebase:

```bash
export FIREBASE_PROJECT_ID=...
export FIREBASE_DATABASE_URL=https://....firebasedatabase.app
export GOOGLE_APPLICATION_CREDENTIALS=/caminho/para/chave.json
export ADMIN_TOKEN=...            # protege POST /admin/recarregar
npm start
```

## Como está feito

| Ficheiro | O quê |
|---|---|
| `src/app.js` | HTTP + WebSocket + encaminhamento. **Dependências do Firebase injectadas** — é o que permite testar uma partida inteira sem base de dados. |
| `src/servidor.js` | Arranque: liga o Firebase real e põe-se à escuta. |
| `src/lobbies.js` | Matchmaking. Salas públicas, por código, e de desafio direto. |
| `src/partida.js` | A partida. Correcção, pontuação, lockstep, walkover, pódio. |
| `src/pontuacao.js` | Fórmula de pontos e eventos caóticos. **Duplica o Kotlin de propósito** — ver abaixo. |
| `src/perguntas.js` | Banco em memória + rampa de dificuldade. Guarda a `respostaCorreta`. |
| `src/formatos.js` | 1x1 / 2x2 / Grupo: capacidade e mínimo. |
| `src/firebase.js` | Verificar tokens, ler perguntas, gravar `/scores`. |

### O servidor não é admin

`firebase-admin` arranca com `databaseAuthVariableOverride: { uid: 'pol-servidor' }`: o processo
é um utilizador **sujeito às rules**, não um admin que as ignora. Os poderes que tem são os que
`database.rules.json` lhe der — ler o que qualquer autenticado lê, e criar registos em `/scores`
com um `formato` de multijogador.

### A única duplicação, e porquê

`src/pontuacao.js` repete `game/Scoring.kt`. É inevitável: quem corrige a resposta tem de saber
pontuá-la. Um desvio entre as duas versões mudaria o jogo sem partir nada, por isso está preso
por `testes/pontuacao.json` — vectores **calculados à mão**, lidos pelo teste daqui e pelo
`PontuacaoVectoresTest` do lado Android. Mexer na fórmula sem mexer nos vectores faz falhar os
dois lados.

Tudo o resto que parece duplicado (formatos, rampa de dificuldade) **mudou de sítio**, não foi
copiado: quando o cliente passar a falar com o servidor, essas cópias saem do Kotlin.

## Tectos assumidos

- **Um processo, estado em memória, sem Redis.** Lobbies e partidas morrem com um reinício —
  daí a drenagem no `SIGTERM`. Para 2 vCore / 4 GB isto sobra; um segundo processo obrigaria a
  estado partilhado, e é aí que um Colyseus ou um Redis voltam à conversa.
- **Sem persistência de partidas.** Uma partida interrompida por um `kill -9` não se recupera.
- **A app continua a agregar o perfil** (`/jogadores/{uid}`), com os números que o servidor lhe
  manda. Continua falsificável — igual ao solo, que nunca terá servidor. O que fecha é a
  pontuação que o adversário vê e o registo em `/scores`.
