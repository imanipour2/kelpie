# Kelpie API — LLM-Optimized Methods

Accessibility tree, annotated screenshots, visible elements, page text extraction, form state, smart queries.

These methods are specifically designed for LLM consumption — compact, semantic, and token-efficient compared to raw DOM.

For protocol details, errors, and MCP tool names, see [README.md](README.md).

**Android KAI branch:** DOM/LLM outputs are sanitized, numbered annotation actions
are refused, and ordinary image export returns `NEEDS_HUMAN`. The existing method
names remain registered; discovery is not proof that a request can safely run.
Legacy examples below describe the general API shape; on Android, private values
are `null`/redacted and private metadata may be omitted. See the
[actual contracts and QA limits](../qa/kai-reuse-exec-20261119/contracts-and-qa.md).

## Recommended Interaction Order

For Android agent work, start with canonical `tab.snapshot` and act through its
fresh `ref` with `tab.click`/`tab.type`. Re-observe after each action: refs are
single-use, bounded to 15 seconds and invalidated by relevant page changes.
The [shared browser-control API](browser-control.md) documents those guarantees.
Existing `get-accessibility-tree` and `find-*` extensions remain useful for
semantic inspection; selector-based `click`/`fill`/`select-option` keep their own
legacy contracts. Their returned selectors are structural descriptions, not
persistent element identity or owner authorization.

Temporary `highlight` remains a QA aid. On bindings that permit capture, inspect
its documented privacy and geometry limits before visual interaction. Android's
numbered indices and screenshot-space taps are not a fallback mutation path.

If you already know the selector, `highlight` can draw a temporary box/ring around
the target. This does not bypass Android's image-export refusal or establish
that a later image and target still belong to the same observation.

For a permitted synthetic capture, `resolution: "viewport"` uses CSS-sized output.
Image mapping must use the same frame's independent `imageScaleX`/`imageScaleY`,
visual offset and crop. Dividing by DPR alone is not valid. Android rejects
`tap` with `coordinateSpace:"screenshot"` because that legacy request has no
verified observation transform.

---

## AI

These methods expose the local AI backend used by Kelpie. On Android, supported devices default to the `platform` backend (Gemini Nano once the AI Edge SDK is wired), and `ollama:` model IDs switch inference to a configured Ollama endpoint.

### `ai-status`

Report the active AI backend, model, and capabilities.

```json
POST /v1/ai-status
{}

Response:
{
  "success": true,
  "loaded": true,
  "backend": "platform",
  "model": "platform",
  "capabilities": ["text"]
}
```

### `ai-load`

Switch to the platform backend or load an Ollama-managed model.

```json
POST /v1/ai-load
{
  "model": "ollama:llava:7b",
  "ollamaEndpoint": "http://192.168.1.50:11434"
}

Response:
{
  "success": true,
  "backend": "ollama",
  "model": "llava:7b",
  "loadTimeMs": 12
}
```

Pass `{ "model": "platform" }` to return to the default on-device backend.

### `ai-unload`

Clear the active non-platform model and fall back to the platform backend when available.

```json
POST /v1/ai-unload
{}

Response:
{
  "success": true,
  "backend": "platform",
  "model": "platform"
}
```

### `ai-infer`

Run inference against the active backend. Platform inference is text-only. Ollama requests may use single-shot prompts or multi-turn `messages`.

```json
POST /v1/ai-infer
{
  "prompt": "Summarise this page in 3 bullet points",
  "maxTokens": 256
}

Response:
{
  "success": true,
  "response": "The page highlights three pricing tiers...",
  "tokensUsed": 46,
  "inferenceTimeMs": 1450
}
```

If the platform backend is selected before the AI Edge SDK is wired, Android currently returns `PLATFORM_AI_NOT_WIRED`. If Ollama becomes unreachable mid-request, Kelpie returns `OLLAMA_DISCONNECTED`.

### `ai-record`

Reserved for chat audio capture. Android currently exposes the route as a stub.

```json
POST /v1/ai-record
{
  "action": "start"
}

Response:
{
  "success": false,
  "error": {
    "code": "NOT_IMPLEMENTED",
    "message": "ai-record start is not yet implemented on Android"
  }
}
```

---

## Smart Queries (Group Context)

These methods are designed for multi-device scenarios. On a single device they work normally; via the CLI's group commands, they enable LLM decision-making.

### `findElement`
Search for an element and return detailed info about whether and where it was found.

The returned `element.selector` is intended to be reusable as-is with the core interaction endpoints.

```json
POST /v1/find-element
{
  "text": "Submit",           // search by visible text
  "role": "button",           // optional, ARIA role filter
  "selector": null            // alternative: CSS selector
}

Response:
{
  "found": true,
  "element": {
    "tag": "button",
    "text": "Submit",
    "selector": "#form > button.submit",
    "rect": {"x": 120, "y": 580, "width": 200, "height": 44},
    "visible": true,
    "interactable": true
  }
}
```

