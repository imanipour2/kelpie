import type { BrowserElement, BrowserSnapshot } from "./protocol.js";

/**
 * Fixed, self-contained reader, serialized into the native gateway contract.
 * Page content is untrusted data. This is neither consent nor an attestation
 * that a hostile page has faithfully described its pixels.
 */
export function collectBrowserSnapshot(options: {
  maxElements?: number; maxChars?: number; prefix: string; includeShadow?: boolean;
}): { snapshot: BrowserSnapshot; targets: Record<string, Element> } {
  const maxElements = Math.min(1000, Math.max(1, options.maxElements ?? 200));
  const maxChars = Math.min(100_000, Math.max(256, options.maxChars ?? 16_000));
  const elements: BrowserElement[] = [];
  const targets: Record<string, Element> = Object.create(null) as Record<string, Element>;
  const privacyRects: { x: number; y: number; width: number; height: number }[] = [];
  const reasons = new Set<string>();
  const privateValues = new Set<string>();
  const roots: (Document | ShadowRoot)[] = [document];
  const budget = { truncated: false };
  let privacyBudget = 0;
  let privacyUnknown = false;
  let scanned = 0;
  let totalCandidates = 0;
  const editors = "input,textarea,select,[contenteditable]:not([contenteditable='false'])";
  const ignored = editors + ",script,style,noscript,template,[hidden],[aria-hidden='true']";
  const sensitive = /password|passwd|one-time|otp|cc-|credit.?card|card.?number|cvv|cvc|token|cookie|secret|pin(?:$|[\s_-])|captcha|checkout|payment|pay now|purchase|transfer|delete|erase|remove|publish|sign.?in|log.?in|auth(?:entication|orization)?|رمز|گذرواژه|کارت|امنیتی|پرداخت|انتقال|حذف|انتشار|ورود|کپچا/i;
  const clickRoles = ["button", "link", "menuitem", "menuitemcheckbox", "menuitemradio", "checkbox", "radio", "switch", "tab", "option"];
  const actionOwners = "input,textarea,select,button,a,label,[contenteditable]," + clickRoles.map((role) => '[role="' + role + '"]').join(",");
  const rectFor = (el: Element) => {
    const r = el.getBoundingClientRect();
    return { x: r.x, y: r.y, width: r.width, height: r.height };
  };
  const privateValue = (value: string, reason = "PRIVATE_FORM_DATA"): void => {
    if (!value) return;
    reasons.add(reason);
    privacyBudget += value.length;
    if (privacyBudget > 65_536 || privateValues.size >= 1000) {
      privacyUnknown = true; reasons.add("PRIVACY_SCAN_LIMIT"); return;
    }
    privateValues.add(value);
    const normalized = value.replace(/\s+/g, " ").trim();
    if (normalized) privateValues.add(normalized);
  };
  // Values are used locally only to remove reflected copies from public labels.
  // They are never copied into targets, metadata, diagnostics or the return value.
  for (const root of roots) {
    for (const el of root.querySelectorAll("*")) {
      if (++scanned > 5000) { privacyUnknown = true; reasons.add("PRIVACY_SCAN_LIMIT"); break; }
      const tag = el.tagName.toLowerCase();
      if (["iframe", "frame", "object", "embed", "canvas", "video"].includes(tag)) reasons.add("OPAQUE_SURFACE");
      if (tag.includes("-")) reasons.add("CUSTOM_ELEMENT_OPAQUE");
      if (el.shadowRoot) { reasons.add("SHADOW_SURFACE"); roots.push(el.shadowRoot); }
      if (el.hasAttribute("data-private") || el.hasAttribute("data-sensitive") || el.hasAttribute("aria-secret")) {
        reasons.add("PRIVATE_SURFACE");
        privateValue(el.textContent, "PRIVATE_SURFACE");
      }
      if (!el.matches(editors)) continue;
      const r = rectFor(el);
      if (r.width > 0 && r.height > 0) privacyRects.push(r);
      if (el instanceof HTMLInputElement && !["button", "submit", "reset", "checkbox", "radio", "image"].includes(el.type)) privateValue(el.value);
      else if (el instanceof HTMLTextAreaElement) privateValue(el.value);
      else if (el instanceof HTMLSelectElement) for (const option of el.selectedOptions) privateValue(option.value);
      else if (el instanceof HTMLElement && el.isContentEditable) privateValue(el.textContent);
    }
    if (privacyUnknown) break;
  }
  // A one-letter option value must not erase unrelated words such as "Save".
  // Short values are still removed when reflected as a complete token/name.
  const shortValue = (secret: string): RegExp => new RegExp("(^|[^\\p{L}\\p{N}_])" + secret.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "(?=$|[^\\p{L}\\p{N}_])", "gu");
  const containsPrivate = (text: string): boolean => Array.from(privateValues).some((secret) => secret.length > 2 ? text.includes(secret) : shortValue(secret).test(text));
  const clean = (text: string, limit = 256): string => {
    if (privacyUnknown) return "redacted";
    let value = text;
    for (const secret of privateValues) value = secret.length > 2 ? value.split(secret).join("redacted") : value.replace(shortValue(secret), "$1redacted");
    value = value.replace(/\s+/g, " ").trim();
    if (value.length > limit) budget.truncated = true;
    return value.slice(0, limit);
  };
  let safeUrl = "redacted";
  try {
    const parsed = new URL(location.href);
    for (const value of [parsed.username, parsed.password, parsed.hash.slice(1), ...Array.from(parsed.searchParams.values())]) {
      privateValue(value, "PRIVATE_URL_DATA");
      try { privateValue(decodeURIComponent(value), "PRIVATE_URL_DATA"); } catch { /* Encoded data stays private. */ }
    }
    if (parsed.search || parsed.hash || parsed.username || parsed.password) reasons.add("PRIVATE_URL_DATA");
    // Query, fragment and credentials never belong in an observation.
    safeUrl = ["http:", "https:"].includes(parsed.protocol) ? parsed.origin + parsed.pathname
      : parsed.protocol === "about:" ? "about:blank" : parsed.protocol;
  } catch { /* Invalid and opaque URLs remain redacted. */ }
  const url = clean(safeUrl, Math.min(4096, Math.floor(maxChars / 2)));
  const title = clean(document.title, Math.min(1024, Math.floor(maxChars / 4)));
  let text = "url: " + url + "\ntitle: " + title;
  let visited = 0;
  const content = (node: Node, depth = 0): string => {
    if (depth > 40 || (node instanceof Element && (node.matches(ignored) || node.closest("[contenteditable]:not([contenteditable='false'])") || getComputedStyle(node).visibility === "hidden" || getComputedStyle(node).display === "none"))) return "";
    if (node.nodeType === Node.TEXT_NODE) {
      const value = node.textContent ?? "";
      if (value.length > 256) budget.truncated = true;
      // Redact before truncation so a prefix of a secret cannot escape.
      return clean(value);
    }
    let value = "";
    for (const child of node.childNodes) {
      value += " " + content(child, depth + 1);
      if (value.length >= 512) { if (child.nextSibling) budget.truncated = true; break; }
    }
    return value;
  };
  const roles: Record<string, string> = { button: "button", select: "combobox", textarea: "textbox", nav: "navigation", main: "main", img: "img" };
  const validRoles = new Set(("button checkbox combobox gridcell heading img link listbox listitem main menu menuitem menuitemcheckbox menuitemradio navigation option radio searchbox slider spinbutton switch tab textbox treeitem").split(" "));
  const sensitiveContext = (element: Element): boolean => {
    const contexts = new Set<Element>([element]);
    for (let owner = element.parentElement?.closest(actionOwners); owner; owner = owner.parentElement?.closest(actionOwners)) {
      if (contexts.size >= 32) return true;
      contexts.add(owner);
    }
    for (const context of contexts) {
      if (context instanceof HTMLLabelElement && context.control) contexts.add(context.control);
      const form = (context instanceof HTMLInputElement || context instanceof HTMLTextAreaElement || context instanceof HTMLSelectElement || context instanceof HTMLButtonElement)
        ? context.form : context.closest("form");
      const root = context.getRootNode();
      const labelled = (context.getAttribute("aria-labelledby")?.split(/\s+/) ?? []).map((id) => root instanceof Document || root instanceof ShadowRoot ? root.getElementById(id)?.textContent ?? "" : "").join(" ");
      const labels = context instanceof HTMLInputElement || context instanceof HTMLTextAreaElement || context instanceof HTMLSelectElement || context instanceof HTMLButtonElement
        ? Array.from(context.labels ?? []).map((label) => label.textContent).join(" ") : "";
      const hints = ["type", "autocomplete", "name", "id", "aria-label", "placeholder", "href", "formaction"].map((key) => context.getAttribute(key) ?? "").join(" ") + " " + labels + " " + labelled + " " +
        (context.matches(editors) ? "" : context.textContent) + " " +
        (form ? ["action", "name", "id", "aria-label"].map((key) => form.getAttribute(key) ?? "").join(" ") : "");
      if (privacyUnknown || sensitive.test(hints) || containsPrivate(hints) || context.closest("[data-private],[data-sensitive],[aria-secret]") ||
        (context instanceof HTMLInputElement && ["password", "hidden", "file", "submit", "image"].includes(context.type)) ||
        (context instanceof HTMLButtonElement && context.type === "submit" && !!form)) return true;
    }
    return false;
  };
  const visit = (root: Document | ShadowRoot): void => {
    for (const el of root.querySelectorAll(root instanceof Document ? "body *" : "*")) {
      if (++visited > 5000 || elements.length >= maxElements) { budget.truncated = true; return; }
      const editor = el.closest("[contenteditable]:not([contenteditable='false'])");
      if (editor && editor !== el) continue;
      const tag = el.tagName.toLowerCase();
      const style = getComputedStyle(el);
      if (["script", "style", "noscript", "template", "option"].includes(tag) || el.closest("[hidden],[aria-hidden='true']")) continue;
      if (el.getClientRects().length === 0 || style.visibility === "hidden" || style.visibility === "collapse") continue;
      const type = el instanceof HTMLInputElement ? el.type : (el.getAttribute("type") ?? "text").toLowerCase();
      if (tag === "input" && type.toLowerCase() === "hidden") continue;
      const inputRole = type === "checkbox" ? "checkbox" : type === "radio" ? "radio" : ["submit", "reset", "button", "image"].includes(type) ? "button" : "textbox";
      const labelled = (el.getAttribute("aria-labelledby")?.split(/\s+/) ?? []).map((id) => {
        const label = root.getElementById(id); return label ? content(label) : "";
      }).join(" ").trim();
      const labels = el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement
        ? Array.from(el.labels ?? []).map((label) => content(label)).join(" ") : "";
      const svgTitle = tag === "svg" ? el.querySelector(":scope > title")?.textContent : "";
      const namedSvg = tag === "svg" && [labelled, el.getAttribute("aria-label"), svgTitle].some((value) => value?.trim());
      const explicitRole = el.getAttribute("role") ?? "";
      const role = (validRoles.has(explicitRole) ? explicitRole : undefined) ?? (tag === "input" ? inputRole : namedSvg ? "img" : tag === "a" && el.hasAttribute("href") ? "link" : /^h[1-6]$/.test(tag) ? "heading" : roles[tag]);
      const isSensitive = sensitiveContext(el);
      const name = isSensitive ? "redacted" : clean([labelled, el.getAttribute("aria-label"), labels, el.getAttribute("alt"), el.getAttribute("placeholder"), svgTitle, content(el)].find((value) => value?.trim()) ?? "");
      if (role || (["p", "li", "td", "th"].includes(tag) && name)) {
        totalCandidates++;
        const ref = options.prefix + String(elements.length + 1);
        const line = "\n[" + ref + "] " + (role ?? tag) + " " + JSON.stringify(name);
        if (text.length + line.length > maxChars) { budget.truncated = true; return; }
        const rect = rectFor(el);
        const visible = rect.width > 0 && rect.height > 0 && rect.x < innerWidth && rect.y < innerHeight && rect.x + rect.width > 0 && rect.y + rect.height > 0;
        const enabled = !el.matches(":disabled") && el.getAttribute("aria-disabled") !== "true" && !el.closest("[inert]");
        const isPrivate = el.matches(editors);
        const allowedOperations: string[] = [];
        if (!isSensitive && enabled && visible && el instanceof HTMLElement && el.getRootNode() instanceof Document) {
          const clickable = el instanceof HTMLInputElement ? ["button", "reset", "checkbox", "radio"].includes(el.type)
            : el instanceof HTMLButtonElement || (el instanceof HTMLAnchorElement && el.hasAttribute("href")) || (!el.matches(editors) && clickRoles.includes(explicitRole));
          const editable = ((el instanceof HTMLInputElement && ["text", "search", "tel", "url", "email"].includes(el.type)) || el instanceof HTMLTextAreaElement || el.isContentEditable) &&
            !((el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) && el.readOnly);
          if (clickable) allowedOperations.push("click");
          if (editable) allowedOperations.push("type");
        }
        text += line;
        elements.push({ ref, role: role ?? tag, name, rect, visible, enabled, sensitivity: isSensitive ? "SENSITIVE" : isPrivate ? "PRIVATE" : "PUBLIC", value: null, allowedOperations });
        targets[ref] = el;
      }
      if (el.shadowRoot && !editor && options.includeShadow !== false) visit(el.shadowRoot);
    }
  };
  visit(document);
  const visual = window.visualViewport;
  const limitations = ["VIEWPORT_OBSERVATION_NOT_TASK_COMPLETENESS", "CROSS_ORIGIN_FRAMES_OPAQUE", "CLOSED_SHADOW_ROOTS_UNKNOWN"];
  if (reasons.size) limitations.push(...reasons);
  if (budget.truncated) limitations.push("ELEMENT_OR_TEXT_LIMIT");
  return { targets, snapshot: {
    url, title, elements, text, truncated: budget.truncated,
    viewport: { width: innerWidth, height: innerHeight },
    devicePixelRatio: Number.isFinite(window.devicePixelRatio) && window.devicePixelRatio > 0 ? window.devicePixelRatio : 1,
    visualViewport: visual ? { offsetLeft: visual.offsetLeft, offsetTop: visual.offsetTop, pageLeft: visual.pageLeft, pageTop: visual.pageTop, width: visual.width, height: visual.height, scale: visual.scale } : null,
    scroll: { x: scrollX, y: scrollY },
    privacyRects, screenshotBlocked: reasons.size > 0, screenshotBlockReasons: Array.from(reasons),
    coverage: { status: reasons.size ? "UNKNOWN" : "INCOMPLETE", observed: elements.length, scanned: Math.min(scanned, 5000), totalCandidates: budget.truncated ? null : totalCandidates, truncated: budget.truncated, unobserved: budget.truncated ? null : Math.max(0, totalCandidates - elements.length), limitations },
  } };
}
