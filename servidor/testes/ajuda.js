/** Relógio falso partilhado pelos testes: o tempo só anda quando o teste manda. */
export function relogioFalso(inicio = 1_000_000) {
  let agora = inicio;
  let seq = 0;
  const agendados = new Map();
  return {
    agora: () => agora,
    agendar: (fn, ms) => { const h = ++seq; agendados.set(h, { fn, em: agora + ms }); return h; },
    cancelar: (h) => agendados.delete(h),
    /** Avança [ms] disparando o que vencer pelo caminho, por ordem de vencimento. */
    avancar(ms) {
      const alvo = agora + ms;
      for (;;) {
        const devidos = [...agendados].filter(([, t]) => t.em <= alvo).sort((a, b) => a[1].em - b[1].em);
        if (devidos.length === 0) break;
        const [h, t] = devidos[0];
        agendados.delete(h);
        agora = t.em;
        t.fn();
      }
      agora = alvo;
    },
    pendentes: () => agendados.size
  };
}

/** Perguntas de teste com resposta certa conhecida: a certa é sempre "certa-N". */
export function perguntasDeTeste(n, dificuldade = 'facil') {
  return Array.from({ length: n }, (_, i) => ({
    pergunta: `pergunta ${i}`,
    opcoes: [`certa-${i}`, `errada-${i}`],
    respostaCorreta: `certa-${i}`,
    dificuldade
  }));
}
