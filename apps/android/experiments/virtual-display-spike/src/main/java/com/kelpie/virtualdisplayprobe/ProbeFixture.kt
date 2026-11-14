package com.kelpie.virtualdisplayprobe

/**
 * Offline-only test fixture. Never displays a user page or transfers credentials.
 * The opaque nonce is encoded into flat RGB pixels so the image can be checked
 * without OCR, storing screenshots or trusting evaluateJavascript alone.
 */
internal object ProbeFixture {
    fun colorFor(sequence: Int): Triple<Int, Int, Int> {
        require(sequence in 0..4095)
        return Triple(
            48 + ((sequence * 31) % 160),
            48 + ((sequence * 47) % 160),
            48 + ((sequence * 71) % 160),
        )
    }

    fun html(): String =
        """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              html,body { width:100%;height:100%;margin:0;padding:0;
                background:rgb(48,48,48);overflow:hidden; }
              #nonce { position:absolute;top:10px;left:10px;
                color:#fff;background:#222;font:18px monospace; }
            </style>
          </head>
          <body>
            <div id="nonce">initial</div>
            <script>
              window.setProbe = function(seq,r,g,b) {
                const color = 'rgb(' + r + ',' + g + ',' + b + ')';
                document.documentElement.style.background = color;
                document.body.style.background = color;
                document.getElementById('nonce').textContent = String(seq);
                window.__kelpieProbeSequence = seq;
                return String(seq);
              };
            </script>
          </body>
        </html>
        """.trimIndent()
}
