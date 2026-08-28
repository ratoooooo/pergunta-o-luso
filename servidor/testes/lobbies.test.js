import { test } from 'node:test';
import assert from 'node:assert/strict';

import { Lobbies, AUTO_ARRANQUE_MS } from '../src/lobbies.js';

/** Relógio falso: o tempo só anda quando o teste manda. */
function relogioFalso() {
  let agora = 1_000_000;
  let seq = 0;
  const agendados = new Map();
  return {
    agora: () => agora,
    agendar: (fn, ms) => { const h = ++seq; agendados.set(h, { fn, em: agora + ms }); return h; },
    cancelar: (h) => agendados.delete(h),
    avancar(ms) {
      agora += ms;
      for (const [h, t] of [...agendados]) {
        if (t.em <= agora) { agendados.delete(h); t.fn(); }
      }
    },
    pendentes: () => agendados.size
  };
}

function montar() {
  const relogio = relogioFalso();
  const arrancadas = [];
  const lobbies = new Lobbies({ relogio, aoArrancar: (l) => arrancadas.push(l), aoMudar: () => {} });
  const procurar = (uid, extra = {}) => lobbies.procurar({
    uid, nome: uid, formatoId: 'grupo', categoria: 'História', modo: 'classico', ...extra
  });
  return { relogio, lobbies, arrancadas, procurar };
}

test('quem cria é anfitrião; quem chega a seguir junta-se ao mesmo lobby', () => {
  const { lobbies, procurar } = montar();
  const a = procurar('ana');
  const b = procurar('bea');
  assert.equal(a.id, b.id);
  assert.equal(a.anfitriaoUid, 'ana');
  assert.equal(a.membros.length, 2);
  assert.equal(lobbies.abertos('grupo').length, 1);
});

test('categoria ou modo diferentes NÃO partilham lobby', () => {
  const { procurar } = montar();
  const a = procurar('ana');
  const b = procurar('bea', { categoria: 'Desporto' });
  const c = procurar('caz', { modo: 'caotico' });
  assert.notEqual(a.id, b.id);
  assert.notEqual(a.id, c.id);
  assert.notEqual(b.id, c.id);
});

test('formatos diferentes não partilham lobby', () => {
  const { procurar } = montar();
  assert.notEqual(procurar('ana').id, procurar('bea', { formatoId: '1x1' }).id);
});

test('1x1 arranca sozinho ao encher, sem esperar pelo temporizador', () => {
  const { arrancadas, procurar, relogio } = montar();
  procurar('ana', { formatoId: '1x1' });
  assert.equal(arrancadas.length, 0);
  procurar('bea', { formatoId: '1x1' });
  assert.equal(arrancadas.length, 1);
  assert.equal(arrancadas[0].membros.length, 2);
  assert.equal(relogio.pendentes(), 0, 'ficou um temporizador vivo depois de arrancar');
});

test('Grupo: abaixo do mínimo não arma temporizador nenhum', () => {
  const { relogio, arrancadas, procurar } = montar();
  procurar('ana'); procurar('bea'); procurar('caz');   // 3 de um mínimo de 4
  assert.equal(relogio.pendentes(), 0);
  relogio.avancar(AUTO_ARRANQUE_MS * 3);
  assert.equal(arrancadas.length, 0, 'uma sala abaixo do mínimo arrancou sozinha');
});

test('Grupo: chegado o mínimo, arranca aos 60 s', () => {
  const { relogio, arrancadas, procurar } = montar();
  for (const uid of ['ana', 'bea', 'caz', 'dan']) procurar(uid);
  assert.equal(arrancadas.length, 0, 'arrancou logo aos 4 — a capacidade é 10');
  relogio.avancar(AUTO_ARRANQUE_MS - 1);
  assert.equal(arrancadas.length, 0);
  relogio.avancar(1);
  assert.equal(arrancadas.length, 1);
  assert.equal(arrancadas[0].membros.length, 4);
});

test('cada entrada nova compra mais 60 s — a janela de graça', () => {
  const { relogio, arrancadas, procurar } = montar();
  for (const uid of ['ana', 'bea', 'caz', 'dan']) procurar(uid);
  relogio.avancar(50_000);
  procurar('eva');                       // entrada nova aos 50 s
  relogio.avancar(50_000);               // 100 s desde o mínimo, 50 s desde a última entrada
  assert.equal(arrancadas.length, 0, 'a entrada nova não reiniciou o temporizador');
  relogio.avancar(10_000);
  assert.equal(arrancadas.length, 1);
  assert.equal(arrancadas[0].membros.length, 5);
});

test('Grupo aceita até 10 e aí arranca sozinho', () => {
  const { arrancadas, procurar } = montar();
  for (let i = 0; i < 9; i++) procurar(`j${i}`);
  assert.equal(arrancadas.length, 0);
  procurar('j9');
  assert.equal(arrancadas.length, 1);
  assert.equal(arrancadas[0].membros.length, 10);
});

test('descer abaixo do mínimo desarma o temporizador', () => {
  const { relogio, lobbies, arrancadas, procurar } = montar();
  for (const uid of ['ana', 'bea', 'caz', 'dan']) procurar(uid);
  assert.equal(relogio.pendentes(), 1);
  lobbies.sair('dan');
  assert.equal(relogio.pendentes(), 0);
  relogio.avancar(AUTO_ARRANQUE_MS * 2);
  assert.equal(arrancadas.length, 0);
});

