# Histórico de vulnerabilidades

← [índice](../00-indice.md)

**Todas corrigidas.** Estão aqui porque o padrão que as causou repete-se — vale mais ler esta
nota antes de escrever rules novas do que redescobri-las.

## 1. Backdoor de auto-login por Intent

`MainActivity` lia `login_email` e `login_password` dos extras do Intent que a lançava, e o
`AuthGate` autenticava com eles em silêncio. Como `MainActivity` é `exported="true"`
(obrigatoriamente — é a LAUNCHER), **qualquer app instalada no dispositivo, ou qualquer pessoa
com adb, forçava a app a entrar numa conta arbitrária**:

```
adb shell am start -n com.ratoooooo.perguntaoluso/.MainActivity -e login_email X -e login_password Y
```

Credenciais em extras de Intent aparecem ainda por cima em logs e traces do sistema em várias
ROMs. Era um atalho de QA para automatizar screenshots.

**Corrigido:** removido por completo. **Regra:** automação de login tem de ficar atrás de
`BuildConfig.DEBUG` + `buildConfigField`, **nunca** a ler credenciais de um componente exportado.

## 2. Leitura pública de `/categorias` e `/jogadores`

Ambos eram `.read: true`. Qualquer pessoa com o URL da RTDB despejava o banco inteiro de
perguntas **com `respostaCorreta` incluída**, ou a tabela completa de jogadores (nomes, stats,
uids), sem sessão nenhuma.

**Corrigido:** `auth != null` nos dois. Obrigou a uma correção no código —
`CategoryRepository.loadCategories` faz um GET REST direto e não enviava credencial nenhuma;
passou a enviar o ID token em `auth=`. Sem isso o jogo ficava inutilizável no segundo ecrã.

## 3. Qualquer autenticado apagava o `/scores` de outro

`.write` era `auth != null`, e **o `.validate` não corre em apagamentos** — nada travava.

**Corrigido:** criar exige que o registo traga o próprio uid; alterar/apagar exige que o registo
**já** seja meu. **Regra:** um `.write` permissivo com `.validate` apertado não protege contra
apagamentos.

## 4. Reescrever o quiz de outra pessoa (Quizzes da Comunidade)

A regra era:

```
".write": "auth != null && (!data.exists()
           || data.child('criadorUid').val() === auth.uid
           || newData.child('votos').exists())"
```

A intenção era "deixar quem não é dono escrever apenas votos". Mas a condição está no nó `$catId`
**inteiro**, e `newData.child('votos').exists()` avalia sobre o **payload completo**: bastava
fazer um `set` na raiz do quiz com um payload fabricado que incluísse uma chave `votos`.
Consequência: **qualquer jogador reescrevia por completo o quiz de outro** — título, descrição,
perguntas — e até o `criadorUid`, falsificando a autoria.

**Corrigido:** `.write` no `$catId` exige dono; `votos/$uid` tem regra própria; os agregados são
escrevíveis mas **só nesses caminhos**; `criadorUid` validado contra `auth.uid`.

**Regra:** uma condição sobre `newData` no nó pai vê o payload todo. Permissões parciais têm de
ser expressas **no sub-caminho**, não no pai.

## 5. `/lobbies` sem dono nem schema

`.read`/`.write` a `auth != null` e mais nada — qualquer autenticado reescrevia qualquer sala.

**Corrigido:** schema fechado + controlo de dono por `.validate` (não `.write`, porque a
transação do matchmaking entra pelo nó do formato inteiro e regras por sala nunca seriam
avaliadas).

## 6. Nomear outra pessoa como anfitrião — e a regressão que a correção causou

Esta merece ser lida por inteiro. A regra deixava passar `hostUid` de outra pessoa desde que não
houvesse membros, ou o estado fosse `cancelled`. **Impacto não teórico:** o jogo só arranca
quando `lobby.hostUid == myUid`, por isso um lobby forjado com um anfitrião que nunca lá está
enche-se de jogadores reais e **nunca começa** — e o matchmaking encaminha gente para lá.
Bastavam alguns lobbies fantasma para travar o matchmaking aleatório. Negação de serviço.

A **primeira correção** exigia que o `hostUid` fosse `auth.uid` ou alguém em `membros`. Passou
8/8 nos testes por REST. **E partiu a app para toda a gente** — "Permission denied" ao escolher o
modo.

Razão: `findOrCreateLobby` corre uma transação sobre `lobbies/$formato` **inteiro** — reescreve
todas as salas do formato, e a RTDB revalida o `hostUid` de cada uma. Havia **6 lobbies órfãos**
de QA antigo (sem membros, `cancelled`, anfitriões em contas desativadas). Falhavam a validação, e
**uma folha inválida chumba a transação toda**.

A **correção certa** acrescenta uma terceira condição — *"o valor não mudou"*:

