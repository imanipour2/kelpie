package com.kelpie.browser.handlers

fun annotationElementsScript(): String =
    """
    (function() {
        ${elementSelectorBuilderScript()}
        ${observationPrivacyScript()}
        var els = document.querySelectorAll('a,button,input,select,textarea,[role=button]');
        return Array.from(els).map(function(el) {
            var r = el.getBoundingClientRect();
            if (r.width <= 0 || r.height <= 0 || kelpieHiddenElement(el)) return null;
            return {
                role: kelpieSafeRole(el),
                name: kelpieSafeName(el).substring(0, 50),
                value: null, sensitivity: kelpiePrivateElement(el) ? 'private' : 'public',
                selector: kelpieSafeSelector(el),
                rect: {x: r.x, y: r.y, width: r.width, height: r.height}
            };
        }).filter(Boolean).slice(0, 50).map(function(item, index) {
            item.index = index;
            return item;
        });
    })()
    """.trimIndent()

fun selectorActivationScript(selector: String): String {
    val escaped = JSEscape.string(selector)
    val resolver =
        """
        (function() {
            var selector = '$escaped';
            var el = null;
            try { el = document.querySelector(selector); } catch (_) {}
            if (el) return document.querySelectorAll(selector).length === 1 ? el : {error: 'ambiguous_target'};
            var normalized = selector.trim().toLowerCase();
            if (!normalized) return null;
            var tags = 'a,button,input,select,textarea,' +
                '[role=button],[role=link],[role=menuitem]';
            var candidates = Array.from(document.querySelectorAll(tags));
            var textFor = function(node) {
                return kelpieSafeName(node).trim().toLowerCase();
            };
            var matches = candidates.filter(function(node) { return textFor(node) === normalized; });
            if (matches.length > 1) return {error: 'ambiguous_target'};
            return matches.length === 1 ? matches[0] : null;
        })()
        """.trimIndent()
    return activationScript(resolver = resolver, selector = selector)
}

@Suppress("UNUSED_PARAMETER")
fun annotationActivationScript(index: Int): String = "({error:'STALE_OBSERVATION',reason:'OBSERVATION_REQUIRED'})"

fun fillElementScript(
    selector: String,
    value: String,
): String =
    """
    (function() {
        ${formFillHelpersScript()}
        return kelpieFillElement('${JSEscape.string(selector)}', '${JSEscape.string(value)}');
    })()
    """.trimIndent()

