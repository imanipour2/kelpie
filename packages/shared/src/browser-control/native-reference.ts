interface NativeReferenceEntry {
  element: HTMLElement; signature: string; rect: [number, number, number, number];
  sensitivity: string; allowedOperations: string[];
}
interface NativeReferencePermit {
  entry?: NativeReferenceEntry; error?: string; check?: () => string | undefined; finish?: () => void;
}
interface NativeReferenceState {
  cleanup: () => void;
  inspect: (ref: string) => NativeReferencePermit;
  take: (ref: string) => NativeReferencePermit;
}

/**
 * Reuses the existing ephemeral registry. No new native authority is granted.
 * State in a page realm is untrusted; native ownership/consent checks remain
 * mandatory. Targets/listeners are released on expiry, invalidation or action.
 */
export function rememberNativeSnapshot(collected: { snapshot: unknown; targets: Record<string, Element> }, key = "__kelpieBrowserControlRefs"): unknown {
  const registry = window as unknown as Record<string, NativeReferenceState | undefined>;
  registry[key]?.cleanup();
  const entries = new Map<string, NativeReferenceEntry>();
  const policies = new Map<string, { sensitivity: string; allowedOperations: string[] }>();
  const observed = (collected.snapshot as { elements?: unknown } | null)?.elements;
  if (Array.isArray(observed)) for (const record of observed.slice(0, 1000)) {
    if (!record || typeof record !== "object") continue;
    const item = record as Record<string, unknown>;
    if (typeof item.ref !== "string") continue;
    const sensitivity = typeof item.sensitivity === "string" && ["PUBLIC", "PRIVATE", "SENSITIVE"].includes(item.sensitivity) && !policies.has(item.ref) ? item.sensitivity : "UNKNOWN";
    const allowedOperations = sensitivity !== "UNKNOWN" && sensitivity !== "SENSITIVE" && Array.isArray(item.allowedOperations)
      ? item.allowedOperations.slice(0, 2).filter((operation: unknown): operation is string => operation === "click" || operation === "type") : [];
    policies.set(item.ref, { sensitivity, allowedOperations });
  }
  const ambiguous = new Set<string>();
  const seen = new Map<Element, string>();
  const url = location.href;
  const capturedAt = performance.now();
  const ttlMs = 15_000;
  let used = false;
  let retired = false;
  let changed = false;
  const signature = (el: HTMLElement): string => {
    const owners = "input,textarea,select,button,a,label,[contenteditable],[role='button'],[role='link'],[role='menuitem'],[role='menuitemcheckbox'],[role='menuitemradio'],[role='checkbox'],[role='radio'],[role='switch'],[role='tab'],[role='option']";
    const contexts = new Set<Element>([el]);
    for (let owner = el.parentElement?.closest(owners); owner; owner = owner.parentElement?.closest(owners)) {
      if (contexts.size >= 32) return "!".repeat(8193);
      contexts.add(owner);
    }
    const description: unknown[] = [];
    for (const context of contexts) {
      if (context instanceof HTMLLabelElement && context.control) contexts.add(context.control);
      const control = context instanceof HTMLInputElement || context instanceof HTMLTextAreaElement || context instanceof HTMLSelectElement || context instanceof HTMLButtonElement;
      const labels = control ? Array.from(context.labels ?? []).map((label) => label.textContent).join(" ") : "";
      const form = control ? context.form : context.closest("form");
      const root = context.getRootNode();
      const labelled = (context.getAttribute("aria-labelledby")?.split(/\s+/) ?? []).map((id) => root instanceof Document || root instanceof ShadowRoot ? root.getElementById(id)?.textContent ?? "" : "").join(" ");
      description.push(context.tagName, ...["role", "type", "aria-label", "aria-labelledby", "placeholder", "autocomplete", "href", "formaction", "name", "id"].map((key) => context.getAttribute(key)), labels, labelled,
        context.matches("input,textarea,select,[contenteditable]:not([contenteditable='false'])") ? "" : context.textContent,
        form ? ["action", "name", "id", "aria-label"].map((key) => form.getAttribute(key)) : null);
    }
    return JSON.stringify(description);
  };
  const geometry = (el: HTMLElement): [number, number, number, number] => {
    const r = el.getBoundingClientRect();
    return [r.x, r.y, r.width, r.height];
  };
  let signatureBytes = 0;
  for (const [ref, candidate] of Object.entries(collected.targets).slice(0, 1000)) {
    if (!(candidate instanceof HTMLElement)) continue;
    const previous = seen.get(candidate);
    if (previous) { ambiguous.add(previous); ambiguous.add(ref); }
    seen.set(candidate, ref);
    const value = signature(candidate);
    signatureBytes += value.length;
    if (signatureBytes > 524_288 || value.length > 8192) { changed = true; break; }
    const policy = policies.get(ref);
    entries.set(ref, { element: candidate, signature: value, rect: geometry(candidate), sensitivity: policy?.sensitivity ?? "UNKNOWN", allowedOperations: policy?.allowedOperations ?? [] });
  }
  policies.clear();
  if (Object.keys(collected.targets).length > 1000) changed = true;
  const events = ["pagehide", "popstate", "hashchange", "resize", "scroll", "input", "change"];
  const observer = new MutationObserver(() => { changed = true; cleanup(); });
  const invalidate = (): void => { changed = true; cleanup(); };
  const cleanup = (): void => {
    retired = true;
    entries.clear(); seen.clear(); ambiguous.clear();
    observer.disconnect();
    for (const event of events) window.removeEventListener(event, invalidate, true);
    clearTimeout(timer);
    if (registry[key] === state) Reflect.deleteProperty(registry, key);
  };
  const check = (entry: NativeReferenceEntry): string | undefined => {
    const el = entry.element;
    if (!el.isConnected) return "STALE_ELEMENT";
    if (retired || changed || location.href !== url || performance.now() - capturedAt >= ttlMs || observer.takeRecords().length > 0) return "STALE_OBSERVATION";
    if (!(el.getRootNode() instanceof Document)) return "UNSUPPORTED";
    if (signature(el) !== entry.signature) return "STALE_ELEMENT";
    const current = geometry(el);
    if (current.some((value, index) => !Number.isFinite(value) || Math.abs(value - (entry.rect[index] ?? NaN)) > 0.5)) return "STALE_ELEMENT";
    const style = getComputedStyle(el);
    const [x, y, width, height] = current;
    if (width <= 0 || height <= 0 || style.display === "none" || style.visibility !== "visible" || style.opacity === "0" || el.closest("[hidden],[aria-hidden='true'],[inert]")) return "STALE_ELEMENT";
    if (el.matches(":disabled") || el.getAttribute("aria-disabled") === "true") return "UNSUPPORTED";
    const centerX = x + width / 2, centerY = y + height / 2;
    if (centerX < 0 || centerY < 0 || centerX >= innerWidth || centerY >= innerHeight) return "STALE_ELEMENT";
    const hit = document.elementFromPoint(centerX, centerY);
    if (!hit || !(hit === el || el.contains(hit)) || style.pointerEvents === "none") return "OCCLUDED";
    return undefined;
  };
  const inspect = (ref: string): NativeReferencePermit => {
    if (used || retired) return { error: "STALE_OBSERVATION" };
    if (ambiguous.has(ref)) return { error: "AMBIGUOUS_TARGET" };
    const entry = entries.get(ref);
    if (!entry) return { error: "STALE_ELEMENT" };
    const error = check(entry);
    return error ? { error } : { entry, check: () => check(entry), finish: cleanup };
  };
  const state: NativeReferenceState = {
    cleanup, inspect,
    take(ref) {
      const permit = inspect(ref);
      // Consume before focus or dispatch; a reentrant request cannot act twice.
      used = true;
      if (permit.error) cleanup();
      return permit;
    },
  };
  for (const event of events) window.addEventListener(event, invalidate, true);
  observer.observe(document, { childList: true, subtree: true, attributes: true, characterData: true });
  const timer = setTimeout(cleanup, ttlMs);
  Object.defineProperty(window, key, { configurable: true, writable: false, value: Object.freeze(state) });
  return collected.snapshot;
}

