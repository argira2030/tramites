package eus.argira.tramites

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale

/**
 * Puente entre la web y el TextToSpeech de Android.
 *
 * La web (shim en index.html) llama a AndroidTts.speak(texto, velocidad, id) y AndroidTts.stop().
 * Esta clase responde en la página con:
 *   window.__ttsStart(id)  al empezar a hablar
 *   window.__ttsDone(id)   al terminar
 *   window.__ttsError(id)  si no hay voz o falla
 * Así la web puede seguir usando su código de speechSynthesis sin cambios.
 */
class TtsBridge(context: Context, private val webView: WebView) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    @Volatile private var initDone = false
    @Volatile private var pending: Triple<String, Double, String>? = null

    override fun onInit(status: Int) {
        initDone = true
        if (status != TextToSpeech.SUCCESS) {
            pending?.let { pending = null; notify("__ttsError", it.third) }
            return
        }
        var result = tts.setLanguage(Locale("es", "ES"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = tts.setLanguage(Locale("es"))
        }
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { notify("__ttsStart", baseId(it)) }
            }
            override fun onDone(utteranceId: String?) {
                // Solo avisamos al terminar el último trozo.
                if (utteranceId != null && !utteranceId.contains(SEP)) notify("__ttsDone", utteranceId)
            }
            @Deprecated("Requerido por la clase base")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { notify("__ttsError", baseId(it)) }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { notify("__ttsError", baseId(it)) }
            }
        })
        pending?.let { (t, r, id) -> pending = null; speak(t, r, id) }
    }

    @JavascriptInterface
    fun speak(text: String, rate: Double, id: String) {
        val safeId = id.filter { it.isLetterOrDigit() }.ifEmpty { "u" }
        if (!initDone) { pending = Triple(text, rate, safeId); return }
        if (!ready) { notify("__ttsError", safeId); return }
        val chunks = split(text)
        if (chunks.isEmpty()) { notify("__ttsDone", safeId); return }
        tts.setSpeechRate(rate.toFloat().coerceIn(0.5f, 2f))
        chunks.forEachIndexed { i, chunk ->
            val last = i == chunks.lastIndex
            tts.speak(
                chunk,
                if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                if (last) safeId else "$safeId$SEP$i"
            )
        }
    }

    @JavascriptInterface
    fun stop() { tts.stop() }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun baseId(utteranceId: String) = utteranceId.substringBefore(SEP)

    /** TextToSpeech admite unos 4000 caracteres por frase: troceamos por frases. */
    private fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        var rest = text.trim()
        while (rest.length > CHUNK) {
            var cut = rest.lastIndexOf(". ", CHUNK)
            if (cut < CHUNK / 2) cut = rest.lastIndexOf(' ', CHUNK)
            if (cut <= 0) cut = CHUNK
            out += rest.substring(0, cut + 1).trim()
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }

    private fun notify(fn: String, id: String) {
        webView.post {
            webView.evaluateJavascript("window.$fn && window.$fn('$id')", null)
        }
    }

    private companion object {
        const val CHUNK = 3500
        const val SEP = "~"
    }
}
