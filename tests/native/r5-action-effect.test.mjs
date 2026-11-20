import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { after, before, test } from "node:test";
import { actOnNativeReference, rememberNativeSnapshot } from "../../packages/shared/dist/browser-control/native-reference.js";

const require = createRequire(new URL("../../packages/shared/package.json", import.meta.url));
const { chromium } = require("playwright-core");
const scrollSource = await readFile(new URL("../../core/android/src/main/java/com/kelpie/browser/handlers/ScrollEffectEvidence.kt", import.meta.url), "utf8");
const scrollSnapshot = scrollSource.match(/val SNAPSHOT\s*=\s*"""([\s\S]*?)"""/)[1];
const scrollTemplate = JSON.parse(scrollSource.match(/return ("[^"\n]*window\.scrollBy[^\n]+")/)[1]);
const scrollDispatch = (x, y) => scrollTemplate.replaceAll("$dx", String(x)).replaceAll("$dy", String(y));
let browser;
before(async () => { browser = await chromium.launch({ headless: true, executablePath: process.env.KELPIE_TEST_CHROMIUM }); });
after(async () => { await browser?.close(); });

async function fixture(body, run) {
  const page = await browser.newPage();
  try {
    await page.setContent(`<html><body>${body}</body></html>`);
    await run(page);
  } finally { await page.close(); }
}

async function remember(page, selector) {
  const expression = `(${rememberNativeSnapshot.toString()})({snapshot:{elements:[{ref:"target",sensitivity:"PUBLIC",allowedOperations:["click","type"]}]},targets:{target:document.querySelector(${JSON.stringify(selector)})}},"__effectFixture")`;
  await page.evaluate(expression);
}

async function action(page, method, text = "", strict = true) {
  return page.evaluate(`(${actOnNativeReference.toString()})("target","__effectFixture",${JSON.stringify(method)},${JSON.stringify(text)},${strict})`);
}

// Match HandlerContext.evaluateJSReturningJSON's expression boundary. A raw
// page.evaluate(program) would accept statements that Android cannot wrap.
async function androidJsonExpression(page, expression) {
  return JSON.parse(await page.evaluate(`JSON.stringify((${expression}))`));
}

async function mobileScrollFixture(direction, run) {
  const context = await browser.newContext({ viewport: { width: 384, height: 720 }, isMobile: true, hasTouch: true, deviceScaleFactor: 3 });
  try {
    const page = await context.newPage();
    await page.setContent(`<!doctype html><html dir="${direction}"><head>
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <style>html,body{margin:0;padding:0}main{width:calc(100vw + 1800px);height:calc(100vh + 6400px)}</style>
      </head><body><main>Mobile scroll fixture</main></body></html>`);
    await run(page);
  } finally { await context.close(); }
}

const near = (actual, expected) => assert.ok(Math.abs(actual - expected) <= 0.5, `${actual} differs from ${expected}`);
const probeScrollEdge = (page, x, y) => androidJsonExpression(page,
  `(()=>{window.scrollTo({left:${x},top:${y},behavior:'instant'});return{x:scrollX,y:scrollY}})()`);

test("R5 click verifies a real visible DOM change", async () => {
  await fixture('<button onclick="document.querySelector(\'p\').textContent=\'Updated\'">Expand</button><p>Initial</p>', async page => {
    await remember(page, "button");
    assert.deepEqual(await action(page, "tab.click"), { ok: true, effectVerified: true });
    assert.equal(await page.locator("p").innerText(), "Updated");
    assert.equal((await action(page, "tab.click")).error, "STALE_OBSERVATION");
  });
});

test("dispatch or a JavaScript counter without a DOM effect is uncertain", async () => {
  await fixture('<button onclick="window.count=(window.count||0)+1">Check</button>', async page => {
    await remember(page, "button");
    assert.deepEqual(await action(page, "tab.click"), { error: "EFFECT_UNVERIFIED", mayHaveCompleted: true });
    assert.equal(await page.evaluate("window.count"), 1);
  });
});

test("reverted DOM mutations and dataset counters do not prove a final effect", async () => {
  for (const handler of ["const p=document.querySelector('p');p.textContent='Changed';p.textContent='Initial'", "this.dataset.counter='1'"]) {
    await fixture(`<button onclick="${handler}">Check</button><p>Initial</p>`, async page => {
      await remember(page, "button");
      assert.deepEqual(await action(page, "tab.click"), { error: "EFFECT_UNVERIFIED", mayHaveCompleted: true });
    });
  }
});

test("unsafe links and credential or non-GET form actions are refused before focus", async () => {
  for (const html of [
    '<a href="javascript:document.body.dataset.done=1">Open</a>',
    '<a href="https://reader:fixture@example.invalid/">Open</a>',
    '<form method="post"><button type="button">Open</button></form>',
    '<form><input type="password"><button type="button">Open</button></form>',
    '<form id="owner"><button type="button">Open</button></form><input form="owner" autocomplete="ONE-TIME-CODE">',
  ]) {
    await fixture(html, async page => {
      const selector = html.startsWith("<a") ? "a" : "button";
      await page.locator(selector).evaluate(el => { el.onfocus = () => { window.focused = true; }; });
      await remember(page, selector);
      assert.deepEqual(await action(page, "tab.click"), { error: "NEEDS_HUMAN", mayHaveCompleted: false });
      assert.equal(await page.evaluate("Boolean(window.focused)"), false);
    });
  }
});

test("focus-only changes cannot satisfy click evidence", async () => {
  await fixture('<button onfocus="document.querySelector(\'p\').textContent=\'Focused\'">Control</button><p>Initial</p>', async page => {
    await remember(page, "button");
    const result = await action(page, "tab.click");
    assert.notEqual(result.ok, true);
    assert.equal(result.mayHaveCompleted, true);
  });
});

test("a native checkbox state transition is a verified control effect", async () => {
  await fixture('<input type="checkbox" aria-label="Option">', async page => {
    await remember(page, "input");
    assert.deepEqual(await action(page, "tab.click"), { ok: true, effectVerified: true });
    assert.equal(await page.locator("input").isChecked(), true);
  });
});

test("R5 type independently reads the original control after input handlers", async () => {
  await fixture('<input aria-label="Search" value="Initial">', async page => {
    await remember(page, "input");
    assert.deepEqual(await action(page, "tab.type", " value"), { ok: true, effectVerified: true });
    assert.equal(await page.locator("input").inputValue(), "Initial value");
  });
});

test("a rejected input mutation cannot report verified", async () => {
  await fixture('<input aria-label="Search" value="Initial" oninput="this.value=\'Initial\'">', async page => {
    await remember(page, "input");
    assert.deepEqual(await action(page, "tab.type", " value"), { error: "EFFECT_UNVERIFIED", mayHaveCompleted: true });
  });
});

test("equal-valued clone replacement cannot prove the original typed node", async () => {
  await fixture('<input aria-label="Search" value="Initial" oninput="this.replaceWith(this.cloneNode(true))">', async page => {
    await remember(page, "input");
    assert.deepEqual(await action(page, "tab.type", " value"), { error: "EFFECT_UNVERIFIED", mayHaveCompleted: true });
  });
});

test("empty R5 typing is refused before focus and legacy dispatch stays compatible", async () => {
  await fixture('<input aria-label="Search" onfocus="window.focused=true"><button onclick="window.dispatched=true">Run</button>', async page => {
    await remember(page, "input");
    assert.deepEqual(await action(page, "tab.type", ""), { error: "UNSUPPORTED", mayHaveCompleted: false });
    assert.equal(await page.evaluate("Boolean(window.focused)"), false);
    await remember(page, "button");
    assert.deepEqual(await action(page, "tab.click", "", false), { ok: true });
    assert.equal(await page.evaluate("window.dispatched"), true);
  });
});

test("production scroll script reaches a separately observed CSS offset even with smooth page CSS", async () => {
  await fixture('<style>html{scroll-behavior:smooth}body{height:4000px;margin:0}</style><p>Scroll fixture</p>', async page => {
    const before = await androidJsonExpression(page, scrollSnapshot);
    assert.equal(before.scrollY, 0);
    assert.ok(before.maxY > 200);
    assert.deepEqual(await androidJsonExpression(page, scrollDispatch(0, 200)), { dispatched: true });
    const after = await androidJsonExpression(page, scrollSnapshot);
    assert.equal(after.scrollY, 200);
    assert.equal(after.height, before.height);
    assert.equal(after.maxY, before.maxY);
  });
});

test("production scroll snapshot reports the actual clamped boundary", async () => {
  await fixture('<style>body{height:4000px;margin:0}</style><p>Scroll fixture</p>', async page => {
    assert.deepEqual(await androidJsonExpression(page, scrollDispatch(0, 100000)), { dispatched: true });
    const after = await androidJsonExpression(page, scrollSnapshot);
    assert.equal(after.scrollY, after.maxY);
    assert.deepEqual(await androidJsonExpression(page, scrollDispatch(0, 200)), { dispatched: true });
    assert.deepEqual(await androidJsonExpression(page, scrollSnapshot), after);
  });
});

test("production scroll handles the negative horizontal CSS domain in RTL", async () => {
  await fixture('<style>html{direction:rtl}body{width:4000px;margin:0}</style><p>RTL fixture</p>', async page => {
    const before = await androidJsonExpression(page, scrollSnapshot);
    assert.equal(before.rtl, true);
    assert.equal(before.originX, 0);
    assert.ok(before.maxX > 100);
    assert.deepEqual(await androidJsonExpression(page, scrollDispatch(-100, 0)), { dispatched: true });
    const after = await androidJsonExpression(page, scrollSnapshot);
    assert.equal(after.scrollX, -100);
    assert.equal(after.maxX, before.maxX);
  });
});

for (const direction of ["ltr", "rtl"]) {
  test(`mobile wide ${direction.toUpperCase()} scroll matches independently probed native endpoints`, async () => {
    await mobileScrollFixture(direction, async page => {
      const leftBottom = await probeScrollEdge(page, -100000, 100000);
      const rightTop = await probeScrollEdge(page, 100000, 0);
      assert.ok(rightTop.x - leftBottom.x > 600);
      assert.ok(leftBottom.y > 1200);
      const before = await androidJsonExpression(page, scrollSnapshot);
      assert.equal(before.rtl, direction === "rtl");
      assert.ok(before.width > await page.evaluate("document.scrollingElement.clientWidth"));
      near(before.rtl ? before.originX - before.maxX : 0, leftBottom.x);
      near(before.rtl ? before.originX : before.maxX, rightTop.x);
      near(before.maxY, leftBottom.y);
      if (before.rtl) assert.ok(before.originX < -100);
      else assert.equal(before.originX, 0);

      assert.deepEqual(await androidJsonExpression(page, scrollDispatch(-180, 260)), { dispatched: true });
      const moved = await androidJsonExpression(page, scrollSnapshot);
      const actual = await page.evaluate("({x:window.scrollX,y:window.scrollY})");
      near(actual.x, before.scrollX - 180);
      near(actual.y, before.scrollY + 260);
      near(moved.scrollX, actual.x);
      near(moved.scrollY, actual.y);
      assert.equal(moved.originX, before.originX);

      await androidJsonExpression(page, scrollDispatch(-100000, 100000));
      const edge = await androidJsonExpression(page, scrollSnapshot);
      near(edge.scrollX, leftBottom.x);
      near(edge.scrollY, leftBottom.y);
      await androidJsonExpression(page, scrollDispatch(-100000, 100000));
      assert.deepEqual(await androidJsonExpression(page, scrollSnapshot), edge);
      await androidJsonExpression(page, scrollDispatch(100000, -100000));
      const restored = await androidJsonExpression(page, scrollSnapshot);
      near(restored.scrollX, rightTop.x);
      near(restored.scrollY, 0);
    });
  });
}

test("mobile pinch scale does not substitute visual viewport size for window scroll bounds", async () => {
  await mobileScrollFixture("rtl", async page => {
    await probeScrollEdge(page, 100000, 0);
    const before = await androidJsonExpression(page, scrollSnapshot);
    const visualWidth = await page.evaluate("visualViewport.width");
    const session = await page.context().newCDPSession(page);
    try {
      await session.send("Emulation.setPageScaleFactor", { pageScaleFactor: 2 });
      const zoomed = await androidJsonExpression(page, scrollSnapshot);
      assert.ok(await page.evaluate("visualViewport.width") < visualWidth);
      for (const key of ["width", "height", "maxX", "maxY", "originX"]) assert.equal(zoomed[key], before[key]);
      await androidJsonExpression(page, scrollDispatch(-80, 120));
      const moved = await androidJsonExpression(page, scrollSnapshot);
      near(moved.scrollX, zoomed.scrollX - 80);
      near(moved.scrollY, zoomed.scrollY + 120);
    } finally { await session.detach(); }
  });
});
