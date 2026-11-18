import { createKelpieBrowserBackend } from "./fixtures/native-browser.js";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { chromium } from "playwright-core";
import type { Browser, Page } from "playwright-core";
import {
  createBrowserClient, createBrowserControlProxy, createPlaywrightBrowserBackend,
} from "../src/browser-control/index.js";
import type { BrowserControlBackend, BrowserTabClient } from "../src/browser-control/index.js";
import { actOnNativeReference, rememberNativeSnapshot } from "../src/browser-control/native-reference.js";
import { collectBrowserSnapshot } from "../src/browser-control/snapshot.js";
import { KAI_RTL_FIXTURE } from "./fixtures/kai-observation.js";

let engine: Browser;
beforeAll(async () => { engine = await chromium.launch({ headless: true, executablePath: process.env.KELPIE_TEST_CHROMIUM }); });
afterAll(async () => { await engine?.close(); });
describe("fixed native atomic DOM actions", () => {
  it("appends through a native setter and refuses replaced or non-text targets", async () => {
    const page = await engine.newPage();
    try {
      await page.setContent('<input id="text" value="initial"><input id="number" type="number" value="12"><input id="file" type="file"><input id="readonly" onfocus="this.readOnly=true"><button id="button">Click</button>');
      const observe = () => page.evaluate("(() => { const collected = (" + collectBrowserSnapshot.toString() + ')({prefix:"t"}); const targets = {}; for (const item of collected.snapshot.elements) { const target = collected.targets[item.ref]; item.ref = target.id; targets[item.ref] = target; } return (' + rememberNativeSnapshot.toString() + ")({snapshot:collected.snapshot,targets}); })()");
      const invoke = (ref: string, method = "tab.type", text = "!"): Promise<unknown> => page.evaluate("(" + actOnNativeReference.toString() + ")(" + JSON.stringify(ref) + ',"__kelpieBrowserControlRefs",' + JSON.stringify(method) + "," + JSON.stringify(text) + ")");
      await page.evaluate(() => { const el = document.querySelector<HTMLInputElement>("#text")!; Object.defineProperty(el, "value", { get: () => "initial", set: () => { throw new Error("React own setter must not run"); } }); });
      await observe();
      expect(await invoke("text")).toEqual({ ok: true });
      expect(await page.evaluate(() => Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.get!.call(document.querySelector("#text")))).toBe("initial!");
      await observe();
      expect(await invoke("number")).toEqual({ error: "UNSUPPORTED", mayHaveCompleted: false });
      await observe();
      expect(await invoke("file")).toEqual({ error: "NEEDS_HUMAN", mayHaveCompleted: false });
      await observe();
      expect(await invoke("readonly")).toEqual({ error: "STALE_OBSERVATION", mayHaveCompleted: true });
      await observe();
      await page.evaluate(() => { const el = document.querySelector("#button")!; el.replaceWith(el.cloneNode(true)); document.querySelector("#button")!.setAttribute("data-sn-ref", "button"); });
      expect(await invoke("button", "tab.click")).toEqual({ error: "STALE_OBSERVATION", mayHaveCompleted: false });
      await observe();
      await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent("pagehide", { persisted: true })));
      expect(await invoke("text")).toEqual({ error: "STALE_OBSERVATION", mayHaveCompleted: false });
    } finally { await page.close(); }
  });
});
const fixture = `<title>Control fixture</title><h1>Observe</h1>
<label>Email<input id="email" value="INITIAL_SECRET"></label>
<label>Password<input type="password" value="PASSWORD_SECRET"></label>
<textarea placeholder="Notes">TEXTAREA_SECRET</textarea>
<div contenteditable="true" role="textbox" aria-label="Draft">EDITABLE_SECRET<span id="private-label">EDITABLE_LABEL_SECRET</span><p>EDITABLE_DESCENDANT_SECRET</p><button>EDITABLE_BUTTON_SECRET</button></div>
<button aria-labelledby="private-label">Public action</button>
<button id="save">Save</button><button>Save draft</button>
<button>Duplicate</button><button>Duplicate</button>
<label>Agree<input id="agree" type="checkbox"></label><select id="choice"><option value="a">Alpha</option><option value="b">Beta</option></select>
<p id="count">0</p><div style="visibility:hidden"><p>HIDDEN_SECRET</p></div>
<svg width="24" height="24" aria-label="Accessible logo"><title>Ignored SVG title</title><rect width="24" height="24"/></svg>
<svg width="24" height="24"><title>Accessible SVG title</title><rect width="24" height="24"/></svg>
<svg width="24" height="24"><rect width="24" height="24"/></svg>
<svg hidden width="24" height="24"><title>HIDDEN_SECRET</title></svg>
<script>document.querySelector('#save').onclick=()=>document.querySelector('#count').textContent=String(Number(document.querySelector('#count').textContent)+1);</script>`;

