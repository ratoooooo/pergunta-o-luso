/**
 * Salas de espera — porte de `/lobbies` + `MultiMatchRepository.findOrCreateLobby`.
 *
 * O algoritmo é o mesmo que já corria em produção; muda o sítio. Duas coisas caem por terra na
 * mudança, e são ganhos, não simplificações:
 *
 *  - **A transação sobre o nó do formato inteiro deixa de existir.** Era necessária porque N
 *    clientes escreviam na mesma subárvore ao mesmo tempo. Um processo com um laço de eventos
 *    não tem concorrência a resolver, e com ela vai-se embora a classe de defeito que já parou
 *    o matchmaking a toda a gente (um lobby órfão a falhar a validação rejeitava a transação
 *    inteira, para todos).
 *  - **O temporizador de 60 s passa a ser do servidor.** Vivia na composição do ecrã do
 *    anfitrião e parava se ele mudasse de app — defeito documentado em
 *    docs/vault/funcionalidades/multiplayer.md.
 *
 * O que NÃO muda: entra-se no primeiro lobby `à espera` com a mesma categoria E o mesmo modo e
 * com lugar; quem cria é anfitrião; arranca ao encher, por decisão do anfitrião a partir do
 * mínimo, ou aos 60 s; o temporizador reinicia a cada entrada nova (é a janela de graça: uma
 * sala a encher continua a esperar, uma sala parada fecha sozinha) e só arma a partir do mínimo.
 */

import { formatoDe } from './formatos.js';

export const AUTO_ARRANQUE_MS = 60_000;

const relogioReal = {
  agora: () => Date.now(),
  agendar: (fn, ms) => setTimeout(fn, ms),
  cancelar: (h) => clearTimeout(h)
};

let sequencia = 0;
const novoId = () => `L${(++sequencia).toString(36)}${Date.now().toString(36)}`;

export class Lobbies {
  /**
   * @param aoArrancar chamado com o lobby quando a partida deve começar (cheio, decisão do
   *                   anfitrião, ou temporizador). Quem trata é o chamador — este módulo não
   *                   sabe o que é uma partida.
   * @param aoMudar    chamado sempre que um lobby muda, para difundir aos membros.
   * @param relogio    injectável para os testes não esperarem 60 s reais.
   */
  constructor({ aoArrancar, aoMudar, relogio = relogioReal }) {
    this.aoArrancar = aoArrancar;
    this.aoMudar = aoMudar;
    this.relogio = relogio;
    this.porId = new Map();     // lobbyId -> lobby
    this.porUid = new Map();    // uid -> lobbyId
  }

  lobbyDe(uid) {
    const id = this.porUid.get(uid);
    return id ? this.porId.get(id) ?? null : null;
  }

  /**
   * Lobbies à espera de um formato, para o ecrã "VER OUTRAS SALAS ABERTAS".
   * Os privados (código da comunidade, desafio de amigo) não são anunciados a ninguém.
   */
  abertos(formatoId) {
    return [...this.porId.values()]
      .filter((l) => l.formatoId === formatoId && l.estado === 'a_espera' && !l.privado);
  }

  porCodigo(codigo) {
    return [...this.porId.values()].find((l) => l.codigo === codigo && l.estado === 'a_espera') ?? null;
  }

  /**
   * Entra no primeiro lobby compatível, ou cria um. Devolve o lobby.
   * Sair do lobby anterior (se houver) é feito aqui — ninguém fica em dois sítios.
   */
  procurar({ uid, nome, formatoId, categoria, modo }) {
    this.sair(uid);
    const formato = formatoDe(formatoId);
    const compativel = [...this.porId.values()].find(
      (l) => !l.privado && l.formatoId === formato.id && l.estado === 'a_espera' &&
             l.categoria === categoria && l.modo === modo &&
             l.membros.length < formato.jogadores
    );
    return compativel ? this.#juntar(compativel, uid, nome) : this.#criar({ uid, nome, formato, categoria, modo });
  }

  /**
   * Entra num lobby **específico** (troca de sala). Devolve `null` quando já não dá — cheio,
   * já começou, ou desapareceu. O chamador cai no `procurar()` nesse caso, que é o que o
   * `switchLobby` do cliente já fazia.
   */
  entrarEm(lobbyId, uid, nome) {
    const lobby = this.porId.get(lobbyId);
    if (!lobby || lobby.estado !== 'a_espera') return null;
    // Num desafio direto só entra quem foi convidado — o id da sala viaja no convite, e sem
    // esta lista bastava conhecê-lo para se meter no duelo de outra pessoa.
    if (lobby.permitidos && !lobby.permitidos.includes(uid)) return null;
    if (lobby.membros.length >= formatoDe(lobby.formatoId).jogadores) return null;
    if (lobby.membros.some((m) => m.uid === uid)) return lobby;
    this.sair(uid);
    return this.#juntar(lobby, uid, nome);
  }

