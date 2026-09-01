import { test } from 'node:test';
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';

import { criarApp } from '../src/app.js';
import { perguntasDeTeste } from './ajuda.js';

/**
 * Partida inteira por WebSocket, com o Firebase substituído por dublês.
 *
 * É o que torna a fase 1 verificável sozinha: corre o servidor a sério (HTTP, upgrade, sessões,
 * encaminhamento, pontuação, pódio) sem tocar na base de produção e sem uma linha de Android.
 * Inclui o cenário que nunca foi observado em emuladores — um Grupo a encher os 10.
 */

const CATEGORIA = 'História';

async function montarServidor({ quiz = null } = {}) {
  const scoresGravados = [];
  const app = criarApp({
    // Nos testes o "token" É o uid — a verificação a sério está no firebase.js.
    verificarToken: async (token) => {
      if (!token.startsWith('uid-')) throw new Error('token inválido');
      return { uid: token };
    },
    nomeDoJogador: async (uid) => `Nome ${uid.slice(4)}`,
    lerCaminho: async (caminho) => {
      if (caminho === 'categorias') return { [CATEGORIA]: { perguntas: perguntasDeTeste(30) } };
      if (caminho.startsWith('categorias_comunitarias/')) return quiz;
      return null;
    },
    gravarScore: async (e) => { scoresGravados.push(e); return `score-${scoresGravados.length}`; },
    segredoAdmin: 'segredo-de-teste',
    aoDesligar: () => {}
  });

  await app.carregarBanco();
  await new Promise((r) => app.servidor.listen(0, '127.0.0.1', r));
  const porta = app.servidor.address().port;
  const clientes = [];

  async function ligar(uid) {
    const ws = new WebSocket(`ws://127.0.0.1:${porta}`, { headers: { authorization: `Bearer ${uid}` } });
    const buffer = [];
    const esperas = [];
    ws.on('message', (bruto) => {
      const m = JSON.parse(bruto.toString());
      if (m.t === 'sonda') return ws.send(JSON.stringify({ t: 'sonda_ok', s: m.s }));
      const i = esperas.findIndex((e) => e.tipos.includes(m.t));
      if (i >= 0) esperas.splice(i, 1)[0].resolver(m);
      else buffer.push(m);
    });
    await new Promise((ok, falhou) => { ws.once('open', ok); ws.once('error', falhou); });

    const cliente = {
      uid,
      ws,
      envia: (m) => ws.send(JSON.stringify(m)),
      recebidas: buffer,
      /**
       * Espera a próxima mensagem de um destes tipos (ou apanha uma que já tenha chegado).
       * Aceita vários tipos de propósito: com um `Promise.race` de duas esperas, a que perde
       * fica registada e engole a mensagem seguinte — foi assim que este teste bloqueou.
       */
      // Margem acima de `REVELACAO_RESPOSTA_MS` (3 s) — a pergunta seguinte ou o pódio só chegam
      // depois desse atraso de revelação, por isso 3000 ms de folga aqui corria à justa com ele.
      espera(tipos, ms = 5000) {
        const lista = Array.isArray(tipos) ? tipos : [tipos];
        const i = buffer.findIndex((m) => lista.includes(m.t));
        if (i >= 0) return Promise.resolve(buffer.splice(i, 1)[0]);
        return new Promise((resolver, rejeitar) => {
          const t = setTimeout(() => rejeitar(new Error(`${uid}: nunca chegou ${lista.join('/')}`)), ms);
          esperas.push({ tipos: lista, resolver: (m) => { clearTimeout(t); resolver(m); } });
        });
      }
    };
    clientes.push(cliente);
    await cliente.espera('sessao');
    return cliente;
  }

  return {
    app, porta, ligar, scoresGravados,
    async fechar() {
      for (const c of clientes) c.ws.close();
      await new Promise((r) => app.servidor.close(r));
    }
  };
}

/** Joga uma partida inteira: todos respondem sempre a primeira opção. */
async function jogarTudo(clientes) {
  const podios = new Map();
  for (let volta = 0; volta < 40 && podios.size < clientes.length; volta++) {
    const activos = clientes.filter((c) => !podios.has(c.uid));
    for (const c of activos) {
      const m = await c.espera(['pergunta', 'podio']);
      if (m.t === 'podio') { podios.set(c.uid, m); continue; }
      c.envia({ t: 'responder', indice: m.indice, opcao: m.opcoes[0], tCliente: Date.now() });
    }
  }
  for (const c of clientes) if (!podios.has(c.uid)) podios.set(c.uid, await c.espera('podio'));
  return podios;
}

