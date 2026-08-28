/**
 * Arranque do processo: liga o Firebase real à app e põe-se à escuta.
 *
 * Tudo o que é lógica está em `app.js`, com as dependências injectadas — aqui só se decide
 * *quais* são. É o que permite aos testes correr a mesma app com um Firebase falso.
 *
 * ### Escutar primeiro, carregar depois
 *
 * A ordem não é arbitrária. Fazer o contrário — inicializar o Firebase e carregar o banco antes
 * do `listen()` — dava um processo que, sem credenciais ou com a RTDB inacessível, morria ao
 * arrancar. Com `Restart=always` no systemd isso é um ciclo de reinício silencioso: nada em
 * escuta, `/saude` sem responder, e a única forma de perceber porquê é o journal.
 *
 * Escutando primeiro, uma falha de Firebase passa a ser um servidor **vivo que se explica**:
 * `/saude` responde e diz `banco.perguntas: 0`. É a diferença entre diagnosticar com um `curl`
 * e diagnosticar por adivinhação.
 *
 * ### Modo esqueleto
 *
 * Sem `FIREBASE_PROJECT_ID`/`FIREBASE_DATABASE_URL` o servidor arranca à mesma, mas **sem
 * conseguir autenticar ninguém**: os handshakes de WebSocket levam 401, porque verificar um ID
 * token exige o Firebase. Serve para validar a infra-estrutura (systemd, Caddy, TLS, firewall)
 * antes de existir uma chave de conta de serviço — que é uma credencial poderosa e não deve ser
 * criada antes de fazer falta.
 */

import { criarApp } from './app.js';
import { iniciarFirebase, verificarToken, lerCaminho, nomeDoJogador, gravarScore } from './firebase.js';

const PORTA = Number(process.env.PORT ?? 2567);
// Só o laço local: quem fala com a internet é o Caddy, que trata do TLS. A porta do Node nunca
// é aberta no ufw.
const ANFITRIAO = process.env.HOST ?? '127.0.0.1';

const temFirebase = Boolean(process.env.FIREBASE_PROJECT_ID && process.env.FIREBASE_DATABASE_URL);

/** Sem Firebase não há como verificar um token — e sem token não entra ninguém. */
const semFirebase = async () => { throw new Error('firebase por configurar'); };

const app = criarApp(
  temFirebase
    ? { verificarToken, lerCaminho, nomeDoJogador, gravarScore, segredoAdmin: process.env.ADMIN_TOKEN ?? '' }
    : {
        verificarToken: semFirebase,
        lerCaminho: semFirebase,
        nomeDoJogador: semFirebase,
        gravarScore: semFirebase,
        segredoAdmin: process.env.ADMIN_TOKEN ?? ''
      }
);

process.on('SIGTERM', app.drenar);
process.on('SIGINT', app.drenar);

app.servidor.listen(PORTA, ANFITRIAO, () => {
  console.log(`[servidor] em ${ANFITRIAO}:${PORTA}`);
  if (!temFirebase) {
    console.warn('[firebase] MODO ESQUELETO: sem FIREBASE_PROJECT_ID/FIREBASE_DATABASE_URL.');
    console.warn('[firebase] /saude responde, mas nenhuma ligação de jogo é aceite (401).');
    return;
  }
  arrancarFirebase();
});

/**
 * Fora do caminho do `listen` de propósito: uma RTDB em baixo ou uma chave inválida não pode
 * derrubar o servidor. Fica sem perguntas — visível no `/saude` — e recupera-se com
 * `POST /admin/recarregar` sem reiniciar nada.
 */
async function arrancarFirebase() {
  try {
    iniciarFirebase();
    const resumo = await app.carregarBanco();
    console.log(`[banco] ${resumo.perguntas} perguntas em ${resumo.categorias} categorias`);
  } catch (e) {
    console.error('[banco] falhou a carregar:', e.message);
    console.error('[banco] o servidor fica a responder; tenta POST /admin/recarregar');
  }
}
