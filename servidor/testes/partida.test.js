import { test } from 'node:test';
import assert from 'node:assert/strict';

import { Partida, REVELACAO_MS, REVELACAO_RESPOSTA_MS, CARENCIA_RECONEXAO_MS } from '../src/partida.js';
import { DURACAO_BASE_MS } from '../src/pontuacao.js';
import { relogioFalso, perguntasDeTeste } from './ajuda.js';

function montar({ formatoId = '1x1', uids = ['ana', 'bea'], modo = 'classico', nPerguntas = 3 } = {}) {
  const relogio = relogioFalso();
  const caixa = new Map(uids.map((u) => [u, []]));
  const difundidas = [];
  const terminos = [];

  const partida = new Partida({
    salaId: 'sala1',
    formatoId,
    categoria: 'História',
    modo,
    membros: uids.map((uid) => ({ uid, nome: uid })),
    perguntas: perguntasDeTeste(nPerguntas),
    relogio,
    difundir: (msg) => { difundidas.push(msg); for (const c of caixa.values()) c.push(msg); },
    enviar: (uid, msg) => caixa.get(uid)?.push(msg),
    aoTerminar: (r) => terminos.push(r)
  });

  const ultima = (uid, tipo) => [...caixa.get(uid)].reverse().find((m) => m.t === tipo);
  const todas = (uid, tipo) => caixa.get(uid).filter((m) => m.t === tipo);

  partida.comecar();
  relogio.avancar(REVELACAO_MS);
  return { relogio, partida, caixa, difundidas, terminos, ultima, todas };
}

const responder = (p, uid, indice, opcao, tCliente) =>
  p.responder(uid, { indice, opcao, tCliente: tCliente ?? null }, 0);

test('a respostaCorreta NUNCA sai na pergunta — só depois de responder', () => {
  const { partida, difundidas, ultima } = montar();
  const pergunta = difundidas.find((m) => m.t === 'pergunta');
  assert.equal('respostaCorreta' in pergunta, false);
  assert.equal(JSON.stringify(pergunta).includes('certa-0'), true, 'a opção certa está lá como OPÇÃO');
  assert.equal(pergunta.opcoes.length, 2);

  responder(partida, 'ana', 0, 'certa-0');
  assert.equal(ultima('ana', 'resposta').respostaCorreta, 'certa-0');
});

test('é o servidor que decide certo/errado e quanto vale', () => {
  const { partida, relogio, ultima } = montar();
  relogio.avancar(2_000);                       // respondeu com 13 s no relógio
  responder(partida, 'ana', 0, 'certa-0');
  const r = ultima('ana', 'resposta');
  assert.equal(r.certa, true);
  assert.equal(r.total, 130);                   // 13 s x 10, fácil, sem sequência
  assert.equal(r.certas, 1);

  responder(partida, 'bea', 0, 'errada-0');
  const rb = ultima('bea', 'resposta');
  assert.equal(rb.certa, false);
  assert.equal(rb.total, 0);
  assert.equal(rb.certas, 0);
});

test('a sequência acumula e reinicia com um erro', () => {
  const { partida, relogio, ultima } = montar({ nPerguntas: 3 });
  responder(partida, 'ana', 0, 'certa-0');
  responder(partida, 'bea', 0, 'errada-0');
  relogio.avancar(REVELACAO_RESPOSTA_MS);       // espera a revelação antes da pergunta abrir
  responder(partida, 'ana', 1, 'certa-1');
  assert.equal(ultima('ana', 'resposta').total, 150 + 200, 'a 2.ª certa devia trazer +50 de sequência');
  responder(partida, 'bea', 1, 'errada-1');
  relogio.avancar(REVELACAO_RESPOSTA_MS);
  responder(partida, 'ana', 2, 'errada-2');
  assert.equal(ultima('ana', 'resposta').total, 350, 'errar não tira pontos no Clássico');
});

test('recusa: pergunta errada, resposta repetida, opção inventada, fora do tempo', () => {
  const { partida, relogio } = montar();
  assert.deepEqual(responder(partida, 'ana', 1, 'certa-1'), { erro: 'pergunta_errada' });
  assert.deepEqual(responder(partida, 'ana', 0, 'nao-existe'), { erro: 'opcao_invalida' });
  assert.deepEqual(responder(partida, 'ana', 0, 'certa-0'), { ok: true });
  assert.deepEqual(responder(partida, 'ana', 0, 'certa-0'), { erro: 'ja_respondeu' });
  assert.deepEqual(responder(partida, 'zé', 0, 'certa-0'), { erro: 'fora_da_partida' });

  // bea nunca respondeu — a pergunta fecha por tempo esgotado, e só depois da revelação abre a seguinte
  relogio.avancar(DURACAO_BASE_MS + 1 + REVELACAO_RESPOSTA_MS);
  assert.deepEqual(responder(partida, 'bea', 0, 'certa-0'), { erro: 'pergunta_errada' });
});

