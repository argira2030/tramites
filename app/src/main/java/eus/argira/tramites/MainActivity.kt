package eus.argira.tramites

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader

/**
 * Trámites: aloja la web (assets/www/index.html) en un WebView sin conexión.
 * - El contenido se sirve desde https://appassets.androidplatform.net/ (WebViewAssetLoader), sin file://.
 * - Los enlaces web y de correo se abren en el navegador / correo del móvil, nunca dentro de la app.
 * - «Llamar» abre el marcador con el número escrito (ACTION_DIAL): no hace falta permiso y no llama solo.
 * - «Ver ubicación» abre la app de mapas si hay una; si no, el navegador.
 * - La voz usa el TextToSpeech nativo de Android (ver TtsBridge).
 * No es una app oficial de ninguna administración.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var tts: TtsBridge? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Iconos de las barras del sistema claros u oscuros según el modo del móvil.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.tr_bg))
        }
        setContentView(webView)

        // Deja la página fuera de las barras del sistema, del notch y del teclado.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<android.view.View>(android.R.id.content)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true   // ajustes y perfiles se guardan en el dispositivo (localStorage)
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                if (uri.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
                openExternal(uri)
                return true
            }
        }

        tts = TtsBridge(this, webView).also { webView.addJavascriptInterface(it, "AndroidTts") }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            webView.loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/www/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    /** Solo se admiten http, https, mailto, tel y el mapa; cualquier otro esquema se ignora. */
    private fun openExternal(uri: Uri) {
        when (uri.scheme) {
            "tel" -> start(Intent(Intent.ACTION_DIAL, uri))
            "mailto" -> start(Intent(Intent.ACTION_SENDTO, uri))
            "http", "https" -> {
                val query = mapQuery(uri)
                if (query != null) {
                    val geo = Uri.parse("geo:0,0?q=" + Uri.encode(query))
                    if (!start(Intent(Intent.ACTION_VIEW, geo))) start(Intent(Intent.ACTION_VIEW, uri))
                } else {
                    start(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
    }

    /** Si el enlace es una búsqueda de OpenStreetMap, devuelve la dirección para abrirla en la app de mapas. */
    private fun mapQuery(uri: Uri): String? {
        val host = uri.host ?: return null
        if (!host.endsWith("openstreetmap.org") || uri.path != "/search") return null
        return uri.getQueryParameter("query")?.takeIf { it.isNotBlank() }
    }

    private fun start(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false // Sin app para ese enlace: no hacemos nada.
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.tr_bg))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onStop() {
        tts?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        tts?.shutdown()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
