package com.mdaopay.app.core.blockchain

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import com.mdaopay.app.core.blockchain.NetworkConfig.CHAIN_ID
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-059 fix: Secure Ethereum JS bridge with origin validation.
 *
 * - Injects _MDAOBridge only for trusted origins
 * - Tracks navigation and removes bridge on untrusted pages
 * - Requires user confirmation via AlertDialog for signing operations
 * - shows origin in the confirmation dialog
 */
@Singleton
class EthereumProviderInjector @Inject constructor(
    private val walletManager: WalletManager
) {
    // ponytail: Trusted dApp origins — extend as needed
    private val allowedOrigins: Set<String> = setOf(
        "https://app.mdaopay.xyz",
        "https://mdaopay.xyz",
    )

    // Track bridges by WebView identity (reference equality, not hashCode)
    private val bridges = IdentityHashMap<WebView, SecureEthereumBridge>()

    /**
     * Inject Ethereum bridge into WebView.
     * Blocks injection if current URL origin is not in allowedOrigins.
     */
    fun inject(webView: WebView) {
        val url = webView.url ?: return
        val origin = extractOrigin(url) ?: return
        if (origin !in allowedOrigins) {
            Log.w(TAG, "Blocked bridge injection for untrusted origin: $origin")
            return
        }

        // Remove stale bridge
        webView.removeJavascriptInterface(BRIDGE_NAME)
        bridges.remove(webView)

        val bridge = SecureEthereumBridge(
            context = webView.context,
            wallet = walletManager.getWalletData()
        )
        bridge.currentOrigin = origin
        bridges[webView] = bridge

        webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        webView.evaluateJavascript(INJECTION_SCRIPT, null)
        Log.d(TAG, "Bridge injected for origin: $origin")
    }

    /**
     * Call on every main-frame navigation.
     * - Trusted origin → update bridge's currentOrigin
     * - Untrusted origin → remove bridge entirely
     */
    fun onNavigation(webView: WebView, url: String?) {
        val origin = url?.let { extractOrigin(it) }
        val bridge = bridges[webView]

        if (origin != null && origin in allowedOrigins) {
            bridge?.currentOrigin = origin
            Log.d(TAG, "Bridge origin updated: $origin")
        } else {
            // Navigated away from trusted origin → remove bridge
            webView.removeJavascriptInterface(BRIDGE_NAME)
            bridges.remove(webView)
            Log.w(TAG, "Bridge removed on navigation to: ${url.orEmpty()}")
        }
    }

    /**
     * Extract scheme://host[:port] from URL.
     * Returns null for invalid/malformed URLs.
     */
    private fun extractOrigin(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val port = uri.port
            if (port > 0 && port != 443 && port != 80) {
                "$scheme://$host:$port"
            } else {
                "$scheme://$host"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse origin from: $url", e)
            null
        }
    }

    // --- Secure bridge with origin validation ---

    // ponytail: inner class to access allowedOrigins for defense-in-depth validation in send()
    private inner class SecureEthereumBridge(
        private val context: Context,
        private val wallet: WalletData?,
    ) {
        @Volatile
        var currentOrigin: String? = null

        @JavascriptInterface
        fun send(request: String): String {
            val origin = currentOrigin ?: return """{"error":"Bridge not initialized"}"""
            // F-059: Re-validate origin against allowedOrigins on every call (defense-in-depth)
            if (origin !in allowedOrigins) {
                logW("Blocked request from untrusted origin: $origin")
                return """{"error":"Origin not allowed"}"""
            }

            val req = try { JSONObject(request) } catch (e: Exception) {
                return """{"error":"Invalid request"}"""
            }
            val method = req.optString("method", "")
            val params = req.optJSONArray("params") ?: JSONArray()
            val id = req.optString("id", "0")

            return try {
                val result = when (method) {
                    "eth_requestAccounts", "eth_accounts" -> handleAccounts(id, origin)
                    "eth_chainId" -> handleChainId()
                    "personal_sign" -> handlePersonalSign(id, params, origin)
                    "eth_sendTransaction" -> handleSendTransaction(params, origin)
                    "eth_blockNumber" -> """{"jsonrpc":"2.0","id":"$id","result":"0x0"}"""
                    "net_version" -> """{"jsonrpc":"2.0","id":"$id","result":"$CHAIN_ID"}"""
                    else -> """{"jsonrpc":"2.0","id":"$id","error":{"code":-32000,"message":"Method $method not supported"}}"""
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "RPC error: ${e.message}", e)
                """{"jsonrpc":"2.0","id":"$id","error":{"code":-32000,"message":"${e.message}"}}"""
            }
        }

        private fun handleAccounts(id: String, origin: String): String {
            if (wallet == null) return """{"jsonrpc":"2.0","id":"$id","result":[]}"""
            // ponytail: Show confirmation for account access from dApp
            if (!confirmAction(origin, "Share your wallet address with this dApp?")) {
                return """{"jsonrpc":"2.0","id":"$id","error":{"code":-32000,"message":"User rejected account access"}}"""
            }
            return """{"jsonrpc":"2.0","id":"$id","result":["${wallet.address}"]}"""
        }

        private fun handleChainId(): String {
            return """{"jsonrpc":"2.0","id":"1","result":"0x${CHAIN_ID.toString(16)}"}"""
        }

        private fun handlePersonalSign(id: String, params: JSONArray, origin: String): String {
            val wallet = wallet ?: return """{"jsonrpc":"2.0","id":"$id","error":{"code":-32000,"message":"Wallet not available"}}"""
            if (params.length() < 2) return """{"jsonrpc":"2.0","id":"$id","error":{"code":-32000,"message":"Missing parameters"}}"""

            val messageHex = params.optString(0, "")
            val messageUtf8 = try {
                if (messageHex.startsWith("0x")) {
                    String(Numeric.hexStringToByteArray(messageHex), Charsets.UTF_8)
                } else {
                    messageHex
                }
            } catch (_: Exception) {
                messageHex.take(200)
            }

            // Show confirmation with origin
            if (!confirmAction(origin, "Sign message:\n\n$messageUtf8")) {
                return """{"jsonrpc":"2.0","id":"$id","error":{"code":-32000,"message":"User rejected signing request"}}"""
            }

            val raw = if (messageHex.startsWith("0x")) Numeric.hexStringToByteArray(messageHex)
                      else messageHex.encodeToByteArray()

            val signatureData = Sign.signPrefixedMessage(raw, wallet.keyPair)
            val r = Numeric.toHexStringNoPrefix(signatureData.r)
            val s = Numeric.toHexStringNoPrefix(signatureData.s)
            val vByte = signatureData.v.firstOrNull()?.toInt()?.let { if (it < 27) it + 27 else it } ?: 27
            val sig = "0x${r.padStart(64, '0')}${s.padStart(64, '0')}${vByte.toString(16).padStart(2, '0')}"
            return """{"jsonrpc":"2.0","id":"$id","result":"$sig"}"""
        }

        private fun handleSendTransaction(params: JSONArray, origin: String): String {
            if (params.length() < 1) return """{"jsonrpc":"2.0","id":"1","error":{"code":-32000,"message":"Missing tx params"}}"""
            if (wallet == null) return """{"jsonrpc":"2.0","id":"1","error":{"code":-32000,"message":"Wallet not available"}}"""
            val tx = params.getJSONObject(0)
            val to = tx.optString("to", "")
            val value = tx.optString("value", "0x0")

            if (!confirmAction(origin, "Send transaction to:\n$to\nValue: $value")) {
                return """{"jsonrpc":"2.0","id":"1","error":{"code":-32000,"message":"User rejected transaction"}}"""
            }

            // ponytail: eth_sendTransaction not yet supported — return error instead of fake hash
            return """{"jsonrpc":"2.0","id":"1","error":{"code":-32000,"message":"eth_sendTransaction not available — use native send flow"}}"""
        }

        /**
         * Show confirmation dialog on main thread and await user decision.
         * Returns true if user approved, false otherwise.
         */
        private fun confirmAction(origin: String, message: String): Boolean {
            val latch = CountDownLatch(1)
            val allowed = BooleanArray(1)

            Handler(Looper.getMainLooper()).post {
                AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle("Confirm Action")
                    .setMessage("Origin: $origin\n\n$message")
                    .setPositiveButton("Approve") { dialog, _ ->
                        allowed[0] = true
                        latch.countDown()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Reject") { dialog, _ ->
                        allowed[0] = false
                        latch.countDown()
                        dialog.dismiss()
                    }
                    .setOnCancelListener { latch.countDown() }
                    .setCancelable(false)
                    .show()
            }

            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
            return allowed[0]
        }

        // ponytail: TAG moved to outer class companion (inner class can't have companion)
        private fun logW(msg: String) = Log.w(BRIDGE_TAG, msg)
    }

    companion object {
        private const val BRIDGE_TAG = "SecureEthereumBridge"
        private const val BRIDGE_NAME = "_MDAOBridge"
        private const val TAG = "EthProvider"
        private val CHAIN_HEX = "0x${CHAIN_ID.toString(16)}"

        private val INJECTION_SCRIPT = """
(function() {
    if (window.ethereum) return;
    var pending = {};
    var counter = 0;

    window.ethereum = {
        isMetaMask: true,
        chainId: '$CHAIN_HEX',
        selectedAddress: null,

        request: function(args) {
            return new Promise(function(resolve, reject) {
                var msgId = 'mdao_' + (++counter);
                pending[msgId] = {resolve: resolve, reject: reject};
                var params = args.params ? JSON.stringify(args.params) : '[]';
                var req = JSON.stringify({id: msgId, method: args.method, params: JSON.parse(params)});
                try {
                    var resp = _MDAOBridge.send(req);
                    var parsed = JSON.parse(resp);
                    if (parsed.error) {
                        reject(new Error(parsed.error.message || JSON.stringify(parsed.error)));
                    } else {
                        window.ethereum.selectedAddress = parsed.result && Array.isArray(parsed.result) ? parsed.result[0] : null;
                        resolve(parsed.result);
                    }
                } catch(e) {
                    reject(e);
                }
            });
        },

        send: function(methodOrObj, paramsOrCb) {
            var args = typeof methodOrObj === 'string'
                ? {method: methodOrObj, params: paramsOrCb || []}
                : methodOrObj;
            return this.request(args);
        },

        on: function(event, fn) {
            if (!this._listeners) this._listeners = {};
            if (!this._listeners[event]) this._listeners[event] = [];
            this._listeners[event].push(fn);
        },

        removeListener: function(event, fn) {
            if (!this._listeners || !this._listeners[event]) return;
            this._listeners[event] = this._listeners[event].filter(function(l) { return l !== fn; });
        }
    };
})();
""".trimIndent()
    }
}