test('mentir no instante da resposta só devolve o rtt real, não mais', () => {
  const { partida, relogio, ultima } = montar();
  relogio.avancar(10_000);                      // faltam 5 s de verdade
  const agora = relogio.agora();
  // Alega ter respondido no primeiro milissegundo, com 300 ms de rtt medido.
  partida.responder('ana', { indice: 0, opcao: 'certa-0', tCliente: agora - 10_000 }, 300);
  // Creditado no máximo 300 ms antes da chegada: 5,3 s -> 6 s (arredonda para cima).
  assert.equal(ultima('ana', 'resposta').total, 60);
});

test('quem não responde leva tempo esgotado, e a partida avança na mesma', () => {
  const { partida, relogio, ultima, difundidas } = montar({ nPerguntas: 2 });
  responder(partida, 'ana', 0, 'certa-0');
  relogio.avancar(DURACAO_BASE_MS + REVELACAO_RESPOSTA_MS);
  assert.equal(difundidas.filter((m) => m.t === 'pergunta').length, 2, 'não passou à pergunta seguinte');
  responder(partida, 'ana', 1, 'certa-1');
  responder(partida, 'bea', 1, 'certa-1');
  relogio.avancar(REVELACAO_RESPOSTA_MS);
  assert.equal(ultima('bea', 'podio').meuScore, 150, 'a bea só devia ter a 2.ª pergunta');
});

test('respondendo todos, avança depois da revelação — não espera pelo cronómetro', () => {
  const { partida, relogio, difundidas } = montar({ nPerguntas: 2 });
  assert.equal(difundidas.filter((m) => m.t === 'pergunta').length, 1);
  responder(partida, 'ana', 0, 'certa-0');
  assert.equal(difundidas.filter((m) => m.t === 'pergunta').length, 1, 'avançou com um só a responder');
  responder(partida, 'bea', 0, 'errada-0');
  assert.equal(difundidas.filter((m) => m.t === 'pergunta').length, 1, 'ainda em revelação, não abriu logo');
  relogio.avancar(REVELACAO_RESPOSTA_MS);
  assert.equal(difundidas.filter((m) => m.t === 'pergunta').length, 2, 'passada a revelação, abriu — sem esperar pelo cronómetro dos 15 s');
});

test('Caótico: Tudo ou Nada penaliza, e o total nunca desce abaixo de zero', () => {
  // índice 3 é tudo_ou_nada no ciclo determinístico
  const { partida, relogio, ultima } = montar({ modo: 'caotico', nPerguntas: 4 });
  for (const i of [0, 1, 2]) {
    responder(partida, 'ana', i, `errada-${i}`);
    responder(partida, 'bea', i, `errada-${i}`);
    relogio.avancar(REVELACAO_RESPOSTA_MS);
  }
  assert.equal(ultima('ana', 'resposta').total, 0);
  responder(partida, 'ana', 3, 'errada-3');
  assert.equal(ultima('ana', 'resposta').total, 0, 'ficou negativo em vez de parar no zero');
});

test('1x1: sair a meio dá walkover a quem fica', () => {
  const { partida, ultima, terminos } = montar();
  responder(partida, 'ana', 0, 'certa-0');
  partida.desistiu('bea');
  const podio = ultima('ana', 'podio');
  assert.equal(podio.walkover, true);
  assert.equal(podio.ganhei, true);
  assert.equal(terminos.length, 1);
  assert.deepEqual(terminos[0].vencedores, ['ana']);
});

test('Grupo: sair um de quatro não acaba nada; ficar um só acaba', () => {
  const { partida, terminos, ultima } = montar({ formatoId: 'grupo', uids: ['a', 'b', 'c', 'd'] });
  partida.desistiu('d');
  assert.equal(terminos.length, 0, 'a partida acabou com três ainda a jogar');
  partida.desistiu('c');
  assert.equal(terminos.length, 0);
  partida.desistiu('b');
  assert.equal(terminos.length, 1);
  assert.equal(ultima('a', 'podio').walkover, true);
});

test('2x2: sai um e a equipa DELE perde, mesmo indo à frente', () => {
  const { partida, ultima, terminos } = montar({ formatoId: '2x2', uids: ['a1', 'a2', 'b1', 'b2'] });
  responder(partida, 'a1', 0, 'certa-0');       // equipa A ganha pontos
  responder(partida, 'a2', 0, 'certa-0');
  partida.desistiu('a2');                       // ... e sai um da A

  assert.deepEqual(terminos[0].vencedores.sort(), ['b1', 'b2']);
  assert.equal(ultima('b1', 'podio').ganhei, true);
  assert.equal(ultima('a1', 'podio').ganhei, false);
  const equipaA = ultima('a1', 'podio').equipas.find((e) => e.nome === 'A');
  assert.ok(equipaA.total > 0, 'a equipa A tinha mesmo mais pontos e perdeu à mesma');
});

