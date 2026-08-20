# Eliminação de conta

← [índice](../00-indice.md)

Exigida pela Play Store: qualquer app que permita criar conta tem de oferecer eliminação **dentro
da app**, sem passar por suporte. `data/AccountDeletionRepository.kt`.

## UI

Botão **Eliminar conta** no fim do Perfil, Coral cheio — o único elemento do ecrã nessa cor.
Terminar sessão é recuperável, isto não é, e as duas não podem parecer a mesma classe de ação;
por isso ficam separadas por um divisor e 28 dp.

Aparece **também para jogadores anónimos** — o perfil, o histórico e os quizzes deles são dados
pessoais na mesma, e a Play não distingue.

O diálogo enumera exactamente o que desaparece e exige que se **escreva `ELIMINAR`** para
desbloquear o botão (que fica Neutral até lá). Um segundo botão "tem a certeza?" aceita-se por
reflexo; escrever a palavra obriga a ler.

## A purga

Todos os caminhos são recolhidos primeiro e escritos numa **única `updateChildren` a partir da
raiz**. A RTDB valida cada folha em separado, por isso ou a limpeza inteira entra ou não entra
nenhuma — nunca há um estado com o perfil apagado e as arestas de amizade a apontar para ele.

| Item | Caminho |
|---|---|
| Perfil agregado | `jogadores/{uid}` |
| Presença | `presenca/{uid}` |
| Histórico | `scores/{k}` para cada registo com o `uid` (filtrado no cliente — não há índice) |
| Amigos, meu lado | `amigos/{uid}/{lista,pedidosEnviados,pedidosRecebidos}/{outro}` |
| Amigos, lado do outro | `amigos/{outro}/{...}/{uid}` |
| Convites, ambos os lados | `convites/{uid}/{...}` e `convites/{outro}/{...}/{uid}` |
| Quizzes criados | `categorias_comunitarias/{id}` onde `criadorUid === uid` |

**Dois detalhes que as rules impuseram ao desenho:**

1. **Não dá para apagar `/amigos/{uid}` nem `/convites/{uid}` inteiros** — nenhum tem `.write` ao
   seu próprio nível, só nos sub-caminhos. Um `"amigos/$uid" to null` seria negado. Apaga-se
   aresta a aresta e a RTDB deita fora o pai quando o último filho sai.
2. **O lado do outro jogador limpa-se sem o ler.** `/amigos/{outro}` só é legível pelo dono — e
   não é preciso: as **minhas** listas já nomeiam todas as contrapartes, e as rules deixam cada
   parte remover-se das estruturas da outra. Apagar um caminho inexistente é inócuo, por isso
   apagam-se às cegas as três arestas por contraparte. Sem isto o amigo ficava com uma entrada
   morta: um nome a apontar para um uid sem perfil.

## Ordem e reautenticação

`purge()` corre **antes** de `FirebaseUser.delete()`: depois do delete o uid deixa de poder
escrever seja o que for e tudo o que ficasse para trás ficaria inalcançável para sempre.

O Firebase recusa `delete()` com sessão antiga (`FirebaseAuthRecentLoginRequiredException`). Esse
caso é apanhado e o diálogo **fica aberto**, agora com campo de palavra-passe, em vez de fechar
sem explicação. A purga é idempotente, por isso repeti-la na retentativa é inofensivo.

**Janela conhecida:** se a reautenticação for pedida e o utilizador desistir aí, os dados já
saíram mas a conta continua a existir, vazia. É recuperável (basta repetir). Fechar isto exigiria
reautenticar *antes* da purga, o que obrigaria a pedir a palavra-passe a toda a gente.

## Quizzes: eliminados, não anonimizados

Anonimizar preservaria conteúdo que outros possam ter gostado, mas exigia mexer na regra de
propriedade — `.write` em `categorias_comunitarias/$catId` exige `criadorUid === auth.uid`, e com
o uid extinto o quiz ficaria **permanentemente não-editável e não-moderável**.

## O ramo de reautenticação — exercitado a 9 ago 2026

Conta e-mail/palavra-passe criada de propósito (`reauth1@starforge.test`, ligada por
`linkWithCredential` a uma sessão anónima que já tinha 2 jogos), deixada a repousar ~15 min — o
Firebase exige sessão com menos de ~5 min para `delete()`. Depois: Perfil → Eliminar conta →
escrever `ELIMINAR` → ELIMINAR DEFINITIVAMENTE. Observado:

1. O diálogo **ficou aberto**, com campo **Palavra-passe** novo e a mensagem
   *"Por segurança, confirma a palavra-passe para concluir."* O botão principal deixou de estar
   Coral. Nada fechou sem explicação.
2. A **janela conhecida reproduziu-se tal e qual**: naquele instante `jogadores/{uid}`,
   `presenca/{uid}` e os 2 registos em `/scores` já não existiam, e a conta **continuava** no
   Auth. A purga tinha corrido, o `delete()` não.
3. Palavra-passe correcta → reautenticou, purgou outra vez (idempotente, sem estoiro) e apagou a
   conta. A app voltou sozinha a uma sessão anónima limpa ("Convidado", 0 pontos, GRUMETE).
4. Confirmado por fora: `auth_get_users` devolve lista vazia para o e-mail, e
   `jogadores/`, `presenca/`, `amigos/`, `convites/` e `/scores` não têm nada com aquele uid.

**Duas coisas que ficaram por confirmar ou correram mal:**

- **Palavra-passe errada não foi testada.** Uma tentativa falhada podia atirar com o rate limit
  do Firebase para cima e deixar a conta a meio (dados apagados, conta viva); preferiu-se
  garantir o ramo principal. `friendlyAuthError` para credencial inválida continua sem
  observação.
- Numa das submissões apareceu, em inglês e cru, *"An internal error has occurred. [ unexpected
  end of stream on com.android.okhttp.Address@… ]"* — falha de transporte. Repetir com a mesma
  palavra-passe resolveu. `friendlyAuthError` não traduz este caso, e a app é toda em português.

## Por fazer

- **A Play Store exige também um URL web de pedido de eliminação**, além do fluxo na app, para o
  formulário Data Safety. Continua por fazer — não é trabalho de app.
- Mapear o erro de transporte acima (e a credencial inválida) em `friendlyAuthError`.

Ver também: [autenticacao](../arquitetura/autenticacao.md) · [rules](../arquitetura/rules.md)
