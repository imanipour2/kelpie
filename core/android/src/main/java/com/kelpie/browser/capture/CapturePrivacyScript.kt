package com.kelpie.browser.capture

/**
 * Bounded, ephemeral inspection of an already native-proven synthetic document.
 * This is NOT a classifier capable of certifying arbitrary web pixels as public.
 * No raw text, form value, URL, attributes or image leaves this inspection.
 */
internal fun capturePrivacyScript(): String =
    """
    (() => {
      const deny = reason => JSON.stringify({safe:false,reason});
      if (!document.body || document.readyState !== 'complete') return deny('DOCUMENT_NOT_READY');
      if (location.href !== 'https://kai-fixture.invalid/') return deny('DOCUMENT_IDENTITY_MISMATCH');
      if (!(innerWidth > 0 && innerHeight > 0)) return deny('ZERO_VIEWPORT');
      const nodes = [...document.querySelectorAll('*')];
      if (nodes.length > 2048) return deny('PRIVACY_BUDGET_EXCEEDED');
      if (document.querySelector('canvas,video,audio,img,svg,iframe,object,embed,applet')) return deny('OPAQUE_PIXELS');
      if (document.querySelector('[data-private],[data-sensitive],[aria-secret],input[type=password],'+
          'input[type=hidden],[autocomplete=one-time-code],[autocomplete^="cc-"]')) return deny('SENSITIVE_CONTENT');
      const fields = [...document.querySelectorAll('input,textarea,select,[contenteditable]')];
      if (fields.some(e => e.value || e.textContent || e.getAttribute('value'))) return deny('EDITABLE_CONTENT');
      const html = document.documentElement.outerHTML;
      if (html.length > 131072) return deny('PRIVACY_BUDGET_EXCEEDED');
      if (/\b(?:bearer\s+|(?:password|passwd|token|cookie|secret|otp)\s*[:=]\s*)[^\s,;]+/i.test(document.body.textContent) ||
          /\b[0-9][0-9 -]{11,22}[0-9]\b/.test(document.body.textContent)) return deny('SENSITIVE_TEXT');
      const scene = [html,innerWidth,innerHeight,scrollX,scrollY,devicePixelRatio];
      for (const node of nodes) {
        if (node.shadowRoot || node.localName.includes('-')) return deny('UNINSPECTABLE_SUBTREE');
        const style = getComputedStyle(node), rect = node.getBoundingClientRect();
        if (style.backgroundImage !== 'none' || style.maskImage && style.maskImage !== 'none' ||
            style.borderImageSource !== 'none' || style.listStyleImage !== 'none') return deny('OPAQUE_PIXELS');
        for (const pseudo of ['::before','::after']) {
          const p = getComputedStyle(node,pseudo);
          if (p.content && !['none','normal','""'].includes(p.content)) return deny('GENERATED_PIXELS');
          if (p.backgroundImage !== 'none') return deny('OPAQUE_PIXELS');
        }
        scene.push(rect.x,rect.y,rect.width,rect.height,style.color,style.backgroundColor,
          style.visibility,style.display,style.opacity,style.transform,style.font);
        for (const attr of ['href','src','action','formaction','poster']) {
          const raw = node.getAttribute(attr);
          if (raw) return deny('EXTERNAL_RESOURCE');
        }
      }
      const text = scene.join('|');
      if (text.length > 262144) return deny('PRIVACY_BUDGET_EXCEEDED');
      let hash = 2166136261;
      for (let i=0;i<text.length;i++) hash=Math.imul(hash^text.charCodeAt(i),16777619);
      return JSON.stringify({safe:true,timeOrigin:performance.timeOrigin,
        digest:(hash>>>0).toString(16).padStart(8,'0'),width:innerWidth,height:innerHeight,
        scrollX,scrollY,devicePixelRatio});
    })()
    """.trimIndent()
