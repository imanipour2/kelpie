package com.kelpie.virtualdisplayprobe

/**
 * Offline document used to test whether ONE WebView survives moving between
 * the Activity window and an app-owned private Presentation.
 * Never loads real websites, cookies, credentials, or external resources.
 */
internal object SameWebViewFixture {
    fun html(): String =
        """
        <!doctype html>
        <html lang="fa" dir="rtl">
          <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              html, body { margin: 0; padding: 0; }
              body { height: 2400px; background: #f4f7fa; font: 18px sans-serif; }
              #color-box {
                position: fixed; z-index: 999; width: 100px; height: 100px;
                left: calc(50% - 50px); top: calc(50% - 50px);
                background: rgb(48,48,48); pointer-events: none;
              }
              input { margin: 12px; width: 80%; padding: 8px; }
            </style>
          </head>
          <body>
            <input id="preserved-input" aria-label="Probe input" value="">
            <p>Same WebView / same DOM / same history / same scroll.</p>
            <div id="color-box" aria-hidden="true"></div>
            <script>
              window.kelpieSeed = function() {
                if (window.__kelpieSeeded) return;
                window.__kelpieSeeded = true;
                window.__kelpieDocumentNonce = 'document-kept-' + Math.random().toString(36).slice(2);
                document.getElementById('preserved-input').value = 'retained-offline-form';
                history.pushState({ marker: 'retained-history-state' }, '', '#same-view');
                window.scrollTo(0, 340);
              };
              window.kelpieProof = function() {
                return JSON.stringify({
                  documentNonce: window.__kelpieDocumentNonce || '',
                  input: document.getElementById('preserved-input').value,
                  historyMarker: (history.state && history.state.marker) || '',
                  scrollY: Math.round(window.scrollY)
                });
              };
              window.setProbe = function(sequence, red, green, blue) {
                document.getElementById('color-box').style.backgroundColor =
                  'rgb(' + red + ',' + green + ',' + blue + ')';
                window.__kelpieProbeSequence = sequence;
                return sequence;
              };
            </script>
          </body>
        </html>
        """.trimIndent()
}
