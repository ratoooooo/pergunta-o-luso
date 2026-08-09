package com.ratoooooo.perguntaoluso.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.annotation.RawRes
import com.ratoooooo.perguntaoluso.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Efeitos sonoros curtos + retorno háptico.
 *
 * ### Porquê `SoundPool` e não `MediaPlayer`
 *
 * A primeira versão deste ficheiro criava um `MediaPlayer` **a cada** som. `MediaPlayer.create()`
 * abre o recurso e descodifica-o na thread que chama — e quem chama é a composição, ou seja, a
 * main thread, no instante exacto em que o jogador responde. O `SoundPool` descodifica **uma vez**
 * ao arrancar, guarda o PCM em memória e o `play()` é só um comando para o mixer: não bloqueia, e
 * aguenta sons sobrepostos (acertar e subir de nível ao mesmo tempo).
 *
 * ### O que este objecto nunca faz
 *
 * A versão anterior chamava `setStreamVolume(STREAM_MUSIC, 85%)` antes de cada som — mexia no
 * volume do telemóvel do jogador, e sem o repor. Isto **nunca** toca no volume do sistema. O
 * `SoundPool` está ligado a `USAGE_GAME`/`STREAM_MUSIC`, por isso segue o volume de multimédia
 * que o jogador escolheu; a zero, não se ouve nada, como deve ser.
 *
 * Também não pede foco de áudio: são sons de 0,4–1,1 s e pedir foco baixaria a música que o
 * jogador tenha a tocar por trás, o que é pior do que misturar.
 *
 * ### Silêncio e vibração
 *
 * - **Som** só em `RINGER_MODE_NORMAL`. Em silêncio ou vibração, nada toca.
 * - **Vibração** em normal e vibração, nunca em silêncio, e só se o jogador tiver o retorno
 *   háptico ligado nas definições (`HAPTIC_FEEDBACK_ENABLED`) — chamar o `Vibrator` diretamente
 *   ignora essa preferência, por isso é lida à mão.
 *
 * ### Nunca bloquear
 *
 * O carregamento é assíncrono. Um som pedido antes de estar pronto é **descartado em silêncio**;
 * nada espera por áudio nenhum. Na prática só pode acontecer nos primeiros instantes depois de
 * abrir a app, muito antes de haver uma pergunta no ecrã.
 */
object SoundEffects {

    enum class Efeito(@RawRes val recurso: Int, internal val haptico: Haptico) {
        CERTO(R.raw.correct, Haptico.TOQUE_LEVE),
        ERRADO(R.raw.wrong, Haptico.TOQUE_FORTE),
        VITORIA(R.raw.victory, Haptico.DUPLO),
        DERROTA(R.raw.defeat, Haptico.TOQUE_LEVE),
        CONQUISTA(R.raw.achievement, Haptico.DUPLO),
        SUBIU_NIVEL(R.raw.levelup, Haptico.DUPLO)
    }

    internal enum class Haptico(val duracaoMs: Long) {
        TOQUE_LEVE(25),
        TOQUE_FORTE(90),
        DUPLO(0) // padrão, ver [vibrar]
    }

    private const val MAX_STREAMS = 4

    @Volatile
    private var pool: SoundPool? = null

    /** Efeito -> sampleId do SoundPool. */
    private val amostras = ConcurrentHashMap<Efeito, Int>()

    /** sampleIds já descodificados e prontos a tocar. */
    private val prontos = ConcurrentHashMap.newKeySet<Int>()

    /**
     * Carrega as amostras. Idempotente — chamar duas vezes não duplica nada.
     * Feito no arranque ([com.ratoooooo.perguntaoluso.MainActivity]) para o primeiro som do
     * jogo já estar descodificado.
     */
    fun init(context: Context) {
        if (pool != null) return
        synchronized(this) {
            if (pool != null) return
            val atributos = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val novo = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(atributos)
                .build()
            novo.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) prontos.add(sampleId)
            }
            val app = context.applicationContext
            Efeito.entries.forEach { efeito ->
                runCatching { novo.load(app, efeito.recurso, 1) }
                    .getOrNull()
                    ?.let { amostras[efeito] = it }
            }
            pool = novo
        }
    }

    /**
     * Toca [efeito] e faz vibrar, conforme as regras acima. Seguro em qualquer thread e
     * seguro antes de [init] (nesse caso inicializa primeiro).
     */
    fun tocar(context: Context, efeito: Efeito) {
        runCatching {
            init(context)
            if (podeTocarSom(context)) {
                val id = amostras[efeito]
                if (id != null && prontos.contains(id)) {
                    pool?.play(id, 1f, 1f, 1, 0, 1f)
                }
            }
            vibrar(context, efeito.haptico)
        }
    }

    /** Liberta o pool. Chamado quando a Activity morre para valer. */
    fun libertar() {
        synchronized(this) {
            pool?.release()
            pool = null
            amostras.clear()
            prontos.clear()
        }
    }

    /**
     * Som em tudo **menos** no modo silencioso.
     *
     * Exigia `RINGER_MODE_NORMAL`, e isso estava errado: no Android o modo de campainha manda
     * nos toques e nas notificações, **não** no áudio de multimédia. Um telemóvel em vibração —
     * que é como muita gente o traz o dia todo — continua a tocar Spotify, YouTube e jogos. Com
     * a regra antiga o jogo ficava mudo nesse estado, e a queixa era "os sons não funcionam".
     *
     * Silêncio continua a calar o jogo: aí o pedido de respeitar o silêncio do sistema é
     * inequívoco. O volume de multimédia é tratado pelo próprio `SoundPool`, que está em
     * `STREAM_MUSIC` — a zero não se ouve nada sem ser preciso verificar aqui.
     */
    private fun podeTocarSom(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audio.ringerMode != AudioManager.RINGER_MODE_SILENT
    }

    private fun podeVibrar(context: Context): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null && audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return false
        val ligado = Settings.System.getInt(
            context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1
        )
        return ligado != 0
    }

    @SuppressLint("MissingPermission") // VIBRATE é normal-level, declarada no manifesto
    private fun vibrar(context: Context, haptico: Haptico) {
        if (!podeVibrar(context)) return
        val vibrador = obterVibrador(context) ?: return
        if (!vibrador.hasVibrator()) return
        val efeito = when (haptico) {
            Haptico.DUPLO -> VibrationEffect.createWaveform(longArrayOf(0, 30, 70, 45), -1)
            else -> VibrationEffect.createOneShot(haptico.duracaoMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        runCatching { vibrador.vibrate(efeito) }
    }

    private fun obterVibrador(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
