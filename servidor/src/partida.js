/**
 * A partida ao vivo. **É aqui que o servidor deixa de acreditar no cliente.**
 *
 * Antes: `MultiMatchViewModel.selectAnswer` comparava a opção com `respostaCorreta`, somava os
 * pontos e escrevia o total na RTDB; as rules só travavam o impossível (`pontuacao <= 4000`).
 * Agora o cliente manda *qual opção tocou* e mais nada — a correcção, os pontos, o relógio e o
 * vencedor são decididos aqui.
 *
 * A `respostaCorreta` fica neste processo e só é revelada a um jogador **depois** de ele ter
 * respondido (ou de a pergunta fechar). Era isto que a arquitectura só-RTDB não conseguia
 * fazer, e o que tornava a limitação nº 1 impossível de fechar.
 */

import { formatoDe } from './formatos.js';
import { paraCliente } from './perguntas.js';
import {
  pontosDaResposta, limitarTotal, eventoPara, duracaoDaPergunta, segundosRestantes
} from './pontuacao.js';

/** Segundos de revelação "encontrámos adversários" antes da primeira pergunta (era 2 500 ms). */
export const REVELACAO_MS = 2_500;

/**
 * Tempo a mostrar o resultado de uma pergunta antes de abrir a seguinte (ou o pódio).
 *
 * Antes: `#fechar()` mandava o placar final e já abria a pergunta seguinte na mesma chamada. Quem
 * respondia por último via o próprio `resposta` (certo/errado) e a pergunta nova quase ao mesmo
 * tempo — sem tempo nenhum para ver o resultado. Mesmo defeito na última pergunta, a caminho do
 * pódio.
 */
export const REVELACAO_RESPOSTA_MS = 3_000;

/**
 * Carência antes de uma ligação perdida contar como desistência.
 *
 * Na RTDB o `onDisconnect` disparava em ~2 s e era logo walkover. Numa rede móvel isso pune um
 * túnel. Dez segundos cobrem o blip e um reinício do servidor, e continuam muito abaixo do que
 * custaria a paciência de quem fica à espera.
 */
export const CARENCIA_RECONEXAO_MS = 10_000;

const relogioReal = {
  agora: () => Date.now(),
  agendar: (fn, ms) => setTimeout(fn, ms),
  cancelar: (h) => clearTimeout(h)
};

export class Partida {
  constructor({
    salaId, formatoId, categoria, modo, membros, perguntas,
    difundir, enviar, aoTerminar, relogio = relogioReal
  }) {
    this.salaId = salaId;
    this.formato = formatoDe(formatoId);
    this.categoria = categoria;
    this.modo = modo;
    this.perguntas = perguntas;
    this.difundir = difundir;
    this.enviar = enviar;
    this.aoTerminar = aoTerminar;
    this.relogio = relogio;

    this.jogadores = new Map(membros.map(({ uid, nome }, i) => [uid, {
      uid,
      nome,
      equipa: this.formato.equipas ? (i < 2 ? 'A' : 'B') : null,
      pontos: 0,
      certas: 0,
      sequencia: 0,
      maxSequencia: 0,
      respondidas: new Set(),
      estado: 'activo'          // activo | ausente | saiu | terminado
    }]));

    this.indice = -1;
    this.fimEm = 0;
    this.terminada = false;
    this._fecho = null;
    this._carencias = new Map();
  }

  get uids() { return [...this.jogadores.keys()]; }

