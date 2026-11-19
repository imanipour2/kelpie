import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { after, before, test } from "node:test";

const require = createRequire(new URL("../../packages/shared/package.json", import.meta.url));
const { chromium } = require("playwright-core");
const sourceRoot = new URL("../../core/android/src/main/java/com/kelpie/browser/", import.meta.url);
const read = (path) => readFile(new URL(path, sourceRoot), "utf8");
const triple = (source) => source.match(/"""([\s\S]*?)"""/)[1];
function trimIndent(value) {
  const lines = value.split("\n");
  if (!lines[0].trim()) lines.shift();
  if (!lines.at(-1).trim()) lines.pop();
  const indent = Math.min(...lines.filter(line => line.trim()).map(line => line.match(/^[ \t]*/)[0].length));
  return lines.map(line => line.trim() ? line.slice(indent) : "").join("\n");
}
const privacy = trimIndent(triple(await read("handlers/ObservationPrivacyScripts.kt")));
const selectors = trimIndent(triple(await read("handlers/ElementSelectorScript.kt")));
const actionSource = await read("agent/execution/R5WebViewActionScripts.kt");
const snapshot = triple(actionSource.slice(actionSource.indexOf("val SNAPSHOT"))).replace("${observationPrivacyScript()}", privacy);
const pixelAudit = trimIndent(triple(await read("capture/CapturePrivacyScript.kt")));
const llmSource = await read("llm/LLMHandler.kt");

// Execute the actual Kotlin-emitted JavaScript, including its shared helpers.
// Kotlin templates used in these methods are controlled test data, not JS code.
function llmScript(name, values = {}) {
  const method = llmSource.slice(llmSource.indexOf(`fun ${name}(`));
  const start = method.indexOf("val js =") + "val js =".length;
  const expression = method.slice(start, method.indexOf("return try", start));
  const parts = expression.match(/"(?:\\.|[^"\\])*"|observationPrivacyScript\(\)|elementSelectorBuilderScript\(\)/g);
  assert.ok(parts?.length, `Actual script missing: ${name}`);
  return parts.map((part) => part === "observationPrivacyScript()" ? privacy
    : part === "elementSelectorBuilderScript()" ? selectors
      : JSON.parse(part).replace(/\$(maxDepth|safe|interactableOnly|filter)/g, (_, key) => String(values[key] ?? ""))).join("");
}

