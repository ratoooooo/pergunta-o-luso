# Relatório Detalhado de Alterações e Correções

Este documento resume de forma exaustiva todas as alterações efetuadas no projeto **Pergunta ó Luso** para a resolução dos problemas de permissão em partidas multijogador (1x1, 2x2 e Grupo), a criação de conta no emulador e o bloqueio da orientação do ecrã em modo vertical (Portrait).

---

## 1. Bloqueio de Rotação do Ecrã (Modo Vertical / Portrait)

### Problema
A aplicação permitia a rotação do ecrã conforme o movimento do dispositivo, o que podia desformatar a interface de jogo (UI em Compose) desenhada exclusivamente para orientação vertical.

### Soluções Aplicadas
1. **Configuração no Manifesto (`AndroidManifest.xml`)**:
   Adicionado a propriedade `android:screenOrientation="portrait"` na declaração da `MainActivity`:
   ```xml
   <activity
       android:name=".MainActivity"
       android:exported="true"
       android:screenOrientation="portrait"
       android:theme="@style/Theme.PerguntaOLuso">
   ```
2. **Forçamento Programático (`MainActivity.kt`)**:
   No ciclo de vida `onCreate`, forçou-se a orientação por código para garantir que, mesmo em versões de Android com overrides de sistema, o ecrã permaneça fixo em Portrait:
   ```kotlin
   requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
   ```

---

## 2. Correção de Erros de Permissão no Multijogador (1x1, 2x2 e Grupo)

### Diagnóstico Técnico
Ao iniciar um jogo nos modos **1x1**, **2x2** ou **Grupo**, o cliente executa uma transação de leitura/escrita no caminho `/lobbies/$format` (em `MultiMatchRepository.kt`).

A regra de segurança original em `database.rules.json` para o nó `lobbies` validava o campo `hostUid` com a seguinte instrução:
```json
"hostUid": { ".validate": "newData.isString() && newData.parent().child('membros').child(newData.val()).exists()" }
```

**O que causava a falha:**
- Quando uma sala de espera era cancelada ou abandonada por todos os utilizadores, o mapa `membros` ficava vazio (`{}`), contudo o registo do nó `/lobbies/$format/$lobbyId` permanecia temporariamente na Realtime Database com a propriedade `hostUid`.
- Quando um novo utilizador tentava entrar ou criar uma nova sala de espera, a transação enviava o estado global de `/lobbies/$format` (que incluía as salas canceladas sem membros).
- O motor de segurança do Firebase validava **todos** os nós descendentes e falhava na sala cancelada sem membros, pois `membros.child(hostUid).exists()` retornava `false`.
- A transação inteira era então rejeitada pelo Firebase com a mensagem:
  `RepoOperation: Transaction at /lobbies/$format failed: DatabaseError: Permission denied`

### Alterações Aplicadas

1. **Atualização das Regras de Segurança (`database.rules.json`)**:
   A validação de `hostUid` foi ajustada para autorizar salas sem membros ou com o estado `cancelled`:
   ```json
   "hostUid": { 
     ".validate": "newData.isString() && (!newData.parent().child('membros').exists() || newData.parent().child('estado').val() === 'cancelled' || newData.parent().child('membros').child(newData.val()).exists())" 
   }
   ```
2. **Publicação das Regras no Firebase**:
   As regras foram validadas e publicadas no Firebase através do Firebase CLI:
   `npx -y firebase-tools@latest deploy --only database`
   
3. **Limpeza de Nós Vazios (`MultiMatchRepository.kt`)**:
   No método `leaveLobby`, alterou-se a transação para eliminar por completo o nó da sala (`data.value = null`) quando `remaining.isEmpty()`, impedindo a acumulação de salas órfãs na base de dados.

---

## 3. Criação e Autenticação de Conta no Emulador

Usando os comandos ADB no emulador Android ligado (`emulator-5554`), foi criada e autenticada a seguinte conta de testes:

- **Nome de utilizador:** `JogadorLuso`
- **E-mail:** `luso.jogador@example.com`
- **Palavra-passe:** [não documentada aqui por segurança]

> A conta foi **desativada** no Firebase Auth depois dos testes (mesmo procedimento das contas
> `teste1-4@starforge.test` na Fase 22). Credenciais de teste nunca devem ficar em texto
> simples num ficheiro do repositório.

A conta foi validada com sucesso, acumulando pontos e permitindo aceder diretamente a todos os modos de jogo (Solo, 1x1, 2x2 e Grupo).

---

## 4. Ficheiros Modificados

1. [app/src/main/AndroidManifest.xml](file:///Users/dinisrato/StudioProjects/StarForge/app/src/main/AndroidManifest.xml) — Bloqueio da orientação para `portrait`.
2. [app/src/main/java/com/ratoooooo/perguntaoluso/MainActivity.kt](file:///Users/dinisrato/StudioProjects/StarForge/app/src/main/java/com/ratoooooo/perguntaoluso/MainActivity.kt) — Definição programática de `requestedOrientation`.
3. [database.rules.json](file:///Users/dinisrato/StudioProjects/StarForge/database.rules.json) — Ajuste da regra de validação `hostUid` sob `/lobbies/$format/$lobbyId`.
4. [app/src/main/java/com/ratoooooo/perguntaoluso/data/MultiMatchRepository.kt](file:///Users/dinisrato/StudioProjects/StarForge/app/src/main/java/com/ratoooooo/perguntaoluso/data/MultiMatchRepository.kt) — Remoção automática de nós de salas sem membros no método `leaveLobby`.
