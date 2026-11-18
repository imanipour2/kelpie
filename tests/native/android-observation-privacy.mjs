import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";

const root = new URL("../../", import.meta.url);
const source = async (path) => readFile(new URL(`core/android/src/main/java/com/kelpie/browser/${path}`, root), "utf8");
function rawFunction(text, name) {
  const offset = text.indexOf(`fun ${name}(`);
  assert.ok(offset >= 0, `Missing script function ${name}`);
  const raw = text.slice(offset).match(/"""([\s\S]*?)"""/);
  assert.ok(raw, `Missing script body ${name}`);
  return raw[1];
}

/** Runs the actual Kotlin-embedded scripts against a DOM, not copied implementations. */
export async function runPrivacyConformance(page) {
  const [privacySource, selectorSource, interactionSource, pageTextSource, diagnosticSource, handlerSource] = await Promise.all([
    source("handlers/ObservationPrivacyScripts.kt"), source("handlers/ElementSelectorScript.kt"),
    source("handlers/InteractionScripts.kt"), source("llm/PageTextSnapshot.kt"),
    source("handlers/CoordinateDiagnosticsHandler.kt"), source("handlers/InteractionHandler.kt"),
  ]);
  const privacy = rawFunction(privacySource, "observationPrivacyScript");
  const selector = rawFunction(selectorSource, "elementSelectorBuilderScript");
  const form = rawFunction(selectorSource, "formControlMutationScript");
  const helpers = rawFunction(interactionSource, "interactionHelpersScript")
    .replaceAll("${observationPrivacyScript()}", privacy);
  const expand = (raw) => raw.replaceAll("${observationPrivacyScript()}", privacy)
    .replaceAll("${elementSelectorBuilderScript()}", selector)
    .replaceAll("${interactionHelpersScript()}", helpers)
    .replaceAll("${formControlMutationScript()}", form);
  const pageText = expand(rawFunction(pageTextSource, "script"))
    .replaceAll("${JSEscape.string(selector)}", "body");
  const annotations = expand(rawFunction(interactionSource, "annotationElementsScript"));
  const fill = (target, value) => expand(rawFunction(interactionSource, "fillElementScript"))
    .replaceAll("${JSEscape.string(selector)}", target)
    .replaceAll("${JSEscape.string(value)}", value);
  const diagnostics = (config) => expand(rawFunction(diagnosticSource, "coordinateDiagnosticsScript"))
    .replaceAll("$configJSON", JSON.stringify(config));
  const typeText = (target, text) => expand(rawFunction(handlerSource, "type"))
    .replace(/\$\{selector\?\.let \{[\s\S]*?\} \?: "null"\}/g, target === null ? "null" : JSON.stringify(target))
    .replaceAll("${JSEscape.string(text)}", text);
  const tap = (x, y) => {
    const execution = { requestedX: x, requestedY: y, appliedX: x, appliedY: y, offsetX: 0, offsetY: 0 };
    return expand(rawFunction(handlerSource, "tapScript"))
      .replace(/\$\{execution\.(\w+)\}/g, (_, key) => String(execution[key]));
  };
  const tapField = async () => {
    const point = await page.evaluate(() => {
      const rect = document.querySelector('#field').getBoundingClientRect();
      return { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 };
    });
    return page.evaluate(tap(point.x, point.y));
  };
  const activate = (target) => {
    const resolver = rawFunction(interactionSource, "selectorActivationScript").replaceAll("$escaped", target);
    const activation = interactionSource.slice(interactionSource.indexOf("private fun activationScript("));
    const lookup = rawFunction(interactionSource, "activationScript").replaceAll("$resolver", resolver);
    return activation.match(/return """([\s\S]*?)"""/)[1].replace(/^\s*\|/gm, "")
      .replaceAll('${elementSelectorBuilderScript().prependIndent("    ").trimStart()}', selector)
      .replaceAll('${interactionHelpersScript().prependIndent("    ").trimStart()}', helpers)
      .replaceAll("$selectorLiteral", JSON.stringify(target)).replaceAll("$elementLookup", lookup)
      .replaceAll("$resolver", resolver);
  };
  // Parse every generated script before creating fixtures; no source snippet is executed as page authority.
  for (const script of [pageText, annotations, fill("#field", "plain"), diagnostics({ points: [] }), activate("#field"), typeText("#field", "plain"), tap(10, 10)]) new Function(`return ${script}`);

  await page.setContent(`<html lang="fa" dir="rtl"><body>
    <button id="public">مرتب‌سازی قیمت</button><a href="https://example.test/path">نتایج</a>
    ${Array.from({ length: 11 }, (_, i) => `<button>گزینه ${i}</button>`).join("")}
    <input id="password" type="password"><input id="otp" autocomplete="one-time-code">
    <input id="card" autocomplete="cc-number"><input id="hidden" type="hidden">
    <input id="file" type="file"><textarea id="notes"></textarea><div contenteditable="true" id="edit"></div>
    <input id="metadata" aria-label="temporary"><span id="copy"></span>
    <a id="query">پیوند</a><button id="query-copy"></button><div hidden id="private-hidden"></div>
    <svg><title>نشان عمومی</title></svg><script>/* must not be exported */</script>
  </body></html>`);
  // Values are generated in memory and never emitted to CI logs or saved as fixture artifacts.
  await page.evaluate(() => {
    const values = Array.from({ length: 10 }, (_, i) => `private-${i}-${Math.random().toString(36).slice(2)}`);
    window.fixtureSecrets = values;
    ["password", "otp", "card", "hidden"].forEach((id, i) => { document.getElementById(id).value = values[i]; });
    document.querySelector("#notes").textContent = values[4];
    document.querySelector("#edit").textContent = values[5];
    document.querySelector("#metadata").setAttribute("aria-label", values[6]);
    document.querySelector("#metadata").setAttribute("placeholder", values[7]);
    document.querySelector("#copy").textContent = values[4];
    document.querySelector("#query").href = `https://example.test/path?token=${values[8]}#${values[9]}`;
    document.querySelector("#query-copy").setAttribute("aria-label", values[8]);
    document.querySelector("#private-hidden").textContent = values[0];
    document.querySelector("#public").id = values[0];
    document.querySelector("button").setAttribute("name", values[1]);
  });
  const bundle = await page.evaluate(`(() => {${selector}${privacy}
    const output = {page:(${pageText}), annotations:(${annotations}), html:kelpieSafeHTML(document.body),
      attrs:kelpieSafeAttributes(document.querySelector('#metadata'))};
    const serialized=JSON.stringify(output);
    return {leaked:window.fixtureSecrets.some(s=>serialized.includes(s)),
      publicName:output.annotations[0].name, value:output.annotations[0].value,
      privateNames:output.annotations.filter(e=>e.sensitivity==='private').map(e=>e.name),
      rawIdentifiers:output.annotations.some(e=>e.selector && (e.selector.includes('#')||e.selector.includes('[name='))),
      pageText:output.page.content, secretAttribute:('aria-label' in output.attrs),
      scriptExported:output.html.includes('must not be exported')};
  })()`);
  assert.equal(bundle.leaked, false, "No generated private value may leave an existing read path");
  assert.equal(bundle.publicName, "مرتب‌سازی قیمت");
  assert.equal(bundle.value, null);
  assert.ok(bundle.privateNames.every((name) => name === "redacted" || name === ""));
  assert.equal(bundle.rawIdentifiers, false);
  assert.equal(bundle.secretAttribute, false);
  assert.equal(bundle.scriptExported, false);
  assert.ok(bundle.pageText.includes("مرتب‌سازی قیمت"));

  const denial = await page.evaluate(diagnostics({ setupExpression: "document.cookie", exportExpression: "document.body.textContent" }));
  assert.equal(denial.error.code, "POLICY_BLOCKED");
  const actionDenial = await page.evaluate(diagnostics({ actions: [{ type: "tap", x: 1, y: 1 }] }));
  assert.equal(actionDenial.error.code, "OBSERVATION_REQUIRED");

  const literal = interactionSource.match(/fun annotationActivationScript\(index: Int\): String =\s*"([^"]+)"/);
  assert.ok(literal);
  assert.equal((await page.evaluate(literal[1])).error, "STALE_OBSERVATION");
  const fillLiteral = interactionSource.match(/fun fillAnnotationScript\([\s\S]*?\): String = "([^"]+)"/);
  assert.ok(fillLiteral);
  assert.equal((await page.evaluate(fillLiteral[1])).reason, "OBSERVATION_REQUIRED");

  await page.setContent('<input id="field" type="text">');
  const value = `test-${randomUUID()}`;
  const filled = await page.evaluate(fill("#field", value));
  assert.equal(filled.error, undefined);
  assert.equal(filled.value, null);
  assert.equal(JSON.stringify(filled).includes(value), false);
  assert.equal(await page.evaluate((expected) => document.querySelector("#field").value === expected, value), true);

  await page.setContent('<input id="field" type="text">');
  await page.evaluate(() => {
    const old = document.querySelector("#field");
    window.detachedField = old;
    old.addEventListener("focus", () => { const replacement = document.createElement("input"); replacement.id = "field"; old.replaceWith(replacement); });
  });
  const stale = await page.evaluate(fill("#field", value));
  assert.equal(stale.error, "stale_element");
  assert.equal(await page.evaluate(() => document.querySelector("#field").value === "" && window.detachedField.value === ""), true);

  await page.setContent('<input id="field" type="password">');
  const sensitive = await page.evaluate(fill("#field", value));
  assert.equal(sensitive.error, "needs_human");
  assert.equal(await page.evaluate(() => document.querySelector("#field").value), "");
  await page.setContent('<input class="duplicate"><input class="duplicate">');
  assert.equal((await page.evaluate(fill(".duplicate", value))).error, "ambiguous_target");

  await page.setContent('<button id="field">مرتب‌سازی قیمت</button>');
  await page.evaluate(() => {
    window.clickCount = 0;
    const button = document.querySelector('#field');
    for (const event of ['mousedown', 'pointerdown', 'click']) button.addEventListener(event, () => { window.clickCount++; });
  });
  assert.equal((await page.evaluate(activate("#field"))).error, undefined);
  assert.equal(await page.evaluate(() => window.clickCount), 1, "Exactly one activation may be dispatched");

  await page.setContent('<button id="field">مرتب‌سازی قیمت</button>');
  await page.evaluate(() => {
    window.clickCount = 0;
    const old = document.querySelector('#field');
    old.addEventListener('click', () => { window.clickCount++; });
    old.addEventListener('focus', () => {
      const replacement = document.createElement('button'); replacement.id = 'field';
      replacement.textContent = old.textContent;
      replacement.addEventListener('click', () => { window.clickCount++; });
      old.replaceWith(replacement);
    });
  });
  assert.equal((await page.evaluate(activate("#field"))).error, 'stale_element');
  assert.equal(await page.evaluate(() => window.clickCount), 0, "A focus rerender must not activate either target");

  await page.setContent('<button id="field">مرتب‌سازی قیمت</button>');
  await page.evaluate(() => { document.querySelector('#field').click = () => { throw new Error('synthetic failure'); }; });
  const uncertain = await page.evaluate(activate("#field"));
  assert.equal(uncertain.error, 'uncertain');
  assert.equal(uncertain.diagnostics.mayHaveCompleted, true);

  const sensitiveControls = [
    '<form><button id="field">ادامه</button></form>',
    '<form><button id="field" type="submit">Continue</button></form>',
    '<form id="details"></form><button id="field" form="details">Continue</button>',
    '<form><input id="field" type="submit" value="Continue"></form>',
    '<form id="details"></form><input id="field" form="details" type="image" alt="Continue">',
    '<label for="field">شماره کارت</label><input id="field">',
    '<label>One-time code<input id="field"></label>',
    '<span id="purpose">Sign in</span><button id="field" type="button" aria-labelledby="purpose">Continue</button>',
    '<form action="/login"><button id="field" type="button">Continue</button></form>',
    '<form action="/payment"><input id="field"></form>',
    '<form action="/authentication"><button id="field" type="button">Continue</button></form>',
    '<button id="field" type="button" formaction="/checkout">Continue</button>',
    '<a id="field" href="/sign-in"><span>Continue</span></a>',
    '<button id="field" type="button">ورود</button>',
  ];
  for (const html of sensitiveControls) {
    await page.setContent(html);
    await page.evaluate(() => {
      window.clickCount = 0;
      document.addEventListener('click', () => { window.clickCount++; });
    });
    assert.equal((await page.evaluate(activate("#field"))).error, 'needs_human', "Sensitive or submitting controls must refuse before dispatch");
    assert.equal(await page.evaluate(() => window.clickCount), 0);
  }
  await page.setContent('<form action="/payment"><label for="field">Amount</label><input id="field"></form>');
  assert.equal((await page.evaluate(fill("#field", value))).error, 'needs_human');
  assert.equal(await page.evaluate(() => document.querySelector('#field').value), '');
  await page.setContent('<form action="/catalog"><button id="field" type="button">مرتب‌سازی قیمت</button></form>');
  assert.equal((await page.evaluate(activate("#field"))).error, undefined, "An ordinary non-submit sort control remains usable");

  await page.setContent('<input id="field" value="prefix-">');
  const typed = await page.evaluate(typeText('#field', value));
  assert.equal(typed.error, undefined);
  assert.equal(typed.typed, 'redacted');
  assert.equal(JSON.stringify(typed).includes(value), false);
  assert.equal(await page.evaluate((expected) => document.querySelector('#field').value === 'prefix-' + expected, value), true);

  await page.setContent('<input id="field">');
  await page.evaluate(() => {
    const old = document.querySelector('#field');
    window.detachedField = old;
    old.addEventListener('focus', () => {
      const replacement = document.createElement('input'); replacement.id = 'field'; old.replaceWith(replacement);
    });
  });
  assert.equal((await page.evaluate(typeText('#field', value))).error, 'STALE_ELEMENT');
  assert.equal(await page.evaluate(() => document.querySelector('#field').value === '' && window.detachedField.value === ''), true);

  for (const attribute of ['name', 'aria-label', 'placeholder']) {
    await page.setContent('<input id="field" name="query" aria-label="Query" placeholder="Query">');
    await page.evaluate((attribute) => {
      const input = document.querySelector('#field');
      input.addEventListener('focus', () => { input.setAttribute(attribute, 'recipient'); });
    }, attribute);
    assert.equal((await page.evaluate(typeText('#field', value))).error, 'STALE_ELEMENT', 'Typing must reject same-node field repurposing');
    assert.equal(await page.evaluate(() => document.querySelector('#field').value === ''), true);
  }

  await page.setContent('<input id="field" type="password">');
  assert.equal((await page.evaluate(typeText('#field', value))).error, 'NEEDS_HUMAN');
  assert.equal(await page.evaluate(() => document.querySelector('#field').value === ''), true);

  await page.setContent('<button id="field" type="button">مرتب‌سازی قیمت</button>');
  await page.evaluate(() => {
    window.clickCount = 0;
    const button = document.querySelector('#field');
    for (const event of ['pointerdown', 'mousedown', 'click']) button.addEventListener(event, () => { window.clickCount++; });
    window.__kelpieTapCalibration = () => { throw new Error('Untrusted calibration hook must not run'); };
  });
  const tapped = await tapField();
  assert.equal(tapped.error, undefined);
  assert.equal(tapped.effectVerified, false);
  assert.equal(await page.evaluate(() => window.clickCount), 1);

  for (const html of [
    '<form><button><span id="field" tabindex="0">Continue</span></button></form>',
    '<form id="details"></form><button form="details"><span id="field" tabindex="0">Continue</span></button>',
    '<button type="button" aria-label="پرداخت"><span id="field" tabindex="0">Continue</span></button>',
    '<a href="/login"><span id="field" tabindex="0">Continue</span></a>',
  ]) {
    await page.setContent(html);
    await page.evaluate(() => { window.clickCount = 0; document.addEventListener('click', () => { window.clickCount++; }); });
    assert.equal((await tapField()).error, 'NEEDS_HUMAN');
    assert.equal(await page.evaluate(() => window.clickCount), 0, 'A child hit must not bypass a sensitive actionable parent');
  }

  await page.setContent('<button id="field" type="button">مرتب‌سازی قیمت</button>');
  await page.evaluate(() => {
    window.clickCount = 0;
    const old = document.querySelector('#field');
    old.addEventListener('click', () => { window.clickCount++; });
    old.addEventListener('focus', () => {
      const replacement = document.createElement('button'); replacement.id = 'field'; replacement.textContent = old.textContent;
      replacement.addEventListener('click', () => { window.clickCount++; }); old.replaceWith(replacement);
    });
  });
  assert.equal((await tapField()).error, 'STALE_ELEMENT');
  assert.equal(await page.evaluate(() => window.clickCount), 0);

  await page.setContent('<a href="/catalog"><span id="field" tabindex="0">Continue</span></a>');
  await page.evaluate(() => {
    window.clickCount = 0;
    const child = document.querySelector('#field');
    child.parentElement.addEventListener('click', (event) => { window.clickCount++; event.preventDefault(); });
    child.addEventListener('focus', () => { child.parentElement.setAttribute('href', '/another-target'); });
  });
  assert.equal((await tapField()).error, 'STALE_ELEMENT', 'A stable child must not hide repurposing of its actionable parent');
  assert.equal(await page.evaluate(() => window.clickCount), 0);

  for (const html of [
    '<button type="button" disabled><span id="field" tabindex="0">Continue</span></button>',
    '<button type="button" aria-disabled="true"><span id="field" tabindex="0">Continue</span></button>',
    '<fieldset disabled><button type="button"><span id="field" tabindex="0">Continue</span></button></fieldset>',
    '<div inert><button type="button"><span id="field" tabindex="0">Continue</span></button></div>',
  ]) {
    await page.setContent(html);
    await page.evaluate(() => { window.clickCount = 0; document.addEventListener('click', () => { window.clickCount++; }); });
    assert.ok(['OCCLUDED', 'STALE_ELEMENT', 'INVALID_TARGET'].includes((await tapField()).error), 'Disabled actionable ancestors must block a child tap');
    assert.equal(await page.evaluate(() => window.clickCount), 0);
  }
  await page.setContent('<button type="button"><span id="field" tabindex="0">Continue</span></button>');
  await page.evaluate(() => {
    window.clickCount = 0;
    const child = document.querySelector('#field');
    document.addEventListener('click', () => { window.clickCount++; });
    child.addEventListener('focus', () => { child.parentElement.disabled = true; });
  });
  assert.ok(['OCCLUDED', 'STALE_ELEMENT', 'INVALID_TARGET'].includes((await tapField()).error), 'A parent disabled during focus must block dispatch');
  assert.equal(await page.evaluate(() => window.clickCount), 0);
  return { groups: 22, status: "PASS", scope: "ephemeral-script DOM semantics; not Android hardware or PNG" };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const require = createRequire(new URL("../../packages/shared/package.json", import.meta.url));
  const { chromium } = require("playwright-core");
  const browser = await chromium.launch({ headless: true, ...(process.env.KELPIE_TEST_EXECUTABLE ? { executablePath: process.env.KELPIE_TEST_EXECUTABLE } : {}) });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    console.log(JSON.stringify(await runPrivacyConformance(page)));
  } finally {
    await browser.close();
  }
}