function nativeBackend(pages: Map<string, Page>): BrowserControlBackend {
  return createKelpieBrowserBackend({ methods: new Set(["evaluate", "getTabs", "getCurrentUrl", "navigate", "click", "type", "screenshot", "scroll"]), async call(method, params) {
    if (method === "getTabs") return { success: true, tabs: [...pages.keys()].map((id) => ({ id })) };
    const page = pages.get(String(params.tabId));
    if (!page) throw new Error("unknown tab");
    if (method === "evaluate") return { success: true, result: await page.evaluate(String(params.expression)) };
    if (method === "getCurrentUrl") return { success: true, url: page.url(), title: await page.title() };
    if (method === "navigate") await page.goto(String(params.url));
    if (method === "click") await page.locator(String(params.selector)).click();
    if (method === "type") {
      const target = page.locator(String(params.selector));
      await target.press("End");
      await target.pressSequentially(String(params.text));
    }
    if (method === "screenshot") return { success: true, image: (await page.screenshot()).toString("base64"), format: "png" };
    if (method === "scroll") await page.mouse.wheel(Number(params.deltaX), Number(params.deltaY));
    return { success: true };
  } });
}

for (const adapter of ["playwright", "native-transport"] as const) {
  describe(`${adapter} portable conformance`, () => {
    let page: Page;
    let tab: BrowserTabClient;
    let other: BrowserTabClient;
    beforeAll(async () => {
      const context = await engine.newContext();
      page = await context.newPage();
      const second = await context.newPage();
      await page.setContent(fixture);
      await second.setContent(fixture);
      const pages = new Map([["a", page], ["b", second]]);
      const backend = adapter === "playwright" ? createPlaywrightBrowserBackend({
        listTabs: async () => [...pages.keys()].map((id) => ({ id })),
        getPage: async (id) => { const found = pages.get(id); if (!found) throw new Error("unknown tab"); return found; },
      }) : nativeBackend(pages);
      const client = createBrowserClient(createBrowserControlProxy(backend));
      tab = await client.tabs.get("a");
      other = await client.tabs.get("b");
    });
    it("omits all form values and hidden content and obeys total text bounds", async () => {
      const snapshot = await tab.snapshot({ maxChars: 800 });
      expect(snapshot.text.length).toBeLessThanOrEqual(800);
      expect(/INITIAL_SECRET|PASSWORD_SECRET|TEXTAREA_SECRET|EDITABLE_SECRET|EDITABLE_DESCENDANT_SECRET|EDITABLE_BUTTON_SECRET|HIDDEN_SECRET/.test(JSON.stringify(snapshot))).toBe(false);
      expect(snapshot.elements?.some((element) => element.name === "Email")).toBe(true);
      const short = await tab.snapshot({ maxElements: 2 });
      expect(short.elements).toHaveLength(2);
      expect(short.truncated).toBe(true);
    });
    it("discloses discarded public text without treating omitted editor values as truncation", async () => {
      try {
        await page.setContent(`<p>${"public ".repeat(4000)}</p>`);
        const long = await tab.snapshot();
        expect(long.truncated).toBe(true);
        expect(long.elements?.[0]?.name.length).toBeLessThanOrEqual(256);
        await page.setContent(`<div contenteditable="true" role="textbox" aria-label="Draft">${"PRIVATE_SECRET".repeat(2000)}</div>`);
        const privateSnapshot = await tab.snapshot();
        expect(privateSnapshot.truncated).toBe(false);
        expect(JSON.stringify(privateSnapshot).includes("PRIVATE_SECRET")).toBe(false);
      } finally { await page.setContent(fixture); }
    });
    it("redacts and blocks a control with a private editor value as its external label", async () => {
      const snapshot = await tab.snapshot();
      expect(snapshot.elements?.some((element) => element.role === "button" && element.name === "redacted" && element.sensitivity === "SENSITIVE" && element.allowedOperations?.length === 0)).toBe(true);
      expect(JSON.stringify(snapshot).includes("EDITABLE_LABEL_SECRET")).toBe(false);
    });
    it("includes visible named SVGs with accessible-name precedence", async () => {
      const snapshot = await tab.snapshot();
      expect(snapshot.elements?.filter((element) => element.role === "img").map((element) => element.name))
        .toEqual(["Accessible logo", "Accessible SVG title"]);
      expect(/Ignored SVG title|HIDDEN_SECRET/.test(snapshot.text)).toBe(false);
    });
    it("rejects cross-tab refs and appends to the observed editable element", async () => {
      const snapshot = await tab.snapshot();
      const email = snapshot.elements?.find((element) => element.name === "Email");
      expect(email).toBeDefined();
      await expect(other.typeText(email!.ref, "wrong")).rejects.toMatchObject({ code: "STALE_REFERENCE", mayHaveCompleted: false });
      await tab.typeText(email!.ref, "-append");
      expect((await page.locator("#email").inputValue()) === "INITIAL_SECRET-append").toBe(true);
      await expect(tab.typeText(email!.ref, "-duplicate")).rejects.toMatchObject({ code: "STALE_REFERENCE" });
    });
    it("rejects an identically replaced node and a prior snapshot ref", async () => {
      const observed = await tab.snapshot();
      const button = observed.elements!.find((element) => element.name === "Save")!;
      await page.locator("#save").evaluate((el) => { el.replaceWith(el.cloneNode(true)); });
      await expect(tab.click(button.ref)).rejects.toMatchObject({ code: adapter === "native-transport" ? "STALE_OBSERVATION" : "STALE_REFERENCE" });
      const current = await tab.snapshot();
      const live = current.elements!.find((element) => element.name === "Save")!;
      await tab.snapshot();
      await expect(tab.click(live.ref)).rejects.toMatchObject({ code: "STALE_REFERENCE" });
    });
    it("returns binary screenshots and refuses unsupported methods", async () => {
      expect((await tab.screenshot()).slice(0, 4)).toEqual(Uint8Array.from([137, 80, 78, 71]));
      if (adapter === "native-transport") await expect(tab.playwright.getByRole("button").click()).rejects.toMatchObject({ code: "UNSUPPORTED", mayHaveCompleted: false });
    });
    it("invalidates refs on navigation", async () => {
      const observed = await tab.snapshot();
      const ref = observed.elements![0]!.ref;
      await tab.goto("data:text/html,<title>Next</title><button>Next</button>");
      expect(await tab.title()).toBe("Next");
      await expect(tab.click(ref)).rejects.toMatchObject({ code: "STALE_REFERENCE" });
    });
  });
}

