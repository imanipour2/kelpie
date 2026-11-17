package com.kelpie.browser.account

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/**
 * The only Android dispatch boundary for the app-owned UOA OAuth request.
 * Google credentials, browser cookies and OAuth pages never enter a Kelpie WebView.
 */
internal object UOAExternalAuthLauncher {
    /** Returns false instead of creating an embedded login if no browser can launch. */
    fun launch(activity: ComponentActivity, requestedUrl: String): Boolean {
        if (!AccountLoginPolicy.isAuthorizeUrl(requestedUrl)) return false
        val url = Uri.parse(requestedUrl)

        // Android 11+ needs CustomTabsService visibility in the merged manifest.
        val provider =
            try {
                CustomTabsClient.getPackageName(activity, emptyList())
            } catch (_: SecurityException) {
                null
            }

        if (!provider.isNullOrBlank()) {
            try {
                val tab = CustomTabsIntent.Builder().build()
                tab.intent.setPackage(provider)
                tab.launchUrl(activity, url)
                return true
            } catch (_: ActivityNotFoundException) {
                // Browser may have changed after provider discovery.
            } catch (_: SecurityException) {
                // No WebView fallback: allow only the OS external browser route.
            }
        }

        val external = Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            activity.startActivity(external)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
