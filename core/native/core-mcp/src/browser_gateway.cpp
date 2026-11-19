#include "kelpie/browser_gateway.h"
#include "kelpie/element_selector_script.h"
#include "browser_schema.h"
#include "browser_contract.h"
#include <algorithm>
#include <cmath>
#include <map>
#include <random>
#include <sstream>
#include <iomanip>
#include <set>
#include <string>
namespace kelpie {
namespace {
using json = nlohmann::json;
json Failure(const std::string& code, bool uncertain = false) {
  return {{"version", 1}, {"ok", false}, {"error", {{"code", code},
    {"message", "Browser request refused or failed. Observe the page before continuing."}, {"mayHaveCompleted", uncertain}}}};
}
json Success(json value) { return {{"version", 1}, {"ok", true}, {"value", std::move(value)}}; }
constexpr auto kReferenceTtl = std::chrono::seconds(15);
constexpr std::size_t kMaxObservedTabs = 16;
bool CopyGeometry(const json& source, json& target, const std::string& key,
                  std::initializer_list<const char*> fields, bool nullable = false) {
  if (!source.contains(key)) return true;
  const auto& value = source.at(key);
  if (nullable && value.is_null()) { target[key] = nullptr; return true; }
  if (!value.is_object()) return false;
  json bounded = json::object();
  for (const auto field : fields) {
    if (!value.contains(field) || !value[field].is_number()) return false;
    const double number = value[field].get<double>();
    if (!std::isfinite(number) || std::abs(number) > 10000000 ||
        ((std::string(field) == "width" || std::string(field) == "height") && number < 0) ||
        (std::string(field) == "scale" && number <= 0)) return false;
    bounded[field] = number;
  }
  target[key] = std::move(bounded); return true;
}
bool CopyFixedStrings(const json& source, json& target, const std::string& key) {
  if (!source.contains(key)) return true;
  const auto& values = source[key];
  if (!values.is_array() || values.size() > 32) return false;
  static const std::set<std::string> operations = {"click", "type", "select-option"};
  static const std::set<std::string> reasons = {"VIEWPORT_OBSERVATION_NOT_TASK_COMPLETENESS", "CROSS_ORIGIN_FRAMES_OPAQUE",
    "CLOSED_SHADOW_ROOTS_UNKNOWN", "ELEMENT_OR_TEXT_LIMIT", "PRIVACY_SCAN_LIMIT", "OPAQUE_SURFACE",
    "CUSTOM_ELEMENT_OPAQUE", "SHADOW_SURFACE", "PRIVATE_SURFACE", "PRIVATE_FORM_DATA", "PRIVATE_URL_DATA", "selector-unavailable"};
  const auto& allowed = key == "allowedOperations" ? operations : reasons;
  for (const auto& value : values) if (!value.is_string() || !allowed.count(value.get<std::string>())) return false;
  target[key] = values; return true;
}
bool CopySnapshotMetadata(const json& source, json& target) {
  if (source.contains("devicePixelRatio")) {
    if (!source["devicePixelRatio"].is_number()) return false;
    const double ratio = source["devicePixelRatio"].get<double>();
    if (!std::isfinite(ratio) || ratio <= 0 || ratio > 100) return false;
    target["devicePixelRatio"] = ratio;
  }
  if (!CopyGeometry(source, target, "viewport", {"width", "height"}) ||
      !CopyGeometry(source, target, "visualViewport", {"offsetLeft", "offsetTop", "pageLeft", "pageTop", "width", "height", "scale"}, true) ||
      !CopyGeometry(source, target, "scroll", {"x", "y"}) ||
      !CopyFixedStrings(source, target, "screenshotBlockReasons")) return false;
  if (source.contains("screenshotBlocked")) {
    if (!source["screenshotBlocked"].is_boolean()) return false;
    target["screenshotBlocked"] = source["screenshotBlocked"];
  }
  if (source.contains("privacyRects")) {
    if (!source["privacyRects"].is_array() || source["privacyRects"].size() > 5000) return false;
    target["privacyRects"] = json::array();
    for (const auto& rect : source["privacyRects"]) {
      json bounded;
      if (!CopyGeometry({{"rect", rect}}, bounded, "rect", {"x", "y", "width", "height"})) return false;
      target["privacyRects"].push_back(bounded["rect"]);
    }
  }
  if (source.contains("coverage")) {
    const auto& coverage = source["coverage"];
    if (!coverage.is_object()) return false;
    const auto status = coverage.value("status", std::string());
    if (status != "INCOMPLETE" && status != "UNKNOWN") return false;
    json bounded = {{"status", status}};
    for (const auto field : {"observed", "scanned", "totalCandidates", "unobserved"}) {
      if ((std::string(field) == "unobserved" || std::string(field) == "totalCandidates") &&
          coverage.contains(field) && coverage[field].is_null()) { bounded[field] = nullptr; continue; }
      if (!coverage.contains(field) || !coverage[field].is_number_integer() || coverage[field] < 0 || coverage[field] > 100000) return false;
      bounded[field] = coverage[field];
    }
    if (!coverage.contains("truncated") || !coverage["truncated"].is_boolean() || !CopyFixedStrings(coverage, bounded, "limitations")) return false;
    bounded["truncated"] = coverage["truncated"];
    target["coverage"] = std::move(bounded);
  }
  return true;
}
bool CopyElementMetadata(const json& source, json& target) {
  if (!CopyGeometry(source, target, "rect", {"x", "y", "width", "height"}) ||
      !CopyFixedStrings(source, target, "allowedOperations")) return false;
  for (const auto field : {"visible", "enabled"}) if (source.contains(field)) {
    if (!source[field].is_boolean()) return false;
    target[field] = source[field];
  }
  if (source.contains("sensitivity")) {
    const auto sensitivity = source["sensitivity"].get<std::string>();
    if (sensitivity != "PUBLIC" && sensitivity != "PRIVATE" && sensitivity != "SENSITIVE") return false;
    target["sensitivity"] = sensitivity;
    if (sensitivity == "SENSITIVE") target["name"] = "redacted";
  }
  // Form values are never part of this contract. This structural projection is
  // not attestation of public page text or a hostile renderer's truthfulness.
  if (source.contains("value")) target["value"] = nullptr;
  return true;
}
std::string Identity() {
  std::random_device random;
  std::ostringstream output;
  output << std::hex << std::setfill('0');
  for (int i = 0; i < 4; ++i) output << std::setw(8) << static_cast<unsigned int>(random());
  return output.str();
}
const std::map<std::string, std::string> kRoutes = {
  {"tabs.list", "get-tabs"}, {"tabs.new", "new-tab"}, {"tabs.select", "switch-tab"}, {"tabs.close", "close-tab"},
  {"tab.goto", "navigate"}, {"tab.back", "back"}, {"tab.forward", "forward"}, {"tab.reload", "reload"},
  {"tab.url", "get-current-url"}, {"tab.title", "get-current-url"}, {"tab.screenshot", "screenshot"},
  {"page.press", "press-key"}, {"page.scroll", "scroll"}, {"tab.click", "click"}, {"tab.type", "type"}
};
}
std::string CollectBrowserSnapshotExpression(const nlohmann::json& options) {
  return "(" + std::string(browser_assets::reader) + ")(" + options.dump() + ")";
}
std::string CollectBrowserSelectorSnapshotExpression(const nlohmann::json& options) {
  return "((read)=>{" + ElementSelectorBuilderScript() +
      "const count=read.snapshot.elements.length;"
      "read.snapshot.elements=read.snapshot.elements.flatMap(item=>{"
      "const node=read.targets[item.ref],selector=kelpieBuildSelector(node,true);"
      "return selector?[{...item,selector,tag:kelpieSafeTag(node),label:item.name}]:[];});"
      "if(read.snapshot.elements.length!==count){read.snapshot.truncated=true;"
      "if(read.snapshot.coverage){read.snapshot.coverage.observed=read.snapshot.elements.length;"
      "read.snapshot.coverage.truncated=true;read.snapshot.coverage.unobserved=null;"
      "read.snapshot.coverage.limitations.push('selector-unavailable');}}"
      "return read.snapshot;})(" + CollectBrowserSnapshotExpression(options) + ")";
}
struct BrowserGateway::Impl {
  json config, request, step = {{"kind", "complete"}, {"response", nullptr}};
  std::set<std::string> native, methods, mutations;
  struct ReferenceLease {
    std::set<std::string> elements;
    std::chrono::steady_clock::time_point captured;
  };
  std::map<std::string, ReferenceLease> refs;
  MonotonicClock now;
  std::chrono::steady_clock::time_point observation_started;
  std::string session = Identity(), stage, key;
  unsigned long generation = 0, invalidation_epoch = 0, operation_epoch = 0;
  bool active = false, uncertain = false, mutation = false, selected_effect = false;
  bool mcp = false, notification = false, extension = false;
  json rpc_id;
  explicit Impl(json value, MonotonicClock clock) : config(std::move(value)), now(std::move(clock)) {
    for (const auto& method : config.value("nativeMethods", json::array())) native.insert(method.get<std::string>());
    for (const auto& method : json::parse(browser_assets::mutations)) mutations.insert(method.get<std::string>());
    methods = {"documentation", "capabilities"};
    for (const auto& route : kRoutes) if (config.value("browserAvailable", true) && native.count(route.second)) methods.insert(route.first);
    if (config.value("fixedReader", false)) methods.insert("tab.snapshot");
    else { methods.erase("tab.click"); methods.erase("tab.type"); }
    if (!config.value("allowActions", true)) {
      for (const auto& method : mutations) methods.erase(method);
    }
    if (config.value("targeting", std::string("direct")) == "selected" && !native.count("get-tabs")) {
      for (auto it = methods.begin(); it != methods.end();) {
        if (it->rfind("tab.", 0) == 0 || it->rfind("page.", 0) == 0) it = methods.erase(it); else ++it;
      }
    }
  }
  std::string ReferenceError(const std::string& tab, const std::string& ref) {
    const auto lease = refs.find(tab);
    if (lease == refs.end()) return "STALE_REFERENCE";
    const auto elapsed = now() - lease->second.captured;
    if (elapsed < std::chrono::steady_clock::duration::zero() || elapsed >= kReferenceTtl) {
      refs.erase(lease); return "STALE_OBSERVATION";
    }
    return lease->second.elements.count(ref) ? std::string() : "STALE_REFERENCE";
  }
  void Finish(json response) {
    if (response.is_object() && response.contains("error")) response["error"]["mayHaveCompleted"] = uncertain || response["error"].value("mayHaveCompleted", false);
    if (mutation) {
      const std::string method = request.value("method", std::string());
      if (method == "tabs.select" || method == "tabs.new" || method == "tabs.close") refs.clear();
      else if (request.contains("tabId")) refs.erase(request["tabId"].get<std::string>());
      else if (request.contains("params") && request["params"].contains("tabId") && request["params"]["tabId"].is_string()) refs.erase(request["params"]["tabId"].get<std::string>());
      else refs.clear();
    }
    active = false;
    if (mcp) response = notification ? json(nullptr) : json{{"jsonrpc", "2.0"}, {"id", rpc_id}, {"result", BrowserMcpResult(response)}};
    step = {{"kind", "complete"}, {"response", std::move(response)}};
  }
  void Dispatch(std::string method, json params, std::string next_stage, bool mutates = false) {
    if (method == "switch-tab" || method == "new-tab" || method == "close-tab") refs.clear();
    stage = std::move(next_stage);
    uncertain = uncertain || mutates;
    selected_effect = selected_effect || (method == "switch-tab" && mutates);
    step = {{"kind", "dispatch"}, {"method", std::move(method)}, {"params", std::move(params)}, {"mutates", mutates}};
  }
  void Execute() {
    const std::string method = request["method"];
    const std::string tab = request.value("tabId", std::string());
    json params = request.value("params", json::object());
    if (!tab.empty()) params["tabId"] = tab;
    if (params.contains("timeoutMs")) { params["timeout"] = params["timeoutMs"]; params.erase("timeoutMs"); }
    if (extension) { Dispatch(method, params, "execute", mutation); return; }
    if (method == "capabilities") { Finish(Success(Capabilities())); return; }
    if (method == "documentation") { Finish(Success(browser_assets::documentation)); return; }
    if (method == "tab.snapshot") {
      refs.erase(tab);
      observation_started = now();
      operation_epoch = invalidation_epoch;
      key = "__kelpie_" + session;
      params.erase("tabId");
      params["prefix"] = session + "s" + std::to_string(++generation) + "e";
      params["includeShadow"] = false;
      const std::string collected = CollectBrowserSnapshotExpression(params);
      // One-shot read-only observers never retain renderer targets/listeners.
      const std::string expression = config.value("allowActions", true)
        ? "(" + std::string(browser_assets::remember) + ")(" + collected + "," + json(key).dump() + ")"
        : "(" + collected + ").snapshot";
      Dispatch("browser-control-evaluate", {{"tabId", tab}, {"expression", expression}}, "observe"); return;
    }
    if (method == "tab.click" || method == "tab.type") {
      const std::string ref = params["ref"];
      const std::string refusal = ReferenceError(tab, ref);
      if (!refusal.empty()) { Finish(Failure(refusal)); return; }
      operation_epoch = invalidation_epoch;
      const std::string expression = "(" + std::string(browser_assets::action) + ")(" + json(ref).dump() + "," + json(key).dump() + "," + json(method).dump() + "," + json(params.value("text", std::string())).dump() + "," + json(config.value("requireActionEffect", false)).dump() + ")";
      Dispatch("browser-control-evaluate", {{"tabId", tab}, {"expression", expression}}, "action", true); return;
    }
    if (method == "tab.screenshot" && (params.value("fullPage", false) || params.value("format", std::string("png")) != "png" || params.contains("quality"))) {
      Finish(Failure("UNSUPPORTED")); return;
    }
    if (method == "tab.screenshot") { params["resolution"] = "viewport"; params.erase("fullPage"); }
    Dispatch(kRoutes.at(method), params, "execute", mutation);
  }
  json Capabilities() const { return {{"version", 1}, {"methods", methods}, {"extensions", {"kelpie"}}}; }
};
BrowserGateway::BrowserGateway(json config, MonotonicClock now) : impl_(std::make_unique<Impl>(std::move(config), std::move(now))) {}
BrowserGateway::~BrowserGateway() = default;
json BrowserGateway::Capabilities() const { return impl_->Capabilities(); }
json BrowserGateway::Next() const { return impl_->step; }
json BrowserGateway::Begin(json request) {
  if (impl_->active) return {{"kind", "complete"}, {"response", Failure("BUSY")}};
  impl_->mcp = false; impl_->extension = false;
  impl_->request = request; impl_->active = true; impl_->uncertain = false; impl_->mutation = false; impl_->selected_effect = false;
  static const json schema = json::parse(browser_assets::request_schema);
  if (!ValidateBrowserJson(schema, &request)) { impl_->Finish(Failure("INVALID_ARGUMENTS")); return Next(); }
  impl_->request = request;
  const std::string method = request["method"];
  impl_->mutation = impl_->mutations.count(method) != 0;
  if (!impl_->methods.count(method)) { impl_->Finish(Failure("UNSUPPORTED")); return Next(); }
  if (method != "capabilities" && method != "documentation" && method != "tabs.list" && method != "tabs.new" && !request.contains("tabId")) {
    impl_->Finish(Failure("TAB_REQUIRED")); return Next();
  }
  if (impl_->mutation && !impl_->config.value("allowActions", true)) { impl_->Finish(Failure("ACCESS_DENIED")); return Next(); }
  const auto params = request["params"];
  if ((params.contains("timeoutMs") && (params["timeoutMs"] == 0 || params["timeoutMs"] > 30000)) ||
      (method == "tab.screenshot" && (params.value("fullPage", false) || params.value("format", std::string("png")) != "png" || params.contains("quality")))) {
    impl_->Finish(Failure("UNSUPPORTED")); return Next();
  }
  if (method == "tab.click" || method == "tab.type") {
    const std::string refusal = impl_->ReferenceError(request["tabId"], params["ref"]);
    if (!refusal.empty()) { impl_->Finish(Failure(refusal)); return Next(); }
  }
  if (impl_->config.value("requireAuthorization", true)) {
    impl_->stage = "authorize";
    impl_->step = {{"kind", "authorize"}, {"request", request}, {"mutates", impl_->mutation}};
  } else { impl_->stage = "authorize"; Advance({{"success", true}}); }
  return Next();
}
json BrowserGateway::Advance(json result) {
  if (!impl_->active) return Next();
  const auto fail = [&] { impl_->Finish(Failure("COMMAND_FAILED", impl_->uncertain)); };
  try {
    if (!result.is_object() || !result.value("success", false) || result.contains("error")) {
      if (impl_->stage == "authorize" || impl_->stage == "authorize-select") {
        InvalidateReferences();
        impl_->Finish(Failure("ACCESS_DENIED"));
      } else {
        // Preserve only fixed refusal codes, never page/renderer diagnostics.
        static const std::set<std::string> screenshot_refusals = {"SCREENSHOT_TOO_LARGE", "NO_FRESH_FRAME",
          "OBSERVATION_RACE", "NEEDS_HUMAN", "UNSUPPORTED_IN_BACKGROUND", "SENSITIVE_CONTENT",
          "KAI_FEATURE_DISABLED", "INVALID_CAPTURE_GEOMETRY", "STALE_OBSERVATION"};
        const std::string code = result.is_object() && result.contains("error") && result["error"].is_object()
          ? result["error"].value("code", std::string()) : std::string();
        if ((impl_->request["method"] == "tab.screenshot" || impl_->request["method"] == "screenshot" ||
             impl_->request["method"] == "screenshot-annotated") && screenshot_refusals.count(code)) impl_->Finish(Failure(code));
        else fail();
      }
      return Next();
    }
    const std::string method = impl_->request["method"];
    const std::string tab = impl_->request.value("tabId", std::string());
    if (impl_->stage == "authorize") {
      if (!impl_->extension && !tab.empty() && method != "tabs.close" && method != "tabs.select" && impl_->config.value("targeting", std::string("direct")) == "selected")
        impl_->Dispatch("get-tabs", json::object(), "inspect-selected");
      else impl_->Execute();
    } else if (impl_->stage == "inspect-selected") {
      std::string active = result.value("activeTab", std::string());
      for (const auto& item : result.value("tabs", json::array())) if (item.value("active", false)) active = item.at("id").get<std::string>();
      if (active == tab) impl_->Execute();
      else if (method == "tab.click" || method == "tab.type") {
        // Switching away invalidates the observed target. Do not switch back
        // and execute a stale ref merely because the caller supplied its tab.
        InvalidateReferences(); impl_->Finish(Failure("STALE_OBSERVATION"));
      }
      else if (!impl_->config.value("allowActions", true) || !impl_->native.count("switch-tab")) impl_->Finish(Failure("ACCESS_DENIED"));
      else if (impl_->config.value("requireAuthorization", true)) {
        impl_->stage = "authorize-select";
        impl_->step = {{"kind", "authorize"}, {"request", {{"version", 1}, {"method", "tabs.select"}, {"tabId", tab}, {"params", json::object()}}}, {"mutates", true}};
      } else impl_->Dispatch("switch-tab", {{"tabId", tab}}, "select", true);
    } else if (impl_->stage == "authorize-select") impl_->Dispatch("switch-tab", {{"tabId", tab}}, "select", true);
    else if (impl_->stage == "select") impl_->Execute();
    else if (impl_->stage == "observe") {
      if (impl_->operation_epoch != impl_->invalidation_epoch) { impl_->Finish(Failure("OBSERVATION_RACE")); return Next(); }
      const auto elapsed = impl_->now() - impl_->observation_started;
      if (elapsed < std::chrono::steady_clock::duration::zero() || elapsed >= kReferenceTtl) { impl_->Finish(Failure("STALE_OBSERVATION")); return Next(); }
      json snapshot = result.at("result");
      if (!snapshot.is_object() || !snapshot.at("text").is_string() || !snapshot.at("elements").is_array()) { fail(); return Next(); }
      if (!snapshot.at("url").is_string() || !snapshot.at("title").is_string() ||
          snapshot["url"].get<std::string>().size() > 16384 || snapshot["title"].get<std::string>().size() > 4096) { fail(); return Next(); }
      std::string url = snapshot["url"], title = snapshot["title"];
      // The shared URL parser already strips private components. Reject the
      // whole unexpected URL here rather than introducing a second parser.
      if (url.find_first_of("?#@\r\n\t") != std::string::npos ||
          (url.rfind("https://", 0) != 0 && url.rfind("http://", 0) != 0 && url != "about:blank")) url = "redacted";
      const auto params = impl_->request["params"];
      if (snapshot["text"].get<std::string>().size() > static_cast<std::size_t>(params.value("maxChars", 16000)) * 4 || snapshot["elements"].size() > static_cast<std::size_t>(params.value("maxElements", 200))) { fail(); return Next(); }
      json elements = json::array();
      std::set<std::string> observed_refs;
      const std::string prefix = impl_->session + "s" + std::to_string(impl_->generation) + "e";
      for (const auto& element : snapshot["elements"]) {
        std::string ref = element.at("ref");
        if (ref.rfind(prefix, 0) != 0 || ref.size() > 128 || !observed_refs.insert(ref).second ||
            !element.at("role").is_string() || element["role"].get<std::string>().size() > 320 ||
            !element.at("name").is_string() || element["name"].get<std::string>().size() > 1024) { fail(); return Next(); }
        json bounded = {{"ref", ref}, {"role", element["role"]}, {"name", element["name"]}};
        if (!CopyElementMetadata(element, bounded)) { fail(); return Next(); }
        elements.push_back(std::move(bounded));
      }
      std::string text = "url: " + url + "\ntitle: " + title;
      for (const auto& element : elements) text += "\n[" + element["ref"].get<std::string>() + "] " +
        element["role"].get<std::string>() + " " + element["name"].dump();
      if (text.size() > static_cast<std::size_t>(params.value("maxChars", 16000)) * 4) { fail(); return Next(); }
      json observation = {{"url", url}, {"title", title}, {"text", text},
        {"truncated", snapshot.value("truncated", false)}, {"elements", elements}};
      if (!CopySnapshotMetadata(snapshot, observation)) { fail(); return Next(); }
      // These fields come from this native session, not the page. They do not
      // claim native consent, renderer attestation or an R5 document epoch.
      observation["observationId"] = impl_->session + "s" + std::to_string(impl_->generation);
      observation["tabId"] = tab;
      observation["ttlMs"] = std::chrono::duration_cast<std::chrono::milliseconds>(kReferenceTtl - elapsed).count();
      if (impl_->config.value("allowActions", true)) {
        if (impl_->refs.size() >= kMaxObservedTabs) {
          const auto oldest = std::min_element(impl_->refs.begin(), impl_->refs.end(), [](const auto& a, const auto& b) { return a.second.captured < b.second.captured; });
          impl_->refs.erase(oldest);
        }
        impl_->refs[tab] = {std::move(observed_refs), impl_->observation_started};
      }
      impl_->Finish(Success(std::move(observation)));
    } else if (impl_->stage == "action") {
      if (impl_->operation_epoch != impl_->invalidation_epoch) { impl_->Finish(Failure("STALE_OBSERVATION", true)); return Next(); }
      if (!impl_->ReferenceError(tab, impl_->request["params"]["ref"]).empty()) { impl_->Finish(Failure("STALE_OBSERVATION", true)); return Next(); }
      const json action = result.at("result");
      // R5 requires local observable effect, not a dispatch acknowledgement.
      if (action.value("ok", false)) {
        if (impl_->config.value("requireActionEffect", false)) {
          if (!action.value("effectVerified", false)) impl_->Finish(Failure("EFFECT_UNVERIFIED", true));
          else impl_->Finish(Success({{"executionStatus", "EXECUTED"}, {"postconditionStatus", "VERIFIED_LOCAL_DOM_EFFECT"}}));
        } else impl_->Finish(Success(nullptr));
      }
      else {
        // The fixed script can prove refusal before any mutation; preserve prior tab-selection uncertainty.
        const std::string code = action.value("error", std::string());
        static const std::set<std::string> refusals = {"STALE_REFERENCE", "UNSUPPORTED", "STALE_ELEMENT",
          "STALE_OBSERVATION", "AMBIGUOUS_TARGET", "OCCLUDED", "NEEDS_HUMAN", "EFFECT_UNVERIFIED"};
        if (!refusals.count(code)) { fail(); return Next(); }
        impl_->uncertain = action.value("mayHaveCompleted", true) || impl_->selected_effect;
        impl_->Finish(Failure(code));
      }
    } else {
      json value = nullptr;
      if (impl_->extension) value = result;
      else if (method == "tabs.list") value = result.at("tabs");
      else if (method == "tabs.new") value = result.contains("tab") ? result["tab"] : json{{"id", result.at("tabId")}};
      else if (method == "tab.url" || method == "tab.title") value = result.value(method == "tab.url" ? "url" : "title", std::string());
      else if (method == "tab.screenshot") {
        const std::string format = result.value("format", std::string("png"));
        if (format != "png" && format != "jpeg" && format != "webp") { fail(); return Next(); }
        value = {{"type", "image"}, {"mimeType", "image/" + format}, {"data", result.at("image")}};
      }
      impl_->Finish(Success(value));
    }
  } catch (...) { fail(); }
  return Next();
}
void BrowserGateway::InvalidateReferences(const std::string& tab_id) {
  ++impl_->invalidation_epoch;
  if (tab_id.empty()) impl_->refs.clear(); else impl_->refs.erase(tab_id);
}
json BrowserGateway::Cancel() {
  InvalidateReferences();
  if (impl_->active) impl_->Finish(Failure("CANCELLED", impl_->uncertain));
  return Next();
}
json BrowserGateway::ToolList() const {
  json schema = json::parse(browser_assets::input_schema);
  schema["properties"]["request"]["properties"]["method"]["enum"] = impl_->methods;
  return json::array({
    {{"name", "kelpie_browser_control"}, {"description", browser_assets::description}, {"inputSchema", schema}},
    {{"name", "kelpie_extensions"}, {"description", "List available product extension names. Fetch schemas with kelpie_extension_tools."},
      {"inputSchema", {{"type", "object"}, {"properties", json::object()}, {"additionalProperties", false}}}},
    {{"name", "kelpie_extension_tools"}, {"description", "Fetch tool schemas for up to 20 named product extensions, then call those tools."},
      {"inputSchema", {{"type", "object"}, {"properties", {{"names", {{"type", "array"}, {"items", {{"type", "string"}}}, {"maxItems", 20}}}}}, {"required", {"names"}}, {"additionalProperties", false}}}},
    {{"name", "kelpie_extension"}, {"description", "Call a discovered product extension with its documented arguments."},
      {"inputSchema", {{"type", "object"}, {"properties", {{"name", {{"type", "string"}, {"minLength", 1}, {"maxLength", 256}}}, {"arguments", {{"type", "object"}}}}}, {"required", {"name", "arguments"}}, {"additionalProperties", false}}}}
  });
}
json BrowserGateway::Extensions(const json& names) const {
  json result = json::array();
  for (const auto& tool : impl_->config.value("extensions", json::array())) {
    if (!impl_->native.count(tool.value("method", std::string()))) continue;
    if (!impl_->config.value("allowActions", true) && tool.value("mutates", true)) continue;
    if (names.is_null()) result.push_back(tool.at("name"));
    else if (std::find(names.begin(), names.end(), tool.at("name")) != names.end())
      result.push_back({{"name", tool.at("name")}, {"description", tool.value("description", std::string())}, {"inputSchema", tool.at("inputSchema")}});
  }
  return result;
}
json BrowserGateway::BeginMcp(json rpc) {
  const json id = rpc.is_object() ? rpc.value("id", json(nullptr)) : json(nullptr);
  const bool notification = rpc.is_object() && !rpc.contains("id") && rpc.contains("jsonrpc") && rpc["jsonrpc"] == "2.0" && rpc.contains("method") && rpc["method"].is_string();
  const auto complete = [](json value) { return json{{"kind", "complete"}, {"response", value}}; };
  const auto error = [&](int code) { return complete(notification ? json(nullptr) : json{{"jsonrpc", "2.0"}, {"id", id}, {"error", {{"code", code}, {"message", "Invalid or unavailable MCP request."}}}}); };
  if (impl_->active) return error(-32000);
  impl_->mcp = false;
  if (!rpc.is_object() || !rpc.contains("jsonrpc") || !rpc["jsonrpc"].is_string() || rpc["jsonrpc"] != "2.0" || !rpc.contains("method") || !rpc["method"].is_string()) return error(-32600);
  const auto answer = [&](json value) { return complete(notification ? json(nullptr) : json{{"jsonrpc", "2.0"}, {"id", id}, {"result", value}}); };
  const std::string method = rpc["method"];
  if (method == "initialize") return answer({{"protocolVersion", "2025-06-18"}, {"capabilities", {{"tools", json::object()}}}, {"serverInfo", {{"name", "kelpie"}, {"version", "1"}}}});
  if (method == "notifications/initialized" || method == "ping") return answer(json::object());
  if (method == "tools/list") return answer({{"tools", ToolList()}});
  if (method != "tools/call") return error(-32601);
  const json params = rpc.value("params", json::object());
  if (!params.is_object() || !params.contains("name") || !params["name"].is_string()) return error(-32602);
  std::string name = params["name"];
  json arguments = params.value("arguments", json::object());
  if (!arguments.is_object()) return error(-32602);
  if (name == "kelpie_extensions" || name == "kelpie_extension_tools") {
    const auto list = ToolList();
    const auto schema = list[name == "kelpie_extensions" ? 1 : 2]["inputSchema"];
    if (!ValidateBrowserJson(schema, &arguments)) return error(-32602);
    return answer(BrowserMcpResult(Success(Extensions(name == "kelpie_extensions" ? json(nullptr) : arguments["names"]))));
  }
  if (name == "kelpie_extension") {
    if (!ValidateBrowserJson(ToolList()[3]["inputSchema"], &arguments)) return error(-32602);
    name = arguments["name"];
    arguments = arguments["arguments"];
  }
  if (name == "kelpie_browser_control") {
    if (arguments.size() != 1 || !arguments.contains("request")) return error(-32602);
    Begin(arguments["request"]);
  } else {
    json found;
    for (const auto& tool : impl_->config.value("extensions", json::array())) if (tool.value("name", std::string()) == name) found = tool;
    if (found.is_null() || !impl_->native.count(found.value("method", std::string()))) return error(-32601);
    if (!ValidateBrowserJson(found.at("inputSchema"), &arguments)) return error(-32602);
    impl_->mutation = found.value("mutates", true);
    if (impl_->mutation && !impl_->config.value("allowActions", true)) return error(-32601);
    impl_->request = {{"version", 1}, {"method", found["method"]}, {"params", arguments}};
    impl_->extension = true; impl_->active = true; impl_->uncertain = false;
    impl_->stage = "authorize";
    impl_->step = {{"kind", "authorize"}, {"request", impl_->request}, {"mutates", impl_->mutation}};
    if (!impl_->config.value("requireAuthorization", true)) Advance({{"success", true}});
  }
  impl_->mcp = true; impl_->rpc_id = id; impl_->notification = notification;
  if (!impl_->active) impl_->Finish(impl_->step["response"]);
  return Next();
}

}
