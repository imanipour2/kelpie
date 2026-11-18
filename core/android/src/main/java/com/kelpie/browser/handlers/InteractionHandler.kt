package com.kelpie.browser.handlers

import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.CancellationException

class InteractionHandler(
    private val ctx: HandlerContext,
) {
    fun register(router: Router) {
        router.register("click") { click(it) }
        router.register("tap") { tap(it) }
        router.register("fill") { fill(it) }
        router.register("type") { type(it) }
        router.register("select-option") { selectOption(it) }
        router.register("check") { check(it) }
        router.register("uncheck") { uncheck(it) }
    }

    private suspend fun click(body: Map<String, Any?>): Map<String, Any?> {
        val selector = body["selector"] as? String ?: return errorResponse("MISSING_PARAM", "selector is required")
        return try {
            val result = ctx.evaluateJSReturningJSON(selectorActivationScript(selector))
            val diagnostics = result["diagnostics"] as? Map<String, Any?>
            when (result["error"]) {
                "needs_human" -> errorResponse("NEEDS_HUMAN", "This control requires human interaction")
                "ambiguous_target" -> errorResponse("AMBIGUOUS_TARGET", "More than one control matches the target")
                "stale_element" -> errorResponse("STALE_ELEMENT", "The control changed before dispatch")
                "uncertain" -> errorResponse(
                    "UNCERTAIN",
                    "Dispatch may have completed; observe again before retrying",
                    mapOf("mayHaveCompleted" to true, "status" to "UNCERTAIN"),
                )
                "not_found" -> {
                    errorResponse("ELEMENT_NOT_FOUND", "No element matches the requested target", diagnostics)
                }

                "not_visible" -> {
                    errorResponse("ELEMENT_NOT_VISIBLE", "The requested target is not visible or is obscured", diagnostics)
                }

                null -> {
                    val center = result["center"] as? Map<*, *>
                    val x = (center?.get("x") as? Number)?.toDouble()
                    val y = (center?.get("y") as? Number)?.toDouble()
                    if (x != null && y != null) {
                        ctx.showTouchIndicator(x, y, ctx.overlayColor(body))
                    } else {
                        ctx.showTouchIndicatorForElement(selector, ctx.overlayColor(body))
                    }
                    successResponse(mapOf("element" to result))
                }

                else -> {
                    errorResponse("ELEMENT_NOT_FOUND", "No element matches the requested target")
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("EVAL_ERROR", "Interaction failed; observe the target before retrying")
        }
    }

    private suspend fun tap(body: Map<String, Any?>): Map<String, Any?> {
        val requestedX = (body["x"] as? Number)?.toDouble() ?: return errorResponse("MISSING_PARAM", "x and y are required")
        val requestedY = (body["y"] as? Number)?.toDouble() ?: return errorResponse("MISSING_PARAM", "x and y are required")
        val coordinateSpace = (body["coordinateSpace"] as? String ?: "viewport").lowercase()
        if (coordinateSpace == "screenshot") {
            return errorResponse("STALE_OBSERVATION", "Screenshot taps require a bound image transform. Use a fresh canonical element reference.")
        }
        if (coordinateSpace != "viewport" || !requestedX.isFinite() || !requestedY.isFinite() || requestedX < 0 || requestedY < 0) {
            return errorResponse("INVALID_PARAMS", "Expected finite nonnegative viewport CSS coordinates")
        }
        val execution =
            try {
                calibratedTapExecution(requestedX, requestedY, TapCalibrationStore.current())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return errorResponse("INVALID_PARAMS", "The calibrated point is outside the current viewport")
            }
        return try {
            val diagnostics = ctx.evaluateJSReturningJSON(tapScript(execution))
            if (diagnostics["error"] != null) {
                val code = safeInteractionError(diagnostics["error"])
                return errorResponse(
                    code,
                    "The target cannot be safely activated; observe again before retrying",
                    mapOf("mayHaveCompleted" to (code == "UNCERTAIN"), "status" to code),
                )
            }
            successResponse(
                mapOf(
                    "x" to execution.requestedX,
                    "y" to execution.requestedY,
                    "appliedX" to execution.appliedX,
                    "appliedY" to execution.appliedY,
                    "offsetX" to execution.offsetX,
                    "offsetY" to execution.offsetY,
                    "diagnostics" to diagnostics,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("EVAL_ERROR", "Interaction failed; observe the target before retrying")
        }
    }

    private suspend fun fill(body: Map<String, Any?>): Map<String, Any?> {
        val selector = body["selector"] as? String ?: return errorResponse("MISSING_PARAM", "selector is required")
        val value = body["value"] as? String ?: return errorResponse("MISSING_PARAM", "value is required")
        // Preserve replace semantics in one DOM operation. The former typing mode
        // cleared first and later re-resolved a potentially different active element.
        return try {
            val result = ctx.evaluateJSReturningJSON(fillElementScript(selector, value))
            val diagnostics = result["diagnostics"] as? Map<String, Any?>
            when (result["error"]) {
                "needs_human" -> errorResponse("NEEDS_HUMAN", "This control requires human interaction")
                "ambiguous_target" -> errorResponse("AMBIGUOUS_TARGET", "More than one control matches the target")
                "stale_element" -> errorResponse("STALE_ELEMENT", "The control changed before dispatch")
                "not_found" -> errorResponse("ELEMENT_NOT_FOUND", "No element matches the requested target", diagnostics)
                "not_editable" -> errorResponse("INVALID_PARAMS", "The requested target is not editable", diagnostics)
                null -> {
                    ctx.showTouchIndicatorForElement(selector, ctx.overlayColor(body))
                    // Canonical fill shape (matches iOS/macOS and FillResponse in
                    // shared/api-types.ts): top-level {selector, value, element}.
                    // Spreading the script result preserves the diagnostics-style
                    // `element` field returned by fillElementScript.
                    successResponse(result)
                }

                else -> {
                    errorResponse("ELEMENT_NOT_FOUND", "No element matches the requested target", diagnostics)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("EVAL_ERROR", "Interaction failed; observe the target before retrying")
        }
    }

    private suspend fun type(body: Map<String, Any?>): Map<String, Any?> {
        val text = body["text"] as? String ?: return errorResponse("MISSING_PARAM", "text is required")
        val selector = body["selector"] as? String
        return try {
            if (ctx.scriptPlaybackState?.isAbortRequested() == true) return errorResponse("BLOCKED", "Script stopped")
            // One fixed operation retains the same DOM object. Re-reading activeElement
            // between delayed characters could type into another control after a focus change.
            val result = ctx.evaluateJSReturningJSON(
                """
                (function() {
                    ${observationPrivacyScript()}
                    ${formControlMutationScript()}
                    var selector = ${selector?.let { "'${JSEscape.string(it)}'" } ?: "null"};
                    var matches = selector ? document.querySelectorAll(selector) : [document.activeElement];
                    if (matches.length !== 1) return {error:'AMBIGUOUS_TARGET'};
                    var el = matches[0];
                    if (!el || !el.isConnected) return {error:'STALE_ELEMENT'};
                    if (kelpieSensitiveAction(el)) return {error:'NEEDS_HUMAN'};
                    var supported = function() {
                        return (el.tagName === 'TEXTAREA' || (el.tagName === 'INPUT' &&
                            ['text','search','email','tel','url','number'].includes(el.type))) && !el.matches(':disabled') &&
                            !el.closest('[inert]') && el.getAttribute('aria-disabled') !== 'true' && !el.readOnly;
                    };
                    if (!supported()) return {error:'INVALID_TARGET'};
                    function signature() {
                        return JSON.stringify(['role','name','aria-label','aria-labelledby','placeholder','autocomplete','formaction']
                            .map(function(key) { return el.getAttribute(key); }).concat([
                                el.type, Array.from(el.labels || []).map(function(label) { return label.textContent; }).join(' '),
                                el.form ? el.form.getAttribute('action') : null]));
                    }
                    var expectedType = el.type, expectedSignature = signature(), expectedForm = el.form;
                    var initialValue = kelpieReadFormControlValue(el);
                    var r = el.getBoundingClientRect();
                    var hit = document.elementFromPoint(r.x + r.width/2, r.y + r.height/2);
                    if (kelpieHiddenElement(el) || r.width <= 0 || r.height <= 0 || !(hit === el || el.contains(hit))) return {error:'OCCLUDED'};
                    el.focus({preventScroll:true});
                    var after = el.getBoundingClientRect();
                    var afterMatches = selector ? document.querySelectorAll(selector) : [document.activeElement];
                    if (!el.isConnected || document.activeElement !== el || !supported() || el.type !== expectedType ||
                        afterMatches.length !== 1 || afterMatches[0] !== el || signature() !== expectedSignature ||
                        el.form !== expectedForm || kelpieReadFormControlValue(el) !== initialValue ||
                        Math.abs(after.x-r.x) > 0.5 || Math.abs(after.y-r.y) > 0.5 ||
                        Math.abs(after.width-r.width) > 0.5 || Math.abs(after.height-r.height) > 0.5) return {error:'STALE_ELEMENT'};
                    if (kelpieSensitiveAction(el)) return {error:'NEEDS_HUMAN'};
                    hit = document.elementFromPoint(after.x + after.width/2, after.y + after.height/2);
                    if (kelpieHiddenElement(el) || !(hit === el || el.contains(hit))) return {error:'OCCLUDED'};
                    try {
                        kelpieWriteFormControlValue(el, initialValue + '${JSEscape.string(text)}');
                        kelpieDispatchFormControlInput(el);
                        kelpieDispatchFormControlChange(el);
                    } catch (_) { return {error:'UNCERTAIN'}; }
                    return {typed:'redacted',dispatched:true,effectVerified:false};
                })()
                """.trimIndent(),
            )
            val code = result["error"] as? String
            if (code != null) errorResponse(safeInteractionError(code), "Typing has no verified result; inspect before retrying") else successResponse(result)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("EVAL_ERROR", "Typing did not return a verified result; observe before retrying")
        }
    }

    private suspend fun selectOption(body: Map<String, Any?>): Map<String, Any?> {
        val selector = body["selector"] as? String ?: return errorResponse("MISSING_PARAM", "selector is required")
        val value = body["value"] as? String ?: return errorResponse("MISSING_PARAM", "value is required")
        // Canonical select-option shape (matches iOS/macOS and SelectOptionResponse
        // in shared/api-types.ts): top-level {selected: {value, text}}.
        val js =
            """
            (function() {
                ${observationPrivacyScript()}
                var matches = document.querySelectorAll('${JSEscape.string(selector)}');
                if (matches.length !== 1) return {error:'AMBIGUOUS_TARGET'};
                var el = matches[0];
                if (el.tagName !== 'SELECT' || el.matches(':disabled') || el.closest('[inert]') ||
                    el.getAttribute('aria-disabled') === 'true') return {error:'INVALID_TARGET'};
                if (kelpieSensitiveAction(el)) return {error:'NEEDS_HUMAN'};
                var requested = '${JSEscape.string(value)}';
                var option = Array.from(el.options).find(function(opt) { return opt.value === requested && !opt.disabled; });
                if (!option) return {error:'INVALID_TARGET'};
                el.value = requested;
                el.dispatchEvent(new Event('input',{bubbles:true}));
                el.dispatchEvent(new Event('change',{bubbles:true}));
                var verified = el.isConnected && el.value === requested && el.selectedIndex === option.index;
                return {selected:{value:'redacted',text:kelpieSafeNodeText(option),index:el.selectedIndex},
                    dispatched:true,effectVerified:verified};
            })()
            """.trimIndent()
        return try {
            val result = ctx.evaluateJSReturningJSON(js)
            if (result["error"] is String) {
                errorResponse(safeInteractionError(result["error"]), "Selection refused; inspect the target again")
            } else if (result.isEmpty()) {
                errorResponse("ELEMENT_NOT_FOUND", "No element matches the requested target")
            } else {
                ctx.showTouchIndicatorForElement(selector, ctx.overlayColor(body))
                successResponse(result)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("EVAL_ERROR", "Interaction failed; observe the target before retrying")
        }
    }

    private suspend fun check(body: Map<String, Any?>): Map<String, Any?> = setChecked(body, true)

    private suspend fun uncheck(body: Map<String, Any?>): Map<String, Any?> = setChecked(body, false)

    private suspend fun setChecked(
        body: Map<String, Any?>,
        checked: Boolean,
    ): Map<String, Any?> {
        val selector = body["selector"] as? String ?: return errorResponse("MISSING_PARAM", "selector is required")
        // Canonical check/uncheck shape (matches iOS/macOS and CheckResponse
        // in shared/api-types.ts): top-level {checked}.
        val js =
            """
            (function() {
                ${observationPrivacyScript()}
                var matches = document.querySelectorAll('${JSEscape.string(selector)}');
                if (matches.length !== 1) return {error:'AMBIGUOUS_TARGET'};
                var el = matches[0];
                if (el.tagName !== 'INPUT' || !['checkbox','radio'].includes(el.type) || el.matches(':disabled') ||
                    el.closest('[inert]') || el.getAttribute('aria-disabled') === 'true') return {error:'INVALID_TARGET'};
                if (kelpieSensitiveAction(el)) return {error:'NEEDS_HUMAN'};
                el.checked = $checked;
                el.dispatchEvent(new Event('input',{bubbles:true}));
                el.dispatchEvent(new Event('change',{bubbles:true}));
                return {checked:el.checked,dispatched:true,effectVerified:el.isConnected && el.checked === $checked};
            })()
            """.trimIndent()
        return try {
            val result = ctx.evaluateJSReturningJSON(js)
            if (result["error"] is String) {
                errorResponse(safeInteractionError(result["error"]), "Checkbox action refused; inspect the target again")
            } else if (result.isEmpty()) {
                errorResponse("ELEMENT_NOT_FOUND", "No element matches the requested target")
            } else {
                ctx.showTouchIndicatorForElement(selector, ctx.overlayColor(body))
                successResponse(result)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("EVAL_ERROR", "Interaction failed; observe the target before retrying")
        }
    }

    private fun safeInteractionError(raw: Any?): String =
        (raw as? String)?.takeIf {
            it in setOf("AMBIGUOUS_TARGET", "STALE_ELEMENT", "OCCLUDED", "NEEDS_HUMAN", "INVALID_TARGET", "UNCERTAIN")
        } ?: "EVAL_ERROR"

    private suspend fun calibratedTapExecution(
        requestedX: Double,
        requestedY: Double,
        calibration: TapCalibration,
    ): TapExecution {
        val viewport = viewportSize()
        val appliedX = requestedX + calibration.offsetX
        val appliedY = requestedY + calibration.offsetY
        require(appliedX.isFinite() && appliedY.isFinite() && appliedX >= 0 && appliedY >= 0 &&
            appliedX < viewport.first && appliedY < viewport.second) { "Point is outside the current viewport" }
        return TapExecution(
            requestedX = requestedX,
            requestedY = requestedY,
            appliedX = appliedX,
            appliedY = appliedY,
            offsetX = calibration.offsetX,
            offsetY = calibration.offsetY,
        )
    }

    private suspend fun viewportSize(): Pair<Double, Double> {
        val result =
            ctx.evaluateJSReturningJSON(
                """
                (function() {
                    return {
                        width: Math.max(window.innerWidth || 0, 1),
                        height: Math.max(window.innerHeight || 0, 1)
                    };
                })()
                """.trimIndent(),
            )
        val width = (result["width"] as? Number)?.toDouble() ?: 1.0
        val height = (result["height"] as? Number)?.toDouble() ?: 1.0
        return width to height
    }

    private fun tapScript(execution: TapExecution): String =
        """
        (function() {
            ${elementSelectorBuilderScript()}
            ${interactionHelpersScript()}
            var requestedX = ${execution.requestedX};
            var requestedY = ${execution.requestedY};
            var appliedX = ${execution.appliedX};
            var appliedY = ${execution.appliedY};
            var offsetX = ${execution.offsetX};
            var offsetY = ${execution.offsetY};
            var eventTarget = document.elementFromPoint(appliedX, appliedY);
            if (!eventTarget || kelpieHiddenElement(eventTarget) || eventTarget.disabled ||
                eventTarget.getAttribute('aria-disabled') === 'true') return {error:'OCCLUDED'};
            if (kelpieSensitiveAction(eventTarget)) return {error:'NEEDS_HUMAN'};
            function ownerOf(node) {
                var owner = node.closest('a,button,input,select,textarea,label,[role="button"],[role="link"]') || node;
                return owner.tagName === 'LABEL' && owner.control ? owner.control : owner;
            }
            function ownerSignature(owner) {
                return JSON.stringify(['role','name','aria-label','href','type','formaction']
                    .map(function(key) { return owner.getAttribute(key); }).concat([
                        kelpieSafeName(owner), owner.form ? owner.form.getAttribute('action') : null]));
            }
            var owner = ownerOf(eventTarget), expectedOwner = ownerSignature(owner), expectedForm = owner.form;
            function supportedOwner() {
                return owner.matches('button,input,select,textarea,a[href]') ||
                    ['button','link','menuitem','menuitemcheckbox','menuitemradio','tab','checkbox','radio','switch','option']
                        .includes(owner.getAttribute('role'));
            }
            if (!supportedOwner()) return {error:'INVALID_TARGET'};
            function ownerBlocked() {
                return owner.disabled || owner.matches(':disabled') || owner.getAttribute('aria-disabled') === 'true' || owner.closest('[inert]');
            }
            if (ownerBlocked()) return {error:'OCCLUDED'};
            var before = eventTarget.getBoundingClientRect();
            var name = kelpieSafeName(eventTarget), role = eventTarget.getAttribute('role');
            var type = eventTarget.type, href = eventTarget.getAttribute('href');
            if (typeof eventTarget.focus === 'function') eventTarget.focus({preventScroll: true});
            var after = eventTarget.getBoundingClientRect(), hit = document.elementFromPoint(appliedX, appliedY);
            if (!eventTarget.isConnected || !owner.isConnected || ownerOf(eventTarget) !== owner ||
                ownerSignature(owner) !== expectedOwner || owner.form !== expectedForm ||
                eventTarget.disabled || eventTarget.type !== type ||
                eventTarget.getAttribute('role') !== role || kelpieSafeName(eventTarget) !== name ||
                eventTarget.getAttribute('href') !== href || Math.abs(after.x-before.x) > 0.5 ||
                Math.abs(after.y-before.y) > 0.5 || Math.abs(after.width-before.width) > 0.5 ||
                Math.abs(after.height-before.height) > 0.5) return {error:'STALE_ELEMENT'};
            if (kelpieSensitiveAction(eventTarget)) return {error:'NEEDS_HUMAN'};
            if (!supportedOwner()) return {error:'INVALID_TARGET'};
            if (ownerBlocked() || kelpieHiddenElement(eventTarget) || !(hit === eventTarget || eventTarget.contains(hit))) return {error:'OCCLUDED'};
            try {
                if (typeof eventTarget.click !== 'function') return {error:'INVALID_TARGET'};
                eventTarget.click();
            } catch (_) { return {error:'UNCERTAIN'}; }
            var result = kelpieTapDiagnostics(eventTarget, requestedX, requestedY, appliedX, appliedY, offsetX, offsetY);
            result.dispatched = true;
            result.effectVerified = false;
            return result;
        })();
        """.trimIndent()
}
