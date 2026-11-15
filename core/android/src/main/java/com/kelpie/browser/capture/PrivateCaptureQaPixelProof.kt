package com.kelpie.browser.capture

/**
 * QA ONLY: force a verifiable, non-sensitive change to the public example.com page.
 *
 * Chromium may suppress plain invalidate() while a WebView is not visible.
 * A fresh ImageReader callback alone does not prove that the page changed.
 * The expected RGB pixel in the upper-left corner binds every returned frame
 * to the current single-use frame request. Never use on private/origin-unchecked pages.
 */
internal object PrivateCaptureQaPixelProof {
    private const val FIRST_RGB = 0x174BA3
    private const val COLOR_STEP = 0x010307

    fun rgb(sequence: Int): Int {
        require(sequence in 1..20)
        return (FIRST_RGB + COLOR_STEP * sequence) and 0xFFFFFF
    }

    fun matches(
        expectedRgb: Int,
        actualPixel: Int,
    ): Boolean = (actualPixel and 0xFFFFFF) == expectedRgb

    fun repaintScript(expectedRgb: Int): String {
        require(expectedRgb in 0..0xFFFFFF)
        val hex = expectedRgb.toString(16).padStart(6, '0')
        return """
            (() => {
                if (location.protocol !== 'https:' || location.hostname !== 'example.com' ||
                    (location.port && location.port !== '443')) return false;
                const id = '__kelpie_private_qa_pixel__';
                let marker = document.getElementById(id);
                if (!marker) {
                    marker = document.createElement('div');
                    marker.id = id;
                    marker.setAttribute('aria-hidden', 'true');
                    marker.style.cssText =
                        'position:fixed!important;left:0!important;top:0!important;' +
                        'width:80px!important;height:80px!important;z-index:2147483647!important;' +
                        'pointer-events:none!important;opacity:1!important;' +
                        'border:0!important;margin:0!important;padding:0!important;';
                    document.documentElement.appendChild(marker);
                }
                marker.style.setProperty('background-color', '#$hex', 'important');
                return true;
            })()
            """.trimIndent()
    }

    const val REMOVE_SCRIPT = "document.getElementById('__kelpie_private_qa_pixel__')?.remove()"
}
