import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createRequire } from "node:module";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";

const fixture = process.argv[2];
if (!fixture) throw new Error("Usage: node tests/core/native/browser-snapshot-selectors.mjs <browser_snapshot_fixture>");
const require = createRequire(new URL("../../packages/shared/package.json", import.meta.url));
const { chromium } = require("playwright-core");
const expression = execFileSync(fixture, ["--snapshot"], { encoding: "utf8" });
const builder = execFileSync(fixture, ["--builder"], { encoding: "utf8" });
const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage();
  await page.route("https://privacy.fixture.invalid/**", route => route.fulfill({contentType:"text/html",body:"<html><body></body></html>"}));
  await page.goto("https://privacy.fixture.invalid/reset/PATH_SECRET");
  await page.setContent(`<html><body>
    <section id="duplicate-parent"><button id="duplicate">First action</button></section>
    <section id="duplicate-parent"><button id="duplicate">Second action</button></section>
    <button id="odd:escaped.id">Escaped action</button>
    <account-secret-123 role="button">Unknown wrapper</account-secret-123>
    <a href="/reset/LINK_PATH_SECRET?token=LINK_QUERY_SECRET">Public link</a>
    <p>PATH_SECRET LINK_PATH_SECRET LINK_QUERY_SECRET</p>
    <input value="INPUT_SECRET"><textarea>TEXTAREA_SECRET</textarea>
    <div contenteditable="true">EDITABLE_SECRET</div>
    <button id="DOM_IDENTIFIER_FIXTURE" name="DOM_NAME_FIXTURE" aria-label="Public label">Public action</button>
  </body></html>`);
  // Exercise the actual shared builder, including roots absent from body * readers.
  const roots = await page.evaluate(`(() => {${builder}
    return [...document.querySelectorAll('html,body,section,button')].map(node => {
      const selector=kelpieBuildSelector(node),matches=selector?document.querySelectorAll(selector):[];
      return {selector,exact:matches.length===1&&matches[0]===node};
    });})()`);
  assert.ok(roots.length >= 8);
  assert.ok(roots.every(({ selector, exact }) => selector && selector.length <= 2000 && exact));
  const snapshot = await page.evaluate(expression);
  for (const secret of ["INPUT_SECRET", "TEXTAREA_SECRET", "EDITABLE_SECRET", "DOM_IDENTIFIER_FIXTURE", "DOM_NAME_FIXTURE", "account-secret-123", "PATH_SECRET", "LINK_PATH_SECRET", "LINK_QUERY_SECRET"]) {
    assert.ok(!JSON.stringify(snapshot).includes(secret));
  }
  for (const label of ["First action", "Second action", "Escaped action", "Public label"]) {
    const element = snapshot.elements.find((item) => item.label === label);
    assert.ok(element, `Missing observed action: ${label}`);
    const observed = await page.evaluate(({ selector }) => {
      const nodes = document.querySelectorAll(selector);
      return { count: nodes.length, label: nodes[0]?.getAttribute("aria-label") || nodes[0]?.textContent.trim() };
    }, element);
    assert.deepEqual(observed, { count: 1, label });
    assert.equal(typeof element.ref, "string");
    assert.equal(typeof element.rect.width, "number");
    assert.equal(element.value, null);
    assert.equal(element.sensitivity, "PUBLIC");
    assert.ok(!element.selector.includes("#") && !element.selector.includes("[name="));
  }
  assert.equal(snapshot.url, "https://privacy.fixture.invalid");
  assert.equal(snapshot.coverage.observed, snapshot.elements.length);
  assert.equal(snapshot.coverage.status, "UNKNOWN");
  assert.equal(snapshot.screenshotBlocked, true);
  assert.equal(await page.evaluate(() => Object.keys(window).some((key) => key.startsWith("__kelpieBrowserControlRefs"))), false);
  if (process.env.KELPIE_TEST_ARTIFACT_DIR) {
    await mkdir(process.env.KELPIE_TEST_ARTIFACT_DIR, { recursive: true });
    // This suite verifies selectors, not the protected Android image exporter.
    // Do not publish an unredacted raw screenshot of its sensitive fixture.
    await writeFile(join(process.env.KELPIE_TEST_ARTIFACT_DIR, "selector-snapshot.json"), JSON.stringify(snapshot, null, 2));
  }
  console.log("Native selector snapshot DOM conformance passed.");
} finally {
  await browser.close();
}
