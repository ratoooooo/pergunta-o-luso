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

Ver também: [rules](../arquitetura/rules.md) ·
[limitacoes-conhecidas](limitacoes-conhecidas.md) · [segredos-e-assinatura](segredos-e-assinatura.md)
