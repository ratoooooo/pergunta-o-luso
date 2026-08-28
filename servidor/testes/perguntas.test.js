import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  normalizarPergunta, progressao, carregarBanco, perguntasParaJogo,
  perguntasDeQuiz, paraCliente, estadoDoBanco, MAX_PERGUNTAS_SALA
} from '../src/perguntas.js';

const q = (dificuldade, n) => ({
  pergunta: `${dificuldade} ${n}`,
  opcoes: ['a', 'b', 'c', 'd'],
  respostaCorreta: 'a',
  dificuldade
});

const banco = (facil, medio, dificil) => [
  ...Array.from({ length: facil }, (_, i) => q('facil', i)),
  ...Array.from({ length: medio }, (_, i) => q('medio', i)),
  ...Array.from({ length: dificil }, (_, i) => q('dificil', i))
];

const patamar = (p) => p.dificuldade;

test('a rampa sobe: fácil primeiro, difícil no fim', () => {
  const r = progressao(banco(50, 50, 50), 10);
  assert.equal(r.length, 10);
  // 10 -> 4 fácil, 3 médio, 3 difícil
  assert.deepEqual(r.map(patamar), [
    'facil', 'facil', 'facil', 'facil', 'medio', 'medio', 'medio', 'dificil', 'dificil', 'dificil'
  ]);
});

test('nunca repete uma pergunta, mesmo quando um patamar é curto', () => {
  // Só há 2 difíceis para 3 lugares: o resto vem das sobras, sem duplicar.
  const r = progressao(banco(50, 50, 2), 10);
  assert.equal(r.length, 10);
  assert.equal(new Set(r.map((p) => p.pergunta)).size, 10);
});

test('banco mais pequeno do que o pedido devolve o que há, não rebenta', () => {
  const r = progressao(banco(2, 1, 0), 10);
  assert.equal(r.length, 3);
});

test('a ordem varia entre partidas (baralha dentro do patamar)', () => {
  const assinatura = () => progressao(banco(30, 30, 30), 10).map((p) => p.pergunta).join('|');
  const corridas = new Set(Array.from({ length: 12 }, assinatura));
  assert.ok(corridas.size > 1, 'a rampa saiu sempre igual — deixou de baralhar');
});

test('Verdadeiro/Falso mantém a ordem canónica; escolha múltipla é baralhada', () => {
  const vf = normalizarPergunta({
    pergunta: 'p', opcoes: ['Falso', 'Verdadeiro'], respostaCorreta: 'Verdadeiro', dificuldade: 'facil'
  });
  assert.deepEqual(vf.opcoes, ['Verdadeiro', 'Falso']);

  const muitas = Array.from({ length: 20 }, () =>
    normalizarPergunta({
      pergunta: 'p', opcoes: ['a', 'b', 'c', 'd'], respostaCorreta: 'a', dificuldade: 'facil'
    }).opcoes.join('')
  );
  assert.ok(new Set(muitas).size > 1, 'as opções saíram sempre na mesma ordem');
});

test('perguntas inválidas são ignoradas em vez de rebentarem a partida', () => {
  assert.equal(normalizarPergunta(null), null);
  assert.equal(normalizarPergunta({ pergunta: 'sem resposta', opcoes: ['a', 'b'] }), null);
  assert.equal(normalizarPergunta({ pergunta: 'p', respostaCorreta: 'a', opcoes: ['só uma'] }), null);
  // opções como mapa (é assim que a RTDB devolve um array esparso)
  const m = normalizarPergunta({ pergunta: 'p', respostaCorreta: 'a', opcoes: { 0: 'a', 1: 'b' } });
  assert.equal(m.opcoes.length, 2);
});

test('o que sai para o cliente NÃO leva a respostaCorreta', () => {
  const p = normalizarPergunta({ pergunta: 'p', opcoes: ['a', 'b'], respostaCorreta: 'a', dificuldade: 'medio' });
  const enviado = paraCliente(p);
  assert.equal('respostaCorreta' in enviado, false);
  assert.deepEqual(Object.keys(enviado).sort(), ['dificuldade', 'opcoes', 'pergunta']);
  assert.equal(JSON.stringify(enviado).includes('respostaCorreta'), false);
});

test('o banco carrega de /categorias e serve por categoria', async () => {
  const ler = async (caminho) => {
    assert.equal(caminho, 'categorias');
    return {
      'História': { perguntas: banco(10, 10, 10) },
      'Vazia': { perguntas: {} }
    };
  };
  const resumo = await carregarBanco(ler);
  assert.equal(resumo.categorias, 1, 'categoria sem perguntas válidas não entra');
  assert.equal(resumo.perguntas, 30);
  assert.equal(estadoDoBanco().categorias, 1);
  assert.equal(perguntasParaJogo('História', 10).length, 10);
  assert.deepEqual(perguntasParaJogo('Não existe', 10), []);
});

test('uma recarga falhada não deixa o servidor sem perguntas', async () => {
  const antes = estadoDoBanco().perguntas;
  await assert.rejects(() => carregarBanco(async () => ({})), /vazio/);
  assert.equal(estadoDoBanco().perguntas, antes, 'o banco anterior foi deitado fora');
});

test('quiz da comunidade é cortado no tecto da sala privada', async () => {
  const ler = async (caminho) => {
    assert.equal(caminho, 'categorias_comunitarias/abc');
    return { titulo: 'O meu quiz', perguntas: banco(20, 0, 0) };
  };
  const quiz = await perguntasDeQuiz(ler, 'abc');
  assert.equal(quiz.titulo, 'O meu quiz');
  assert.equal(quiz.perguntas.length, MAX_PERGUNTAS_SALA);

  assert.equal(await perguntasDeQuiz(async () => null, 'x'), null);
  assert.equal(await perguntasDeQuiz(async () => ({ titulo: 't', perguntas: [] }), 'x'), null);
});
