package com.kelpie.browser.agent.execution

import com.kelpie.browser.handlers.observationPrivacyScript
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Fixed WebView scripts; CSS selectors and input are JSON-quoted data, never program code. */
internal object R5WebViewActionScripts {
    private fun q(text: String) = Json.encodeToString(text)

    val SNAPSHOT = """
        (() => {${observationPrivacyScript()}
          const uniqueSelector=e=>{
          if(e.id){const byId='#'+CSS.escape(e.id);
            if(document.querySelectorAll(byId).length===1)return byId;}
          const parts=[];let node=e;
          for(let depth=0;node&&node.nodeType===1&&depth<24;depth++){
            const parent=node.parentElement;let part=node.localName;
            if(parent){let same=0,index=0;
              for(const sibling of parent.children){
                if(sibling.localName===node.localName){
                  same++;if(sibling===node)index=same;}}
              if(same>1)part+=':nth-of-type('+index+')';}
            if(node.id)part+='#'+CSS.escape(node.id);
            parts.unshift(part);
            if(node.id&&document.querySelectorAll('#'+CSS.escape(node.id)).length===1)break;
            node=parent;}
          const css=parts.join(' > ');
          if(css.length>500)return '';
          try{return document.querySelectorAll(css).length===1?css:'';}catch(_){return '';}};
        if(kelpiePrivacyBudgetExceeded) return JSON.stringify({url:null,title:'redacted',text:'redacted',
          elements:[],coverage:'INCOMPLETE',redacted:true,humanChallengeDetected:false});
        return JSON.stringify({url:kelpieSafeURL(location.href),title:kelpieSafeText(document.title),
          text:kelpieSafeNodeText(document.body).slice(0,28000),coverage:'INCOMPLETE',redacted:true,
          elements:[...document.querySelectorAll('a,button,input,textarea,select,[role=tab]')]
            .filter(e=>e.getClientRects().length).slice(0,160).map(e=>({
              tag:e.tagName.toLowerCase(),name:kelpieSafeName(e).slice(0,120),
              selector:(()=>{const selector=uniqueSelector(e);return kelpieSafeText(selector)===selector?selector:'';})(),
              role:(e.getAttribute('role')||'').slice(0,48),
              type:(e.getAttribute('type')||'').slice(0,24)})),
          scroll:{x:scrollX,y:scrollY},
          // Observation only: challenge ownership and answers stay with the
          // visited website / human. Never read a challenge token or bypass it.
          humanChallengeDetected:
            !!document.querySelector('[class*=captcha],[id*=captcha],.cf-turnstile')||
            [...document.querySelectorAll('iframe[src]')].some(f=>
              /recaptcha|hcaptcha|challenges\.cloudflare\.com|turnstile/i.test(f.src))})})()
        """

    // The agent controls ordinary navigation and explicitly non-sensitive buttons.
    // Unknown POST form submissions, secrets, finance and destructive actions
    // require an independently reviewed human authorization path (not supplied here).
    // These guards are defense-in-depth, not a universal semantic risk classifier.
    fun click(selector: String): String =
        """
        (()=>{const e=document.querySelector(__SEL__);
          if(!e||!e.getClientRects().length||!e.isConnected||e.disabled) return 'DENIED';
          const action=e.closest('button,[role=button],input[type=submit],a[href]')||e;
          const form=action.closest('form');
          const label=[action.getAttribute('aria-label')||'',action.getAttribute('value')||'',
            action.textContent||'',action.getAttribute('name')||''].join(' ').slice(0,600);
          const formIdentity=form?[form.getAttribute('action')||'',form.id||'',
            form.getAttribute('aria-label')||''].join(' '):'';
          const financial=/(payment|checkout|pay now|credit.card|card.number|bank.transfer|iban|wire.transfer|پرداخت|درگاه|بانک|رمز پویا|رمز دوم|شماره کارت|شماره شبا|انتقال وجه)/i;
          const destructive=/(delete.account|delete.all|delete.permanent|remove.account|publish|make.public|unsubscribe|حذف حساب|حذف دائم|انتشار عمومی)/i;
          const secret=!!action.closest('input[type=password],[autocomplete=one-time-code],[autocomplete^="cc-"]');
          const formHasSecrets=!!form&&!!form.querySelector('input[type=password],[autocomplete=one-time-code],[autocomplete^="cc-"]');
          const nonGetFormButton=!!form&&(form.method||'get').toLowerCase()!=='get'&&
            !!action.closest('button,[role=button],input[type=submit]');
          const anchor=action.closest('a[href]');
          const unsafeLink=!!anchor&&!/^https?:$/i.test(anchor.protocol);
          if(secret||formHasSecrets||nonGetFormButton||unsafeLink||financial.test(label)||
            financial.test(formIdentity)||destructive.test(label)||destructive.test(formIdentity))
            return 'DENIED_SENSITIVE';
          e.click();return 'DISPATCHED';})()
        """.replace("__SEL__", q(selector))