```
newData.isString() && (newData.val() === auth.uid
                    || newData.parent().child('membros').child(newData.val()).exists()
                    || data.val() === newData.val())
```

Carregar um valor inalterado é sempre seguro; forjar exige *criar* ou *alterar*.

> **A lição:** a primeira correção passou 8/8 porque foi testada **isoladamente**, com lobbies
> criados de propósito. O caminho real — uma transação de ascendente com dados alheios lá dentro
> — não estava coberto. **Regras revalidadas por transações de ascendente têm de ser testadas com
> lixo pré-existente na base de dados, não com um nó limpo.**

## 7. Ler as respostas de partidas alheias

`/multisalas/$salaId` tinha `.read: auth != null` — dava para listar uma sala qualquer e ler
`meta.perguntas`, com as `respostaCorreta`.

**Corrigido:** leitura só por membros (`meta.membrosNomes` tem de conter o `auth.uid`). Isto
partiu a entrada por código de sala, que não acrescentava o jogador a essa lista — corrigido
por sua vez.

## 8. Credenciais em texto simples na documentação

Duas vezes: a password partilhada de `teste1-4@starforge.test` no resumo técnico, e
`luso.jogador@example.com` com password no relatório de alterações. **Corrigidas** (substituídas
por `[não documentada aqui por segurança]`) e as contas desativadas no Firebase Auth.

**Por terminar:** as 4 contas `teste*@starforge.test` estão **desativadas mas não eliminadas** —
a CLI do Firebase não tem `auth:delete` e a via administrativa exigia extrair o refresh token do
config store, o que foi recusado por parecer exfiltração de credenciais. São 4 cliques na consola.

---

*Os achados seguintes são do pentest de 31 ago 2026 (ronda 2).*

## 9. Leitura pública de `/presenca`

`.read: true` — qualquer pessoa com o URL da RTDB via os UIDs online naquele instante, sem
credencial nenhuma. Verificado ao vivo: um GET sem token devolveu o UID de um jogador real.

**Corrigido:** `.read: "auth != null"`. Todos os consumidores reais passam pelo AuthGate antes de
ver o ecrã que mostra este dado.

## 10. `/jogadores` sem tectos numéricos

Os `.validate` de campos como `xpTotal`, `pontos`, `jogos`, `vitorias`, etc. só exigiam
`newData.isNumber()` — sem chão (`>= 0`) nem tecto. Verificado ao vivo: uma conta de teste
escreveu `xpTotal: 999 999 999 999`, `pontos: 123 456 789 012` e `jogos: -9999` — aceites
sem erro. A conta ficou no topo do ranking.

**Corrigido:** todos os campos numéricos de `/jogadores/$uid` e sub-nós (`modos`, `categorias`,
`multiVitorias`, `multiJogos`) têm agora `>= 0` e um tecto generoso que bloqueia valores
impossíveis sem estorvar anos de jogo real.

## 11. Dono de `/scores/$scoreId` podia alterar score e formato pós-jogo

O `.write` permitia ao dono de um registo existente qualquer operação (PATCH, PUT), não só apagar.
**Confirmado ao vivo com um jogo 1x1 real:**

1. Duas contas jogaram uma partida 1x1 no servidor de produção.
2. O dono fez `PATCH {"score": 5999}` no seu registo `formato: "1x1"` — **aceite**. O score real
   era 365.
3. O dono fez `PUT` com `formato: "solo"` e `score: 6000` — **aceite**. Recategorizou o registo
   de multijogador como solo, apagando a evidência de que era um 1x1.

**Corrigido:** a regra agora permite exactamente três caminhos: (1) `pol-servidor` pode tudo,
(2) criar um registo novo com o próprio uid, (3) **apagar** um registo que já é meu. Alterar
(`data.exists() && newData.exists()`) por um jogador é bloqueado.

## 12. Convites — resposta reescreve campos imutáveis

`convites/$uid/enviados/$outro` deixava o convidado (`$outro`) reescrever **todos** os campos do
convite (nome, salaId, formato, categoria, modo, ts) desde que tivesse um `recebidos` pendente.
O convidado só deveria poder mudar `estado` (de `pendente` para `aceite`/`recusado`).

**Corrigido:** quando `$outro` escreve com `newData`, os 6 campos imutáveis têm de manter o
valor que já está na base de dados. Só `estado` pode mudar.

## 13. `android:allowBackup="true"` no AndroidManifest

O backup automático do Android incluía o armazenamento local da app, que contém tokens de sessão
do Firebase Auth. Um `adb backup` seguido de `adb restore` noutro dispositivo poderia reutilizar
a sessão sem re-autenticação.

**Corrigido:** `android:allowBackup="false"`. Nenhuma funcionalidade da app depende de restauro
de backup.

Ver também: [rules](../arquitetura/rules.md) ·
[limitacoes-conhecidas](limitacoes-conhecidas.md) · [segredos-e-assinatura](segredos-e-assinatura.md)
