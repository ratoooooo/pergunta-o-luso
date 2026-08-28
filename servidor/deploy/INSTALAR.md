# Fase 0 — infra-estrutura no VPS

Passo a passo, comandos exactos. Nada aqui liga o jogo: no fim há um servidor a responder ao
`/saude` por HTTPS, em **modo esqueleto** — sem Firebase, e a recusar qualquer ligação de jogo
com 401. É deliberado: a chave de conta de serviço é uma credencial poderosa e só se cria quando
a autenticação for ligada, numa fase posterior.

Pressupostos já verificados: `perguntaoluso.duckdns.org` resolve para o IP do VPS, SSH só por
chave, `ufw` activo com a 22 aberta.

## 1. Utilizador de sistema

Sem shell e sem home: este utilizador só existe para correr um processo.

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin pol-servidor
sudo mkdir -p /opt/pol-servidor
sudo chown pol-servidor:pol-servidor /opt/pol-servidor
```

## 2. Node 22 LTS

Ver primeiro o que os repositórios já dão — o Ubuntu 26.04 pode já trazer 22 ou mais recente, e
nesse caso não vale a pena acrescentar um repositório externo:

```bash
apt-cache policy nodejs
```

Se a versão candidata for ≥ 22:

```bash
sudo apt-get install -y nodejs npm
```

Caso contrário, NodeSource:

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt-get install -y nodejs
```

Confirmar: `node -v` tem de dar `v22.` ou superior (`package.json` exige `>=22`).

## 3. Caddy

```bash
sudo apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt-get update && sudo apt-get install -y caddy
```

## 4. Firewall — antes do Caddy arrancar

O Let's Encrypt valida pela porta 80; sem ela aberta o certificado nunca é emitido. A **2567 não
se abre**: quem fala com o Node é o Caddy, por `localhost`.

```bash
sudo ufw allow 80/tcp comment 'ACME + redireccionamento para HTTPS'
sudo ufw allow 443/tcp comment 'Caddy'
sudo ufw status verbose
```

## 5. Ambiente e unidade systemd

```bash
sudo install -m 640 -o root -g pol-servidor /dev/null /etc/pol-servidor.env
echo 'PORT=2567' | sudo tee /etc/pol-servidor.env
```

Copiar os dois ficheiros desta pasta:

```bash
sudo cp deploy/pol-servidor.service /etc/systemd/system/pol-servidor.service
sudo cp deploy/Caddyfile /etc/caddy/Caddyfile
sudo systemctl daemon-reload
sudo systemctl enable pol-servidor.service
sudo systemctl reload caddy
```

Se o `node` não estiver em `/usr/bin/node` (confirma com `command -v node`), corrigir o
`ExecStart` da unidade — o systemd não procura no `PATH`.

## 6. Levar o código e arrancar

Do Mac, na raiz do repositório:

```bash
./servidor/deploy/publicar.sh ubuntu@<ip-do-vps>
```

O script faz rsync para `/tmp`, instala em `/opt/pol-servidor` com o dono certo, corre
`npm ci --omit=dev` e reinicia o serviço. Não usa git: o VPS não tem — nem precisa de ter —
credencial para este repositório privado.

## 7. Aceitação

Os três, com o comando exacto e o que tem de sair:

```bash
# 1. responde de fora, com TLS válido (o -v mostra o emissor; falharia se fosse autoassinado)
curl -sS https://perguntaoluso.duckdns.org/saude

# 2. recupera sozinho de um reinício
sudo systemctl restart pol-servidor.service && sleep 3 \
  && curl -sS https://perguntaoluso.duckdns.org/saude

# 3. a porta do Node NÃO está exposta — isto tem de falhar (timeout do ufw)
curl -sS --max-time 8 http://<ip-do-vps>:2567/saude ; echo "saida=$?"
```

O `/saude` nesta fase responde com `"banco":{"perguntas":0}` — é o esperado em modo esqueleto,
e é precisamente por o servidor escutar **antes** de tentar o Firebase que ele consegue
dizer isso em vez de morrer calado.

Um quarto, que confirma que o esqueleto não deixa entrar ninguém:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' \
  -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  https://perguntaoluso.duckdns.org/     # 401
```

## Diagnóstico

```bash
sudo systemctl status pol-servidor.service
sudo journalctl -u pol-servidor -n 50 --no-pager
sudo journalctl -u caddy -n 50 --no-pager     # emissão do certificado aparece aqui
```