    // A compact one-way hash, not a dump of user input or a trusted claim of
    // causality. Two stable post-click observations are required by native code.
    const val DOCUMENT_FINGERPRINT = """
        (()=>{const body=document.body;if(!body)return '';
          const text=(body.innerText||'').slice(0,12000);
          const state=[...document.querySelectorAll('button,[role=button],[aria-expanded],'+
            '[aria-pressed],select,input[type=checkbox],input[type=radio]')]
            .slice(0,160).map(e=>[
              e.tagName,e.getAttribute('aria-expanded')||'',e.getAttribute('aria-pressed')||'',
              e.disabled?'1':'0',
              (e instanceof HTMLInputElement&&(e.type==='checkbox'||e.type==='radio'))?
                (e.checked?'1':'0'):'',
              (e instanceof HTMLSelectElement)?e.selectedIndex:''].join(':')).join('|');
          const s=text+'|'+state;let h=2166136261;
          for(let i=0;i<s.length;i++)h=Math.imul(h^s.charCodeAt(i),16777619);
          return String(h>>>0);})()
        """

    fun select(
        selector: String,
        value: String,
    ): String =
        """
        (()=>{const e=document.querySelector(${q(selector)});
          if(!(e instanceof HTMLSelectElement)||!e.getClientRects().length||e.disabled) return false;
          const found=[...e.options].filter(o=>!o.disabled&&(o.value===${q(value)}||o.text.trim()===${q(value)}));
          if(found.length!==1)return false;
          e.value=found[0].value;
          e.dispatchEvent(new Event('input',{bubbles:true}));
          e.dispatchEvent(new Event('change',{bubbles:true}));return true;})()
        """

    fun verifySelect(
        selector: String,
        value: String,
    ): String =
        """
        (()=>{const e=document.querySelector(${q(selector)});
          if(!(e instanceof HTMLSelectElement))return false;
          const selected=e.selectedOptions[0];
          return !!selected&&(selected.value===${q(value)}||selected.text.trim()===${q(value)});})()
        """

    fun waitFor(selector: String?): String =
        if (selector == null) {
            "document.readyState === 'complete'"
        } else {
            "(()=>{const e=document.querySelector(__SEL__);return !!e&&!!e.getClientRects().length;})()"
                .replace("__SEL__", q(selector))
        }

    fun fill(
        selector: String,
        value: String,
    ): String =
        """
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

    fun verifyFill(
        selector: String,
        value: String,
    ): String = "(()=>{const e=document.querySelector(${q(selector)});return !!e&&e.value===${q(value)};})()"

    fun scroll(
        x: Double,
        y: Double,
    ) = "(()=>{window.scrollBy($x,$y);return true;})()"

    const val SCROLL = "JSON.stringify({x:scrollX,y:scrollY})"
}
