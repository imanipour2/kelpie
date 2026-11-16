package com.kelpie.browser.agent.execution

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Fixed WebView scripts; CSS selectors and input are JSON-quoted data, never program code. */
internal object R5WebViewActionScripts {
    private fun q(text: String) = Json.encodeToString(text)

    const val SNAPSHOT = """
        (() => JSON.stringify({url:location.href,title:document.title,
          text:(document.body?.innerText||'').slice(0,28000),
          elements:[...document.querySelectorAll('a,button,input,textarea,select,[role=tab]')]
            .filter(e=>e.getClientRects().length).slice(0,160).map(e=>({
              tag:e.tagName.toLowerCase(),name:(e.getAttribute('aria-label')||
              e.getAttribute('placeholder')||e.innerText||'').slice(0,120),
              selector:e.id ? '#'+CSS.escape(e.id) : '',
              type:(e.getAttribute('type')||'').slice(0,24)})),
          scroll:{x:scrollX,y:scrollY}}))()
        """

    fun click(selector: String): String = """
        (()=>{const e=document.querySelector(${q(selector)});
          if(!e||!e.getClientRects().length) return 'DENIED';
          const t=(e.getAttribute('aria-label')||e.innerText||'').slice(0,200);
          if(e.closest('form')||e.matches('button,[type=submit],[type=password],[type=file]')||
            /delete|publish|payment|checkout|purchase|حذف|پرداخت|انتشار/i.test(t)) return 'DENIED';
          e.click(); return 'DISPATCHED';})()
        """

    fun fill(selector: String, value: String): String = """
        (()=>{const e=document.querySelector(${q(selector)});
          if(!e||!e.getClientRects().length||!
            (e instanceof HTMLInputElement||e instanceof HTMLTextAreaElement)) return false;
          const t=(e.type||'text').toLowerCase(), a=(e.autocomplete||'').toLowerCase();
          if(['password','file','hidden','submit','checkbox','radio'].includes(t)||
            a.startsWith('cc-')||a==='one-time-code') return false;
          const d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');
          if(!d?.set) return false;
          d.set.call(e,${q(value)});
          e.dispatchEvent(new Event('input',{bubbles:true}));
          e.dispatchEvent(new Event('change',{bubbles:true})); return true;})()
        """

    fun verifyFill(selector: String, value: String): String =
        "(()=>{const e=document.querySelector(${q(selector)});return !!e&&e.value===${q(value)};})()"

    fun scroll(x: Double, y: Double) =
        "(()=>{window.scrollBy($x,$y);return true;})()"

    const val SCROLL = "JSON.stringify({x:scrollX,y:scrollY})"
}