  /**
   * Sala não anunciada: quiz da comunidade por código, ou desafio direto a um amigo.
   *
   * É o mesmo lobby de sempre com três campos opcionais — `codigo` (entra quem souber),
   * `permitidos` (entra só quem lá estiver) e `perguntas` (as do quiz, em vez das da categoria).
   * Na RTDB isto eram três caminhos diferentes com regras próprias, e a ordem obrigatória
   * "lobby -> código -> sala" existia só porque as rules precisavam de ver o lobby escrito antes.
   */
  criarPrivado({ uid, nome, formatoId, categoria, modo, codigo = null, permitidos = null, perguntas = null }) {
    this.sair(uid);
    // Os campos privados vão no próprio `#criar`, e não a seguir: `#criar` já difunde o lobby,
    // e preenchê-los depois fazia a PRIMEIRA mensagem sair sem código — quem entrasse por ela
    // ficava com uma sala privada sem código nenhum para partilhar.
    return this.#criar({
      uid, nome, formato: formatoDe(formatoId), categoria, modo,
      extra: { privado: true, codigo, permitidos, perguntas }
    });
  }

  #criar({ uid, nome, formato, categoria, modo, extra = null }) {
    const lobby = {
      id: novoId(),
      privado: false,
      codigo: null,
      permitidos: null,
      perguntas: null,
      formatoId: formato.id,
      categoria,
      modo,
      estado: 'a_espera',
      anfitriaoUid: uid,
      membros: [{ uid, nome, entrouEm: this.relogio.agora() }],
      criadoEm: this.relogio.agora(),
      autoEm: null,
      _temporizador: null,
      ...extra
    };
    this.porId.set(lobby.id, lobby);
    this.porUid.set(uid, lobby.id);
    this.#reavaliar(lobby);
    return lobby;
  }

  #juntar(lobby, uid, nome) {
    lobby.membros.push({ uid, nome, entrouEm: this.relogio.agora() });
    this.porUid.set(uid, lobby.id);
    this.#reavaliar(lobby);
    return lobby;
  }

  /**
   * Sai do lobby onde estiver. Se ficar vazio, o lobby desaparece — é o que impede o "estado
   * velho de QA a causar emparelhamentos fantasma", que na RTDB não tinha quem o limpasse.
   * Se sair o anfitrião, herda o membro mais antigo dos que ficam.
   */
  sair(uid) {
    const lobby = this.lobbyDe(uid);
    this.porUid.delete(uid);
    if (!lobby) return null;

    lobby.membros = lobby.membros.filter((m) => m.uid !== uid);
    if (lobby.membros.length === 0) {
      this.#pararTemporizador(lobby);
      this.porId.delete(lobby.id);
      return null;
    }
    if (lobby.anfitriaoUid === uid) {
      lobby.anfitriaoUid = [...lobby.membros].sort((a, b) => a.entrouEm - b.entrouEm)[0].uid;
    }
    this.#reavaliar(lobby);
    return lobby;
  }

  /** O anfitrião carregou em INICIAR JOGO. Devolve `false` quando não podia. */
  forcarInicio(uid) {
    const lobby = this.lobbyDe(uid);
    if (!lobby || lobby.estado !== 'a_espera') return false;
    if (lobby.anfitriaoUid !== uid) return false;
    if (!this.podeComecar(lobby)) return false;
    this.#arrancar(lobby);
    return true;
  }

  podeComecar(lobby) {
    return lobby.membros.length >= formatoDe(lobby.formatoId).minJogadores;
  }

  /**
   * Depois de cada entrada ou saída: arranca se encheu, arma/reinicia o temporizador se já dá
   * para começar, desarma-o se deixou de dar.
   *
   * A guarda do mínimo está aqui e **só** aqui. No cliente estava repetida no ecrã e no
   * `forceStartGame`, porque o temporizador podia disparar depois de alguém sair e a contagem
   * do ecrã já não valia. Com um só sítio a decidir, a repetição deixa de fazer falta.
   */
  #reavaliar(lobby) {
    const formato = formatoDe(lobby.formatoId);
    if (lobby.membros.length >= formato.jogadores) {
      this.#arrancar(lobby);
      return;
    }
    this.#pararTemporizador(lobby);
    if (this.podeComecar(lobby)) {
      lobby.autoEm = this.relogio.agora() + AUTO_ARRANQUE_MS;
      lobby._temporizador = this.relogio.agendar(() => {
        // Reconfirma: entre agendar e disparar pode ter saído gente.
        if (lobby.estado === 'a_espera' && this.podeComecar(lobby)) this.#arrancar(lobby);
      }, AUTO_ARRANQUE_MS);
    } else {
      lobby.autoEm = null;
    }
    this.aoMudar?.(lobby);
  }

  #pararTemporizador(lobby) {
    if (lobby._temporizador != null) this.relogio.cancelar(lobby._temporizador);
    lobby._temporizador = null;
  }

  #arrancar(lobby) {
    if (lobby.estado !== 'a_espera') return;
    this.#pararTemporizador(lobby);
    lobby.estado = 'a_jogar';
    lobby.autoEm = null;
    for (const m of lobby.membros) this.porUid.delete(m.uid);
    this.porId.delete(lobby.id);
    this.aoArrancar?.(lobby);
  }

  /** Estado para o `GET /saude`. */
  resumo() {
    return { lobbies: this.porId.size, jogadoresEmEspera: this.porUid.size };
  }
}
