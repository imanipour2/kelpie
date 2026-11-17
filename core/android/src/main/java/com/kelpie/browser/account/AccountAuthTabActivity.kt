package com.kelpie.browser.account

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.browser.auth.AuthTabIntent

/**
 * Browser-owned OAuth for UOA, with the owner's current browser credential store.
 *
 * No Google login WebView is created and no credentials/cookies are copied to Kelpie.
 * For browsers without Auth Tab, AndroidX falls back to Custom Tabs. An OS browser
 * callback can also reach AccountCallbackActivity; UOA's attempt/PKCE state remains
 * the single authority for redeeming a code at most once.
 */
class AccountAuthTabActivity : ComponentActivity() {
    private val launcher =
        AuthTabIntent.registerActivityResultLauncher(this) { result ->
            val nonce = intent.getStringExtra(EXTRA_NONCE)
            if (result.resultCode == AuthTabIntent.RESULT_OK && result.resultUri != null) {
                setResult(
                    Activity.RESULT_OK,
                    Intent().setData(result.resultUri).putExtra(EXTRA_NONCE, nonce),
                )
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nonce = intent.getStringExtra(EXTRA_NONCE)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_NONCE, nonce))
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null || !AccountLoginPolicy.isAuthorizeUrl(url) || nonce.isNullOrBlank()) {
            finish()
            return
        }

        setContentView(TextView(this).apply { text = "Continue sign-in in your browser" })
        if (savedInstanceState != null) return // Do not duplicate browser windows on Activity recreation.
        try {
            AuthTabIntent.Builder()
                .build()
                .launch(launcher, Uri.parse(url), "com.unlikeotherai.kelpie")
        } catch (_: ActivityNotFoundException) {
            openSystemBrowser(url)
        } catch (_: UnsupportedOperationException) {
            openSystemBrowser(url)
        }
    }

    private fun openSystemBrowser(url: String) {
        // The externally delivered callback is still verified by UOAAuthorization.
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            // The Activity intentionally remains until the browser flow returns or the user closes it.
        } catch (_: ActivityNotFoundException) {
            finish()
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NONCE = "nonce"
    }
}
