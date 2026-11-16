package com.kelpie.browser

class MainActivity : KelpieBrowserActivity() {
    override fun enablePrivateCaptureQa(): Boolean = com.unlikeotherai.kelpie.app.BuildConfig.PRIVATE_CAPTURE_QA_ENABLED

    override fun enableAgentConsentQa(): Boolean = com.unlikeotherai.kelpie.app.BuildConfig.R5_NATIVE_CONSENT_QA_ENABLED

    override fun enableAgentObserveQa(): Boolean = com.unlikeotherai.kelpie.app.BuildConfig.R5_OBSERVE_QA_ENABLED

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