test('empate não é vitória — em 1x1 e em 2x2', () => {
  const solo = montar({ nPerguntas: 1 });
  responder(solo.partida, 'ana', 0, 'certa-0');
  responder(solo.partida, 'bea', 0, 'certa-0');
  solo.relogio.avancar(REVELACAO_RESPOSTA_MS);
  assert.deepEqual(solo.terminos[0].vencedores, []);
  assert.equal(solo.ultima('ana', 'podio').ganhei, false);

  const duplas = montar({ formatoId: '2x2', uids: ['a1', 'a2', 'b1', 'b2'], nPerguntas: 1 });
  responder(duplas.partida, 'a1', 0, 'certa-0');
  responder(duplas.partida, 'b1', 0, 'certa-0');
  responder(duplas.partida, 'a2', 0, 'errada-0');
  responder(duplas.partida, 'b2', 0, 'errada-0');
  duplas.relogio.avancar(REVELACAO_RESPOSTA_MS);
  assert.deepEqual(duplas.terminos[0].vencedores, []);
});

test('quem saiu fica no fim do ranking, à frente de ninguém', () => {
  const { partida, relogio, ultima } = montar({ formatoId: 'grupo', uids: ['a', 'b', 'c'], nPerguntas: 1 });
  responder(partida, 'a', 0, 'errada-0');       // 0 pontos, mas fica
  responder(partida, 'b', 0, 'certa-0');
  partida.desistiu('c');                        // c tinha 0 e saiu
  relogio.avancar(DURACAO_BASE_MS);
  const ranking = ultima('a', 'podio').ranking;
  assert.deepEqual(ranking.map((r) => r.uid), ['b', 'a', 'c']);
});

test('perder a ligação não é desistir — há carência para voltar', () => {
  const { partida, relogio, terminos, difundidas } = montar();
  partida.desligou('bea');
  assert.equal(terminos.length, 0, 'a queda contou logo como desistência');
  assert.ok(difundidas.some((m) => m.t === 'ausente' && m.uid === 'bea'));

  relogio.avancar(CARENCIA_RECONEXAO_MS - 1);
  assert.equal(partida.reconectou('bea'), true);
  assert.equal(terminos.length, 0);
  relogio.avancar(CARENCIA_RECONEXAO_MS * 2);
  assert.equal(terminos.length, 0, 'a carência disparou depois de a bea ter voltado');
});

test('passada a carência sem voltar, é desistência', () => {
  const { partida, relogio, terminos } = montar();
  partida.desligou('bea');
  relogio.avancar(CARENCIA_RECONEXAO_MS);
  assert.equal(terminos.length, 1);
  assert.deepEqual(terminos[0].vencedores, ['ana']);
});

test('o relatório final leva o que o perfil precisa, por jogador', () => {
  const { partida, terminos, relogio } = montar({ nPerguntas: 2 });
  responder(partida, 'ana', 0, 'certa-0');
  responder(partida, 'bea', 0, 'errada-0');
  relogio.avancar(REVELACAO_RESPOSTA_MS);
  responder(partida, 'ana', 1, 'certa-1');
  responder(partida, 'bea', 1, 'certa-1');
  relogio.avancar(REVELACAO_RESPOSTA_MS);

  const r = terminos[0];
  assert.equal(r.formato, '1x1');
  assert.equal(r.categoria, 'História');
  assert.equal(r.modo, 'classico');
  assert.equal(r.totalPerguntas, 2);
  const ana = r.jogadores.find((j) => j.uid === 'ana');
  assert.deepEqual(
    { certas: ana.certas, maxSequencia: ana.maxSequencia, saiu: ana.saiu },
    { certas: 2, maxSequencia: 2, saiu: false }
  );
  assert.equal(relogio.pendentes(), 0, 'ficaram temporizadores vivos depois do fim');
});

test('depois do pódio, nada mais mexe no resultado', () => {
  const { partida, relogio, terminos } = montar({ nPerguntas: 1 });
  responder(partida, 'ana', 0, 'certa-0');
  responder(partida, 'bea', 0, 'errada-0');
  relogio.avancar(REVELACAO_RESPOSTA_MS);
  assert.equal(terminos.length, 1);
  assert.deepEqual(responder(partida, 'ana', 0, 'certa-0'), { erro: 'fora_da_partida' });
  partida.desistiu('bea');
  assert.equal(terminos.length, 1, 'terminou duas vezes');
});

test('walkover 0-0: quem fica ganha, não empata', () => {
  // O `finishSoloWalkover` do cliente agregava `won = true` sem olhar ao placar. Sem isto, um
  // 1x1 em que o adversário sai antes da primeira resposta dava empate a zero.
  const { partida, terminos, ultima } = montar();
  partida.desistiu('bea');
  assert.deepEqual(terminos[0].vencedores, ['ana']);
  assert.equal(ultima('ana', 'podio').ganhei, true);
  assert.equal(ultima('ana', 'podio').meuScore, 0);
});