// Materialize the real shared interaction templates with controlled fixture data.
// No test-side click/fill implementation is used.
const interactionSource = await read("handlers/InteractionScripts.kt");
const proofSource = await read("agent/execution/R5ElementActionProofScripts.kt");
const elementSource = await read("handlers/ElementSelectorScript.kt");
const fromFunction = (source, name) => source.slice(source.indexOf(`fun ${name}(`));
const controlledLiteral = value => {
  assert.match(value, /^[a-zA-Z0-9 #._:\[\]=-]*$/);
  return value;
};
const sharedInteraction = trimIndent(triple(fromFunction(interactionSource, "interactionHelpersScript"))
  .replace("${observationPrivacyScript()}", privacy));
const formMutation = trimIndent(triple(fromFunction(elementSource, "formControlMutationScript")));
const sharedFill = trimIndent(triple(fromFunction(interactionSource, "formFillHelpersScript"))
  .replace("${elementSelectorBuilderScript()}", selectors)
  .replace("${interactionHelpersScript()}", sharedInteraction)
  .replace("${formControlMutationScript()}", formMutation));
function activation(selector) {
  const resolver = trimIndent(triple(fromFunction(interactionSource, "selectorActivationScript"))
    .replaceAll("$escaped", controlledLiteral(selector)));
  const source = fromFunction(interactionSource, "activationScript");
  const lookup = trimIndent(triple(source.slice(source.indexOf("val elementLookup"))).replaceAll("$resolver", resolver));
  return triple(source.slice(source.indexOf('return """')))
    .replace(/^\s*\|/gm, "")
    .replace('${elementSelectorBuilderScript().prependIndent("    ").trimStart()}', selectors)
    .replace('${interactionHelpersScript().prependIndent("    ").trimStart()}', sharedInteraction)
    .replaceAll("$selectorLiteral", `'${controlledLiteral(selector)}'`)
    .replaceAll("$elementLookup", lookup).replaceAll("$resolver", resolver).trim();
}
function r5Click(selector) {
  return triple(fromFunction(actionSource, "click"))
    .replace("${q(selector)}", JSON.stringify(selector))
    .replace("${selectorActivationScript(selector)}", activation(selector));
}
function r5Mutation(kind, selector, value, token = "native-fixture-proof") {
  const proof = trimIndent(triple(fromFunction(proofSource, "mutationProofScript"))
    .replace("${Json.encodeToString(actionToken)}", JSON.stringify(token)));
  return triple(fromFunction(actionSource, kind))
    .replace("${formFillHelpersScript()}", sharedFill)
    .replace("${mutationProofScript(actionToken)}", proof)
    .replace("${q(selector)}", JSON.stringify(selector)).replace("${q(value)}", JSON.stringify(value));
}
function r5Verify(kind, selector, value, token = "native-fixture-proof") {
  return trimIndent(triple(fromFunction(proofSource, "verifyMutationScript"))
    .replace("${Json.encodeToString(actionToken)}", JSON.stringify(token))
    .replace("${Json.encodeToString(selector)}", JSON.stringify(selector))
    .replace("${Json.encodeToString(kind)}", JSON.stringify(kind))
    .replace("${Json.encodeToString(value)}", JSON.stringify(value)));
}
function r5Wait(selector) {
  return triple(fromFunction(actionSource, "waitFor")).replace("${q(selector)}", JSON.stringify(selector));
}

let browser;
before(async () => {
  browser = await chromium.launch({ headless: true, executablePath: process.env.KELPIE_TEST_CHROMIUM });
});
after(async () => { await browser?.close(); });

async function fixture(body, run, query = "") {
  const page = await browser.newPage();
  await page.route("https://kai-fixture.invalid/**", route => route.fulfill({ contentType: "text/html", body: `<html><head><title>Public fixture</title></head><body>${body}</body></html>` }));
  try {
    await page.goto(`https://kai-fixture.invalid/${query}`);
    await run(page);
  } finally { await page.close(); }
}

const sensitive = `<form action="/submit?token=QUERY_SECRET">
 <input id="password" type="password" value="PASSWORD_SECRET" aria-label="PASSWORD_LABEL_SECRET">
 <input autocomplete="one-time-code" value="894231">
 <input autocomplete="cc-number" value="4111111111111111">
 <input type="hidden" value="HIDDEN_SECRET">
 <textarea>TEXTAREA_SECRET</textarea><div contenteditable="true">EDITABLE_SECRET</div>
 <button type="button">Public action</button></form>
 <p data-sensitive>ACCOUNT_SECRET</p><div hidden>INVISIBLE_SECRET</div>
 <p>PASSWORD_SECRET PASSWORD_LABEL_SECRET QUERY_SECRET HIDDEN_SECRET TEXTAREA_SECRET EDITABLE_SECRET ACCOUNT_SECRET</p>`;
const secrets = ["PASSWORD_SECRET", "PASSWORD_LABEL_SECRET", "894231", "4111111111111111", "HIDDEN_SECRET", "TEXTAREA_SECRET", "EDITABLE_SECRET", "ACCOUNT_SECRET", "INVISIBLE_SECRET", "QUERY_SECRET"];

function noSecrets(value) {
  const wire = JSON.stringify(value);
  for (const secret of secrets) assert.equal(wire.includes(secret), false, `Leaked synthetic ${secret}`);
}

test("R5 snapshot strips values, echoed secrets, hidden content and URL query", async () => {
  await fixture(sensitive, async page => {
    const result = JSON.parse(await page.evaluate(snapshot));
    noSecrets(result);
    assert.equal(result.redacted, true);
    assert.ok(result.elements.some(item => item.name === "Public action"));
    assert.equal(result.url, "https://kai-fixture.invalid");
  }, "?token=QUERY_SECRET");
});

test("R5 snapshot cannot leak arbitrary role or input-type attributes", async () => {
  await fixture('<button role="ATTRIBUTE_SECRET">Public action</button><input type="TYPE_SECRET">', async page => {
    const result = JSON.parse(await page.evaluate(snapshot));
    assert.equal(JSON.stringify(result).includes("ATTRIBUTE_SECRET"), false);
    assert.equal(JSON.stringify(result).includes("TYPE_SECRET"), false);
    assert.equal(result.elements[0].role, "button");
  });
});

test("R5 snapshot strips URL path tokens and arbitrary DOM identifiers", async () => {
  await fixture('<a id="DOM_ID_SECRET" href="/reset/PATH_SECRET">Public link</a><p>PATH_SECRET</p>', async page => {
    const result = JSON.parse(await page.evaluate(snapshot));
    assert.equal(result.url, "https://kai-fixture.invalid");
    assert.equal(JSON.stringify(result).includes("PATH_SECRET"), false);
    assert.equal(JSON.stringify(result).includes("DOM_ID_SECRET"), false);
    assert.equal(await page.locator(result.elements[0].selector).count(), 1);
  }, "reset/PATH_SECRET");
});

test("legacy accessibility and visible-elements reads share the actual sanitizer", async () => {
  await fixture(sensitive, async page => {
    for (const script of [llmScript("getAccessibilityTree", { safe: "body", maxDepth: 10 }), llmScript("getVisibleElements", { interactableOnly: false })]) {
      const result = await page.evaluate(script);
      noSecrets(result);
      assert.ok(JSON.stringify(result).includes("Public action"));
    }
  });
});

test("legacy reads cannot export arbitrary custom tag, ID or role identifiers", async () => {
  await fixture('<account-secret-123 id="DOM_ID_SECRET" role="ROLE_SECRET">Private wrapper</account-secret-123><button id="PUBLIC_ID_SECRET">Public action</button>', async page => {
    for (const script of [llmScript("getAccessibilityTree", {safe:"body",maxDepth:10}),
      llmScript("getVisibleElements", {interactableOnly:false}),
      llmScript("findByText", {safe:"private wrapper",filter:"true"}), snapshot]) {
      const result = await page.evaluate(script);
      const wire = JSON.stringify(result);
      for (const identifier of ["account-secret-123", "DOM_ID_SECRET", "ROLE_SECRET", "PUBLIC_ID_SECRET"]) {
        assert.equal(wire.includes(identifier), false, `Leaked raw identifier ${identifier}`);
      }
    }
  });
});

test("legacy form-state never returns field values or secret form actions", async () => {
  await fixture(sensitive, async page => {
    const result = await page.evaluate(llmScript("getFormState"));
    noSecrets(result);
    assert.equal(result.forms.length, 1);
    assert.ok(result.forms[0].fields.every(field => field.value === "redacted"));
    assert.equal(result.forms[0].action, "https://kai-fixture.invalid");
  });
});

test("sensitive-input lookup returns a sanitized descriptor", async () => {
  await fixture(sensitive, async page => {
    const result = await page.evaluate(llmScript("findInput", { safe: "password" }));
    noSecrets(result);
    assert.equal(result.element.name, "redacted");
    assert.equal(result.element.type, "password");
  });
});

test("privacy-budget exhaustion returns no original data", async () => {
  await fixture('<button>Public action</button>' + '<input value="BUDGET_SECRET">'.repeat(513), async page => {
    const result = JSON.parse(await page.evaluate(snapshot));
    assert.equal(result.text, "redacted");
    assert.deepEqual(result.elements, []);
    assert.equal(JSON.stringify(result).includes("BUDGET_SECRET"), false);
  });
});

test("pixel scene audit allows a bounded synthetic text scene and detects change", async () => {
  await fixture('<section style="background:#f2ce41">Public synthetic A</section>', async page => {
    const before = JSON.parse(await page.evaluate(pixelAudit));
    assert.equal(before.safe, true);
    assert.match(before.digest, /^[0-9a-f]{8}$/);
    await page.evaluate(() => { document.querySelector("section").textContent = "Public synthetic B"; });
    const after = JSON.parse(await page.evaluate(pixelAudit));
    assert.equal(after.safe, true);
    assert.notEqual(before.digest, after.digest);
  });
});

test("pixel audit denies every sensitive and opaque fixture without exporting content", async () => {
  const cases = [sensitive, '<canvas></canvas>', '<video></video>', '<iframe src="https://foreign.invalid/"></iframe>',
    '<img src="data:image/png;base64,AAAA">', '<svg></svg>', '<div style="background-image:linear-gradient(red,blue)">Public</div>',
    '<input value="ACCOUNT_SECRET">', '<textarea>TEXTAREA_SECRET</textarea>', '<style>p:before{content:"SECRET"}</style><p>Public</p>',
    '<p>cookie=RAW_SECRET</p>', '<p>4111111111111111</p>', '<private-widget>Unknown closed shadow host</private-widget>'];
  for (const body of cases) await fixture(body, async page => {
    const result = JSON.parse(await page.evaluate(pixelAudit));
    assert.equal(result.safe, false);
    assert.equal("digest" in result, false);
    noSecrets(result);
    assert.equal(JSON.stringify(result).includes("RAW_SECRET"), false);
  });
});

test("pixel audit refuses same-origin URL tokens and oversized DOM", async () => {
  await fixture("Public text", async page => {
    assert.equal(JSON.parse(await page.evaluate(pixelAudit)).safe, false);
  }, "?token=QUERY_SECRET");
  await fixture("<span>Public</span>".repeat(2050), async page => {
    assert.equal(JSON.parse(await page.evaluate(pixelAudit)).reason, "PRIVACY_BUDGET_EXCEEDED");
  });
});


test("observed R5 click shares one-click dispatch and rejects ambiguity and occlusion", async () => {
  await fixture('<button id="public" type="button">Open section</button>', async page => {
    await page.evaluate(() => { window.actionCount = 0; document.querySelector("button").onclick = () => window.actionCount++; });
    assert.equal(await page.evaluate(r5Click("#public")), "DISPATCHED");
    assert.equal(await page.evaluate(() => window.actionCount), 1);
    await page.evaluate(() => document.body.append(document.querySelector("button").cloneNode(true)));
    assert.equal(await page.evaluate(r5Click("button")), "DENIED");
    assert.equal(await page.evaluate(() => window.actionCount), 1);
  });
  await fixture('<button id="public" type="button">Open section</button><div style="position:fixed;inset:0;z-index:10"></div>', async page => {
    assert.equal(await page.evaluate(r5Click("#public")), "DENIED");
  });
});

test("R5 click refuses focus replacement, secret forms and unsafe anchor protocols", async () => {
  await fixture('<button id="public" type="button">Open section</button>', async page => {
    await page.evaluate(() => {
      window.actionCount = 0;
      const button = document.querySelector("button");
      button.onclick = () => window.actionCount++;
      button.onfocus = () => button.replaceWith(button.cloneNode(true));
    });
    assert.equal(await page.evaluate(r5Click("#public")), "DENIED");
    assert.equal(await page.evaluate(() => window.actionCount), 0);
  });
  for (const body of ['<form method="post"><button type="button" id="public">Continue</button></form>',
    '<form><input type="password"><button type="button" id="public">Continue</button></form>',
    '<form id="publicForm"></form><input type="password" form="publicForm"><button type="button" form="publicForm" id="public">Continue</button>',
    '<a id="public" href="javascript:window.actionCount=1">Open section</a>']) {
    await fixture(body, async page => assert.equal(await page.evaluate(r5Click("#public")), "DENIED_SENSITIVE"));
  }
});

test("R5 fill refuses ambiguous, covered and focus-replaced targets before writing", async () => {
  for (const body of ['<input id="query"><input id="query">',
    '<input id="query"><div style="position:fixed;inset:0;z-index:10"></div>', '<input id="query" readonly>']) {
    await fixture(body, async page => {
      assert.equal(await page.evaluate(r5Mutation("fill", "#query", "Public text")), false);
      assert.deepEqual(await page.locator("input").evaluateAll(nodes => nodes.map(node => node.value)), body.includes("readonly") ? [""] : body.includes("inset") ? [""] : ["", ""]);
    });
  }
  await fixture('<input id="query">', async page => {
    await page.evaluate(() => { const input = document.querySelector("input"); input.onfocus = () => input.replaceWith(input.cloneNode(true)); });
    assert.equal(await page.evaluate(r5Mutation("fill", "#query", "Public text")), false);
    assert.equal(await page.locator("input").inputValue(), "");
  });
});

test("R5 fill verification is one-shot and rejects an equal-valued replacement", async () => {
  await fixture('<input id="query">', async page => {
    assert.equal(await page.evaluate(r5Mutation("fill", "#query", "Public text")), true);
    assert.equal(await page.evaluate(r5Verify("fill", "#query", "Public text")), true);
    assert.equal(await page.evaluate(r5Verify("fill", "#query", "Public text")), false);
    assert.equal(await page.evaluate(r5Mutation("fill", "#query", "Public text")), true);
    await page.evaluate(() => { const input = document.querySelector("input"); const clone = input.cloneNode(true); clone.value = input.value; input.replaceWith(clone); });
    assert.equal(await page.evaluate(r5Verify("fill", "#query", "Public text")), false);
    assert.equal(await page.evaluate(r5Verify("fill", "#query", "Public text")), false);
  });
});

test("R5 select shares sensitivity checks and binds the selected option identity", async () => {
  const select = '<select id="grade"><option value="c15">CLASSIC 15</option><option value="p25">PRO 25</option></select>';
  await fixture(select, async page => {
    assert.equal(await page.evaluate(r5Mutation("select", "#grade", "PRO 25")), true);
    assert.equal(await page.locator("select").inputValue(), "p25");
    assert.equal(await page.evaluate(r5Verify("select", "#grade", "PRO 25")), true);
    assert.equal(await page.evaluate(r5Mutation("select", "#grade", "PRO 25")), true);
    await page.evaluate(() => { const option = document.querySelector("select").selectedOptions[0]; option.replaceWith(option.cloneNode(true)); });
    assert.equal(await page.evaluate(r5Verify("select", "#grade", "PRO 25")), false);
  });
  await fixture(select.replace('id="grade"', 'id="grade" name="credit_card"'), async page => {
    assert.equal(await page.evaluate(r5Mutation("select", "#grade", "PRO 25")), false);
    assert.equal(await page.locator("select").inputValue(), "c15");
  });
  await fixture(select, async page => {
    await page.evaluate(() => { const select = document.querySelector("select"); select.onfocus = () => select.replaceWith(select.cloneNode(true)); });
    assert.equal(await page.evaluate(r5Mutation("select", "#grade", "PRO 25")), false);
  });
});

test("expired and superseded mutation proofs cannot certify a later command", async () => {
  await fixture('<input id="query">', async page => {
    await page.clock.install();
    assert.equal(await page.evaluate(r5Mutation("fill", "#query", "First", "proof-a")), true);
    assert.equal(await page.evaluate(r5Mutation("fill", "#query", "Second", "proof-b")), true);
    assert.equal(await page.evaluate(r5Verify("fill", "#query", "First", "proof-a")), false);
    assert.equal(await page.evaluate(r5Verify("fill", "#query", "Second", "proof-b")), true);
    assert.equal(await page.evaluate(r5Mutation("fill", "#query", "Third", "proof-c")), true);
    await page.clock.runFor(10001);
    assert.equal(await page.evaluate(r5Verify("fill", "#query", "Third", "proof-c")), false);
  });
});

test("R5 wait refuses an ambiguous or hidden selector", async () => {
  for (const [body, expected] of [['<p id="ready">Ready</p>', true], ['<p id="ready">One</p><p id="ready">Two</p>', false], ['<p id="ready" hidden>Hidden</p>', false]]) {
    await fixture(body, async page => assert.equal(await page.evaluate(r5Wait("#ready")), expected));
  }
});
