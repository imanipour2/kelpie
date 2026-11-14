package com.kelpie.browser.qa

/** Synthetic RTL document for an actual Kelpie TabStore WebView; no network resources. */
internal object P2OfflineFixture {
    const val BASE = "https://fixture.invalid/p2/"

    fun colorFor(sequence: Int): Triple<Int, Int, Int> {
        require(sequence in 1..20)
        return Triple(48 + (sequence * 31) % 160, 48 + (sequence * 47) % 160, 48 + (sequence * 71) % 160)
    }

    fun html(): String =
        """
        <!doctype html>
        <html lang="fa" dir="rtl">
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>body{margin:0;height:2400px;background:#f4f7fa;font:18px sans-serif}
        #color-box{position:fixed;z-index:999;width:100px;height:100px;
        left:calc(50% - 50px);top:calc(50% - 50px);background:rgb(48,48,48);
        pointer-events:none}input{margin:12px;padding:8px;width:80%}</style></head>
        <body><input id="probe-input" aria-label="مقدار آزمایشی">
        <p>آزمون آفلاین Kelpie P2 — یک تب واقعی از مدیریت تب مرورگر</p>
        <div id="color-box" aria-hidden="true"></div>
        <script>
        window.kelpieSeed=function(){
          if(window.__p2seeded)return;
          window.__p2seeded=true;
          window.__p2nonce='fixture-'+Math.random().toString(36).slice(2);
          document.getElementById('probe-input').value='state-kept';
          history.pushState({marker:'p2-history'},'','#p2');
          window.scrollTo(0,340);
        };
        window.kelpieProof=function(){
          return JSON.stringify({nonce:window.__p2nonce||'',
            input:document.getElementById('probe-input').value,
            history:(history.state&&history.state.marker)||'',
            scrollY:Math.round(window.scrollY)});
        };
        window.setProbe=function(n,r,g,b){
          document.getElementById('color-box').style.backgroundColor=
            'rgb('+r+','+g+','+b+')';
          return n;
        };
        </script></body></html>
        """.trimIndent()
}
