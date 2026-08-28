/**
 * A app do servidor: HTTP + WebSocket + encaminhamento das mensagens.
 *
 * Está separada do arranque (`servidor.js`) por uma razão só: **as dependências do Firebase são
 * injectadas**. É isso que permite correr uma partida inteira nos testes, com N jogadores, sem
 * tocar na base de dados de produção nem precisar de rede — a fase 1 do plano tinha de ser
 * verificável sozinha, antes de existir uma linha de Android.
 *
 * Escuta só em 127.0.0.1 — quem fala com a internet é o Caddy, que trata do TLS. O ID token do
 * Firebase viaja no cabeçalho `Authorization` do handshake, nunca na query string (o URL fica
 * em logs de proxy e em histórico).
 *
 * Ver PROTOCOLO.md para as mensagens.
 */

import http from 'node:http';
import { WebSocketServer } from 'ws';

import { Lobbies } from './lobbies.js';
import { Partida } from './partida.js';
import { formatoDe } from './formatos.js';
import {
  carregarBanco, estadoDoBanco, perguntasParaJogo, perguntasDeQuiz
} from './perguntas.js';

export const VERSAO = '0.1.0';

/** Perguntas por partida no matchmaking aleatório (o `QUESTION_COUNT` do cliente). */
const PERGUNTAS_POR_PARTIDA = 10;

/** Quanto tempo se espera pelas partidas em curso antes de sair à força, ao actualizar. */
const DRENAGEM_MAX_MS = 5 * 60_000;