describe("Playwright semantic conformance", () => {
  it("enforces exact names and strict ambiguity, fill versus append, checkbox and select", async () => {
    const page = await engine.newPage();
    await page.setContent(fixture);
    const tab = await createBrowserClient(createBrowserControlProxy(createPlaywrightBrowserBackend({ listTabs: async () => [{ id: "a" }], getPage: async () => page }))).tabs.get("a");
    await tab.playwright.getByRole("button", { name: "Save", exact: true }).click();
    expect(await page.locator("#count").innerText()).toBe("1");
    await expect(tab.playwright.getByRole("button", { name: "Duplicate", exact: true }).click({ timeoutMs: 50 })).rejects.toMatchObject({ code: "COMMAND_FAILED" });
    const email = tab.playwright.getByLabel("Email", { exact: true });
    await email.fill("replaced");
    await email.pressSequentially("-append");
    expect(await page.locator("#email").inputValue()).toBe("replaced-append");
    await tab.playwright.getByLabel("Agree").check();
    expect(await page.locator("#agree").isChecked()).toBe(true);
    await tab.playwright.getByLabel("Agree").uncheck();
    expect(await page.locator("#agree").isChecked()).toBe(false);
    expect(await tab.playwright.locator("#choice").selectOption("b")).toEqual(["b"]);
    await expect(tab.playwright.getByText("Never present", { exact: true }).waitFor({ timeoutMs: 25 })).rejects.toMatchObject({ code: "COMMAND_FAILED", mayHaveCompleted: false });
    await page.close();
  });
});