/** One mutation implementation is shared by ordinary handlers and observed R5 actions. */
fun formFillHelpersScript(): String =
    """
    ${elementSelectorBuilderScript()}
    ${interactionHelpersScript()}
    ${formControlMutationScript()}
    function kelpieFillElement(selector, value) {
        var targets;
        try { targets = document.querySelectorAll(selector); } catch (_) { return {error: 'not_found'}; }
        if (targets.length > 1) return {error: 'ambiguous_target'};
        var el = targets.length === 1 ? targets[0] : null;
        if (!el) {
            return {error: 'not_found', diagnostics: kelpieNotFoundDiagnostics(selector, null)};
        }
        var tag = el.tagName ? el.tagName.toLowerCase() : '';
        var editable = ['input', 'textarea', 'select'].includes(tag) || !!el.isContentEditable;
        if (!editable || el.matches(':disabled') || el.closest('[inert]') || el.getAttribute('aria-disabled') === 'true' || !!el.readOnly) {
            return {error: 'not_editable', diagnostics: kelpieEditableDiagnostics(el, selector, null)};
        }
        if (kelpieSensitiveAction(el)) return {error: 'needs_human'};
        kelpieRememberSecret(value);
        var expectedRole = el.getAttribute('role'), expectedType = el.type;
        var expectedName = el.getAttribute('name'), expectedLabel = el.getAttribute('aria-label');
        var expectedPlaceholder = el.getAttribute('placeholder'), expectedTag = el.tagName;
        var beforeRect = el.getBoundingClientRect();
        function actionableRect() {
            var r = el.getBoundingClientRect();
            if (r.width <= 0 || r.height <= 0 || kelpieHiddenElement(el)) return false;
            var x = r.left + r.width / 2, y = r.top + r.height / 2;
            if (x < 0 || y < 0 || x >= window.innerWidth || y >= window.innerHeight) return false;
            var hit = document.elementFromPoint(x, y);
            return !!hit && (hit === el || el.contains(hit));
        }
        if (!actionableRect()) return {error: 'not_visible'};
        el.focus();
        var afterTargets = document.querySelectorAll(selector), afterRect = el.getBoundingClientRect();
        if (!el.isConnected || afterTargets.length !== 1 || afterTargets[0] !== el ||
            el.getAttribute('role') !== expectedRole || el.type !== expectedType || el.tagName !== expectedTag ||
            el.getAttribute('name') !== expectedName || el.getAttribute('aria-label') !== expectedLabel ||
            el.getAttribute('placeholder') !== expectedPlaceholder || el.matches(':disabled') || el.closest('[inert]') ||
            el.getAttribute('aria-disabled') === 'true' || el.readOnly ||
            Math.abs(afterRect.x - beforeRect.x) > 0.5 || Math.abs(afterRect.y - beforeRect.y) > 0.5 ||
            Math.abs(afterRect.width - beforeRect.width) > 0.5 || Math.abs(afterRect.height - beforeRect.height) > 0.5) {
            return {error: 'stale_element'};
        }
        if (kelpieSensitiveAction(el)) return {error: 'needs_human'};
        if (!actionableRect()) return {error: 'not_visible'};
        if (el.isContentEditable) {
            el.textContent = value;
        } else {
            kelpieWriteFormControlValue(el, value);
        }
        kelpieDispatchFormControlInput(el);
        kelpieDispatchFormControlChange(el);
        return {
            selector: kelpieSafeSelector(el),
            value: null,
            sensitivity: 'private',
            element: kelpieElementSummary(el)
        };
    }
    """.trimIndent()

@Suppress("UNUSED_PARAMETER")
fun fillAnnotationScript(
    index: Int,
    value: String,
): String = "({error:'STALE_OBSERVATION',reason:'OBSERVATION_REQUIRED'})"