test('INICIAR JOGO: só o anfitrião, e só a partir do mínimo', () => {
  const { lobbies, arrancadas, procurar } = montar();
  procurar('ana'); procurar('bea'); procurar('caz');
  assert.equal(lobbies.forcarInicio('ana'), false, 'arrancou com 3 num mínimo de 4');
  procurar('dan');
  assert.equal(lobbies.forcarInicio('bea'), false, 'quem não é anfitrião conseguiu arrancar');
  assert.equal(lobbies.forcarInicio('ana'), true);
  assert.equal(arrancadas.length, 1);
  assert.equal(lobbies.forcarInicio('ana'), false, 'arrancou duas vezes');
});

test('2x2 exige os 4 — com 3 uma equipa ficaria com um jogador só', () => {
  const { lobbies, procurar } = montar();
  for (const uid of ['ana', 'bea', 'caz']) procurar(uid, { formatoId: '2x2' });
  assert.equal(lobbies.forcarInicio('ana'), false);
});

test('sai o anfitrião: herda o membro mais antigo dos que ficam', () => {
  const { lobbies, procurar } = montar();
  procurar('ana'); procurar('bea'); procurar('caz');
  const lobby = lobbies.sair('ana');
  assert.equal(lobby.anfitriaoUid, 'bea');
  assert.equal(lobby.membros.length, 2);
});

test('lobby vazio desaparece — não fica lixo a apanhar jogadores', () => {
  const { lobbies, procurar } = montar();
  procurar('ana');
  assert.equal(lobbies.abertos('grupo').length, 1);
  assert.equal(lobbies.sair('ana'), null);
  assert.equal(lobbies.abertos('grupo').length, 0);
  assert.equal(lobbies.resumo().jogadoresEmEspera, 0);
});

test('trocar de sala tira do lobby anterior, sem ficar nos dois', () => {
  const { lobbies, procurar } = montar();
  const primeiro = procurar('ana');
  const outro = procurar('bea', { categoria: 'Desporto' });
  const destino = lobbies.entrarEm(outro.id, 'ana', 'ana');
  assert.equal(destino.id, outro.id);
  assert.equal(destino.membros.length, 2);
  assert.equal(lobbies.porId.has(primeiro.id), false, 'o lobby de origem ficou vazio e não foi limpo');
  assert.equal(lobbies.lobbyDe('ana').id, outro.id);
});

test('trocar para uma sala cheia ou já a jogar devolve null', () => {
  const { lobbies, procurar } = montar();
  const cheio = procurar('ana', { formatoId: '1x1' });
  procurar('bea', { formatoId: '1x1' });                 // encheu e arrancou
  assert.equal(lobbies.entrarEm(cheio.id, 'caz', 'caz'), null);
  assert.equal(lobbies.entrarEm('nao-existe', 'caz', 'caz'), null);
});

test('procurar duas vezes não duplica o jogador', () => {
  const { lobbies, procurar } = montar();
  procurar('ana');
  procurar('ana');
  const lobby = lobbies.lobbyDe('ana');
  assert.equal(lobby.membros.filter((m) => m.uid === 'ana').length, 1);
  assert.equal(lobbies.abertos('grupo').length, 1, 'ficou um lobby órfão para trás');
});

test('salas privadas não são anunciadas nem apanham quem procura', () => {
  const { lobbies, procurar } = montar();
  const privada = lobbies.criarPrivado({
    uid: 'ana', nome: 'ana', formatoId: 'grupo', categoria: 'História', modo: 'classico', codigo: '4242'
  });
  assert.deepEqual(lobbies.abertos('grupo'), [], 'a sala privada apareceu na lista de salas abertas');
  const bea = procurar('bea');
  assert.notEqual(bea.id, privada.id, 'o matchmaking aleatório atirou alguém para uma sala privada');
  assert.equal(lobbies.porCodigo('4242').id, privada.id);
  assert.equal(lobbies.porCodigo('0000'), null);
});

test('entra-se numa sala privada pelo código, e o quiz vem com ela', () => {
  const { lobbies } = montar();
  const perguntas = [{ pergunta: 'p', opcoes: ['a', 'b'], respostaCorreta: 'a', dificuldade: 'facil' }];
  const privada = lobbies.criarPrivado({
    uid: 'ana', nome: 'ana', formatoId: 'grupo', categoria: 'O meu quiz', modo: 'classico',
    codigo: '4242', perguntas
  });
  const entrou = lobbies.entrarEm(privada.id, 'bea', 'bea');
  assert.equal(entrou.id, privada.id);
  assert.equal(entrou.perguntas, perguntas);
});

test('desafio direto: só entra quem foi convidado', () => {
  const { lobbies } = montar();
  const desafio = lobbies.criarPrivado({
    uid: 'ana', nome: 'ana', formatoId: '1x1', categoria: 'História', modo: 'classico',
    permitidos: ['ana', 'bea']
  });
  assert.equal(lobbies.entrarEm(desafio.id, 'intruso', 'intruso'), null);
  assert.equal(lobbies.entrarEm(desafio.id, 'bea', 'bea').id, desafio.id);
});

test('a primeira difusão de uma sala privada já leva o código', () => {
  // O código era escrito depois do `aoMudar`, e a primeira mensagem saía com `codigo: null`.
  const relogio = relogioFalso();
  const vistos = [];
  const lobbies = new Lobbies({ relogio, aoArrancar: () => {}, aoMudar: (l) => vistos.push(l.codigo) });
  lobbies.criarPrivado({
    uid: 'ana', nome: 'ana', formatoId: 'grupo', categoria: 'q', modo: 'classico', codigo: '4242'
  });
  assert.deepEqual(vistos, ['4242']);
});
