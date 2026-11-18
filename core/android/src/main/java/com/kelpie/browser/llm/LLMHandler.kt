package com.kelpie.browser.llm

import com.kelpie.browser.handlers.HandlerContext
import com.kelpie.browser.handlers.JSEscape
import com.kelpie.browser.handlers.ScreenshotHandler
import com.kelpie.browser.handlers.ScreenshotRefusal
import com.kelpie.browser.handlers.ScreenshotResolution
import com.kelpie.browser.handlers.elementSelectorBuilderScript
import com.kelpie.browser.handlers.observationPrivacyScript
import com.kelpie.browser.network.Router
import com.kelpie.browser.network.errorResponse
import com.kelpie.browser.network.successResponse
import kotlinx.coroutines.CancellationException

class LLMHandler(
    private val ctx: HandlerContext,
) {
    fun register(router: Router) {
        router.register("get-accessibility-tree") { getAccessibilityTree(it) }
        router.register("get-visible-elements") { getVisibleElements(it) }
        router.register("get-page-text") { getPageText(it) }
        router.register("get-form-state") { getFormState() }
        router.register("find-element") { findElement(it) }
        router.register("find-button") { findButton(it) }
        router.register("find-link") { findLink(it) }
        router.register("find-input") { findInput(it) }
        router.register("screenshot-annotated") { screenshotAnnotated(it) }
        router.register("click-annotation") { clickAnnotation(it) }
        router.register("fill-annotation") { fillAnnotation(it) }
    }

    private suspend fun getAccessibilityTree(body: Map<String, Any?>): Map<String, Any?> {
        val root = body["root"] as? String ?: "body"
        val maxDepth = ((body["maxDepth"] as? Number)?.toInt() ?: 5).coerceIn(0, 12)
        val safe = JSEscape.string(root)
        val js =
            "(function(){" + observationPrivacyScript() + "var visited=0;function walk(el,d){if(d>$maxDepth||visited>=2000)return null;visited++;" +
                "var role=kelpieSafeRole(el);var name=kelpieSafeName(el).substring(0,50);" +
                "var node={role:role,name:name,sensitivity:kelpiePrivateElement(el)?'private':'public'};" +
                "if(el.getAttribute('aria-checked'))" +
                "node.checked=el.getAttribute('aria-checked')==='true';" +
                "if(el.disabled)node.disabled=true;" +
                "if(document.activeElement===el)node.focused=true;" +
                "var ch=[];if(!kelpiePrivateElement(el))for(var c of el.children)" +
                "{var cn=walk(c,d+1);if(cn)ch.push(cn);}" +
                "if(ch.length)node.children=ch;return node;}" +
                "var r=document.querySelector('$safe');" +
                "if(!r)return{tree:{role:'none'},nodeCount:0};" +
                "return{tree:walk(r,0),nodeCount:r.querySelectorAll('*').length,truncated:visited>=2000,coverage:'INCOMPLETE'};})()"
        return try {
            successResponse(PageTextSnapshot.readObject(ctx, body, js))
        } catch (error: com.kelpie.browser.ai.openai.OpenAIException) {
            errorResponse(error.code, "Could not read the requested document")
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            errorResponse("EVAL_ERROR", "Could not read the requested document")
        }
    }

    private suspend fun getVisibleElements(body: Map<String, Any?>): Map<String, Any?> {
        val interactableOnly = body["interactableOnly"] as? Boolean ?: false
        val js =
            "(function(){" + elementSelectorBuilderScript() + observationPrivacyScript() + "var els=document.querySelectorAll('*');var visible=[];" +
                "for(var el of els){if(kelpieHiddenElement(el))continue;var r=el.getBoundingClientRect();" +
                "if(r.width>0&&r.height>0&&r.top<window.innerHeight&&" +
                "r.bottom>0&&r.left<window.innerWidth&&r.right>0){" +
                "var tag=el.tagName.toLowerCase();" +
                "if($interactableOnly&&" +
                "!['a','button','input','select','textarea'].includes(tag)&&" +
                "!el.onclick&&el.getAttribute('role')!=='button')continue;" +
                "var selector=kelpieSafeSelector(el);if(!selector||selector.length>2000)continue;visible.push({selector:selector,tag:tag," +
                "text:kelpieSafeNodeText(el).substring(0,100)," +
                "rect:{x:r.x,y:r.y,width:r.width,height:r.height}," +
                "role:kelpieSafeRole(el)," +
                "interactable:['a','button','input','select','textarea']" +
                ".includes(tag)});if(visible.length>=200)break;}}" +
                "return{viewport:{width:window.innerWidth," +
                "height:window.innerHeight,scrollX:window.scrollX," +
                "scrollY:window.scrollY},elements:visible,count:visible.length,truncated:visible.length>=200,coverage:'INCOMPLETE'};})()"
        return try {
            successResponse(ctx.evaluateJSReturningJSON(js))
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            errorResponse("EVAL_ERROR", "Could not read the requested document")
        }
    }

    private suspend fun getPageText(body: Map<String, Any?>): Map<String, Any?> =
        try {
            successResponse(PageTextSnapshot.read(ctx, body))
        } catch (error: com.kelpie.browser.ai.openai.OpenAIException) {
            errorResponse(error.code, "Could not read the requested document")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorResponse("EVAL_ERROR", "Could not read page context")
        }

    private suspend fun getFormState(): Map<String, Any?> {
        val js =
            """
            (function() {
                ${elementSelectorBuilderScript()}
                ${observationPrivacyScript()}
                var forms = Array.from(document.querySelectorAll('form'));
                return {forms: forms.slice(0,50).map(function(form) {
                    var controls = Array.from(form.querySelectorAll('input,select,textarea'));
                    var fields = controls.slice(0,200).map(function(el) {
                        return {name:'redacted',type:kelpieSafeType(el),selector:kelpieSafeSelector(el),value:null,
                            sensitivity:'private',required:!!el.required,valid:!!(el.validity && el.validity.valid),
                            disabled:!!el.disabled,emptyRequired:!!el.required && !el.value};
                    });
                    var button = form.querySelector('button[type=submit],input[type=submit]');
                    return {selector:kelpieSafeSelector(form),action:kelpieSafeURL(form.action),
                        method:form.method === 'post' ? 'post' : 'get',fields:fields,
                        isValid:controls.length > 200 ? null : fields.every(function(field) { return field.valid; }),
                        truncated:controls.length > 200,fieldCount:controls.length,
                        emptyRequired:fields.filter(function(field) { return field.emptyRequired; })
                            .map(function(field) { return field.selector; }),
                        submitButton:button?{selector:kelpieSafeSelector(button),text:kelpieSafeName(button),disabled:!!button.disabled}:null};
                }),formCount:forms.length,truncated:forms.length>50,redacted:true};
            })()
            """.trimIndent()
        return try {
            successResponse(ctx.evaluateJSReturningJSON(js))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("EVAL_ERROR", "Could not read form metadata")
        }
    }

    private suspend fun findElement(body: Map<String, Any?>): Map<String, Any?> {
        val text = body["text"] as? String ?: return errorResponse("MISSING_PARAM", "text is required")
        return findByText(text, roleFilter(body["role"] as? String))
    }

    private suspend fun findButton(body: Map<String, Any?>): Map<String, Any?> {
        val text = body["text"] as? String ?: return errorResponse("MISSING_PARAM", "text is required")
        return findByText(text, "['BUTTON','A','INPUT'].includes(el.tagName)||el.getAttribute('role')==='button'")
    }

    private suspend fun findLink(body: Map<String, Any?>): Map<String, Any?> {
        val text = body["text"] as? String ?: return errorResponse("MISSING_PARAM", "text is required")
        return findByText(text, "el.tagName==='A'||el.getAttribute('role')==='link'")
    }

    /**
     * `find-element`'s optional ARIA role filter: explicit `role` attributes
     * win, otherwise the element's implicit role is used.
     */
    private fun roleFilter(role: String?): String {
        val q = role?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return "true"
        return "(function(q){var a=(el.getAttribute('role')||'').toLowerCase();if(a)return a===q;" +
            "var t=el.tagName,y=(el.type||'').toLowerCase();" +
            "if(q==='button')return t==='BUTTON'||(t==='INPUT'&&['button','submit','reset'].includes(y));" +
            "if(q==='link')return t==='A'&&el.hasAttribute('href');" +
            "if(q==='textbox')return t==='TEXTAREA'||(t==='INPUT'&&!['button','submit','reset','checkbox','radio','hidden'].includes(y));" +
            "if(q==='checkbox')return t==='INPUT'&&y==='checkbox';if(q==='heading')return /^H[1-6]$/.test(t);return false;})" +
            "('" + JSEscape.string(q) + "')"
    }

    private suspend fun findByText(
        text: String,
        filter: String,
    ): Map<String, Any?> {
        val safe = JSEscape.string(text.lowercase())
        val js =
            "(function(){" + elementSelectorBuilderScript() + observationPrivacyScript() + "var all=document.querySelectorAll('*'),best=null,bt='';" +
                "for(var el of all){if(!($filter))continue;" +
                "var t=kelpieSafeNodeText(el);" +
                "if(!t.toLowerCase().includes('$safe'))continue;" +
                "var r=el.getBoundingClientRect();" +
                "if(r.width<=0||r.height<=0)continue;" +
                "if(!best||t.length<=bt.length){best=el;bt=t;}}" +
                "if(!best)return{found:false};var b=best.getBoundingClientRect();" +
                "return{found:true,element:{" +
                "tag:best.tagName.toLowerCase(),text:bt.substring(0,100)," +
                "selector:kelpieSafeSelector(best)," +
                "rect:{x:b.x,y:b.y,width:b.width,height:b.height}}};})()"
        return try {
            ctx.evaluateJSReturningJSON(js)
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            errorResponse("EVAL_ERROR", "Could not read the requested document")
        }
    }

    private suspend fun findInput(body: Map<String, Any?>): Map<String, Any?> {
        val label = body["label"] as? String ?: ""
        val safe = JSEscape.string(label.lowercase())
        val js =
            "(function(){" + elementSelectorBuilderScript() + observationPrivacyScript() + "var inputs=document.querySelectorAll('input,select,textarea');" +
                "for(var el of inputs){" +
                "var lbl=el.getAttribute('aria-label')||el.placeholder||el.name||'';" +
                "if(lbl.toLowerCase().includes('$safe')){" +
                "var r=el.getBoundingClientRect();" +
                "return{found:true,element:{tag:el.tagName.toLowerCase()," +
                "type:kelpieSafeType(el),name:'redacted',sensitivity:'private'," +
                "selector:kelpieSafeSelector(el)," +
                "rect:{x:r.x,y:r.y,width:r.width,height:r.height}}};}}return{found:false};})()"
        return try {
            ctx.evaluateJSReturningJSON(js)
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            errorResponse("EVAL_ERROR", "Could not read the requested document")
        }
    }

    private suspend fun screenshotAnnotated(body: Map<String, Any?>): Map<String, Any?> {
        val format = body["format"] as? String ?: "png"
        val quality = (body["quality"] as? Number)?.toInt() ?: 80
        val resolution =
            ScreenshotResolution.parse(body["resolution"] ?: "viewport")
                ?: return errorResponse("INVALID_PARAMS", "resolution must be 'native' or 'viewport'")
        return try {
            val handler = ScreenshotHandler(ctx)
            if (body["fullPage"] == true) handler.captureFullPagePayload(format, quality, resolution)
            successResponse(handler.captureAnnotatedPayload(format, quality, resolution))
        } catch (error: ScreenshotRefusal) {
            errorResponse(error.code, error.message ?: "Capture refused")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            errorResponse("NO_FRESH_FRAME", "No safely verified annotated frame was produced")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun clickAnnotation(body: Map<String, Any?>): Map<String, Any?> = annotationRequired()

    @Suppress("UNUSED_PARAMETER")
    private suspend fun fillAnnotation(body: Map<String, Any?>): Map<String, Any?> = annotationRequired()

    private fun annotationRequired(): Map<String, Any?> =
        errorResponse(
            "STALE_OBSERVATION",
            "Numbered legacy targets are display-only. A validated Observation/ElementRef is required.",
            mapOf("reason" to "OBSERVATION_REQUIRED"),
        )
}