fun interactionHelpersScript(): String =
    """
    ${observationPrivacyScript()}
    function kelpieRectJSON(rect) {
        return {x: rect.x, y: rect.y, width: rect.width, height: rect.height};
    }
    function kelpieViewportDiagnostics() {
        return {
            viewport: {width: window.innerWidth || 0, height: window.innerHeight || 0},
            scrollPosition: {x: window.scrollX || 0, y: window.scrollY || 0}
        };
    }
    function kelpieElementSummary(node) {
        if (!node) return null;
        var rect = typeof node.getBoundingClientRect === 'function'
            ? node.getBoundingClientRect()
            : {x: 0, y: 0, width: 0, height: 0};
        return {
            tag: node.tagName ? kelpieSafeTag(node) : null,
            role: kelpieSafeRole(node),
            text: kelpieSafeName(node).substring(0, 100),
            sensitivity: kelpiePrivateElement(node) ? 'private' : 'public',
            selector: node.tagName ? kelpieSafeSelector(node) : null,
            rect: kelpieRectJSON(rect)
        };
    }
    function kelpieBaseDiagnostics(selector, annotationIndex) {
        var diagnostics = kelpieViewportDiagnostics();
        if (selector) diagnostics.selector = kelpieSafeText(selector) === selector ? selector : null;
        if (annotationIndex !== null && annotationIndex !== undefined) diagnostics.annotationIndex = annotationIndex;
        return diagnostics;
    }
    function kelpieSelectorTokens(selector) {
        var matches = (selector || '').toLowerCase().match(/[a-z0-9_-]+/g) || [];
        var seen = new Set();
        return matches.filter(function(token) {
            if (token.length < 2) return false;
            if (['div', 'span', 'button', 'input', 'select', 'textarea', 'role', 'aria', 'data'].includes(token)) return false;
            if (seen.has(token)) return false;
            seen.add(token);
            return true;
        }).slice(0, 6);
    }
    function kelpieSimilarElements(selector) {
        var tokens = kelpieSelectorTokens(selector);
        if (!tokens.length) return [];
        return Array.from(document.querySelectorAll('a,button,input,select,textarea,[role=button]')).map(function(node) {
            var rect = node.getBoundingClientRect();
            if (rect.width <= 0 || rect.height <= 0) return null;
            var haystack = kelpieSafeName(node).toLowerCase();
            var score = tokens.reduce(function(total, token) {
                return total + (haystack.indexOf(token) >= 0 ? 1 : 0);
            }, 0);
            if (!score) return null;
            var summary = kelpieElementSummary(node);
            summary.score = score;
            return summary;
        }).filter(Boolean).sort(function(lhs, rhs) {
            return rhs.score - lhs.score;
        }).slice(0, 5).map(function(item) {
            return {
                selector: item.selector,
                text: item.text,
                tag: item.tag,
                role: item.role,
                rect: item.rect
            };
        });
    }
    function kelpieNotFoundDiagnostics(selector, annotationIndex) {
        var diagnostics = kelpieBaseDiagnostics(selector, annotationIndex);
        if (selector) diagnostics.similarElements = kelpieSimilarElements(selector);
        return diagnostics;
    }
    function kelpieEditableDiagnostics(el, selector, annotationIndex) {
        var tag = el.tagName ? kelpieSafeTag(el) : null;
        var diagnostics = kelpieBaseDiagnostics(selector, annotationIndex);
        diagnostics.tag = tag;
        diagnostics.targetRect = kelpieRectJSON(el.getBoundingClientRect());
        diagnostics.isInput = ['input', 'textarea', 'select'].includes(tag);
        diagnostics.disabled = !!el.disabled;
        diagnostics.readOnly = !!el.readOnly;
        diagnostics.isContentEditable = !!el.isContentEditable;
        return diagnostics;
    }
    function kelpieTapDiagnostics(target, requestedX, requestedY, appliedX, appliedY, offsetX, offsetY) {
        var diagnostics = kelpieBaseDiagnostics(null, null);
        diagnostics.requestedPoint = {x: requestedX, y: requestedY};
        diagnostics.clickedPoint = {x: appliedX, y: appliedY};
        diagnostics.offset = {x: offsetX, y: offsetY};
        diagnostics.actualElementAtPoint = kelpieElementSummary(target);
        return diagnostics;
    }
    """.trimIndent()

