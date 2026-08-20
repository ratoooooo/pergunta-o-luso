#!/usr/bin/env node
/**
 * Apaga do Firebase Auth as contas de teste que a limpeza de 9 ago 2026 deixou para trás.
 *
 * A CLI do Firebase não tem `auth:delete` — só `auth:export` / `auth:import`. Os dados da RTDB
 * dessas contas já foram purgados (perfis, scores, arestas de amigos, presença); falta só
 * extinguir as contas do Auth, e isso exige credencial de administrador.
 *
 * COMO CORRER
 *   1. Consola do Firebase → Definições do projeto → Contas de serviço → Gerar nova chave
 *      privada. Guarda o JSON FORA do repositório (é uma credencial).
 *   2. export GOOGLE_APPLICATION_CREDENTIALS=/caminho/para/chave.json
 *   3. npm install firebase-admin
 *   4. node qa/apagar-contas-teste.js --dry-run     # lista o que ia apagar
 *      node qa/apagar-contas-teste.js               # apaga mesmo
 *
 * O script recusa-se a tocar nas seis contas de KEEP_EMAILS, e recusa-se a correr se alguma
 * delas aparecer na lista de alvos.
 */
'use strict';

const admin = require('firebase-admin');

/** As únicas contas que sobrevivem. Decidido com o dono do projeto a 9 ago 2026. */
const KEEP_EMAILS = new Set([
  'teste_um_2026@starforge.test',
  'teste_dois_2026@starforge.test',
  'teste_tres_2026@starforge.test',
  'teste_quatro_2026@starforge.test',
  'rato@gmail.com',
  'inis.teste@example.com',
]);

const dryRun = process.argv.includes('--dry-run');

admin.initializeApp({ credential: admin.credential.applicationDefault() });

async function listAll() {
  const out = [];
  let pageToken;
  do {
    const page = await admin.auth().listUsers(1000, pageToken);
    out.push(...page.users);
    pageToken = page.pageToken;
  } while (pageToken);
  return out;
}

(async () => {
  const users = await listAll();
  const keep = users.filter((u) => u.email && KEEP_EMAILS.has(u.email));
  const doomed = users.filter((u) => !(u.email && KEEP_EMAILS.has(u.email)));

  if (keep.length !== KEEP_EMAILS.size) {
    console.error(`ABORTADO: esperava ${KEEP_EMAILS.size} contas a preservar, encontrei ${keep.length}.`);
    console.error('Confirma que nenhuma delas já foi apagada antes de continuar.');
    process.exit(1);
  }
  for (const u of doomed) {
    if (u.email && KEEP_EMAILS.has(u.email)) {
      console.error(`ABORTADO: ${u.email} está na lista de alvos e não devia.`);
      process.exit(1);
    }
  }

  console.log(`total ${users.length} · preservar ${keep.length} · apagar ${doomed.length}\n`);
  console.log('PRESERVAR:');
  keep.forEach((u) => console.log(`  ${u.email}  ${u.uid}`));
  console.log('\nAPAGAR:');
  doomed.forEach((u) => console.log(`  ${(u.email || '(anónima)').padEnd(34)} ${u.uid}`));

  if (dryRun) {
    console.log('\n--dry-run: nada foi apagado.');
    return;
  }

  // deleteUsers aceita no máximo 1000 uids por chamada.
  let apagadas = 0;
  const falhas = [];
  for (let i = 0; i < doomed.length; i += 1000) {
    const lote = doomed.slice(i, i + 1000).map((u) => u.uid);
    const r = await admin.auth().deleteUsers(lote);
    apagadas += r.successCount;
    r.errors.forEach((e) => falhas.push(`${lote[e.index]}: ${e.error.message}`));
  }

  console.log(`\napagadas ${apagadas} · falhas ${falhas.length}`);
  falhas.forEach((f) => console.log(`  ${f}`));
  process.exit(falhas.length ? 1 : 0);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
