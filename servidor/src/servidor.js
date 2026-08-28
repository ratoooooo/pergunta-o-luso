/**
 * Arranque do processo: liga o Firebase real à app e põe-se à escuta.
 *
 * Tudo o que é lógica está em `app.js`, com as dependências injectadas — aqui só se decide
 * *quais* são. É o que permite aos testes correr a mesma app com um Firebase falso.
 */

import { criarApp } from './app.js';
import { iniciarFirebase, verificarToken, lerCaminho, nomeDoJogador, gravarScore } from './firebase.js';

const PORTA = Number(process.env.PORT ?? 2567);
// Só o laço local: quem fala com a internet é o Caddy, que trata do TLS. A porta do Node nunca
// é aberta no ufw.
const ANFITRIAO = process.env.HOST ?? '127.0.0.1';

iniciarFirebase();

const app = criarApp({
  verificarToken,
  lerCaminho,
  nomeDoJogador,
  gravarScore,
  segredoAdmin: process.env.ADMIN_TOKEN ?? ''
});

process.on('SIGTERM', app.drenar);
process.on('SIGINT', app.drenar);

const resumo = await app.carregarBanco();
console.log(`[banco] ${resumo.perguntas} perguntas em ${resumo.categorias} categorias`);
app.servidor.listen(PORTA, ANFITRIAO, () => console.log(`[servidor] em ${ANFITRIAO}:${PORTA}`));