test('sem token válido não há ligação nenhuma', async () => {
  const s = await montarServidor();
  const semNada = new WebSocket(`ws://127.0.0.1:${s.porta}`);
  await assert.rejects(
    new Promise((ok, falhou) => { semNada.once('open', ok); semNada.once('error', falhou); }),
    /401|Unexpected server response/
  );
  const mau = new WebSocket(`ws://127.0.0.1:${s.porta}`, { headers: { authorization: 'Bearer lixo' } });
  await assert.rejects(
    new Promise((ok, falhou) => { mau.once('open', ok); mau.once('error', falhou); }),
    /401|Unexpected server response/
  );
  await s.fechar();
});

test('o nome vem do perfil, não do que o cliente disser', async () => {
  const s = await montarServidor();
  const ana = await s.ligar('uid-ana');
  ana.envia({ t: 'procurar', formato: 'grupo', categoria: CATEGORIA, modo: 'classico', nome: 'ADMINISTRADOR' });
  const sala = await ana.espera('sala');
  assert.equal(sala.membros[0].nome, 'Nome ana');
  await s.fechar();
});

test('1x1 do princípio ao fim: enche, joga 10, e o servidor grava os /scores', async () => {
  const s = await montarServidor();
  const ana = await s.ligar('uid-ana');
  const bea = await s.ligar('uid-bea');

  ana.envia({ t: 'procurar', formato: '1x1', categoria: CATEGORIA, modo: 'classico' });
  await ana.espera('sala');
  bea.envia({ t: 'procurar', formato: '1x1', categoria: CATEGORIA, modo: 'classico' });

  const partida = await ana.espera('partida');
  assert.equal(partida.totalPerguntas, 10);
  assert.equal(partida.membros.length, 2);

  const podios = await jogarTudo([ana, bea]);
  assert.equal(podios.size, 2);
  assert.equal(podios.get('uid-ana').ranking.length, 2);
  // Os dois têm de ver exactamente o mesmo ranking — foi o que os 5 emuladores confirmaram.
  assert.deepEqual(
    podios.get('uid-ana').ranking.map((r) => [r.uid, r.pontos]),
    podios.get('uid-bea').ranking.map((r) => [r.uid, r.pontos])
  );
  assert.equal(podios.get('uid-ana').ganhei + podios.get('uid-bea').ganhei <= 1, true, 'ganharam os dois');

  assert.equal(s.scoresGravados.length, 2);
  for (const e of s.scoresGravados) {
    assert.equal(e.formato, '1x1');
    assert.equal(e.total, 10);
    assert.equal(e.categoria, CATEGORIA);
    assert.ok(e.correctCount >= 0 && e.correctCount <= 10);
    assert.ok(e.score >= 0 && e.score <= 6000, 'passou o tecto das rules de /scores');
  }
  await s.fechar();
});

test('Grupo enche os 10 e arranca sozinho — o que nunca se conseguiu com emuladores', async () => {
  const s = await montarServidor();
  const clientes = [];
  for (let i = 0; i < 10; i++) clientes.push(await s.ligar(`uid-j${i}`));

  for (const c of clientes) {
    c.envia({ t: 'procurar', formato: 'grupo', categoria: CATEGORIA, modo: 'classico' });
    await c.espera('sala');
  }

  const partida = await clientes[0].espera('partida');
  assert.equal(partida.membros.length, 10, 'a sala não chegou aos 10');

  const podios = await jogarTudo(clientes);
  assert.equal(podios.size, 10);
  const referencia = podios.get('uid-j0').ranking.map((r) => r.uid);
  for (const c of clientes) {
    assert.deepEqual(podios.get(c.uid).ranking.map((r) => r.uid), referencia, `ranking diferente em ${c.uid}`);
  }
  assert.equal(s.scoresGravados.length, 10);
  assert.equal(new Set(s.scoresGravados.map((e) => e.uid)).size, 10, 'gravou o mesmo jogador duas vezes');
  await s.fechar();
});

test('2x2: as equipas saem do servidor e vêm com a partida', async () => {
  const s = await montarServidor();
  const clientes = [];
  for (const n of ['a1', 'a2', 'b1', 'b2']) clientes.push(await s.ligar(`uid-${n}`));
  for (const c of clientes) {
    c.envia({ t: 'procurar', formato: '2x2', categoria: CATEGORIA, modo: 'classico' });
    await c.espera('sala');
  }
  const partida = await clientes[0].espera('partida');
  assert.deepEqual(partida.membros.map((m) => m.equipa), ['A', 'A', 'B', 'B']);

  const podios = await jogarTudo(clientes);
  const podio = podios.get('uid-a1');
  assert.equal(podio.equipas.length, 2);
  assert.equal(podio.equipas[0].jogadores.length, 2);
  await s.fechar();
});

