import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import {
  pontosDaResposta, limitarTotal, eventoPara, duracaoDaPergunta,
  segundosRestantes, bonusSequencia, DURACAO_BASE_MS, EVENTOS
} from '../src/pontuacao.js';

const vectores = JSON.parse(
  readFileSync(fileURLToPath(new URL('./pontuacao.json', import.meta.url)), 'utf8')
).vectores;

test('vectores dourados — a fórmula bate certo com o Kotlin', () => {
  assert.ok(vectores.length >= 19, 'os vectores foram esvaziados');
  for (const v of vectores) {
    assert.equal(
      pontosDaResposta({
        certa: v.certa,
        segundosRestantes: v.segundosRestantes,
        dificuldade: v.dificuldade,
        evento: v.evento,
        sequenciaDepois: v.sequenciaDepois
      }),
      v.esperado,
      v.nome
    );
  }
});

test('o total corrente nunca desce abaixo de zero', () => {
  // Uma resposta errada em tudo_ou_nada logo na primeira pergunta valia -50.
  assert.equal(limitarTotal(-50), 0);
  assert.equal(limitarTotal(0), 0);
  assert.equal(limitarTotal(120), 120);
});

test('eventos caóticos são determinísticos pelo índice e só existem no Caótico', () => {
  assert.equal(eventoPara('classico', 0), null);
  assert.equal(eventoPara('classico', 7), null);
  const ciclo = [0, 1, 2, 3, 4].map((i) => eventoPara('caotico', i));
  assert.deepEqual(ciclo, [
    EVENTOS.PERGUNTA_DUPLA,
    EVENTOS.VELOCIDADE_MAXIMA,
    EVENTOS.ROUBO,
    EVENTOS.TUDO_OU_NADA,
    EVENTOS.PERGUNTA_DUPLA
  ]);
});

test('só a Velocidade Máxima encurta a pergunta', () => {
  assert.equal(duracaoDaPergunta(null), DURACAO_BASE_MS);
  assert.equal(duracaoDaPergunta(EVENTOS.ROUBO), DURACAO_BASE_MS);
  assert.equal(duracaoDaPergunta(EVENTOS.VELOCIDADE_MAXIMA), DURACAO_BASE_MS / 2);
});

test('segundos restantes arredondam para cima, e nunca para negativo', () => {
  assert.equal(segundosRestantes(15_000), 15);
  assert.equal(segundosRestantes(14_001), 15); // 14,001 s ainda conta como 15
  assert.equal(segundosRestantes(1), 1);
  assert.equal(segundosRestantes(0), 0);
  assert.equal(segundosRestantes(-500), 0);    // resposta a chegar depois do fim
});

test('o bónus de sequência tem patamares, não é linear', () => {
  assert.deepEqual([0, 1, 2, 3, 4, 10].map(bonusSequencia), [0, 0, 50, 75, 100, 100]);
});
