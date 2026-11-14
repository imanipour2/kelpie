package com.kelpie.browser

import android.content.Intent
import android.os.Bundle
import com.kelpie.browser.network.KelpieRecoveryNotification
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

/** Optional standalone-window host; other apps may embed KelpieBrowserSession.view(). */
open class KelpieBrowserActivity : ComponentActivity() {
    private lateinit var browser: KelpieBrowserSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        KelpieRecoveryNotification.approveFromIntent(intent)
        browser = KelpieBrowserSession(this)
        setContentView(browser.view())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (KelpieRecoveryNotification.approveFromIntent(intent)) {
            // A SINGLE_TOP launch can deliver its Intent to an already resumed Activity.
            KelpieRecoveryNotification.activityResumed()
        }
    }

    override fun onResume() {
        super.onResume()
        KelpieRecoveryNotification.activityResumed()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::browser.isInitialized) browser.trimMemory()
    }
}