test('sala privada por código: cria, entra pelo código, joga o quiz da comunidade', async () => {
  const s = await montarServidor({
    quiz: { titulo: 'Quiz do Zé', perguntas: perguntasDeTeste(3, 'medio') }
  });
  const ana = await s.ligar('uid-ana');
  const bea = await s.ligar('uid-bea');

  // 1x1: enche aos dois e arranca sozinha. Em Grupo continuaria a exigir o mínimo de 4, tal
  // como na app de hoje — o formato da sala privada é escolha de quem a cria.
  ana.envia({ t: 'privada_criar', formato: '1x1', quizId: 'quiz1' });
  const sala = await ana.espera('sala');
  assert.match(sala.codigo, /^[0-9]{4}$/);
  assert.equal(sala.categoria, 'Quiz do Zé');

  bea.envia({ t: 'privada_entrar', codigo: '0000' });
  assert.equal((await bea.espera('erro')).codigo, 'codigo_invalido');

  bea.envia({ t: 'privada_entrar', codigo: sala.codigo });
  await bea.espera('sala');
  ana.envia({ t: 'procurar_nao' });          // mensagem desconhecida não derruba nada
  await ana.espera('erro');

  const partida = await ana.espera('partida');
  assert.equal(partida.totalPerguntas, 3, 'a sala privada não usou as perguntas do quiz');

  const podios = await jogarTudo([ana, bea]);
  assert.equal(podios.size, 2);
  assert.equal(s.scoresGravados[0].categoria, 'Quiz do Zé');
  await s.fechar();
});

test('desafio direto: só o convidado entra na sala', async () => {
  const s = await montarServidor();
  const ana = await s.ligar('uid-ana');
  const bea = await s.ligar('uid-bea');
  const zeca = await s.ligar('uid-zeca');

  ana.envia({ t: 'desafio_criar', formato: '1x1', categoria: CATEGORIA, modo: 'classico', paraUid: 'uid-bea' });
  const sala = await ana.espera('sala');

  zeca.envia({ t: 'desafio_entrar', salaId: sala.lobbyId });
  assert.equal((await zeca.espera('erro')).codigo, 'desafio_expirado');

  bea.envia({ t: 'desafio_entrar', salaId: sala.lobbyId });
  const partida = await bea.espera('partida');
  assert.equal(partida.membros.length, 2);
  await s.fechar();
});

test('cair a meio da partida abre carência, e voltar retoma a mesma sala', async () => {
  const s = await montarServidor();
  const ana = await s.ligar('uid-ana');
  const bea = await s.ligar('uid-bea');
  for (const c of [ana, bea]) c.envia({ t: 'procurar', formato: '1x1', categoria: CATEGORIA, modo: 'classico' });
  await ana.espera('partida');
  await ana.espera('pergunta');

  bea.ws.close();
  const ausente = await ana.espera('ausente');
  assert.equal(ausente.uid, 'uid-bea');

  const beaOutraVez = await s.ligar('uid-bea');
  assert.equal((await beaOutraVez.espera('aviso')).codigo, 'reentraste');
  await ana.espera('voltou');
  await s.fechar();
});

test('/saude responde, e /admin/recarregar exige o segredo', async () => {
  const s = await montarServidor();
  const base = `http://127.0.0.1:${s.porta}`;

  const saude = await (await fetch(`${base}/saude`)).json();
  assert.equal(saude.versao.length > 0, true);
  assert.equal(saude.banco.perguntas, 30);
  assert.equal(saude.partidas, 0);

  assert.equal((await fetch(`${base}/admin/recarregar`, { method: 'POST' })).status, 403);
  const ok = await fetch(`${base}/admin/recarregar`, {
    method: 'POST', headers: { 'x-admin-token': 'segredo-de-teste' }
  });
  assert.equal(ok.status, 200);
  assert.equal((await ok.json()).perguntas, 30);
  await s.fechar();
});

test('em drenagem recusa partidas novas mas deixa acabar a que está a correr', async () => {
  const s = await montarServidor();
  const ana = await s.ligar('uid-ana');
  const bea = await s.ligar('uid-bea');
  for (const c of [ana, bea]) c.envia({ t: 'procurar', formato: '1x1', categoria: CATEGORIA, modo: 'classico' });
  await ana.espera('partida');

  s.app.drenar();
  const zeca = await s.ligar('uid-zeca');
  zeca.envia({ t: 'procurar', formato: '1x1', categoria: CATEGORIA, modo: 'classico' });
  assert.equal((await zeca.espera('erro')).codigo, 'em_manutencao');

  const podios = await jogarTudo([ana, bea]);
  assert.equal(podios.size, 2, 'a partida em curso foi derrubada pela drenagem');
  assert.equal(s.scoresGravados.length, 2);
  await s.fechar();
});
