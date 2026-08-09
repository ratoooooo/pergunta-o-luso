# Firebase e identidade da app

← [índice](../00-indice.md)

## Projeto

- **Projeto Firebase:** `supermercado-tia-lucia` — um projeto vazio já existente, reutilizado
  porque a criação de projetos novos estava bloqueada pela quota da conta. O nome não tem nada
  a ver com este jogo; é herança, não engano.
- **RTDB:** `supermercado-tia-lucia-default-rtdb`
- **Config:** `app/google-services.json` — **não está no repositório** (contém API key e app id).
  Cada programador gera o seu; ver o README.

## Identidade da app

**`applicationId` = `namespace` = `com.ratoooooo.perguntaoluso`**

É a identidade permanente na Play Store: **não pode mudar depois de publicada** (mudá-la obriga
a ficha nova, com instalações e avaliações a zero). Por isso foi mudada antes de submeter, a
partir do `com.starforge.app` herdado do nome de trabalho do projeto.

- App Android no Firebase: "Pergunta o Luso", `1:516301571634:android:74f3383aba01795aa2de2b`
- `versionCode` 1 / `versionName` "1.0" — a app nunca foi publicada, não há linha de versões a
  preservar.
- `rootProject.name` continua `PerguntaOLuso`; `@string/app_name` é "Pergunta ó Luso".

Nada do lado dos dados mudou com o rename: as rules são todas indexadas por `auth.uid`, nunca
pelo package, por isso contas e progresso existentes continuaram válidos.

## Dependências e permissões

Deliberadamente mínimas — é o que sustenta a política de privacidade:

- `firebase-database-ktx`, `firebase-auth-ktx`. **Sem analytics, sem publicidade, sem Crashlytics.**
- Permissões: `INTERNET`, `ACCESS_NETWORK_STATE` (vêm das libs por merge de manifesto) e
  `VIBRATE` (declarada à mão — ver [som-haptico](../funcionalidades/som-haptico.md)).

## Pontas soltas conhecidas

- A app Android antiga (`com.starforge.app`) **continua registada no projeto Firebase**, por
  pedido, para poder ser confirmada antes de remover. Enquanto lá estiver, reaparece no
  `google-services.json` a cada `apps:sdkconfig`. O plugin escolhe o bloco certo em build, por
  isso a coexistência é inofensiva.
- As rules de `/matchmakingN` continuam publicadas mas o caminho é **código morto** — ver
  [multiplayer](../funcionalidades/multiplayer.md).

Ver também: [rtdb-schema](rtdb-schema.md) · [rules](rules.md) · [autenticacao](autenticacao.md)
