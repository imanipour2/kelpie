package com.kelpie.browser.agent.execution

import com.kelpie.browser.handlers.formFillHelpersScript
import com.kelpie.browser.handlers.observationPrivacyScript
import com.kelpie.browser.handlers.selectorActivationScript
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Fixed WebView scripts; CSS selectors and input are JSON-quoted data, never program code. */
internal object R5WebViewActionScripts {
    private fun q(text: String) = Json.encodeToString(text)

    val SNAPSHOT = """
        (() => {${observationPrivacyScript()}
          const uniqueSelector=e=>{
          const parts=[];let node=e;
          for(let depth=0;node&&node.nodeType===1&&depth<24;depth++){
            const parent=node.parentElement;let part=node.localName;
            const allowed='html body main section article nav header footer aside div span p form fieldset legend '+
              'label a button input textarea select option optgroup ul ol li table thead tbody tfoot tr th td '+
              'details summary dialog h1 h2 h3 h4 h5 h6';
            if(!allowed.split(' ').includes(part))return '';
            if(parent){let same=0,index=0;
              for(const sibling of parent.children){
                if(sibling.localName===node.localName){
                  same++;if(sibling===node)index=same;}}
              if(same>1)part+=':nth-of-type('+index+')';}
            parts.unshift(part);
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
              tag:['a','button','input','textarea','select','div','span'].includes(e.localName)?e.localName:'generic',
              name:kelpieSafeName(e).slice(0,120),
              selector:(()=>{const selector=uniqueSelector(e);return kelpieSafeText(selector)===selector?selector:'';})(),
              role:kelpieSafeRole(e),type:kelpieSafeType(e)})),
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
        (()=>{try {
          if(document.querySelectorAll(${q(selector)}).length!==1) return 'DENIED';
          const outcome=${selectorActivationScript(selector)};
          if(outcome && !outcome.error) return 'DISPATCHED';
          if(outcome && outcome.error==='needs_human') return 'DENIED_SENSITIVE';
          if(outcome && outcome.error==='uncertain') return 'UNCERTAIN';
          return 'DENIED';
        } catch(_) {return 'DENIED';}})()
        """

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
        actionToken: String,
    ): String =
        """
        (()=>{${formFillHelpersScript()}
          ${mutationProofScript(actionToken)}
          const selector=${q(selector)}, value=${q(value)};
          let matches;try {matches=document.querySelectorAll(selector);}catch(_){return false;}
          if(matches.length!==1) return false;
          const e=matches[0];
          if(!(e instanceof HTMLSelectElement)||e.multiple) return false;
          const found=[...e.options].filter(o=>!o.disabled&&!o.closest('optgroup:disabled')&&
            (o.value===value||o.text.trim()===value));
          if(found.length!==1||!kelpieRememberActionTarget(e,found[0]))return false;
          const outcome=kelpieFillElement(selector,found[0].value);
          const current=document.querySelectorAll(selector);
          if(!outcome||outcome.error||!e.isConnected||current.length!==1||current[0]!==e||
            e.selectedOptions.length!==1||e.selectedOptions[0]!==found[0]||kelpieSensitiveAction(e)){
            kelpieRetireActionProof();return false;}
          kelpieArmActionProof();return true;})()
        """

    fun verifySelect(
        selector: String,
        value: String,
        actionToken: String,
    ): String = verifyMutationScript(selector, value, actionToken, "select")

    fun waitFor(selector: String?): String =
        if (selector == null) {
            "document.readyState === 'complete'"
        } else {
            """
            (()=>{try {const matches=document.querySelectorAll(${q(selector)});
              if(matches.length!==1)return false;const e=matches[0],style=getComputedStyle(e);
              return e.isConnected&&e.getClientRects().length>0&&
                style.visibility==='visible'&&style.display!=='none';}catch(_){return false;}})()
            """
        }

    fun fill(
        selector: String,
        value: String,
        actionToken: String,
    ): String =
        """
        (()=>{${formFillHelpersScript()}
          ${mutationProofScript(actionToken)}
          const selector=${q(selector)}, value=${q(value)};
          let matches;try {matches=document.querySelectorAll(selector);}catch(_){return false;}
          if(matches.length!==1)return false;const e=matches[0];
          if(!(e instanceof HTMLInputElement||e instanceof HTMLTextAreaElement)||
            ['password','file','hidden','submit','image','button','reset','checkbox','radio'].includes(e.type))return false;
          if(!kelpieRememberActionTarget(e,null))return false;
          const outcome=kelpieFillElement(selector,value);
          const current=document.querySelectorAll(selector);
          if(!outcome||outcome.error||!e.isConnected||current.length!==1||current[0]!==e||kelpieSensitiveAction(e)){
            kelpieRetireActionProof();return false;}
          kelpieArmActionProof();return true;})()
        """

    fun verifyFill(
        selector: String,
        value: String,
        actionToken: String,
    ): String = verifyMutationScript(selector, value, actionToken, "fill")

    fun scroll(
        x: Double,
        y: Double,
    ) = "(()=>{window.scrollBy($x,$y);return true;})()"

    const val SCROLL = "JSON.stringify({x:scrollX,y:scrollY})"
}