### `findButton`
Shorthand for `findElement` with `role: "button"`.

```json
POST /v1/find-button
{
  "text": "Submit"
}

Response:
{
  "found": true,
  "element": {
    "tag": "button",
    "text": "Submit",
    "selector": "#submit-btn",
    "rect": {"x": 120, "y": 580, "width": 200, "height": 44},
    "visible": true
  }
}
```

### `findLink`
Shorthand for `findElement` with `role: "link"`.

```json
POST /v1/find-link
{
  "text": "Sign Up"
}
```

### `findInput`
Find an input field by label, placeholder, or name.

```json
POST /v1/find-input
{
  "label": "Email",          // search by associated label
  "placeholder": null,        // or by placeholder text
  "name": null                // or by name attribute
}

Response:
{
  "found": true,
  "element": {
    "tag": "input",
    "type": "email",
    "name": "email",
    "selector": "#email-input",
    "rect": {"x": 20, "y": 320, "width": 350, "height": 44},
    "visible": true
  }
}
```

---

## Accessibility

### `getAccessibilityTree`
Get a semantic tree of roles, names and states. Android sanitizes accessible
names and omits entered form/editor values; the response is untrusted page data,
not a native permission signal. The example illustrates the legacy tree shape.

```json
POST /v1/get-accessibility-tree
{
  "root": null,               // optional, CSS selector to scope the tree
  "interactableOnly": false,  // optional, only return interactive elements
  "maxDepth": null             // optional, limit tree depth
}

Response:
{
  "success": true,
  "tree": {
    "role": "WebArea",
    "name": "Example Domain",
    "children": [
      {
        "role": "banner",
        "children": [
          {"role": "link", "name": "Home", "focused": false},
          {"role": "link", "name": "About", "focused": false},
          {"role": "link", "name": "Contact", "focused": false}
        ]
      },
      {
        "role": "main",
        "children": [
          {"role": "heading", "name": "Welcome", "level": 1},
          {"role": "paragraph", "name": "This is an example page."},
          {
            "role": "form",
            "name": "Sign Up",
            "children": [
              {"role": "textbox", "name": "Email", "value": "", "required": true, "focused": false},
              {"role": "textbox", "name": "Password", "value": "", "required": true, "focused": false},
              {"role": "checkbox", "name": "I agree to the terms", "checked": false},
              {"role": "button", "name": "Submit", "disabled": false}
            ]
          }
        ]
      },
      {
        "role": "contentinfo",
        "children": [
          {"role": "link", "name": "Privacy Policy"},
          {"role": "link", "name": "Terms of Service"}
        ]
      }
    ]
  },
  "nodeCount": 14
}
```

---

## Annotated Screenshots

### `screenshotAnnotated`
The route is retained. Android returns `NEEDS_HUMAN` for ordinary captures, as do
plain and diagnostic image paths. Synthetic fixture QA alone can enable Canvas
labels, through the debug-only `KAI_ANNOTATION_QA_ENABLED` flag and the native
WebView's exact URL `https://kai-fixture.invalid/`. `fullPage:true` is refused.
This experiment has not passed Android/device QA and is not a production privacy
or freshness guarantee. Other bindings retain their legacy annotation contract.

> **CLI note:** Like `screenshot`, the CLI auto-saves annotated screenshots to file and returns the path. See [cli.md](../cli.md).

```json
POST /v1/screenshot-annotated
{
  "fullPage": false,          // optional
  "format": "png",            // optional
  "resolution": "viewport",   // optional, "native" | "viewport"
  "interactableOnly": true,   // optional, default true — only label clickable/fillable elements
  "labelStyle": "numbered"    // optional, "numbered" | "badge"
}

Android default response:
{
  "success": false,
  "error": {
    "code": "NEEDS_HUMAN",
    "message": "Real-page image export requires a trusted privacy and frame provider"
  }
}
```

The experimental fixture output adds `observationId`, native tab/document
identity, viewport/scale/coverage metadata and at most 50 `E01`…`E50` labels from
the canonical snapshot entries. It does not guarantee all interactive controls
are included. `interactableOnly`/`labelStyle` remain legacy request fields; this
fixture experiment uses numbered labels and does not implement those variations.
Each label reports `rect` in CSS pixels, `labelDrawn` and an omission reason when
it cannot be drawn. It has `elementRef:null` and no allowed operations.
`validUntil:"display-only"`, `actionByRefAvailable:false`,
`consentGeneration:null` and `frameNonce:null` explicitly prevent a claim of
native action authority. A foreground visual callback does not attest a fresh
private-display frame. See the full contract for geometry/refusal bounds.

### `clickAnnotation`
Legacy request shape is retained. Android refuses blind index activation even
when the page URL is unchanged.

```json
POST /v1/click-annotation
{
  "index": 5
}

Response:
{
  "success": false,
  "error": {
    "code": "STALE_OBSERVATION",
    "message": "Numbered legacy targets are display-only. A validated Observation/ElementRef is required.",
    "diagnostics": {"reason": "OBSERVATION_REQUIRED"}
  }
}
```