private fun activationScript(
    resolver: String,
    selector: String? = null,
): String {
    val selectorLiteral = selector?.let { "'${JSEscape.string(it)}'" } ?: "null"
    val elementLookup =
        """
        var el = $resolver;
        if (el && el.error) return el;
        if (!el) return {error: 'not_found', diagnostics: kelpieNotFoundDiagnostics(requestedSelector, null)};
        if (kelpieSensitiveAction(el)) return {error: 'needs_human'};
        if (el.matches(':disabled') || el.closest('[inert]') || el.getAttribute('aria-disabled') === 'true') return {error: 'not_visible'};
        """.trimIndent()

    return """
        |(function() {
        |    ${elementSelectorBuilderScript().prependIndent("    ").trimStart()}
        |    ${interactionHelpersScript().prependIndent("    ").trimStart()}
        |    var requestedSelector = $selectorLiteral;
        |    var requestedAnnotationIndex = null;
        |    $elementLookup
        |    var expectedRole = el.getAttribute('role'), expectedName = kelpieSafeName(el);
        |    var expectedType = el.type, expectedTag = el.tagName, expectedHref = el.getAttribute('href');
        |    el.scrollIntoView({block: 'center', inline: 'center'});
        |    if (!el.isConnected || ($resolver) !== el) return {error: 'stale_element'};
        |    var rect = el.getBoundingClientRect();
        |    if (rect.width <= 0 || rect.height <= 0 || kelpieHiddenElement(el)) {
        |        var diagnostics = kelpieBaseDiagnostics(requestedSelector, requestedAnnotationIndex);
        |        diagnostics.targetRect = kelpieRectJSON(rect);
        |        return {error: 'not_visible', diagnostics: diagnostics};
        |    }
        |    var centerX = rect.left + rect.width / 2;
        |    var centerY = rect.top + rect.height / 2;
        |    var hit = document.elementFromPoint(centerX, centerY);
        |    if (!hit) {
        |        var diagnostics = kelpieBaseDiagnostics(requestedSelector, requestedAnnotationIndex);
        |        diagnostics.targetRect = kelpieRectJSON(rect);
        |        diagnostics.targetCenter = {x: centerX, y: centerY};
        |        return {error: 'not_visible', diagnostics: diagnostics};
        |    }
        |    if (!(hit === el || el.contains(hit))) {
        |        var diagnostics = kelpieBaseDiagnostics(requestedSelector, requestedAnnotationIndex);
        |        diagnostics.targetRect = kelpieRectJSON(rect);
        |        diagnostics.targetCenter = {x: centerX, y: centerY};
        |        diagnostics.actualElementAtPoint = kelpieElementSummary(hit);
        |        diagnostics.obstruction = kelpieElementSummary(hit);
        |        return {error: 'not_visible', diagnostics: diagnostics};
        |    }
        |    if (typeof hit.focus === 'function') {
        |        try { hit.focus({preventScroll: true}); } catch (error) { try { hit.focus(); } catch (focusError) {} }
        |    }
        |    var currentRect = el.getBoundingClientRect();
        |    if (!el.isConnected || !hit.isConnected || ($resolver) !== el ||
        |        el.getAttribute('role') !== expectedRole || kelpieSafeName(el) !== expectedName ||
        |        el.type !== expectedType || el.tagName !== expectedTag || el.getAttribute('href') !== expectedHref ||
        |        Math.abs(currentRect.x - rect.x) > 0.5 || Math.abs(currentRect.y - rect.y) > 0.5 ||
        |        Math.abs(currentRect.width - rect.width) > 0.5 || Math.abs(currentRect.height - rect.height) > 0.5) {
        |        return {error: 'stale_element'};
        |    }
        |    if (kelpieSensitiveAction(el) || kelpieSensitiveAction(hit)) return {error: 'needs_human'};
        |    if (el.matches(':disabled') || el.closest('[inert]') || el.getAttribute('aria-disabled') === 'true' || kelpieHiddenElement(el) ||
        |        document.elementFromPoint(centerX, centerY) !== hit) return {error: 'not_visible'};
        |    // A single click prevents duplicate side effects from synthetic down/up/click chains.
        |    try {
        |        if (typeof hit.click === 'function') hit.click();
        |        else hit.dispatchEvent(new MouseEvent('click', {
        |            bubbles: true, cancelable: true, composed: true,
        |            clientX: centerX, clientY: centerY, button: 0, buttons: 0, detail: 1
        |        }));
        |    } catch (_) {
        |        return {error: 'uncertain', diagnostics: {mayHaveCompleted: true, status: 'UNCERTAIN'}};
        |    }
        |    return {
        |        tag: kelpieSafeTag(el),
        |        role: kelpieSafeRole(el),
        |        name: kelpieSafeName(el).substring(0, 50),
        |        selector: kelpieSafeSelector(el),
        |        text: kelpieSafeNodeText(el).substring(0, 100),
        |        rect: {x: rect.x, y: rect.y, width: rect.width, height: rect.height},
        |        center: {x: centerX, y: centerY}
        |    };
        |})()
        """.trimMargin()
}

data class TapExecution(
    val requestedX: Double,
    val requestedY: Double,
    val appliedX: Double,
    val appliedY: Double,
    val offsetX: Double,
    val offsetY: Double,
)
