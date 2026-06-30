package com.mdaopay.app.core.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// ponytail: Security wrapper delegating to caller's WebViewClient.
// HTTPS-only enforcement + domain whitelist + file/content access blocking.
// F-038 fix: whitelist domains, allowFileAccess=false, shouldOverrideUrlLoading.
private class SecureWebViewClient(delegate: WebViewClient) : WebViewClient() {
    private val d: WebViewClient = delegate

    // F-038: Domain whitelist — extend as needed
    private val allowedDomains = setOf(
        "app.mdaopay.xyz",
        "mdaopay.xyz",
        "api.mdaopay.com",
    )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return d.shouldOverrideUrlLoading(view, request)
        // Block non-HTTPS URLs (blocks file://, content://, http://, etc.)
        if (uri.scheme?.lowercase() != "https") return true
        val host = uri.host?.lowercase() ?: return true
        // Allow only whitelisted domains
        if (host !in allowedDomains && allowedDomains.none { host.endsWith(".$it") }) return true
        return d.shouldOverrideUrlLoading(view, request)
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        val lower = url.lowercase()
        if (!lower.startsWith("https://")) return true
        // Extract host and check whitelist
        val host = try {
            android.net.Uri.parse(url)?.host?.lowercase()
        } catch (_: Exception) { null } ?: return true
        if (host !in allowedDomains && allowedDomains.none { host.endsWith(".$it") }) return true
        return d.shouldOverrideUrlLoading(view, url)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) =
        d.onPageStarted(view, url, favicon)

    override fun onPageFinished(view: WebView?, url: String?) =
        d.onPageFinished(view, url)

    override fun onReceivedError(
        view: WebView?, request: WebResourceRequest?, error: WebResourceError?
    ) = d.onReceivedError(view, request, error)

    override fun onReceivedHttpError(
        view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?
    ) = d.onReceivedHttpError(view, request, errorResponse)

    override fun onReceivedSslError(
        view: WebView?, handler: SslErrorHandler?, error: SslError?
    ) = d.onReceivedSslError(view, handler, error)

    override fun onReceivedClientCertRequest(view: WebView?, request: ClientCertRequest?) =
        d.onReceivedClientCertRequest(view, request)

    override fun onReceivedHttpAuthRequest(
        view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?
    ) = d.onReceivedHttpAuthRequest(view, handler, host, realm)

    override fun onLoadResource(view: WebView?, url: String?) =
        d.onLoadResource(view, url)

    override fun onPageCommitVisible(view: WebView?, url: String?) =
        d.onPageCommitVisible(view, url)

    override fun onFormResubmission(view: WebView?, dontResend: Message?, resend: Message?) =
        d.onFormResubmission(view, dontResend, resend)

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) =
        d.doUpdateVisitedHistory(view, url, isReload)

    override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) =
        d.onScaleChanged(view, oldScale, newScale)

    override fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent?) =
        d.onUnhandledKeyEvent(view, event)

    override fun shouldOverrideKeyEvent(view: WebView?, event: KeyEvent?): Boolean =
        d.shouldOverrideKeyEvent(view, event)

    override fun onReceivedLoginRequest(
        view: WebView?, realm: String?, account: String?, args: String?
    ) = d.onReceivedLoginRequest(view, realm, account, args)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MDAOWebView(
    url: String,
    modifier: Modifier = Modifier,
    webViewClient: WebViewClient = WebViewClient(),
    onWebViewCreated: (WebView) -> Unit = {}
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                // F-038: block dangerous access
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.allowFileAccessFromFileURLs = false
                settings.allowUniversalAccessFromFileURLs = false
                // HTTPS-only enforcement via secure wrapper
                this.webViewClient = SecureWebViewClient(webViewClient)
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        modifier = modifier
    )
}
