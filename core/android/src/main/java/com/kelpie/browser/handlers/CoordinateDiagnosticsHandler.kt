package com.kelpie.browser.handlers

import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

class CoordinateDiagnosticsHandler(
    private val ctx: HandlerContext,
) {
    fun register(router: Router) {
        router.register("coordinate-diagnostics") { coordinateDiagnostics(it) }
    }

    private suspend fun coordinateDiagnostics(body: Map<String, Any?>): Map<String, Any?> {
        val format =
            validScreenshotFormat(body["screenshotFormat"])
                ?: return errorResponse("INVALID_PARAMS", "screenshotFormat must be 'png' or 'jpeg'")
        val resolution =
            ScreenshotResolution.parse(body["screenshotResolution"] ?: "viewport")
                ?: return errorResponse("INVALID_PARAMS", "screenshotResolution must be 'native' or 'viewport'")

        return try {
            val result =
                ctx.evaluateJSReturningJSON(
                    coordinateDiagnosticsScript(JSONObject(body).toString()),
                )
            val payload = result.toMutableMap()
            val error = payload["error"] as? Map<*, *>
            if (error != null) {
                return errorResponse(
                    error["code"] as? String ?: "EVAL_ERROR",
                    error["message"] as? String ?: "Coordinate diagnostics failed",
                )
            }
            if (body["captureScreenshot"] as? Boolean == true) {
                val screenshot =
                    ctx.captureScreenshotPayload(format, 80, resolution)
                        ?: return errorResponse("SCREENSHOT_FAILED", "Failed to capture screenshot")
                payload["screenshot"] = screenshot
            }
            successResponse(payload)
        } catch (error: ScreenshotRefusal) {
            errorResponse(error.code, error.message ?: "Capture refused")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("EVAL_ERROR", "Coordinate diagnostics failed")
        }
    }

    private fun validScreenshotFormat(raw: Any?): String? {
        if (raw == null) return "png"
        val value = raw as? String ?: return null
        return if (value == "png" || value == "jpeg") value else null
    }
}

/** Read-only diagnostics; action dispatch must use the consent-gated interaction path. */
fun coordinateDiagnosticsScript(configJSON: String): String =
    """
    (function() {
        ${observationPrivacyScript()}
        var config = $configJSON;
        function fail(code, message) { return {error: {code: code, message: message}}; }
        if (config.setupExpression || config.exportExpression) {
            return fail('POLICY_BLOCKED', 'Arbitrary diagnostic JavaScript is disabled');
        }
        if (Array.isArray(config.actions) && config.actions.length) {
            return fail('OBSERVATION_REQUIRED', 'Use the validated interaction path for actions');
        }
        function elementInfo(node) {
            if (!node || node.nodeType !== 1) return null;
            var r = node.getBoundingClientRect();
            return {tag: node.tagName.toLowerCase(), id: null, classes: [],
                text: kelpieSafeName(node).substring(0, 100),
                sensitivity: kelpiePrivateElement(node) ? 'private' : 'public',
                rect: {x:r.x,y:r.y,width:r.width,height:r.height}, visible: r.width > 0 && r.height > 0};
        }
        function viewport() {
            var visual = window.visualViewport;
            return {width: window.innerWidth || 0, height: window.innerHeight || 0,
                scrollX: window.scrollX || 0, scrollY: window.scrollY || 0,
                devicePixelRatio: window.devicePixelRatio || 1,
                visualViewport: visual ? {offsetLeft: visual.offsetLeft, offsetTop: visual.offsetTop,
                    pageLeft: visual.pageLeft, pageTop: visual.pageTop,
                    width: visual.width, height: visual.height, scale: visual.scale} : null};
        }
        function sample(point) {
            var x = Number(point.x), y = Number(point.y);
            if (!Number.isFinite(x) || !Number.isFinite(y)) throw new Error('INVALID_PARAMS');
            var target = document.elementFromPoint(x, y), visual = window.visualViewport;
            var result = {label: null, x:x, y:y,
                pageX: x + (visual ? visual.pageLeft : window.scrollX || 0),
                pageY: y + (visual ? visual.pageTop : window.scrollY || 0),
                elementFromPoint: elementInfo(target),
                elementsFromPoint: (document.elementsFromPoint ? document.elementsFromPoint(x,y) : [])
                    .slice(0,8).map(elementInfo).filter(Boolean)};
            if (point.expectedSelector) {
                var expected = document.querySelectorAll(point.expectedSelector);
                result.expectedSelector = null;
                result.matchesExpected = expected.length === 1 && !!target &&
                    (target === expected[0] || expected[0].contains(target));
            }
            return result;
        }
        try {
            var input = Array.isArray(config.points) ? config.points : [];
            if (input.length > 200) return fail('INVALID_PARAMS', 'At most 200 diagnostic points are supported');
            var points = input.map(sample);
            var checks = points.filter(function(point) { return point.matchesExpected !== undefined; });
            return {coordinateSpace:'viewport-css-pixels',inputSource:'none',
                inputCapabilities:{trustedNativeInput:false,availableInputSources:[]},viewport:viewport(),
                setupResult:null,points:points,actions:[],eventLog:[],exportResult:null,
                classification:{status:!checks.length?'needs-review':checks.every(function(p){return p.matchesExpected;})?'pass':'fail',
                    reason:'Read-only hit testing; no action was dispatched'}};
        } catch (_) { return fail('INVALID_PARAMS', 'Invalid diagnostic coordinates or selector'); }
    })()
    """.trimIndent()