export function criarApp({
  verificarToken, lerCaminho, nomeDoJogador, gravarScore,
  segredoAdmin = '', drenagemMaxMs = DRENAGEM_MAX_MS, aoDesligar = () => process.exit(0)
}) {

  const arranqueEm = Date.now();
  const sessoes = new Map();     // uid -> { ws, rtt, sondaEm, sonda }
  const partidas = new Map();    // salaId -> Partida
  const partidaDe = new Map();   // uid -> salaId
  let aDrenar = false;

  // ---- envio ----------------------------------------------------------------

  function enviar(uid, msg) {
    const s = sessoes.get(uid);
    if (s && s.ws.readyState === s.ws.OPEN) s.ws.send(JSON.stringify(msg));
  }

  const difundirPara = (uids) => (msg) => { for (const uid of uids) enviar(uid, msg); };

  // ---- lobbies --------------------------------------------------------------

  const lobbies = new Lobbies({
    aoMudar: (lobby) => difundirLobby(lobby),
    aoArrancar: (lobby) => { arrancarPartida(lobby).catch((e) => falharLobby(lobby, e)); }
  });

  function estadoDoLobby(lobby, uid) {
    const formato = formatoDe(lobby.formatoId);
    return {
      t: 'sala',
      lobbyId: lobby.id,
      formato: formato.id,
      categoria: lobby.categoria,
      modo: lobby.modo,
      codigo: lobby.codigo,
      membros: lobby.membros.map((m) => ({ uid: m.uid, nome: m.nome })),
      souAnfitriao: lobby.anfitriaoUid === uid,
      minimo: formato.minJogadores,
      capacidade: formato.jogadores,
      podeComecar: lobbies.podeComecar(lobby),
      autoEm: lobby.autoEm
    };
  }

  function difundirLobby(lobby) {
    for (const m of lobby.membros) enviar(m.uid, estadoDoLobby(lobby, m.uid));
    // A lista de salas abertas mudou para toda a gente que ainda anda à procura neste formato.
    const lista = listaDeSalas(lobby.formatoId);
    for (const outro of lobbies.abertos(lobby.formatoId)) {
      for (const m of outro.membros) enviar(m.uid, lista);
    }
  }

  function listaDeSalas(formatoId) {
    return {
      t: 'salas',
      formato: formatoId,
      salas: lobbies.abertos(formatoId).map((l) => ({
        lobbyId: l.id,
        categoria: l.categoria,
        modo: l.modo,
        anfitriao: l.membros.find((m) => m.uid === l.anfitriaoUid)?.nome ?? 'Jogador',
        jogadores: l.membros.length,
        capacidade: formatoDe(l.formatoId).jogadores
      }))
    };
  }

  function falharLobby(lobby, erro) {
    console.error('[lobby] falhou a arrancar', lobby.id, erro?.message);
    for (const m of lobby.membros) {
      partidaDe.delete(m.uid);
      enviar(m.uid, { t: 'erro', codigo: 'arranque_falhou', msg: 'Não foi possível começar a partida.' });
    }
  }

  // ---- partidas -------------------------------------------------------------

  async function arrancarPartida(lobby) {
    const perguntas = lobby.perguntas ?? perguntasParaJogo(lobby.categoria, PERGUNTAS_POR_PARTIDA);
    if (perguntas.length === 0) throw new Error(`sem perguntas para "${lobby.categoria}"`);

    const uids = lobby.membros.map((m) => m.uid);
    const partida = new Partida({
      salaId: lobby.id,
      formatoId: lobby.formatoId,
      categoria: lobby.categoria,
      modo: lobby.modo,
      membros: lobby.membros.map((m) => ({ uid: m.uid, nome: m.nome })),
      perguntas,
      difundir: difundirPara(uids),
      enviar,
      aoTerminar: (relatorio) => terminarPartida(relatorio)
    });

    partidas.set(lobby.id, partida);
    for (const uid of uids) partidaDe.set(uid, lobby.id);
    partida.comecar();
  }

  /**
   * Fim de partida. O servidor grava `/scores` de **todos** os jogadores — é ele que tem os
   * números, e as rules recusam a um cliente criar um registo que não seja `formato: "solo"`.
   *
   * O agregado do perfil (`/jogadores/{uid}`: XP, conquistas, sequência de dias) continua a ser
   * escrito pela app, com estes mesmos números — ver a secção 4 do plano. Duplicar aqui o
   * `accumulate` + `StreakDiario` + `Progressao` era a alternativa, e obrigava a mantê-los
   * sincronizados com o Kotlin para sempre.
   */
  async function terminarPartida(relatorio) {
    partidas.delete(relatorio.salaId);
    for (const j of relatorio.jogadores) partidaDe.delete(j.uid);

    await Promise.all(relatorio.jogadores.map((j) => gravarScore({
      uid: j.uid,
      modo: relatorio.modo,
      categoria: relatorio.categoria,
      formato: relatorio.formato,
      score: j.pontos,
      correctCount: j.certas,
      total: relatorio.totalPerguntas
    })));

    if (aDrenar && partidas.size === 0) desligar();
  }

  // ---- mensagens ------------------------------------------------------------

  const acoes = {
    async procurar(uid, nome, m) {
      if (aDrenar) return enviar(uid, { t: 'erro', codigo: 'em_manutencao', msg: 'A actualizar. Tenta daqui a um minuto.' });
      const lobby = lobbies.procurar({
        uid, nome,
        formatoId: m.formato,
        categoria: String(m.categoria ?? ''),
        modo: m.modo === 'caotico' ? 'caotico' : 'classico'
      });
      enviar(uid, estadoDoLobby(lobby, uid));
      enviar(uid, listaDeSalas(lobby.formatoId));
    },

    async trocar_sala(uid, nome, m) {
      const lobby = lobbies.entrarEm(String(m.lobbyId ?? ''), uid, nome);
      if (lobby) return enviar(uid, estadoDoLobby(lobby, uid));
      // A sala escolhida encheu ou já começou entretanto. Em vez de deixar o jogador em lado
      // nenhum — que era o defeito B3 do cliente —, cai-se no matchmaking normal.
      enviar(uid, { t: 'aviso', codigo: 'sala_indisponivel' });
      await acoes.procurar(uid, nome, m);
    },

    async iniciar(uid) {
      if (!lobbies.forcarInicio(uid)) {
        enviar(uid, { t: 'erro', codigo: 'nao_pode_comecar', msg: 'Ainda não dá para começar.' });
      }
    },

    async sair(uid) {
      const salaId = partidaDe.get(uid);
      if (salaId) partidas.get(salaId)?.desistiu(uid);
      else lobbies.sair(uid);
    },

    async responder(uid, _nome, m) {
      const partida = partidas.get(partidaDe.get(uid));
      if (!partida) return enviar(uid, { t: 'erro', codigo: 'sem_partida' });
      const r = partida.responder(
        uid,
        { indice: Number(m.indice), opcao: m.opcao ?? null, tCliente: Number(m.tCliente) || null },
        sessoes.get(uid)?.rtt ?? 0
      );
      if (r.erro) enviar(uid, { t: 'erro', codigo: r.erro });
    },

    async ping(uid, _nome, m) {
      enviar(uid, { t: 'pong', t0: m.t0, tS: Date.now() });
    },

    /** Resposta à sonda que o servidor mandou — é assim que ele mede o rtt, sem acreditar no cliente. */
    async sonda_ok(uid, _nome, m) {
      const s = sessoes.get(uid);
      if (s && s.sonda === m.s) s.rtt = Date.now() - s.sondaEm;
    },

    async privada_criar(uid, nome, m) {
      if (aDrenar) return enviar(uid, { t: 'erro', codigo: 'em_manutencao' });
      const quiz = await perguntasDeQuiz(lerCaminho, String(m.quizId ?? ''));
      if (!quiz) return enviar(uid, { t: 'erro', codigo: 'quiz_invalido', msg: 'Esse quiz não dá para jogar.' });
      const codigo = codigoLivre();
      const lobby = lobbies.criarPrivado({
        uid, nome,
        formatoId: m.formato,
        categoria: quiz.titulo,
        modo: 'classico',
        codigo,
        perguntas: quiz.perguntas
      });
      enviar(uid, estadoDoLobby(lobby, uid));
    },

    async privada_entrar(uid, nome, m) {
      const lobby = lobbies.porCodigo(String(m.codigo ?? '').trim());
      if (!lobby) return enviar(uid, { t: 'erro', codigo: 'codigo_invalido', msg: 'Código de sala inválido ou expirado!' });
      const entrou = lobbies.entrarEm(lobby.id, uid, nome);
      if (!entrou) return enviar(uid, { t: 'erro', codigo: 'sala_cheia' });
      enviar(uid, estadoDoLobby(entrou, uid));
    },

    /** Desafio direto: cria a sala já, para o id poder viajar dentro do convite em `/convites`. */
    async desafio_criar(uid, nome, m) {
      if (aDrenar) return enviar(uid, { t: 'erro', codigo: 'em_manutencao' });
      const convidado = String(m.paraUid ?? '');
      if (!convidado || convidado === uid) return enviar(uid, { t: 'erro', codigo: 'convidado_invalido' });
      const lobby = lobbies.criarPrivado({
        uid, nome,
        formatoId: m.formato ?? '1x1',
        categoria: String(m.categoria ?? ''),
        modo: m.modo === 'caotico' ? 'caotico' : 'classico',
        permitidos: [uid, convidado]
      });
      enviar(uid, estadoDoLobby(lobby, uid));
    },

    async desafio_entrar(uid, nome, m) {
      const lobby = lobbies.entrarEm(String(m.salaId ?? ''), uid, nome);
      if (!lobby) return enviar(uid, { t: 'erro', codigo: 'desafio_expirado', msg: 'O desafio já não está disponível.' });
      enviar(uid, estadoDoLobby(lobby, uid));
    }
  };

  /** Código de 4 dígitos livre. Em memória não há colisão silenciosa possível: ou está ou não está. */
  function codigoLivre() {
    for (let i = 0; i < 200; i++) {
      const c = String(1000 + Math.floor(Math.random() * 9000));
      if (!lobbies.porCodigo(c)) return c;
    }
    throw new Error('sem códigos de sala livres');
  }

  function sondar(uid) {
    const s = sessoes.get(uid);
    if (!s) return;
    s.sonda = Math.random().toString(36).slice(2, 8);
    s.sondaEm = Date.now();
    enviar(uid, { t: 'sonda', s: s.sonda });
  }

  // ---- ligações -------------------------------------------------------------

  const wss = new WebSocketServer({ noServer: true });

  wss.on('connection', (ws, _req, { uid, nome }) => {
    // Uma segunda ligação do mesmo jogador substitui a primeira: é o caso normal de reconexão
    // (a antiga ainda não foi dada como morta) e o de ter a app aberta em dois sítios.
    sessoes.get(uid)?.ws.close(4008, 'sessao_substituida');
    sessoes.set(uid, { ws, rtt: 0, sonda: null, sondaEm: 0 });

    enviar(uid, { t: 'sessao', uid, nome, versao: VERSAO, agora: Date.now() });
    sondar(uid);

    const salaId = partidaDe.get(uid);
    if (salaId && partidas.get(salaId)?.reconectou(uid)) {
      enviar(uid, { t: 'aviso', codigo: 'reentraste' });
    }

    ws.on('message', async (bruto) => {
      let m;
      try { m = JSON.parse(bruto.toString()); } catch { return enviar(uid, { t: 'erro', codigo: 'json_invalido' }); }
      const acao = acoes[m?.t];
      if (!acao) return enviar(uid, { t: 'erro', codigo: 'accao_desconhecida' });
      try {
        await acao(uid, nome, m);
      } catch (e) {
        console.error('[msg]', m.t, uid, e.message);
        enviar(uid, { t: 'erro', codigo: 'falha_interna' });
      }
    });

    ws.on('close', () => {
      if (sessoes.get(uid)?.ws !== ws) return;   // já foi substituída por uma ligação nova
      sessoes.delete(uid);
      const sala = partidaDe.get(uid);
      if (sala) partidas.get(sala)?.desligou(uid);   // carência antes de contar como desistência
      else lobbies.sair(uid);
    });
  });

  // ---- HTTP ------------------------------------------------------------------

  const servidor = http.createServer(async (req, res) => {
    if (req.method === 'GET' && req.url === '/saude') {
      return responder(res, 200, {
        versao: VERSAO,
        uptimeSegundos: Math.round((Date.now() - arranqueEm) / 1000),
        partidas: partidas.size,
        ...lobbies.resumo(),
        sessoes: sessoes.size,
        banco: estadoDoBanco(),
        aDrenar
      });
    }
    if (req.method === 'POST' && req.url === '/admin/recarregar') {
      if (!segredoAdmin || req.headers['x-admin-token'] !== segredoAdmin) return responder(res, 403, { erro: 'nao_autorizado' });
      try {
        return responder(res, 200, await carregarBanco(lerCaminho));
      } catch (e) {
        return responder(res, 500, { erro: e.message });
      }
    }
    responder(res, 404, { erro: 'nao_existe' });
  });

  const responder = (res, codigo, corpo) => {
    res.writeHead(codigo, { 'content-type': 'application/json' });
    res.end(JSON.stringify(corpo));
  };

  /**
   * O token é verificado **antes** de a ligação existir: um handshake sem token válido leva 401 e
   * nunca chega a ser um WebSocket. Recusar depois de aceitar deixava sockets anónimos abertos.
   */
  servidor.on('upgrade', async (req, socket, head) => {
    const recusar = (codigo, motivo) => {
      socket.write(`HTTP/1.1 ${codigo} ${motivo}\r\nConnection: close\r\n\r\n`);
      socket.destroy();
    };
    const cabecalho = req.headers.authorization ?? '';
    if (!cabecalho.startsWith('Bearer ')) return recusar(401, 'Unauthorized');

    try {
      const { uid } = await verificarToken(cabecalho.slice(7));
      const nome = await nomeDoJogador(uid);
      wss.handleUpgrade(req, socket, head, (ws) => wss.emit('connection', ws, req, { uid, nome }));
    } catch (e) {
      console.warn('[auth] recusado:', e.message);
      recusar(401, 'Unauthorized');
    }
  });

  // ---- ciclo de vida ---------------------------------------------------------

  /**
   * Actualizar sem derrubar partidas: `SIGTERM` não mata — recusa matchmaking novo e deixa as
   * partidas em curso acabar. Uma partida dura ~3 min, por isso a janela de 5 chega. O
   * `TimeoutStopSec` da unidade systemd tem de ser maior do que isto.
   */
  function drenar() {
    if (aDrenar) return;
    aDrenar = true;
    console.log(`[drenagem] a aguardar ${partidas.size} partida(s)`);
    for (const uid of sessoes.keys()) if (!partidaDe.has(uid)) enviar(uid, { t: 'erro', codigo: 'em_manutencao' });
    if (partidas.size === 0) return desligar();
    setTimeout(() => { console.warn('[drenagem] tempo esgotado'); desligar(); }, drenagemMaxMs).unref();
  }

  function desligar() {
    console.log('[drenagem] terminado');
    for (const s of sessoes.values()) s.ws.close(1001, 'a_reiniciar');
    servidor.close(() => aoDesligar());
  }

  return { servidor, lobbies, partidas, sessoes, drenar, carregarBanco: () => carregarBanco(lerCaminho) };
}
