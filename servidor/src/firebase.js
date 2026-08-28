/**
 * A única ponte para o Firebase. Duas coisas entram (verificar quem é, ler perguntas) e uma sai
 * (gravar `/scores`).
 *
 * ### O servidor NÃO é admin
 *
 * `databaseAuthVariableOverride` faz o processo autenticar-se na RTDB como um utilizador normal
 * com `auth.uid === 'pol-servidor'`, **sujeito às rules** — em vez do admin por omissão, que as
 * ignora por completo. Os poderes do processo passam a ser exactamente os que
 * `database.rules.json` lhe der, e mais nenhum: ler o que qualquer autenticado lê, e criar
 * registos em `/scores` com um `formato` de multijogador.
 *
 * A chave de conta de serviço continua a ser uma credencial poderosa (quem a tiver pode
 * reinicializar o SDK sem o override e passar a admin). O que isto reduz é o estrago de um
 * defeito **neste** processo, não o de a chave fugir — para essa parte valem as permissões do
 * ficheiro e o utilizador dedicado do systemd.
 */

import admin from 'firebase-admin';

export const UID_DO_SERVIDOR = 'pol-servidor';

let app = null;

export function iniciarFirebase({
  projectId = process.env.FIREBASE_PROJECT_ID,
  databaseURL = process.env.FIREBASE_DATABASE_URL
} = {}) {
  if (app) return app;
  if (!projectId || !databaseURL) {
    throw new Error('faltam FIREBASE_PROJECT_ID / FIREBASE_DATABASE_URL');
  }
  app = admin.initializeApp({
    credential: admin.credential.applicationDefault(),   // GOOGLE_APPLICATION_CREDENTIALS
    projectId,
    databaseURL,
    databaseAuthVariableOverride: { uid: UID_DO_SERVIDOR }
  });
  return app;
}

/**
 * Verifica o ID token do Firebase Auth que o cliente traz no handshake. Devolve o `uid` — que
 * fica preso à ligação e é o único que o servidor usa daí em diante. **Nenhuma mensagem
 * posterior transporta uid**: é isto que substitui o `auth.uid === $uid` das rules.
 */
export async function verificarToken(idToken) {
  const decodificado = await admin.auth().verifyIdToken(idToken);
  return { uid: decodificado.uid, anonimo: decodificado.firebase?.sign_in_provider === 'anonymous' };
}

export async function lerCaminho(caminho) {
  const snap = await admin.database().ref(caminho).get();
  return snap.exists() ? snap.val() : null;
}

/**
 * Nome de exibição, lido do perfil e **não** enviado pelo cliente — até aqui era o dispositivo
 * que mandava o nome para o lobby, e portanto podia mandar o de outra pessoa.
 */
export async function nomeDoJogador(uid) {
  const nome = await lerCaminho(`jogadores/${uid}/nome`);
  return (typeof nome === 'string' && nome.trim()) ? nome.trim() : 'Jogador';
}

/**
 * Grava o registo em bruto de um jogador numa partida. Campos e tectos iguais aos do
 * `ScoreRepository.saveScore` — as rules validam folha a folha e recusam campos não declarados.
 *
 * Não lança para cima: uma falha a gravar o `/scores` não pode derrubar a partida de toda a
 * gente, e o pódio já foi entregue. Devolve o id ou `null`.
 */
export async function gravarScore({ uid, modo, categoria, formato, score, correctCount, total }) {
  try {
    const ref = admin.database().ref('scores').push();
    await ref.set({
      uid, modo, categoria, formato,
      score, correctCount, total,
      timestamp: Date.now()
    });
    return ref.key;
  } catch (e) {
    console.error('[scores] falhou a gravar', { uid, formato }, e.message);
    return null;
  }
}
