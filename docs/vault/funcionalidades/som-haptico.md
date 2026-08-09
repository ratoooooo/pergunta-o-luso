# Som e retorno háptico

← [índice](../00-indice.md)

`audio/SoundEffects.kt`. Seis efeitos: certo, errado, vitória, derrota, conquista, subida de nível.

## Como funciona

**`SoundPool`**, não `MediaPlayer`: descodifica uma vez ao arrancar, guarda o PCM em memória, e o
`play()` é só um comando para o mixer — **não bloqueia** e aguenta sons sobrepostos. Uma versão
anterior criava um `MediaPlayer` por som, o que descodificava na thread que chamava (a main
thread, no instante exacto em que o jogador responde).

Ligado a `USAGE_GAME` / `STREAM_MUSIC`, por isso **segue o volume de multimédia** do jogador; a
zero não se ouve nada. **Nunca escreve no volume do sistema** (uma versão anterior punha o volume
a 85 % antes de cada som, sem repor).

Não pede foco de áudio: são sons de 0,4–1,1 s e pedir foco baixaria a música que o jogador tenha
a tocar por trás.

Um som pedido antes de estar carregado é **descartado em silêncio** — nada espera por áudio.

## Silêncio e vibração

| Modo de campainha | Som | Vibração |
|---|---|---|
| NORMAL | sim | sim |
| VIBRAÇÃO | **sim** | sim |
| SILÊNCIO | não | não |

**O modo de campainha não governa multimédia no Android** — um telemóvel em vibração continua a
tocar Spotify, YouTube e jogos. Exigir `RINGER_MODE_NORMAL` deixava o jogo mudo no estado em que
muita gente traz o telemóvel o dia todo. Silêncio continua a calar tudo.

A vibração respeita também `Settings.System.HAPTIC_FEEDBACK_ENABLED` — chamar o `Vibrator`
diretamente ignora essa preferência, por isso é lida à mão. Exige a permissão `VIBRATE` no
manifesto (nível *normal*: concedida na instalação, sem prompt, sem acesso a dados); sem a
declarar, `vibrate()` lança `SecurityException`.

## Os sons

Sintetizados de raiz, **não são de stock**: sinusoide com 2.ª e 3.ª harmónicas fracas, ataque de
6 ms, decaimento exponencial — timbre de marimba/kalimba, a condizer com o registo sticker. Tudo
em pentatónica de Dó maior, para qualquer par que se sobreponha continuar consonante. Certo sobe,
errado desce e é mais fraco (assinala sem repreender), vitória é um arpejo a subir, derrota o
mesmo ao contrário. 22,05 kHz mono, **184 KB** ao todo.

## Quando tocam

O som segue o **estado**, não o toque (`LaunchedEffect(isAnswered, …)`) — por isso o **tempo
esgotado também soa**, que é precisamente quando o jogador não está a olhar para o ecrã.

No pódio os três sons possíveis são **encadeados, não sobrepostos**: resultado → +950 ms subida
de nível → +1150 ms conquista. Juntos eram um amontoado onde não se percebia que tinham
acontecido três coisas. Subida de nível e conquistas novas são detectadas por **comparação do
perfil antes e depois** de agregar a partida — sem campos novos. Sem perfil anterior a lista sai
vazia de propósito, senão um veterano a abrir a app ouvia a fanfarra de tudo o que já tinha.

## Armadilha de teste

Um emulador lançado com **`-no-audio`** parece funcionar por dentro: o `AudioFlinger` cria a
track, o mixer corre, o `play()` devolve um id válido — e o QEMU deita o som fora à saída. Um
`play()` que devolve um id **não é prova de som audível**.

A evidência utilizável é a thread de saída do `dumpsys media.audio_flinger`, que passa de
`Standby: yes` a `Standby: no` quando algo está mesmo a ser renderizado. Contar ocorrências de
`createTrack_l` no logcat **não serve**: o `SoundPool` cria a `AudioTrack` uma vez e reutiliza-a.

```bash
# relançar um emulador com áudio
$ANDROID_HOME/emulator/emulator -avd <NOME> -no-snapshot-load
```

Ver também: [modos-de-jogo](modos-de-jogo.md) · [firebase](../arquitetura/firebase.md)
