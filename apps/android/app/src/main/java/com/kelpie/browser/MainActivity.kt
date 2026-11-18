package com.kelpie.browser

class MainActivity : KelpieBrowserActivity() {
    override fun enablePrivateCaptureQa(): Boolean = com.unlikeotherai.kelpie.app.BuildConfig.PRIVATE_CAPTURE_QA_ENABLED

    override fun enableAgentConsentQa(): Boolean = com.unlikeotherai.kelpie.app.BuildConfig.R5_NATIVE_CONSENT_QA_ENABLED

    override fun enableR5BackgroundAgentQa(): Boolean = com.unlikeotherai.kelpie.app.BuildConfig.R5_BACKGROUND_AGENT_QA_ENABLED

    override fun requireR5FirstEntryConsent(): Boolean =
        com.unlikeotherai.kelpie.app.BuildConfig.R5_BACKGROUND_AGENT_QA_ENABLED &&
            com.unlikeotherai.kelpie.app.BuildConfig.LOCAL_HTTP_PORT == 18461

    private val updates get() = (application as KelpieApp).updates

    override fun onResume() {
        super.onResume()
        updates.attach(this)
    }

    override fun onPause() {
        updates.detach(this)
        super.onPause()
    }
}