/** Legacy selector adapter; canonical native actions use the atomic function below. */
export function resolveNativeReference(ref: string, key = "__kelpieBrowserControlRefs"): { selector?: string; error?: string } {
  const state = (window as unknown as Record<string, NativeReferenceState | undefined>)[key];
  if (!state) return { error: "STALE_OBSERVATION" };
  const permit = state.inspect(ref);
  if (!permit.entry) return { error: permit.error ?? "STALE_ELEMENT" };
  const el = permit.entry.element;
  const parts: string[] = [];
  for (let node: Element | null = el; node; node = node.parentElement) {
    const parent: Element | null = node.parentElement;
    const index = parent ? Array.from(parent.children).indexOf(node) + 1 : 1;
    parts.unshift(node.tagName.toLowerCase() + ":nth-child(" + String(index) + ")");
  }
  const selector = parts.join(" > ");
  const matches = document.querySelectorAll(selector);
  if (matches.length !== 1 || matches[0] !== el) return { error: "STALE_ELEMENT" };
  return { selector };
}

/**
 * One atomic action on an observed object. Legacy "ok" acknowledges dispatch.
 * R5 additionally requires a synchronous local DOM/control effect; neither mode
 * claims business completion. Native still fences authority and document identity.
 */
export function actOnNativeReference(ref: string, key: string, method: string, text = "", requireEffect = false): { ok?: true; effectVerified?: true; error?: string; mayHaveCompleted?: boolean } {
  const state = (window as unknown as Record<string, NativeReferenceState | undefined>)[key];
  if (!state) return { error: "STALE_OBSERVATION", mayHaveCompleted: false };
  const permit = state.take(ref);
  if (!permit.entry) return { error: permit.error ?? "STALE_ELEMENT", mayHaveCompleted: false };
  const el = permit.entry.element;
  try {
    // Reuse the canonical reader's privacy policy, including related labels and
    // actionable ancestor/form context; missing observation metadata grants no action.
    if (permit.entry.sensitivity === "SENSITIVE") return { error: "NEEDS_HUMAN", mayHaveCompleted: false };
    const metadata = ["type", "autocomplete", "name", "id", "aria-label", "placeholder", "href", "formaction"]
      .map((attribute) => el.getAttribute(attribute) ?? "").join(" ") + " " +
      (el.matches("input,textarea,select,[contenteditable]") ? "" : el.textContent) + " " +
      (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement
        ? Array.from(el.labels ?? []).map((label) => label.textContent).join(" ") : "");
    const anchor = el.closest("a[href]");
    const control = el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement || el instanceof HTMLButtonElement;
    const form = control ? el.form : el.closest("form");
    if ((anchor instanceof HTMLAnchorElement && (!["http:", "https:"].includes(anchor.protocol) || anchor.username || anchor.password)) ||
      (form && Array.from(form.elements).some(field => field.matches('input[type="password"],[autocomplete="one-time-code" i],[autocomplete^="cc-" i]'))) ||
      (form && form.method.toLowerCase() !== "get" && el.matches('button,[role="button"],input[type="submit"],input[type="reset"]')) ||
      /password|one-time|otp|cc-|credit.?card|card.?number|cvv|cvc|token|cookie|secret|captcha|checkout|payment|transfer|bank|iban|delete|erase|remove|publish|sign.?in|log.?in|رمز|گذرواژه|کارت|پرداخت|بانک|شبا|درگاه|انتقال|حذف|انتشار|ورود/i.test(metadata) ||
      el.closest("[data-sensitive],[data-private],[aria-secret]") ||
      (el instanceof HTMLInputElement && ["password", "file", "hidden", "submit", "image"].includes(el.type)) ||
      (el instanceof HTMLButtonElement && el.type === "submit" && el.form)) return { error: "NEEDS_HUMAN", mayHaveCompleted: false };
    if (method !== "tab.click" && method !== "tab.type") return { error: "UNSUPPORTED", mayHaveCompleted: false };
    if (requireEffect && method === "tab.type" && text.length === 0) return { error: "UNSUPPORTED", mayHaveCompleted: false };
    if (!permit.entry.allowedOperations.includes(method === "tab.click" ? "click" : "type")) return { error: "UNSUPPORTED", mayHaveCompleted: false };
    const clickable = (): boolean => el instanceof HTMLInputElement ? ["button", "reset", "checkbox", "radio"].includes(el.type)
      : el instanceof HTMLButtonElement || (el instanceof HTMLAnchorElement && el.hasAttribute("href")) ||
        (!el.matches("input,textarea,select,[contenteditable]:not([contenteditable='false'])") &&
          ["button", "link", "menuitem", "menuitemcheckbox", "menuitemradio", "checkbox", "radio", "switch", "tab", "option"].includes(el.getAttribute("role") ?? ""));
    const editable = (): boolean =>
      ((el instanceof HTMLInputElement && ["text", "search", "tel", "url", "email"].includes(el.type)) ||
        el instanceof HTMLTextAreaElement || el.isContentEditable) &&
      !el.matches(":disabled") && !((el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) && el.readOnly);
    if (method === "tab.click" ? !clickable() : !editable()) return { error: "UNSUPPORTED", mayHaveCompleted: false };
    const mayHaveCompleted = true; // Focus itself can invoke page handlers.
    el.focus({ preventScroll: true });
    const error = permit.check?.();
    if (error) return { error, mayHaveCompleted };
    if (method === "tab.click" ? !clickable() : !editable()) return { error: "UNSUPPORTED", mayHaveCompleted };
    // Compare bounded final visible state, not mutation notifications. Transient
    // changes reverted by a handler, private data-* counters and focus-only changes
    // cannot establish an effect. This local string is never returned or persisted.
    const visibleScene = (): string | undefined => {
      const body = document.querySelector("body");
      if (!body) return undefined;
      const parts = [JSON.stringify([scrollX, scrollY, innerWidth, innerHeight])];
      const walker = document.createTreeWalker(body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
      let count = 0, bytes = parts[0]?.length ?? 0;
      for (let node: Node | null = body; node; node = walker.nextNode()) {
        if (++count > 4096) return undefined;
        const element = node instanceof Element ? node : node.parentElement;
        if (!(element instanceof HTMLElement) || element.closest("[hidden],[aria-hidden='true']")) continue;
        const rect = element.getBoundingClientRect(), style = getComputedStyle(element);
        if (rect.width <= 0 || rect.height <= 0 || rect.right <= 0 || rect.bottom <= 0 || rect.left >= innerWidth || rect.top >= innerHeight ||
          style.display === "none" || style.visibility !== "visible" || style.opacity === "0") continue;
        let hidden = false, depth = 0;
        for (let parent = element.parentElement; parent; parent = parent.parentElement) {
          if (++depth > 64 || getComputedStyle(parent).opacity === "0") { hidden = true; break; }
        }
        if (hidden) continue;
        const hit = document.elementFromPoint(Math.max(0, Math.min(innerWidth - 1, rect.left + rect.width / 2)),
          Math.max(0, Math.min(innerHeight - 1, rect.top + rect.height / 2)));
        if (!hit || !(hit === element || element.contains(hit) || hit.contains(element))) continue;
        const item = node.nodeType === Node.TEXT_NODE
          ? ["text", node.textContent?.slice(0, 256)]
          : ["element", element.tagName, rect.x, rect.y, rect.width, rect.height,
            style.color, style.backgroundColor, style.opacity, style.transform, style.fontSize, style.fontWeight,
            element instanceof HTMLInputElement && ["checkbox", "radio"].includes(element.type) ? element.checked : null,
            ...["aria-expanded", "aria-checked", "aria-selected", "aria-pressed", "open"].map(attribute => element.getAttribute(attribute))];
        const encoded = JSON.stringify(item);
        bytes += encoded.length;
        if (bytes > 131072) return undefined;
        parts.push(encoded);
      }
      return parts.join("\n");
    };
    // Arm after focus and its revalidation.
    const beforeScene = requireEffect && method === "tab.click" ? visibleScene() : undefined;
    if (requireEffect && method === "tab.click" && beforeScene === undefined) return { error: "UNSUPPORTED", mayHaveCompleted: true };
    let expectedValue: string | undefined;
    let readValue: (() => string) | undefined;
    if (method === "tab.click") el.click();
    else if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) {
      const prototype = el instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
      const value = Object.getOwnPropertyDescriptor(prototype, "value");
      if (!value?.get || !value.set) return { error: "UNSUPPORTED", mayHaveCompleted };
      const getter = value.get.bind(el);
      expectedValue = String(value.get.call(el)) + text;
      readValue = () => String(getter());
      value.set.call(el, expectedValue);
      el.dispatchEvent(new InputEvent("input", { bubbles: true, inputType: "insertText", data: text }));
    } else {
      expectedValue = el.textContent + text;
      readValue = () => el.textContent;
      el.append(document.createTextNode(text));
      el.dispatchEvent(new InputEvent("input", { bubbles: true, inputType: "insertText", data: text }));
    }
    if (requireEffect) {
      let verified = false;
      if (method === "tab.type") {
        // A handler may reject the value or substitute an equal-valued clone.
        verified = el.isConnected && el.ownerDocument === document && editable() && readValue?.() === expectedValue;
      } else {
        const afterScene = visibleScene();
        verified = afterScene !== undefined && afterScene !== beforeScene;
      }
      if (!verified) return { error: "EFFECT_UNVERIFIED", mayHaveCompleted: true };
      return { ok: true, effectVerified: true };
    }
    return { ok: true };
  } finally {
    permit.finish?.();
  }
}
