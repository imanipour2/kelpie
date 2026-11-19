package com.kelpie.browser.handlers

/**
 * Privacy helpers for existing ephemeral DOM reads. No registry, listener, live DOM
 * mutation or authority is created. Detached copies prevent attribute/text exports
 * from recovering values that an element summary intentionally withholds.
 */
fun observationPrivacyScript(): String =
    """
    var kelpiePrivateSelector = 'input,textarea,select,[contenteditable]:not([contenteditable="false"]),' +
        '[data-sensitive],[data-private],[aria-secret="true"]';
    var kelpieSecrets = [];
    var kelpieSecretBytes = 0;
    var kelpiePrivacyBudgetExceeded = false;
    function kelpieRememberSecret(value) {
        if (value === null || value === undefined || value === '') return;
        var text = String(value);
        if (text.length > 16384 || kelpieSecrets.length >= 1024 || kelpieSecretBytes + text.length > 65536) {
            kelpiePrivacyBudgetExceeded = true;
            return;
        }
        if (kelpieSecrets.indexOf(text) < 0) {
            kelpieSecrets.push(text);
            kelpieSecretBytes += text.length;
        }
    }
    function kelpieRememberURLSecrets(raw) {
        try {
            var url = new URL(raw, location.href);
            url.searchParams.forEach(function(value) { kelpieRememberSecret(value); });
            url.pathname.split('/').filter(Boolean).forEach(function(value) {
                kelpieRememberSecret(value);
                try { kelpieRememberSecret(decodeURIComponent(value)); } catch (_) {}
            });
            if (url.hash) kelpieRememberSecret(url.hash.substring(1));
            kelpieRememberSecret(url.username);
            kelpieRememberSecret(url.password);
        } catch (_) {}
    }
    function kelpieMayCarryPrivateValue(node) {
        var nonTextTypes = ['checkbox','radio','button','submit','reset','image'];
        return node.tagName !== 'INPUT' || nonTextTypes.indexOf(String(node.type).toLowerCase()) < 0 ||
            !!node.closest('[data-sensitive],[data-private],[aria-secret]');
    }
    var kelpiePrivateNodes = Array.from(document.querySelectorAll(kelpiePrivateSelector));
    if (kelpiePrivateNodes.length > 512) kelpiePrivacyBudgetExceeded = true;
    kelpiePrivateNodes.slice(0, 512).forEach(function(node) {
        if (kelpieMayCarryPrivateValue(node)) {
            kelpieRememberSecret(node.value);
            kelpieRememberSecret(node.getAttribute('value'));
            if (node.tagName !== 'INPUT') kelpieRememberSecret(node.textContent);
        }
        kelpieRememberSecret(node.getAttribute('aria-label'));
        kelpieRememberSecret(node.getAttribute('placeholder'));
    });
    kelpieRememberURLSecrets(location.href);
    var kelpieURLNodes = Array.from(document.querySelectorAll('[href],[src],[action]'));
    if (kelpieURLNodes.length > 512) kelpiePrivacyBudgetExceeded = true;
    kelpieURLNodes.slice(0,512).forEach(function(node) {
        ['href','src','action'].forEach(function(name) {
            var raw = node.getAttribute(name);
            if (raw) kelpieRememberURLSecrets(raw);
        });
    });
    kelpieSecrets.sort(function(a, b) { return b.length - a.length; });

    function kelpieReplaceSecrets(text) {
        kelpieSecrets.forEach(function(secret) { text = text.split(secret).join('\u0000'); });
        return text.split('\u0000').join('redacted');
    }

    function kelpieSafeURL(raw) {
        try {
            var url = new URL(String(raw), location.href);
            if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
            var clean = url.origin;
            return kelpiePrivacyBudgetExceeded ? null : kelpieReplaceSecrets(clean);
        } catch (_) { return null; }
    }
    function kelpieSafeText(value) {
        if (kelpiePrivacyBudgetExceeded) return 'redacted';
        var text = String(value === null || value === undefined ? '' : value);
        if (text.length > 200000) return 'redacted';
        return kelpieReplaceSecrets(text).replace(/https?:\/\/[^\s<>"']+/gi, function(url) { return kelpieSafeURL(url) || 'redacted'; })
            .replace(/\b(?:bearer\s+|(?:password|passwd|token|cookie|secret|otp)\s*[:=]\s*)[^\s,;]+/gi, 'redacted')
            .replace(/\b[0-9][0-9 -]{11,22}[0-9]\b/g, 'redacted');
    }
    function kelpiePrivateElement(node) {
        return !!(node && node.nodeType === 1 && node.closest(kelpiePrivateSelector));
    }
    function kelpieHiddenElement(node) {
        for (var parent = node; parent && parent.nodeType === 1; parent = parent.parentElement) {
            var style = getComputedStyle(parent);
            if (parent.hidden || parent.getAttribute('aria-hidden') === 'true' || style.display === 'none' ||
                style.visibility === 'hidden' || style.visibility === 'collapse' || Number(style.opacity) === 0) return true;
        }
        return false;
    }
    function kelpieSafeTag(node) {
        var tag = node && node.tagName ? node.tagName.toLowerCase() : '';
        var known = 'a abbr address area article aside audio b base bdi bdo blockquote body br button canvas caption cite code '+
            'col colgroup data datalist dd del details dfn dialog div dl dt em embed fieldset figcaption figure footer form '+
            'h1 h2 h3 h4 h5 h6 head header hgroup hr html i iframe img input ins kbd label legend li link main map mark menu '+
            'meta meter nav noscript object ol optgroup option output p picture pre progress q rp rt ruby s samp script search '+
            'section select slot small source span strong style sub summary sup table tbody td template textarea tfoot th thead '+
            'time title tr track u ul var video wbr svg path g rect circle ellipse line polyline polygon text tspan defs symbol use';
        return known.split(' ').indexOf(tag) >= 0 ? tag : 'generic';
    }
    function kelpieSafeType(node) {
        var type = String(node && node.type || '').toLowerCase();
        return ['text','password','email','tel','number','search','url','hidden','file',
            'checkbox','radio','submit','button','reset','date','time','range','color',
            'select-one','select-multiple','textarea'].indexOf(type) >= 0 ? type : 'unknown';
    }
    function kelpieSafeRole(node) {
        var role = node && node.getAttribute('role');
        var roles = ['button','link','textbox','checkbox','radio','combobox','listbox','option','switch',
            'menu','menuitem','tab','tablist','tabpanel','slider','spinbutton','searchbox','heading','img',
            'dialog','alert','status','progressbar','list','listitem','table','row','cell','grid','gridcell',
            'navigation','main','form','region','group','generic','presentation','none'];
        if (roles.indexOf(role) >= 0) return role;
        var tag = node && node.tagName ? node.tagName.toLowerCase() : '';
        return ['a','button','input','select','textarea','div','span','p','img','svg','form','label',
            'ul','ol','li','table','tr','td','th','body','html','section','article','header','footer',
            'h1','h2','h3','h4','h5','h6'].indexOf(tag) >= 0 ? tag : 'generic';
    }
    function kelpieSensitiveAction(node) {
        if (!node || node.nodeType !== 1) return true;
        if (kelpiePrivacyBudgetExceeded) return true;
        var owner = node.closest('input,textarea,select,button,a,label,[role="button"],[role="link"],[contenteditable],[data-sensitive],[data-private]') || node;
        if (owner.tagName === 'LABEL' && owner.control) owner = owner.control;
        var type = String(owner.type || '').toLowerCase();
        var form = owner.form || owner.closest('form');
        if (owner.tagName === 'INPUT' && ['submit','image'].indexOf(type) >= 0) return true;
        if (owner.tagName === 'BUTTON' && type === 'submit' && form) return true;
        var anchor = owner.closest('a[href]');
        if (anchor && (!/^https?:$/i.test(anchor.protocol) || anchor.username || anchor.password)) return true;
        if (form && Array.from(form.elements).some(function(field) {
            return field.matches('input[type=password],[autocomplete="one-time-code" i],[autocomplete^="cc-" i]');
        })) return true;
        if (form && String(form.method || 'get').toLowerCase() !== 'get' &&
            owner.matches('button,[role=button],input[type=submit]')) return true;
        var root = owner.getRootNode();
        var labels = Array.from(owner.labels || []).map(function(label) { return label.textContent || ''; }).join(' ');
        var labelled = (owner.getAttribute('aria-labelledby') || '').split(/\s+/).map(function(id) {
            var label = root.getElementById ? root.getElementById(id) : null;
            return label ? label.textContent || '' : '';
        }).join(' ');
        var hints = ['type','autocomplete','name','id','aria-label','placeholder','href','formaction']
            .map(function(key) { return owner.getAttribute(key) || ''; }).join(' ') + ' ' + labels + ' ' + labelled + ' ' + owner.textContent;
        if (form && typeof form.getAttribute === 'function') {
            hints += ' ' + ['action','name','id','aria-label'].map(function(key) { return form.getAttribute(key) || ''; }).join(' ');
        }
        // Match the canonical snapshot sensitivity vocabulary; raw hints are never returned.
        var sensitiveCredentials = /password|one-time|otp|cc-|credit.?card|card.?number|cvv|cvc|token|cookie|secret|pin(?:$|[\s_-])|captcha|رمز|گذرواژه|کارت|امنیتی/i;
        var sensitiveOperations = /checkout|payment|transfer|bank|iban|delete|erase|remove|publish|sign.?in|log.?in|پرداخت|بانک|درگاه|شبا|انتقال|حذف|انتشار|ورود/i;
        var containsPrivateValue = kelpiePrivateNodes.some(function(field) {
            if (!kelpieMayCarryPrivateValue(field)) return false;
            var value = field.value || (field.isContentEditable ? field.textContent : '');
            return value && hints.indexOf(String(value)) >= 0;
        });
        return ['password','hidden','file'].indexOf(type) >= 0 ||
            /^(IFRAME|OBJECT|EMBED)$/.test(owner.tagName) ||
            !!owner.closest('[data-sensitive],[data-private],[aria-secret]') ||
            containsPrivateValue || sensitiveCredentials.test(hints) || sensitiveOperations.test(hints) ||
            /passwd|pay now|purchase|auth(?:entication|orization)?|کپچا/i.test(hints);
    }
    function kelpieSafeClone(node) {
        if (!node || node.nodeType !== 1 || kelpiePrivateElement(node) || kelpieHiddenElement(node) || kelpieSafeTag(node) === 'generic') return null;
        var copy = node.cloneNode(true);
        var originals = [node].concat(Array.from(node.querySelectorAll('*')));
        var copies = [copy].concat(Array.from(copy.querySelectorAll('*')));
        if (originals.length > 10000) return null;
        for (var i = originals.length - 1; i >= 0; i--) {
            var original = originals[i], target = copies[i];
            var style = getComputedStyle(original);
            if (kelpiePrivateElement(original) || kelpieSafeTag(original) === 'generic' || /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE|META|LINK|IFRAME|OBJECT|EMBED)$/.test(original.tagName) ||
                original.hidden || original.getAttribute('aria-hidden') === 'true' || style.display === 'none' ||
                style.visibility === 'hidden' || style.visibility === 'collapse' || Number(style.opacity) === 0) {
                if (i === 0) return null;
                target.remove();
                continue;
            }
            Array.from(target.attributes).forEach(function(attribute) { target.removeAttribute(attribute.name); });
            var attrs = kelpieSafeAttributes(original);
            Object.keys(attrs).forEach(function(name) { target.setAttribute(name, attrs[name]); });
        }
        // Redact decoded text before HTML serialization can entity-escape private values.
        var textNodes = document.createTreeWalker(copy, NodeFilter.SHOW_TEXT);
        var textNode;
        while ((textNode = textNodes.nextNode())) textNode.data = kelpieSafeText(textNode.data);
        return copy;
    }
    function kelpieSafeNodeText(node) {
        if (kelpiePrivateElement(node)) return 'redacted';
        var copy = kelpieSafeClone(node);
        return copy ? kelpieSafeText(copy.textContent || '').trim() : '';
    }
    function kelpieSafeName(node) {
        if (!node) return '';
        if (kelpieHiddenElement(node)) return '';
        if (kelpiePrivateElement(node)) return 'redacted';
        var title = node.tagName.toLowerCase() === 'svg' ? node.querySelector('title') : null;
        var label = node.getAttribute('aria-label') || node.getAttribute('alt') || (title ? kelpieSafeNodeText(title) : '');
        if (!label) {
            label = (node.getAttribute('aria-labelledby') || '').split(/\s+/).map(function(id) {
                var labelNode = document.getElementById(id);
                return labelNode ? kelpieSafeNodeText(labelNode) : '';
            }).join(' ').trim();
        }
        return kelpieSafeText(label || kelpieSafeNodeText(node)).trim();
    }
    function kelpieSafeSelector(node) {
        var selector = typeof kelpieBuildSelector === 'function' ? kelpieBuildSelector(node, true) : null;
        return selector && kelpieSafeText(selector) === selector ? selector : null;
    }
    function kelpieSafeAttributes(node) {
        var result = {};
        if (!node || node.nodeType !== 1) return result;
        ['disabled','required','readonly','multiple','checked','selected'].forEach(function(name) {
            if (node.hasAttribute(name)) result[name] = '';
        });
        ['aria-checked','aria-disabled','aria-expanded','aria-selected','aria-hidden'].forEach(function(name) {
            var value = node.getAttribute(name);
            if (['true','false','mixed'].indexOf(value) >= 0) result[name] = value;
        });
        if (node.hasAttribute('role')) result.role = kelpieSafeRole(node);
        if (node.tagName === 'INPUT') result.type = kelpieSafeType(node);
        if (!kelpiePrivateElement(node)) {
            var href = node.getAttribute('href');
            if (href) result.href = kelpieSafeURL(href) || 'redacted';
        }
        return result;
    }
    function kelpieSafeHTML(node) {
        var copy = kelpieSafeClone(node);
        return copy ? kelpieSafeText(copy.outerHTML) : '';
    }
    """.trimIndent()
