#!/usr/bin/env node
/**
 * Apaga do Firebase Auth todas as contas que não estejam em [KEEP_EMAILS].
 *
 * A CLI do Firebase não tem `auth:delete` — só `auth:export` / `auth:import` —, por isso extinguir
 * contas exige credencial de administrador. Foi escrito para as 67 que a limpeza de 9 ago 2026
 * deixou para trás; **essas já não existem** (o dono apagou-as na consola a 20 ago 2026, ver
 * `docs/vault/manutencao/limpeza-dados-teste.md`). O que sobra são as sessões anónimas que a QA
 * vai deixando ficar, e é para essas que o script serve daqui para a frente.
 *
 * COMO CORRER
 *   1. Consola do Firebase → Definições do projeto → Contas de serviço → Gerar nova chave
 *      privada. Guarda o JSON FORA do repositório (é uma credencial).
 *   2. export GOOGLE_APPLICATION_CREDENTIALS=/caminho/para/chave.json
 *   3. npm install          # a partir de qa/, que tem o seu package.json
 *   4. node qa/apagar-contas-teste.js --dry-run     # lista o que ia apagar
 *      node qa/apagar-contas-teste.js               # apaga mesmo
 *
 * O script recusa-se a tocar nas quatro contas de KEEP_EMAILS, e recusa-se a correr se alguma
 * delas aparecer na lista de alvos.
 */
'use strict';

/**
 * As únicas contas que sobrevivem: as 4 dos emuladores, ainda em uso para QA de multijogador.
 *
 * Eram seis (9 ago 2026) — `rato@gmail.com` e `inis.teste@example.com` também cá estavam. A 20
 * ago 2026 o dono apagou-as na consola, de propósito e fora deste script. Ficam fora da lista
 * porque a guarda abaixo exige que **todas** as contas de KEEP_EMAILS existam: com duas que já
 * não existem, o script abortava sempre e nunca chegava a correr.
 */
const KEEP_EMAILS = new Set([
  'teste_um_2026@starforge.test',
  'teste_dois_2026@starforge.test',
  'teste_tres_2026@starforge.test',
  'teste_quatro_2026@starforge.test',
]);

const dryRun = process.argv.includes('--dry-run');

function abortar(mensagem) {
  console.error(`ABORTADO: ${mensagem}`);
  process.exit(1);
}

/**
 * `firebase-admin` é carregado só aqui, e não no topo, para o `require` deste ficheiro não
 * depender de uma credencial nem do pacote estar instalado.
 *
 * API modular (`firebase-admin/app`, `firebase-admin/auth`). A antiga, com namespace —
 * `admin.credential.applicationDefault()`, `admin.auth()` — foi **removida** no firebase-admin
 * v13/v14 e rebenta com `Cannot read properties of undefined`.
 */
function auth() {
  let App;
  let Auth;
  try {
    App = require('firebase-admin/app');
    Auth = require('firebase-admin/auth');
  } catch (e) {
    abortar('`firebase-admin` não está instalado. Corre: npm install (a partir de qa/)');
  }
  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    abortar(
      'GOOGLE_APPLICATION_CREDENTIALS não está definida — sem chave de conta de serviço não há\n' +
        '  acesso de admin. Ver as instruções no cabeçalho deste ficheiro.'
    );
  }
  const app = App.getApps().length
    ? App.getApps()[0]
    : App.initializeApp({ credential: App.applicationDefault() });
  return Auth.getAuth(app);
}

async function listAll(a) {
  const out = [];
  let pageToken;
  do {
    const page = await a.listUsers(1000, pageToken);
    out.push(...page.users);
    pageToken = page.pageToken;
  } while (pageToken);
  return out;
}

(async () => {
  const a = auth();
  const users = await listAll(a);
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
    const r = await a.deleteUsers(lote);
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
