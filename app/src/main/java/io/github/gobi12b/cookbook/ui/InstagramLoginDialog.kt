package io.github.gobi12b.cookbook.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.gobi12b.cookbook.data.InstagramSession

/**
 * Full-screen Instagram sign-in. Closes itself as soon as Instagram sets its session cookie.
 * The password goes straight to instagram.com; the app only keeps the WebView's cookies.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramLoginDialog(onConnected: () -> Unit, onDismiss: () -> Unit) {
    val connected by rememberUpdatedState(onConnected)
    var progress by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }

    fun check() {
        if (!done && InstagramSession.isConnected()) {
            done = true
            CookieManager.getInstance().flush()
            connected()
        }
    }

    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Dialogs get their own window: match status-bar icon colour to our header.
        val view = LocalView.current
        val lightBars = !C.Dark
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = lightBars
                WindowCompat.getInsetsController(w, view).isAppearanceLightNavigationBars = lightBars
            }
        }
        Column(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Close, "Close", onClick = onDismiss)
                Column(Modifier.weight(1f)) {
                    T("Connect Instagram", size = 18.sp, weight = FontWeight.Bold)
                    T("So your cookbook can read reel titles & photos", size = 12.sp, color = C.TextDim, maxLines = 1)
                }
            }
            if (progress < 1f) LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = C.Accent, trackColor = C.Bg,
            ) else Box(Modifier.height(2.dp))
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        // Must be MATCH_PARENT: with Compose's default WRAP_CONTENT the page sees a 0-px-tall
                        // viewport, and Instagram's `height: 100%` layout collapses into a blank screen.
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) = check()
                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = check()
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, p: Int) { progress = p / 100f; if (p > 60) check() }
                        }
                        loadUrl(InstagramSession.LOGIN_URL)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