  #activos() {
    return [...this.jogadores.values()].filter((j) => j.estado === 'activo' || j.estado === 'ausente');
  }

  #equipas() {
    return {
      A: [...this.jogadores.values()].filter((j) => j.equipa === 'A'),
      B: [...this.jogadores.values()].filter((j) => j.equipa === 'B')
    };
  }

  comecar() {
    this.difundir({
      t: 'partida',
      salaId: this.salaId,
      categoria: this.categoria,
      modo: this.modo,
      formato: this.formato.id,
      totalPerguntas: this.perguntas.length,
      membros: [...this.jogadores.values()].map((j) => ({ uid: j.uid, nome: j.nome, equipa: j.equipa }))
    });
    this.relogio.agendar(() => this.#abrir(0), REVELACAO_MS);
  }

  #abrir(indice) {
    if (this.terminada) return;
    this.indice = indice;
    const pergunta = this.perguntas[indice];
    const evento = eventoPara(this.modo, indice);
    const duracao = duracaoDaPergunta(evento);
    this.fimEm = this.relogio.agora() + duracao;

    this.difundir({
      t: 'pergunta',
      indice,
      total: this.perguntas.length,
      evento,
      duracao,
      fimEm: this.fimEm,
      agora: this.relogio.agora(),   // o cliente afere o relógio com isto além do ping
      ...paraCliente(pergunta)
    });

    this._fecho = this.relogio.agendar(() => this.#fechar(), duracao);
  }

  /**
   * Uma resposta. Tudo o que podia ser mentira é verificado:
   * índice corrente, primeira vez, dentro do tempo, e a opção tem mesmo de existir na pergunta.
   *
   * [tCliente] é o instante que o cliente julga ser tempo de servidor, e [rtt] a ida-e-volta
   * medida pelos pings. Sem isto, quem tem 200 ms de latência perdia pontos por causa da rede:
   * o crédito é o instante alegado, **preso** ao intervalo `[chegada − rtt, chegada]`. O ganho
   * possível por mentir fica limitado ao rtt real do próprio jogador — que é exactamente o que
   * se lhe quer devolver, e não mais do que isso.
   */
  responder(uid, { indice, opcao, tCliente }, rtt = 0) {
    const j = this.jogadores.get(uid);
    if (!j || this.terminada) return { erro: 'fora_da_partida' };
    if (j.estado === 'saiu' || j.estado === 'terminado') return { erro: 'fora_da_partida' };
    if (indice !== this.indice) return { erro: 'pergunta_errada' };
    if (j.respondidas.has(indice)) return { erro: 'ja_respondeu' };

    const chegada = this.relogio.agora();
    if (chegada > this.fimEm) return { erro: 'tarde_demais' };

    const pergunta = this.perguntas[indice];
    if (opcao != null && !pergunta.opcoes.includes(opcao)) return { erro: 'opcao_invalida' };

    const creditado = Math.min(Math.max(Number(tCliente) || chegada, chegada - Math.max(0, rtt)), chegada);
    const certa = opcao === pergunta.respostaCorreta;
    this.#pontuar(j, indice, certa, this.fimEm - creditado);

    this.enviar(uid, {
      t: 'resposta',
      indice,
      certa,
      respostaCorreta: pergunta.respostaCorreta,   // só agora, e só a quem já respondeu
      total: j.pontos,
      certas: j.certas
    });
    this.#placar();
    this.#talvezFechar();
    return { ok: true };
  }

  #pontuar(j, indice, certa, msRestantes) {
    j.respondidas.add(indice);
    j.sequencia = certa ? j.sequencia + 1 : 0;
    if (j.sequencia > j.maxSequencia) j.maxSequencia = j.sequencia;
    if (certa) j.certas += 1;

    const delta = pontosDaResposta({
      certa,
      segundosRestantes: segundosRestantes(msRestantes),
      dificuldade: this.perguntas[indice].dificuldade,
      evento: eventoPara(this.modo, indice),
      sequenciaDepois: j.sequencia
    });
    j.pontos = limitarTotal(j.pontos + delta);
  }

  #placar() {
    this.difundir({
      t: 'placar',
      indice: this.indice,
      pontos: Object.fromEntries([...this.jogadores.values()].map((j) => [j.uid, j.pontos]))
    });
  }

  /** Avança assim que todos os que ainda jogam responderam — não espera pelo cronómetro. */
  #talvezFechar() {
    const activos = this.#activos();
    if (activos.length > 0 && activos.every((j) => j.respondidas.has(this.indice))) this.#fechar();
  }

  #fechar() {
    if (this.terminada) return;
    if (this._fecho != null) { this.relogio.cancelar(this._fecho); this._fecho = null; }

    // Quem não respondeu leva o tratamento de tempo esgotado: sequência a zero, e a penalização
    // do Tudo ou Nada, tal como o `registerTimeout` fazia no cliente.
    for (const j of this.#activos()) {
      if (!j.respondidas.has(this.indice)) this.#pontuar(j, this.indice, false, 0);
    }
    this.#placar();

    const seguinte = this.indice + 1;
    // Reaproveita `_fecho` para o atraso de revelação — dá jeito porque `#terminar` já o cancela
    // se a partida acabar por outra via (ex.: desistência) enquanto se espera. Chamar `#fechar`
    // de novo durante a revelação (ex.: alguém desiste e `#talvezFechar` vê os outros já
    // responderam) reagenda o mesmo `seguinte` — reinicia a contagem, não é bonito mas é inofensivo.
    this._fecho = this.relogio.agendar(() => {
      this._fecho = null;
      if (this.terminada) return;
      if (seguinte < this.perguntas.length) this.#abrir(seguinte);
      else this.#terminar({ walkover: false });
    }, REVELACAO_RESPOSTA_MS);
  }

  /**
   * Ligação perdida. Abre a carência antes de contar como desistência — durante ela o jogador
   * conta como presente para o avanço das perguntas, para os outros não ficarem à espera dele.
   */
  desligou(uid) {
    const j = this.jogadores.get(uid);
    if (!j || j.estado === 'saiu' || j.estado === 'terminado' || this.terminada) return;
    j.estado = 'ausente';
    this.difundir({ t: 'ausente', uid, ateEm: this.relogio.agora() + CARENCIA_RECONEXAO_MS });
    this._carencias.set(uid, this.relogio.agendar(() => this.desistiu(uid), CARENCIA_RECONEXAO_MS));
    this.#talvezFechar();
  }

  reconectou(uid) {
    const j = this.jogadores.get(uid);
    if (!j || j.estado !== 'ausente') return false;
    const h = this._carencias.get(uid);
    if (h != null) { this.relogio.cancelar(h); this._carencias.delete(uid); }
    j.estado = 'activo';
    this.difundir({ t: 'voltou', uid });
    return true;
  }

  /** Saída definitiva: pediu para sair, ou a carência expirou. */
  desistiu(uid) {
    const j = this.jogadores.get(uid);
    if (!j || j.estado === 'saiu' || this.terminada) return;
    const h = this._carencias.get(uid);
    if (h != null) { this.relogio.cancelar(h); this._carencias.delete(uid); }
    j.estado = 'saiu';
    this.difundir({ t: 'saiu', uid });
    this.#reavaliarWalkover(uid);
    if (!this.terminada) this.#talvezFechar();
  }

  /**
   * Critérios migrados tal e qual de `MultiMatchViewModel.onRoom`:
   *
   *  - **2x2:** sai qualquer um e a equipa dele perde. Escolhido em vez de "1 contra 2", que
   *    tornava o total de equipa injusto.
   *  - **Sem equipas:** se sobrar UM só activo numa sala que tinha mais do que um, acabou. A
   *    condição é genérica de propósito — no Grupo, sair um de quatro deixa três e o jogo segue.
   */
  #reavaliarWalkover(quemSaiu) {
    if (this.formato.equipas) {
      const equipaQueSaiu = this.jogadores.get(quemSaiu)?.equipa;
      this.#terminar({ walkover: true, equipaDerrotada: equipaQueSaiu });
      return;
    }
    const activos = this.#activos();
    // Quem fica ganha o walkover, mesmo 0-0. É o comportamento do `finishSoloWalkover`, que
    // agregava `won = true` sem olhar ao placar: ficar sozinho não é empatar, e um 1x1 em que
    // ninguém chegou a responder daria empate pelo critério normal.
    if (this.jogadores.size > 1 && activos.length === 1) {
      this.#terminar({ walkover: true, vencedorForcado: activos[0].uid });
    }
  }

  #terminar({ walkover, equipaDerrotada = null, vencedorForcado = null }) {
    if (this.terminada) return;
    this.terminada = true;
    if (this._fecho != null) { this.relogio.cancelar(this._fecho); this._fecho = null; }
    for (const h of this._carencias.values()) this.relogio.cancelar(h);
    this._carencias.clear();

    const resultado = this.formato.equipas
      ? this.#resultadoPorEquipas(equipaDerrotada)
      : this.#resultadoIndividual(vencedorForcado);

    for (const j of this.jogadores.values()) {
      if (j.estado !== 'saiu') j.estado = 'terminado';
      this.enviar(j.uid, {
        t: 'podio',
        walkover,
        ...resultado,
        ganhei: resultado.vencedores.includes(j.uid),
        meuScore: j.pontos,
        minhasCertas: j.certas,
        maxSequencia: j.maxSequencia,
        totalPerguntas: this.perguntas.length
      });
    }

    this.aoTerminar?.({
      salaId: this.salaId,
      formato: this.formato.id,
      categoria: this.categoria,
      modo: this.modo,
      totalPerguntas: this.perguntas.length,
      vencedores: resultado.vencedores,
      jogadores: [...this.jogadores.values()].map((j) => ({
        uid: j.uid, nome: j.nome, pontos: j.pontos, certas: j.certas,
        maxSequencia: j.maxSequencia, saiu: j.estado === 'saiu'
      }))
    });
  }

  /** 1x1 e Grupo: ganha quem tem a pontuação **estritamente** mais alta. Empate não é vitória. */
  #resultadoIndividual(vencedorForcado = null) {
    const ranking = [...this.jogadores.values()]
      .map((j) => ({ uid: j.uid, nome: j.nome, pontos: j.pontos, saiu: j.estado === 'saiu' }))
      // quem saiu vai para o fim, e só depois desempata a pontuação — igual ao cliente
      .sort((a, b) => (a.saiu === b.saiu ? b.pontos - a.pontos : (a.saiu ? 1 : -1)));

    if (vencedorForcado) return { ranking, vencedores: [vencedorForcado] };
    const topo = ranking[0];
    const vencedores = topo && (ranking.length < 2 || topo.pontos > ranking[1].pontos) ? [topo.uid] : [];
    return { ranking, vencedores };
  }

  /** 2x2: ganha a equipa com total **estritamente** maior; num walkover, a que ficou. */
  #resultadoPorEquipas(equipaDerrotada) {
    const { A, B } = this.#equipas();
    const total = (equipa) => equipa.reduce((n, j) => n + j.pontos, 0);
    const totais = { A: total(A), B: total(B) };

    let vencedora = null;
    if (equipaDerrotada) vencedora = equipaDerrotada === 'A' ? 'B' : 'A';
    else if (totais.A !== totais.B) vencedora = totais.A > totais.B ? 'A' : 'B';

    const equipas = ['A', 'B'].map((nome) => ({
      nome,
      total: totais[nome],
      venceu: vencedora === nome,
      jogadores: (nome === 'A' ? A : B).map((j) => ({ uid: j.uid, nome: j.nome, pontos: j.pontos }))
    }));

    const vencedores = vencedora
      ? (vencedora === 'A' ? A : B).map((j) => j.uid)
      : [];
    return { equipas, vencedores };
  }
}
