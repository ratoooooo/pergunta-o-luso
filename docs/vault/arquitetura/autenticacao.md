# Autenticação

← [índice](../00-indice.md)

## Regra base: jogar nunca exige conta

`AuthGate` (em `AuthGate.kt`, envolve a app toda) bloqueia qualquer ecrã até
`AuthRepository.ensureSignedIn()` resolver:

- Se já existe `FirebaseAuth.currentUser` (a sessão persiste entre arranques pelo próprio SDK —
  nada é guardado à mão), usa-se essa.
- Caso contrário faz `signInAnonymously()` antes do primeiro frame de UI real. É rápido o
  suficiente para não haver ecrã de loading — vê-se o fundo creme por um instante e depois o Início.

**Todo o jogador tem sempre um uid.** É isso que permite às rules exigirem `auth != null` em todo
o lado sem estragar a experiência de quem nunca criou conta.

## Contas são opcionais e opt-in

- **Registar:** se a sessão atual é anónima, a credencial e-mail/password é **ligada** à sessão
  com `linkWithCredential` — o uid mantém-se e **todo o progresso anónimo transita** (scores +
  perfil agregado). Se não for anónima, cria conta nova. Password ≥ 8 caracteres e igual à
  confirmação.
- **Login:** entra numa conta existente (troca o uid; a sessão anónima descartável é deitada fora,
  com o progresso que tivesse).
- **Terminar sessão:** `signOutToAnonymous()` — sai e imediatamente restabelece uma sessão anónima
  nova, para a app continuar jogável. O botão só aparece no Perfil, nunca no Início, para não
  competir com as ações de jogo.
- **Reautenticação:** o Firebase recusa operações sensíveis com sessão antiga
  (`FirebaseAuthRecentLoginRequiredException`). Só a [eliminação de
  conta](../funcionalidades/eliminacao-conta.md) precisa disto.

O e-mail **nunca** é escrito na base de dados de jogo — só existe no Firebase Auth. O que é
público entre jogadores é nome, avatar, nível e estatísticas.

## Nota de segurança

Existiu um **backdoor de auto-login** por extras de Intent, removido. Está descrito em
[historico-vulnerabilidades](../seguranca/historico-vulnerabilidades.md) — vale a pena ler antes
de voltar a automatizar login para QA.

Ver também: [rules](rules.md) · [eliminacao-conta](../funcionalidades/eliminacao-conta.md)