describe("KAI shared observation security fixtures (not Android device acceptance)", () => {
  const observe = async (page: Page, maxElements = 200) => page.evaluate<import("../src/browser-control/protocol.js").BrowserSnapshot>(
    "(" + rememberNativeSnapshot.toString() + ")((" + collectBrowserSnapshot.toString() +
    ')({prefix:"kai-e",maxElements:' + String(maxElements) + "}))",
  );
  const action = (page: Page, ref: string, method = "tab.click", text = "") => page.evaluate(
    "(" + actOnNativeReference.toString() + ")(" + JSON.stringify(ref) +
    ',"__kelpieBrowserControlRefs",' + JSON.stringify(method) + "," + JSON.stringify(text) + ")",
  );
  const local = async (html: string, test: (page: Page) => Promise<void>) => {
    const page = await engine.newPage();
    try { await page.setContent(html); await test(page); } finally { await page.close(); }
  };
  it("T01 records twelve RTL controls with unique refs, roles and finite rectangles", async () => {
    await local(KAI_RTL_FIXTURE, async (page) => {
      const snapshot = await observe(page);
      const elements = snapshot.elements ?? [];
      expect(elements.length).toBeGreaterThanOrEqual(12);
      expect(new Set(elements.map((element) => element.ref)).size).toBe(elements.length);
      expect(elements.filter((element) => element.name === "ارزان‌ترین")).toHaveLength(1);
      expect(elements.every((element) => element.rect && Object.values(element.rect).every(Number.isFinite))).toBe(true);
      expect(elements.every((element) => element.rect && element.rect.width > 0 && element.rect.height > 0)).toBe(true);
      expect(elements.every((element) => element.value === null)).toBe(true);
      expect(snapshot.coverage?.status).toBe("INCOMPLETE");
      expect(snapshot.devicePixelRatio).toBeGreaterThan(0);
    });
  });
  it("T03 removes reflected form secrets from names, ARIA, placeholder and title", async () => {
    const markers = ["KAI_FAKE_PASSWORD_A", "KAI_FAKE_HIDDEN_B", "KAI_FAKE_PRIVATE_C", "KAI_FAKE_AREA_D", "824561", "4000000000000002"] as const;
    const html = [
      "<title>" + markers[2] + "</title>",
      '<input type="password" value="' + markers[0] + '" aria-label="' + markers[0] + '">',
      '<input type="hidden" value="' + markers[1] + '">',
      '<input value="' + markers[2] + '" placeholder="' + markers[2] + '">',
      "<textarea>" + markers[3] + "</textarea>",
      '<input autocomplete="one-time-code" value="' + markers[4] + '">',
      '<input autocomplete="cc-number" value="' + markers[5] + '">',
      '<input type="file" aria-label="Private attachment">',
      '<button aria-label="' + markers[1] + '">Control</button>',
      '<button placeholder="' + markers[2] + '">Other</button>',
    ].join("\n");
    await local(html, async (page) => {
      const snapshot = await observe(page);
      const serialized = JSON.stringify(snapshot);
      // Only a boolean/count can enter failure logs, never the synthetic values.
      expect(markers.filter((marker) => serialized.includes(marker)).length).toBe(0);
      expect(snapshot.privacyRects?.length).toBeGreaterThanOrEqual(6);
      expect(snapshot.elements?.filter((element) => element.sensitivity === "SENSITIVE").length).toBeGreaterThanOrEqual(4);
      expect(snapshot.elements?.filter((element) => element.sensitivity === "SENSITIVE").every((element) => element.name === "redacted" && element.allowedOperations?.length === 0)).toBe(true);
      expect(snapshot.screenshotBlocked).toBe(true);
      expect(snapshot.screenshotBlockReasons).toContain("PRIVATE_FORM_DATA");
    });
  });
  it("T03 omits URL query, fragment and credentials", async () => {
    const page = await engine.newPage();
    try {
      await page.route("**/*", (route) => route.fulfill({ body: "<title>KAI_QUERY</title><button aria-label='KAI_FRAGMENT'>fixture-password</button>", contentType: "text/html" }));
      await page.goto("https://fixture-user:fixture-password@kai.invalid/path?token=KAI_QUERY#KAI_FRAGMENT");
      const snapshot = await observe(page);
      expect(snapshot.url).toBe("https://kai.invalid/path");
      const serialized = JSON.stringify(snapshot);
      expect(["fixture-user", "fixture-password", "KAI_QUERY", "KAI_FRAGMENT"].some((marker) => serialized.includes(marker))).toBe(false);
      expect(snapshot.screenshotBlocked).toBe(true);
      expect(snapshot.screenshotBlockReasons).toContain("PRIVATE_URL_DATA");
    } finally { await page.close(); }
  });
  it("T03 blocks hidden and explicitly private content even without a visible control rectangle", async () => {
    await local('<input type="hidden" value="KAI_HIDDEN_PRIVATE"><p>KAI_HIDDEN_PRIVATE</p>', async (page) => {
      const snapshot = await observe(page);
      expect(snapshot.privacyRects).toHaveLength(0);
      expect(snapshot.screenshotBlocked).toBe(true);
      expect(JSON.stringify(snapshot).includes("KAI_HIDDEN_PRIVATE")).toBe(false);
    });
    await local('<p data-private>KAI_PRIVATE_SURFACE</p>', async (page) => {
      const snapshot = await observe(page);
      expect(snapshot.privacyRects).toHaveLength(0);
      expect(snapshot.screenshotBlocked).toBe(true);
      expect(JSON.stringify(snapshot).includes("KAI_PRIVATE_SURFACE")).toBe(false);
    });
  });
  it("T03 redacts selected values reflected in labels without erasing unrelated words", async () => {
    await local('<select aria-label="Choice"><option value="KAI_SELECT_PRIVATE">Chosen</option></select><select><option value="a">Alpha</option></select><p>KAI_SELECT_PRIVATE</p><button aria-label="KAI_SELECT_PRIVATE">Action</button><button>Save</button>', async (page) => {
      const snapshot = await observe(page);
      expect(JSON.stringify(snapshot).includes("KAI_SELECT_PRIVATE")).toBe(false);
      expect(snapshot.screenshotBlockReasons).toContain("PRIVATE_FORM_DATA");
      expect(snapshot.elements?.some((element) => element.name === "Save" && element.allowedOperations?.includes("click"))).toBe(true);
    });
  });
  it("T02 rejects reorder, new banner and SPA replacement before an incorrect click", async () => {
    for (const change of ["reorder", "banner", "replace", "rename"]) {
      await local(KAI_RTL_FIXTURE, async (page) => {
        const snapshot = await observe(page);
        const ref = snapshot.elements?.find((element) => element.name === "ارزان‌ترین")?.ref ?? "";
        await page.evaluate((kind) => {
          const target = document.querySelector("#sort")!;
          if (kind === "reorder") target.parentElement!.append(target);
          if (kind === "banner") document.body.prepend(document.createElement("button"));
          if (kind === "replace") target.replaceWith(target.cloneNode(true));
          if (kind === "rename") target.textContent = "انتشار";
        }, change);
        expect(await action(page, ref)).toEqual({ error: "STALE_OBSERVATION", mayHaveCompleted: false });
        expect(await page.evaluate("window.effects")).toBe(0);
      });
    }
  });
  it("T02 rejects URL, pagehide and same-URL reload transitions", async () => {
    await local(KAI_RTL_FIXTURE, async (page) => {
      const snapshot = await observe(page);
      const ref = snapshot.elements?.[0]?.ref ?? "";
      await page.evaluate(() => window.dispatchEvent(new PopStateEvent("popstate")));
      expect(await action(page, ref)).toMatchObject({ error: "STALE_OBSERVATION" });
      await observe(page);
      await page.reload();
      expect(await action(page, ref)).toMatchObject({ error: "STALE_OBSERVATION" });
    });
  });
  it("T04 refuses an occluded target and an off-viewport target", async () => {
    await local('<button id="target">Control</button><div style="position:fixed;inset:0;z-index:2;background:white"></div>', async (page) => {
      const snapshot = await observe(page);
      expect(await action(page, snapshot.elements?.[0]?.ref ?? "")).toEqual({ error: "OCCLUDED", mayHaveCompleted: false });
    });
    await local('<button style="position:absolute;top:3000px">Control</button>', async (page) => {
      const snapshot = await observe(page);
      expect(snapshot.elements?.[0]?.visible).toBe(false);
      expect(await action(page, snapshot.elements?.[0]?.ref ?? "")).toEqual({ error: "STALE_ELEMENT", mayHaveCompleted: false });
    });
  });
  it("T04 detects duplicate refs to one DOM object instead of choosing blindly", async () => {
    await local("<button>Control</button>", async (page) => {
      await page.evaluate("(" + rememberNativeSnapshot.toString() + ')({snapshot:{},targets:{a:document.querySelector("button"),b:document.querySelector("button")}})');
      expect(await action(page, "a")).toEqual({ error: "AMBIGUOUS_TARGET", mayHaveCompleted: false });
    });
  });
  it("T06 marks frames, open/closed custom shadow hosts and canvas opaque for screenshots", async () => {
    await local('<iframe srcdoc="<button>Inside</button>"></iframe><iframe src="about:blank"></iframe><x-open></x-open><x-closed></x-closed><canvas></canvas><button>Public</button>', async (page) => {
      await page.evaluate(() => {
        document.querySelector("x-open")!.attachShadow({ mode: "open" }).innerHTML = "<button>Shadow</button>";
        document.querySelector("x-closed")!.attachShadow({ mode: "closed" }).innerHTML = "<input value='PRIVATE'>";
      });
      const snapshot = await observe(page);
      expect(snapshot.screenshotBlocked).toBe(true);
      expect(snapshot.coverage?.status).toBe("UNKNOWN");
      expect(snapshot.screenshotBlockReasons).toEqual(expect.arrayContaining(["OPAQUE_SURFACE", "SHADOW_SURFACE", "CUSTOM_ELEMENT_OPAQUE"]));
    });
  });
  it("T07 reports partial coverage above fifty elements without a false end-of-list", async () => {
    await local(Array.from({ length: 65 }, (_, index) => "<button>Control " + String(index) + "</button>").join(""), async (page) => {
      const snapshot = await observe(page, 50);
      expect(snapshot.elements).toHaveLength(50);
      expect(snapshot.truncated).toBe(true);
      expect(snapshot.coverage).toMatchObject({ status: "INCOMPLETE", observed: 50, truncated: true, totalCandidates: null, unobserved: null });
    });
  });
  it("T10 refuses credential/payment/destructive controls without dispatch", async () => {
    for (const html of [
      '<input type="password" aria-label="Password">',
      '<input autocomplete="one-time-code" aria-label="Code">',
      '<input autocomplete="cc-number" aria-label="Card">',
      '<button type="button">پرداخت</button>',
      '<button type="button">حذف</button>',
      '<button type="button">Publish</button>',
    ]) {
      await local(html, async (page) => {
        const snapshot = await observe(page);
        expect(await action(page, snapshot.elements?.[0]?.ref ?? "")).toEqual({ error: "NEEDS_HUMAN", mayHaveCompleted: false });
      });
    }
  });
  it("T10 applies related labels, form identity and actionable ancestor sensitivity before focus", async () => {
    for (const html of [
      '<form action="/payment"><input aria-label="Notes" onfocus="window.effects++"></form>',
      '<form id="billing" action="/payment"></form><input form="billing" aria-label="Notes" onfocus="window.effects++">',
      '<form name="sign-in"><input aria-label="Notes" onfocus="window.effects++"></form>',
      '<form id="transfer"><input aria-label="Notes" onfocus="window.effects++"></form>',
      '<label for="target">One-time code</label><input id="target" aria-label="Notes" onfocus="window.effects++">',
      '<span id="caption">Pay now</span><input aria-labelledby="caption" onfocus="window.effects++">',
      '<div role="button" aria-label="Delete"><input aria-label="Notes" onfocus="window.effects++"></div>',
    ]) {
      await local(html + "<script>window.effects=0</script>", async (page) => {
        const snapshot = await observe(page);
        const field = snapshot.elements?.find((element) => element.role === "textbox");
        expect(field?.sensitivity).toBe("SENSITIVE");
        expect(field?.allowedOperations).toEqual([]);
        expect(await action(page, field?.ref ?? "", "tab.type", "x")).toEqual({ error: "NEEDS_HUMAN", mayHaveCompleted: false });
        expect(await page.evaluate("window.effects")).toBe(0);
      });
    }
  });
  it("T12 advertises and executes only supported interactive roles and editable types", async () => {
    const html = '<p tabindex="0" onclick="window.effects++">Summary</p><div role="textbox" tabindex="0">Readout</div><input type="number" aria-label="Count"><input readonly aria-label="Readonly"><select aria-label="Choice"><option value="">Choose</option></select><input aria-label="Search"><div contenteditable="true" role="textbox" aria-label="Draft"></div><button type="button" onclick="window.effects++">Run</button><div role="button" tabindex="0" onclick="window.effects++">Custom</div><script>window.effects=0</script>';
    await local(html, async (page) => {
      for (const [name, method] of [["Summary", "tab.click"], ["Readout", "tab.type"], ["Count", "tab.type"], ["Readonly", "tab.type"], ["Choice", "tab.click"]]) {
        const snapshot = await observe(page);
        const target = snapshot.elements?.find((element) => element.name === name);
        expect(target?.allowedOperations).toEqual([]);
        expect(await action(page, target?.ref ?? "", method, "x")).toEqual({ error: "UNSUPPORTED", mayHaveCompleted: false });
      }
      expect(await page.evaluate("window.effects")).toBe(0);
      const snapshot = await observe(page);
      for (const name of ["Search", "Draft"]) expect(snapshot.elements?.find((element) => element.name === name)?.allowedOperations).toEqual(["type"]);
      for (const name of ["Run", "Custom"]) {
        const next = await observe(page);
        const target = next.elements?.find((element) => element.name === name);
        expect(target?.allowedOperations).toEqual(["click"]);
        expect(await action(page, target?.ref ?? "")).toEqual({ ok: true });
      }
      expect(await page.evaluate("window.effects")).toBe(2);
    });
  });
  it("T12 refuses a form that becomes sensitive during focus before typing", async () => {
    await local('<form action="/filter"><input aria-label="Notes" onfocus="this.form.action=\'/payment\'" oninput="window.effects++"></form><script>window.effects=0</script>', async (page) => {
      const snapshot = await observe(page);
      const target = snapshot.elements?.find((element) => element.role === "textbox");
      expect(target?.allowedOperations).toEqual(["type"]);
      expect(await action(page, target?.ref ?? "", "tab.type", "x")).toEqual({ error: "STALE_OBSERVATION", mayHaveCompleted: true });
      expect(await page.evaluate("window.effects")).toBe(0);
    });
  });
  it("T12 consumes a reference before dispatch and never repeats an uncertain action", async () => {
    await local(KAI_RTL_FIXTURE, async (page) => {
      const snapshot = await observe(page);
      const ref = snapshot.elements?.find((element) => element.name === "ارزان‌ترین")?.ref ?? "";
      expect(await action(page, ref)).toEqual({ ok: true });
      expect(await action(page, ref)).toEqual({ error: "STALE_OBSERVATION", mayHaveCompleted: false });
      expect(await page.evaluate("window.effects")).toBe(1);
    });
  });
  it("T12 rechecks synchronous focus mutations before click", async () => {
    await local('<button type="button" onfocus="this.textContent=\'Delete\'" onclick="window.effects++">Control</button><script>window.effects=0</script>', async (page) => {
      const snapshot = await observe(page);
      expect(await action(page, snapshot.elements?.[0]?.ref ?? "")).toEqual({ error: "STALE_OBSERVATION", mayHaveCompleted: true });
      expect(await page.evaluate("window.effects")).toBe(0);
    });
  });
  it("T13 expires the registry and releases its retained state after fifteen seconds", async () => {
    await local(KAI_RTL_FIXTURE, async (page) => {
      await page.clock.install();
      const snapshot = await observe(page);
      await page.clock.fastForward(15_001);
      expect(await action(page, snapshot.elements?.[0]?.ref ?? "")).toEqual({ error: "STALE_OBSERVATION", mayHaveCompleted: false });
      expect(await page.evaluate('Object.hasOwn(window,"__kelpieBrowserControlRefs")')).toBe(false);
    });
  });
});
