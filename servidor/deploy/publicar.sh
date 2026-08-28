#!/usr/bin/env bash
# Leva esta pasta para o VPS e reinicia o serviço.
#
# O host é argumento e não tem valor por omissão de propósito: o endereço do VPS não fica
# escrito no repositório.
#
# Reiniciar aqui é seguro porque o SIGTERM põe o servidor em drenagem — as partidas em curso
# acabam antes de o processo sair (ver TimeoutStopSec em pol-servidor.service).
set -euo pipefail

[ $# -eq 1 ] || { echo "uso: $0 utilizador@host" >&2; exit 1; }
ALVO="$1"
AQUI="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> a enviar $AQUI para $ALVO"
rsync -az --delete --exclude node_modules --exclude .git "$AQUI/" "$ALVO:/tmp/pol-servidor-novo/"

echo "==> a instalar em /opt/pol-servidor"
ssh "$ALVO" 'set -euo pipefail
  sudo rsync -a --delete --exclude node_modules /tmp/pol-servidor-novo/ /opt/pol-servidor/
  sudo chown -R pol-servidor:pol-servidor /opt/pol-servidor
  # pol-servidor é --no-create-home (INSTALAR.md, passo 1): sem $HOME, o npm não tem onde pôr a
  # cache e falha com EACCES a tentar criar /home/pol-servidor. /opt/pol-servidor já lhe pertence
  # (chown na linha acima), por isso serve de HOME só para esta chamada.
  cd /opt/pol-servidor && sudo -u pol-servidor env HOME=/opt/pol-servidor npm ci --omit=dev
  sudo systemctl restart pol-servidor.service
  sleep 2
  systemctl is-active pol-servidor.service
  curl -fsS http://127.0.0.1:2567/saude && echo'