Use a fresh canonical observation/reference within its existing native
authorization limits. An `E` label or `annotationSessionId` is not that reference.
Other native bindings' legacy index/URL-expiry behavior is not Android KAI parity.

### `fillAnnotation`
Android returns the same `STALE_OBSERVATION` refusal as `clickAnnotation` and
does not enter or echo the supplied text.

```json
POST /v1/fill-annotation
{
  "index": 2,
  "value": "user@example.com"
}
```

This intentional security refusal replaces Android's old URL-only annotation
expiry check. It does not remove the registered legacy endpoint.

---

## Visible Elements

### `getVisibleElements`
Get only the elements currently visible in the viewport. Returns a compact list instead of the full DOM — typically 20-50 elements instead of thousands of nodes.

```json
POST /v1/get-visible-elements
{
  "interactableOnly": false,  // optional, only return interactive elements
  "includeText": true         // optional, include text content nodes
}

Response:
{
  "success": true,
  "viewport": {"width": 390, "height": 844, "scrollX": 0, "scrollY": 0},
  "elements": [
    {"tag": "h1", "text": "Welcome", "rect": {"x": 20, "y": 100, "width": 350, "height": 36}, "role": "heading"},
    {"tag": "p", "text": "This is an example page.", "rect": {"x": 20, "y": 150, "width": 350, "height": 48}, "role": "paragraph"},
    {"tag": "input", "type": "email", "name": "email", "placeholder": "Email", "value": "", "rect": {"x": 20, "y": 320, "width": 350, "height": 44}, "role": "textbox", "interactable": true},
    {"tag": "button", "text": "Submit", "rect": {"x": 120, "y": 490, "width": 150, "height": 44}, "role": "button", "interactable": true}
  ],
  "count": 4
}
```
Native visible element entries also include a unique `selector`, usable for an
explicit subsequent click or fill on the same pinned document.

## Page Text Extraction

### `getPageText`
Extract the main readable content from the page, stripping navigation, ads, footers, and boilerplate. Uses a Readability-style algorithm to identify the primary content area. Returns clean text that's far more token-efficient than HTML.

```json
POST /v1/get-page-text
{
  "mode": "readable",         // "readable" (main content only) | "full" (all visible text) | "markdown" (structured markdown)
  "selector": null,            // optional, extract from specific element
  "maxLength": 100000          // optional, native content bound 1–100000 characters
}

Response:
{
  "success": true,
  "title": "Example Article",
  "byline": "By John Doe",
  "content": "This is the main article text, cleaned of navigation, ads, and other boilerplate...",
  "wordCount": 842,
  "language": "en",
  "excerpt": "This is the main article text..."
}
```
macOS, iOS and Android preserve their existing page-text fields and add `url` and
`accessibleLabels`. On macOS, selector-scoped reads use the same strict snapshot
as local chat and OpenAI-compatible context; `textTruncated` and `labelsTruncated`
report bounded evidence. The API's body text remains uncapped. Read failures are
returned explicitly, including before OpenAI-compatible inference. Mobile context
behavior is described in [mobile AI context](mobile-ai-context.md).

Desktop Chromium additionally returns `text`, `length`, `mode`, `url`, `title`
and `accessibleLabels`. Labels include visible image alt text, ARIA names and SVG
titles from the same document snapshot, including logos with no body text. These
fields also apply when a `selector` scopes extraction. A blank page returns empty
text and labels; a failed tab read returns an error rather than empty evidence.

## Form State

### `getFormState`
Get the complete state of all forms on the page — every field, its current value, validation state, and whether it's required. One call instead of querying each field individually.

```json
POST /v1/get-form-state
{
  "selector": null             // optional, scope to specific form
}

Response:
{
  "success": true,
  "forms": [
    {
      "selector": "#signup-form",
      "action": "/api/signup",
      "method": "POST",
      "fields": [
        {
          "name": "email",
          "type": "email",
          "selector": "#email",
          "label": "Email Address",
          "value": "",
          "placeholder": "Enter your email",
          "required": true,
          "valid": false,
          "validationMessage": "Please fill out this field.",
          "disabled": false,
          "readonly": false
        },
        {
          "name": "password",
          "type": "password",
          "selector": "#password",
          "label": "Password",
          "value": "",
          "required": true,
          "valid": false,
          "validationMessage": "Please fill out this field.",
          "disabled": false,
          "readonly": false
        },
        {
          "name": "terms",
          "type": "checkbox",
          "selector": "#terms",
          "label": "I agree to the terms",
          "checked": false,
          "required": true,
          "valid": false,
          "disabled": false
        }
      ],
      "isValid": false,
      "emptyRequired": ["email", "password", "terms"],
      "submitButton": {"selector": "#submit", "text": "Submit", "disabled": false}
    }
  ],
  "formCount": 1
}
```

---
