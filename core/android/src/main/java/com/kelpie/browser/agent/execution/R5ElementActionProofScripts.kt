package com.kelpie.browser.agent.execution

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Command-local DOM identity, supplementing the native document/renderer lease.
 * A single slot holds no input value, expires after ten seconds and is consumed
 * before verification. Replacing a node with an equal-valued clone cannot pass.
 */
internal fun mutationProofScript(actionToken: String): String =
    """
    const kelpieProofKey='__kelpieR5CommandElementProof';
    let kelpieRetireActionProof=()=>{}, kelpieArmActionProof=()=>{};
    function kelpieRememberActionTarget(target, option) {
      const prior=Object.getOwnPropertyDescriptor(window,kelpieProofKey);
      if(prior&&!prior.configurable)return false;
      if(prior&&typeof prior.value==='function'){
        const timer=Object.getOwnPropertyDescriptor(prior.value,'expiryTimer');
        if(timer&&Number.isInteger(timer.value))clearTimeout(timer.value);
      }
      const token=${Json.encodeToString(actionToken)}, started=performance.now();
      const identity=()=>[target.tagName,target.type,target.getAttribute('role'),
        target.getAttribute('name'),target.getAttribute('aria-label'),target.getAttribute('placeholder'),
        target.getAttribute('autocomplete')];
      const expected=identity();let armed=false,expiryTimer;
      const retire=()=>{
        const current=Object.getOwnPropertyDescriptor(window,kelpieProofKey);
        if(current&&current.value===proof)delete window[kelpieProofKey];
        clearTimeout(expiryTimer);
      };
      const proof=(suppliedToken,selector,kind,value)=>{
        const current=Object.getOwnPropertyDescriptor(window,kelpieProofKey);
        if(suppliedToken!==token||!current||current.value!==proof)return false;
        retire();
        const age=performance.now()-started;
        if(!armed||!Number.isFinite(age)||age<0||age>10000)return false;
        const matches=document.querySelectorAll(selector);
        if(matches.length!==1||matches[0]!==target||!target.isConnected||
          identity().some((item,index)=>item!==expected[index])||
          target.matches(':disabled')||target.closest('[inert]')||target.readOnly||
          target.getAttribute('aria-disabled')==='true'||kelpieSensitiveAction(target)||
          kelpieHiddenElement(target))return false;
        const rect=target.getBoundingClientRect();
        if(rect.width<=0||rect.height<=0)return false;
        const hit=document.elementFromPoint(rect.left+rect.width/2,rect.top+rect.height/2);
        if(!hit||!(hit===target||target.contains(hit)))return false;
        if(kind==='fill')return !option&&target.value===value;
        if(kind!=='select'||!(target instanceof HTMLSelectElement)||target.multiple||
          !option||!option.isConnected||option.disabled||option.closest('optgroup:disabled')||
          target.selectedOptions.length!==1||target.selectedOptions[0]!==option)return false;
        return option.value===value||option.text.trim()===value;
      };
      try {
        Object.defineProperty(window,kelpieProofKey,{value:proof,configurable:true,enumerable:false});
        expiryTimer=setTimeout(retire,10000);
        Object.defineProperty(proof,'expiryTimer',{value:expiryTimer});
      } catch(_) {retire();return false;}
      kelpieRetireActionProof=retire;
      kelpieArmActionProof=()=>{armed=true;};
      return true;
    }
    """.trimIndent()

internal fun verifyMutationScript(
    selector: String,
    value: String,
    actionToken: String,
    kind: String,
): String =
    """
    (()=>{try {
      const slot=Object.getOwnPropertyDescriptor(window,'__kelpieR5CommandElementProof');
      if(!slot||typeof slot.value!=='function')return false;
      return slot.value(${Json.encodeToString(actionToken)},${Json.encodeToString(selector)},
        ${Json.encodeToString(kind)},${Json.encodeToString(value)})===true;
    }catch(_){return false;}})()
    """.trimIndent()
