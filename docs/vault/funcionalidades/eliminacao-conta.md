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

## Por fazer

- **O ramo de reautenticação nunca foi exercitado** — só é alcançável por contas
  e-mail/palavra-passe com sessão antiga; o teste correu com conta anónima. O código trata a
  exceção, mas isso é análise, não observação.
- **A Play Store exige também um URL web de pedido de eliminação**, além do fluxo na app, para o
  formulário Data Safety. Continua por fazer — não é trabalho de app.

Ver também: [autenticacao](../arquitetura/autenticacao.md) · [rules](../arquitetura/rules.md)
