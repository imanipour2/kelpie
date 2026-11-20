#include "kelpie/browser_gateway.h"
#include "kelpie/browser_gateway_c_api.h"
#include "kelpie/mcp_c_api.h"
#include "browser_schema.h"
#include "browser_contract.h"
#include <cassert>
#include <iostream>
using json = nlohmann::json;
json Request(const std::string& method, json params = json::object(), const std::string& tab = "a") {
  return {{"version", 1}, {"method", method}, {"tabId", tab}, {"params", params}};
}
void Contracts() {
  const auto schema = json::parse(kelpie::browser_assets::request_schema);
  int index = 0;
  for (const auto& fixture : json::parse(kelpie::browser_assets::cases)) {
    auto value = fixture["input"];
    const bool accepted = kelpie::ValidateBrowserJson(schema, &value);
    if (accepted != fixture["accepted"]) std::cerr << "Schema fixture mismatch " << index << " accepted=" << accepted << " expected=" << fixture["accepted"] << "\n";
    ++index;
    assert(accepted == fixture["accepted"]);
    if (accepted) assert(value == fixture["output"]);
  }
}
void Sequencing() {
  kelpie::BrowserGateway gateway({{"nativeMethods", {"navigate", "get-current-url", "switch-tab", "click", "type"}}, {"fixedReader", true}});
  auto step = gateway.Begin(Request("tab.goto", {{"url", "https://example.org"}}));
  assert(step["kind"] == "authorize");
  assert(gateway.Begin(Request("tab.url"))["response"]["error"]["code"] == "BUSY");
  assert(gateway.Next() == step);
  step = gateway.Advance({{"success", true}});
  assert(step["method"] == "navigate");
  step = gateway.Advance({{"success", false}, {"error", "SECRET script token"}});
  assert(step["response"]["error"]["mayHaveCompleted"] == true);
  assert(step.dump().find("SECRET") == std::string::npos);
  step = gateway.Begin(Request("tab.url"));
  step = gateway.Advance({{"success", false}});
  assert(step["response"]["error"]["mayHaveCompleted"] == false);
  gateway.Begin(Request("tab.goto", {{"url", "url"}}));
  gateway.Advance({{"success", true}});
  assert(gateway.Cancel()["response"]["error"]["mayHaveCompleted"] == true);
  step = gateway.Begin(Request("locator.fill", {{"locator", {{{"kind", "css"}, {"value", "input"}}}}, {"value", "x"}}));
  assert(step["response"]["error"]["code"] == "UNSUPPORTED");
}
void References() {
  kelpie::BrowserGateway gateway({{"nativeMethods", {"click", "type", "navigate"}}, {"fixedReader", true}, {"requireAuthorization", false}});
  auto step = gateway.Begin(Request("tab.snapshot"));
  assert(step["method"] == "browser-control-evaluate");
  std::string expression = step["params"]["expression"];
  const auto start = expression.find("\"prefix\":\"") + 10;
  const auto end = expression.find('"', start);
  const std::string ref = expression.substr(start, end - start) + "1";
  json snapshot = {{"url", "url"}, {"title", "title"}, {"text", "button Save"}, {"elements", {{{"ref", ref}, {"role", "button"}, {"name", "Save"}}}}};
  step = gateway.Advance({{"success", true}, {"result", snapshot}});
  assert(step["response"]["ok"] == true);
  step = gateway.Begin(Request("tab.click", {{"ref", ref}}, "b"));
  assert(step["response"]["error"]["code"] == "STALE_REFERENCE");
  step = gateway.Begin(Request("tab.click", {{"ref", ref}}));
  assert(step["method"] == "browser-control-evaluate");
  assert(step["mutates"] == true);
  step = gateway.Advance({{"success", true}, {"result", {{"error", "STALE_REFERENCE"}, {"mayHaveCompleted", false}}}});
  assert(step["response"]["error"]["mayHaveCompleted"] == false);
  assert(gateway.Begin(Request("tab.click", {{"ref", ref}}))["response"]["error"]["code"] == "STALE_REFERENCE");
  step = gateway.Begin(Request("tab.snapshot"));
  expression = step["params"]["expression"];
  const auto fresh_start = expression.find("\"prefix\":\"") + 10;
  const std::string fresh_ref = expression.substr(fresh_start, expression.find('"', fresh_start) - fresh_start) + "1";
  snapshot["elements"][0]["ref"] = fresh_ref;
  gateway.Advance({{"success", true}, {"result", snapshot}});
  gateway.InvalidateReferences("a");
  assert(gateway.Begin(Request("tab.click", {{"ref", fresh_ref}}))["response"]["error"]["code"] == "STALE_REFERENCE");
}
void Mcp() {
  kelpie::BrowserGateway gateway({{"nativeMethods", {"screenshot"}}, {"requireAuthorization", false}});
  auto step = gateway.BeginMcp({{"jsonrpc", "2.0"}, {"id", 1}, {"method", "tools/list"}});
  const auto tools = step["response"]["result"]["tools"];
  assert(tools.size() == 4);
  assert(tools.dump().size() < 24000);
  step = gateway.BeginMcp({{"jsonrpc", "2.0"}, {"id", 2}, {"method", "tools/call"}, {"params", {{"name", "kelpie_browser_control"}, {"arguments", {{"request", Request("tab.screenshot")}}}}}});
  assert(step["method"] == "screenshot");
  step = gateway.Advance({{"success", true}, {"image", "AQID"}});
  const auto content = step["response"]["result"]["content"];
  assert(content.size() == 2);
  assert(content[0]["type"] == "image");
  assert(content[1]["text"].get<std::string>().find("AQID") == std::string::npos);
  const json extension = {{"name", "shot"}, {"method", "screenshot"}, {"mutates", false}, {"inputSchema", {{"type", "object"}}}};
  kelpie::BrowserGateway rich({{"nativeMethods", {"screenshot"}}, {"requireAuthorization", false}, {"extensions", json::array({extension})}});
  rich.BeginMcp({{"jsonrpc", "2.0"}, {"id", 3}, {"method", "tools/call"}, {"params", {{"name", "kelpie_extension"}, {"arguments", {{"name", "shot"}, {"arguments", json::object()}}}}}});
  step = rich.Advance({{"success", true}, {"image", "AQID"}, {"format", "jpeg"}});
  const auto blocks = step["response"]["result"]["content"];
  assert(blocks.size() == 2 && blocks[0]["mimeType"] == "image/jpeg");
  assert(blocks[1]["text"].get<std::string>().find("AQID") == std::string::npos);
  kelpie::BrowserGateway fallback({{"nativeMethods", {"navigate", "get-current-url"}}, {"browserAvailable", false}});
  assert(fallback.Capabilities()["methods"].size() == 2);
  assert(fallback.Begin(Request("tab.goto", {{"url", "url"}}))["response"]["error"]["code"] == "UNSUPPORTED");
}
void McpControlWithoutOwnerGrant() {
  kelpie::BrowserGateway gateway({{"nativeMethods", {"screenshot", "get-tabs"}}, {"requireAuthorization", true}});
  for (const auto& method : {"initialize", "ping", "tools/list", "notifications/initialized"}) {
    json request = {{"jsonrpc", "2.0"}, {"method", method}};
    if (std::string(method) != "notifications/initialized") request["id"] = 41;
    const auto step = gateway.BeginMcp(request);
    assert(step["kind"] == "complete");
    assert(!step.contains("method"));  // No WebView dispatch or owner authorization.
    if (std::string(method) == "initialize") {
      assert(step["response"]["result"]["protocolVersion"] == "2025-06-18");
    }
  }
  auto step = gateway.BeginMcp({{"jsonrpc", "2.0"}, {"id", 42}, {"method", "tools/call"},
    {"params", {{"name", "kelpie_browser_control"}, {"arguments", {{"request", Request("tab.screenshot")}}}}}});
  assert(step["kind"] == "authorize");
  step = gateway.Advance({{"success", false}});
  assert(step["kind"] == "complete");
  assert(step["response"]["result"]["isError"] == true);
  assert(step.dump().find("image/png") == std::string::npos);
  assert(gateway.BeginMcp({{"jsonrpc", "2.0"}, {"id", 43}, {"method", "ping"}})["kind"] == "complete");
}
void ExtensionReachability() {
  const json schema = {{"type", "object"}, {"properties", {{"url", {{"type", "string"}}}}}, {"required", {"url"}}, {"additionalProperties", false}};
  const json tool = {{"name", "kelpie_bookmarks_add"}, {"method", "bookmarks-add"}, {"mutates", true}, {"inputSchema", schema}};
  kelpie::BrowserGateway gateway({{"nativeMethods", {"bookmarks-add"}}, {"requireAuthorization", false}, {"extensions", json::array({tool})}});
  auto step = gateway.BeginMcp({{"jsonrpc", "2.0"}, {"id", 1}, {"method", "tools/call"}, {"params", {{"name", "kelpie_extension"}, {"arguments", {{"name", "kelpie_bookmarks_add"}, {"arguments", {{"url", "https://example.org"}}}}}}}});
  assert(step["method"] == "bookmarks-add");
  assert(step["params"]["url"] == "https://example.org");
  assert(gateway.Advance({{"success", true}})["response"]["result"]["isError"] == false);
  const json nullable_tool = {{"name", "mutable"}, {"method", "bookmarks-add"}, {"inputSchema", {{"type", "object"}}}};
  kelpie::BrowserGateway nullable({{"nativeMethods", {"bookmarks-add"}}, {"requireAuthorization", false}, {"extensions", json::array({nullable_tool})}});
  nullable.BeginMcp({{"jsonrpc", "2.0"}, {"id", 2}, {"method", "tools/call"}, {"params", {{"name", "mutable"}, {"arguments", {{"tabId", nullptr}}}}}});
  assert(nullable.Advance({{"success", true}})["response"]["result"]["isError"] == false);
}
void ReadOnlySnapshotLifetime() {
  for (int turn = 0; turn < 3; ++turn) {
    kelpie::BrowserGateway gateway({{"nativeMethods", {"click", "type"}}, {"fixedReader", true},
      {"allowActions", false}, {"requireAuthorization", false}});
    const auto step = gateway.Begin(Request("tab.snapshot"));
    const std::string expression = step["params"]["expression"];
    assert(expression.find(kelpie::browser_assets::reader) != std::string::npos);
    assert(expression.find(kelpie::browser_assets::remember) == std::string::npos);
    assert(expression.find(".snapshot") != std::string::npos);
    gateway.Cancel();
    assert(gateway.Begin(Request("tab.click", {{"ref", "e1"}}))["response"]["error"]["code"] == "UNSUPPORTED");
  }
}
void SelectedAndMapping() {
  const json config = {{"nativeMethods", {"get-tabs", "switch-tab", "scroll", "screenshot", "get-current-url", "navigate"}}, {"targeting", "selected"}, {"allowActions", false}, {"requireAuthorization", false}};
  kelpie::BrowserGateway read_only(config);
  auto step = read_only.Begin(Request("tab.url"));
  assert(step["method"] == "get-tabs");
  step = read_only.Advance({{"success", true}, {"activeTab", "a"}, {"tabs", {{{"id", "a"}, {"active", true}}}}});
  assert(step["method"] == "get-current-url");
  read_only.Advance({{"success", true}, {"url", "url"}});
  read_only.Begin(Request("tab.url", json::object(), "b"));
  step = read_only.Advance({{"success", true}, {"activeTab", "a"}});
  assert(step["response"]["error"]["code"] == "ACCESS_DENIED");
  assert(step["response"]["error"]["mayHaveCompleted"] == false);
  step = read_only.Begin(Request("tab.screenshot", {{"fullPage", true}}, "b"));
  assert(step["kind"] == "complete");
  assert(step["response"]["error"]["mayHaveCompleted"] == false);
  auto writable = config; writable["allowActions"] = true;
  kelpie::BrowserGateway actions(writable);
  step = actions.Begin(Request("page.scroll", {{"deltaY", 42}}));
  step = actions.Advance({{"success", true}, {"activeTab", "b"}});
  assert(step["method"] == "switch-tab");
  step = actions.Advance({{"success", true}});
  assert(step["method"] == "scroll");
  assert(step["params"]["deltaX"] == 0);
  assert(step["params"]["deltaY"] == 42);
  step = actions.Advance({{"success", false}});
  assert(step["response"]["error"]["mayHaveCompleted"] == true);
  kelpie::BrowserGateway direct({{"nativeMethods", {"navigate", "screenshot"}}, {"requireAuthorization", false}});
  step = direct.Begin(Request("tab.goto", {{"url", "url"}, {"timeoutMs", 234}}));
  assert(step["params"]["timeout"] == 234);
  assert(!step["params"].contains("timeoutMs"));
  direct.Cancel();
  step = direct.Begin(Request("tab.screenshot", {{"fullPage", false}}));
  assert(step["method"] == "screenshot");
  assert(!step["params"].contains("fullPage"));
  step = direct.Advance({{"success", true}, {"image", "AQID"}, {"format", "webp"}});
  assert(step["response"]["value"]["mimeType"] == "image/webp");
  direct.Begin(Request("tab.screenshot"));
  assert(direct.Advance({{"success", true}, {"image", "AQID"}, {"format", "invalid"}})["response"]["error"]["code"] == "COMMAND_FAILED");
  direct.Begin(Request("tab.screenshot"));
  const auto too_large = direct.Advance({{"success", false}, {"error", {{"code", "SCREENSHOT_TOO_LARGE"}, {"message", "budget"}}}});
  assert(too_large["response"]["error"]["code"] == "SCREENSHOT_TOO_LARGE");
  assert(too_large["response"]["error"]["mayHaveCompleted"] == false);
  direct.Begin(Request("tab.goto", {{"url", "url"}}));
  assert(direct.Advance({{"success", false}, {"error", {{"code", "SCREENSHOT_TOO_LARGE"}}}})["response"]["error"]["code"] == "COMMAND_FAILED");
}
void CAbi() {
  assert(!kelpie_browser_selector_snapshot_expression(nullptr));
  assert(!kelpie_browser_selector_snapshot_expression("[]"));
  assert(!kelpie_browser_selector_snapshot_expression(std::string(4097, 'x').c_str()));
  char* reader = kelpie_browser_selector_snapshot_expression("{\"maxChars\":6000,\"maxElements\":150}");
  assert(reader && json::parse(reader)["expression"] == kelpie::CollectBrowserSelectorSnapshotExpression({{"maxChars",6000},{"maxElements",150}}));
  kelpie_mcp_free_string(reader);
  const auto gateway = kelpie_browser_gateway_create("{\"nativeMethods\":[]}");
  assert(gateway);
  char* result = kelpie_browser_gateway_begin(gateway, "{\"version\":1,\"method\":\"documentation\"}");
  assert(json::parse(result)["kind"] == "authorize");
  kelpie_mcp_free_string(result);
  result = kelpie_browser_gateway_advance(gateway, "{\"success\":true}");
  assert(json::parse(result)["response"]["ok"] == true);
  kelpie_mcp_free_string(result);
  kelpie_browser_gateway_destroy(gateway);
  const auto large_gateway = kelpie_browser_gateway_create("{\"nativeMethods\":[\"type\"],\"fixedReader\":true}");
  // JSON escaping can make a valid 65,536-code-unit string much larger than 128KiB.
  std::string escaped;
  for (int index = 0; index < 65536; ++index) escaped += "\\u0001";
  const std::string large_request = "{\"version\":1,\"method\":\"tab.type\",\"tabId\":\"a\",\"params\":{\"ref\":\"unobserved\",\"text\":\"" + escaped + "\"}}";
  result = kelpie_browser_gateway_begin(large_gateway, large_request.c_str());
  assert(json::parse(result)["response"]["error"]["code"] == "STALE_REFERENCE");
  kelpie_mcp_free_string(result);
  std::string unicode;
  for (int index = 0; index < 32768; ++index) unicode += "\xf0\x9f\x98\x80";
  const auto unicode_request = Request("tab.type", {{"ref", "unobserved"}, {"text", unicode}}).dump();
  result = kelpie_browser_gateway_begin(large_gateway, unicode_request.c_str());
  assert(json::parse(result)["response"]["error"]["code"] == "STALE_REFERENCE");
  kelpie_mcp_free_string(result);
  kelpie_browser_gateway_destroy(large_gateway);
}
void Notifications() {
  kelpie::BrowserGateway gateway(json{{"nativeMethods", json::array()}});
  assert(gateway.BeginMcp({{"jsonrpc", "2.0"}, {"method", "notifications/unknown"}})["response"].is_null());
  assert(gateway.BeginMcp({{"jsonrpc", "2.0"}, {"method", "tools/call"}, {"params", {{"name", 123}}}})["response"].is_null());
}
json SnapshotFor(const json& step) {
  const std::string expression = step["params"]["expression"];
  const auto start = expression.find("\"prefix\":\"") + 10;
  const std::string ref = expression.substr(start, expression.find('"', start) - start) + "1";
  return {{"url", "https://fixture.test/"}, {"title", "Fixture"}, {"text", "button Save"},
    {"elements", {{{"ref", ref}, {"role", "button"}, {"name", "Save"}}}}};
}
std::string Observe(kelpie::BrowserGateway& gateway, const std::string& tab = "a") {
  const auto snapshot = SnapshotFor(gateway.Begin(Request("tab.snapshot", json::object(), tab)));
  assert(gateway.Advance({{"success", true}, {"result", snapshot}})["response"]["ok"] == true);
  return snapshot["elements"][0]["ref"];
}
void ObservationMetadata() {
  kelpie::BrowserGateway gateway({{"nativeMethods", {"click", "type"}}, {"fixedReader", true}, {"requireAuthorization", false}});
  auto snapshot = SnapshotFor(gateway.Begin(Request("tab.snapshot")));
  auto& element = snapshot["elements"][0];
  element["rect"] = {{"x", 12.5}, {"y", 20}, {"width", 80}, {"height", 40}, {"extra", "SYNTHETIC_SECRET"}};
  element["visible"] = true; element["enabled"] = true;
  element["value"] = "SYNTHETIC_SECRET"; element["sensitivity"] = "SENSITIVE";
  element["name"] = "SYNTHETIC_SECRET"; element["allowedOperations"] = json::array();
  snapshot["text"] = "button SYNTHETIC_SECRET";
  snapshot["viewport"] = {{"width", 390}, {"height", 844}};
  snapshot["devicePixelRatio"] = 3.0;
  snapshot["visualViewport"] = nullptr; snapshot["scroll"] = {{"x", 0}, {"y", 120}};
  snapshot["coverage"] = {{"status", "INCOMPLETE"}, {"observed", 1}, {"scanned", 501},
    {"totalCandidates", nullptr}, {"truncated", true}, {"unobserved", nullptr}, {"limitations", {"ELEMENT_OR_TEXT_LIMIT"}}};
  snapshot["privacyRects"] = {{{"x", 12.5}, {"y", 20}, {"width", 80}, {"height", 40}}};
  snapshot["screenshotBlocked"] = true; snapshot["screenshotBlockReasons"] = {"PRIVATE_FORM_DATA"};
  snapshot["observationId"] = "SYNTHETIC_SECRET"; snapshot["consentGeneration"] = "SYNTHETIC_SECRET";
  const auto response = gateway.Advance({{"success", true}, {"result", snapshot}})["response"];
  assert(response["ok"] == true);
  const auto& value = response["value"];
  assert(value["tabId"] == "a" && value["ttlMs"] <= 15000 && value["ttlMs"] > 0);
  assert(value["observationId"].is_string() && value["observationId"] != snapshot["observationId"]);
  assert(value["elements"][0]["rect"]["x"] == 12.5 && value["elements"][0]["value"].is_null());
  assert(value["elements"][0]["name"] == "redacted" && value["coverage"] == snapshot["coverage"]);
  assert(value["devicePixelRatio"] == 3.0);
  assert(value["screenshotBlocked"] == true && value["privacyRects"] == snapshot["privacyRects"]);
  assert(response.dump().find("SYNTHETIC_SECRET") == std::string::npos);
  snapshot = SnapshotFor(gateway.Begin(Request("tab.snapshot")));
  snapshot["elements"][0]["sensitivity"] = "PRIVATE";
  snapshot["elements"][0]["name"] = "جستجو"; snapshot["elements"][0]["value"] = "SYNTHETIC_SECRET";
  const auto private_label = gateway.Advance({{"success", true}, {"result", snapshot}})["response"];
  assert(private_label["value"]["elements"][0]["name"] == "جستجو");
  assert(private_label.dump().find("SYNTHETIC_SECRET") == std::string::npos);
  // Malformed geometry must not reach bitmap correlation or leave usable refs.
  snapshot = SnapshotFor(gateway.Begin(Request("tab.snapshot")));
  snapshot["elements"][0]["rect"] = {{"x", 0}, {"y", 0}, {"width", -1}, {"height", 4}};
  const std::string ref = snapshot["elements"][0]["ref"];
  assert(gateway.Advance({{"success", true}, {"result", snapshot}})["response"]["ok"] == false);
  assert(gateway.Begin(Request("tab.click", {{"ref", ref}}))["response"]["error"]["code"] == "STALE_REFERENCE");
}
void NativeReferenceLifetime() {
  auto now = std::chrono::steady_clock::time_point{};
  const json config = {{"nativeMethods", {"click", "type", "switch-tab"}}, {"fixedReader", true}, {"requireAuthorization", false}};
  kelpie::BrowserGateway gateway(config, [&now] { return now; });
  auto ref = Observe(gateway);
  now += std::chrono::milliseconds(15000);
  auto step = gateway.Begin(Request("tab.click", {{"ref", ref}}));
  assert(step["kind"] == "complete" && step["response"]["error"]["code"] == "STALE_OBSERVATION");
  assert(step["response"]["error"]["mayHaveCompleted"] == false);
  ref = Observe(gateway);
  gateway.Cancel(); // An idle native cancellation also retires observations.
  assert(gateway.Begin(Request("tab.click", {{"ref", ref}}))["response"]["error"]["code"] == "STALE_REFERENCE");
  const auto pending = SnapshotFor(gateway.Begin(Request("tab.snapshot")));
  gateway.InvalidateReferences("a");
  step = gateway.Advance({{"success", true}, {"result", pending}});
  assert(step["response"]["error"]["code"] == "OBSERVATION_RACE");
  assert(gateway.Begin(Request("tab.click", {{"ref", pending["elements"][0]["ref"]}}))["response"]["error"]["code"] == "STALE_REFERENCE");
  ref = Observe(gateway);
  assert(gateway.Begin(Request("tab.click", {{"ref", ref}}))["kind"] == "dispatch");
  now += std::chrono::seconds(15);
  step = gateway.Advance({{"success", true}, {"result", {{"ok", true}}}});
  assert(step["response"]["error"]["code"] == "STALE_OBSERVATION" && step["response"]["error"]["mayHaveCompleted"] == true);
  ref = Observe(gateway);
  assert(gateway.Begin(Request("tab.click", {{"ref", ref}}))["kind"] == "dispatch");
  gateway.InvalidateReferences("a");
  step = gateway.Advance({{"success", true}, {"result", {{"ok", true}}}});
  assert(step["response"]["error"]["code"] == "STALE_OBSERVATION" && step["response"]["error"]["mayHaveCompleted"] == true);
  ref = Observe(gateway);
  step = gateway.Begin(Request("tabs.select", json::object(), "b"));
  assert(step["method"] == "switch-tab"); gateway.Advance({{"success", true}});
  assert(gateway.Begin(Request("tab.click", {{"ref", ref}}))["response"]["error"]["code"] == "STALE_REFERENCE");
  // Native lifetime starts before the async reader and cannot be extended by it.
  const auto slow = SnapshotFor(gateway.Begin(Request("tab.snapshot")));
  now += std::chrono::seconds(15);
  assert(gateway.Advance({{"success", true}, {"result", slow}})["response"]["error"]["code"] == "STALE_OBSERVATION");
  const auto oldest = Observe(gateway, "first");
  for (int i = 0; i < 16; ++i) { now += std::chrono::milliseconds(1); Observe(gateway, "other" + std::to_string(i)); }
  assert(gateway.Begin(Request("tab.click", {{"ref", oldest}}, "first"))["response"]["error"]["code"] == "STALE_REFERENCE");
}
void RefusalAndAtomicPublication() {
  const json config = {{"nativeMethods", {"click", "type", "screenshot"}}, {"fixedReader", true}, {"requireAuthorization", false}};
  kelpie::BrowserGateway gateway(config);
  for (const auto code : {"STALE_ELEMENT", "STALE_OBSERVATION", "AMBIGUOUS_TARGET", "OCCLUDED", "NEEDS_HUMAN"}) {
    const auto ref = Observe(gateway);
    gateway.Begin(Request("tab.click", {{"ref", ref}}));
    const auto step = gateway.Advance({{"success", true}, {"result", {{"error", code}, {"mayHaveCompleted", false}, {"secret", "SYNTHETIC_SECRET"}}}});
    assert(step["response"]["error"]["code"] == code && step["response"]["error"]["mayHaveCompleted"] == false);
    assert(step.dump().find("SYNTHETIC_SECRET") == std::string::npos);
  }
  auto snapshot = SnapshotFor(gateway.Begin(Request("tab.snapshot")));
  const auto duplicate = snapshot["elements"][0]; snapshot["elements"].push_back(duplicate);
  assert(gateway.Advance({{"success", true}, {"result", snapshot}})["response"]["ok"] == false);
  assert(gateway.Begin(Request("tab.click", {{"ref", duplicate["ref"]}}))["response"]["error"]["code"] == "STALE_REFERENCE");
  for (const auto code : {"NEEDS_HUMAN", "NO_FRESH_FRAME", "OBSERVATION_RACE", "KAI_FEATURE_DISABLED", "INVALID_CAPTURE_GEOMETRY", "UNSUPPORTED", "FRAME_PRIVACY_UNVERIFIED"}) {
    gateway.Begin(Request("tab.screenshot"));
    const auto step = gateway.Advance({{"success", false}, {"error", {{"code", code}, {"message", "SYNTHETIC_SECRET"}}}});
    assert(step["response"]["error"]["code"] == code && step["response"]["error"]["mayHaveCompleted"] == false);
    assert(step.dump().find("SYNTHETIC_SECRET") == std::string::npos);
    if (std::string(code) == "UNSUPPORTED" || std::string(code) == "FRAME_PRIVACY_UNVERIFIED") {
      assert(step["response"]["error"]["message"].get<std::string>().find("sanitized page snapshot") != std::string::npos);
      assert(!step["response"].contains("value"));
    }
  }
}
void NativeAuthorizationRecheck() {
  auto now = std::chrono::steady_clock::time_point{};
  const json config = {{"nativeMethods", {"click", "type", "get-tabs", "switch-tab"}}, {"fixedReader", true}};
  kelpie::BrowserGateway gateway(config, [&now] { return now; });
  gateway.Begin(Request("tab.snapshot"));
  auto snapshot = SnapshotFor(gateway.Advance({{"success", true}}));
  gateway.Advance({{"success", true}, {"result", snapshot}});
  const auto ref = snapshot["elements"][0]["ref"];
  assert(gateway.Begin(Request("tab.click", {{"ref", ref}}))["kind"] == "authorize");
  now += std::chrono::seconds(15);
  assert(gateway.Advance({{"success", true}})["response"]["error"]["code"] == "STALE_OBSERVATION");
  gateway.Begin(Request("tab.snapshot")); snapshot = SnapshotFor(gateway.Advance({{"success", true}}));
  gateway.Advance({{"success", true}, {"result", snapshot}});
  gateway.Begin(Request("tab.snapshot")); gateway.Advance({{"success", false}});
  assert(gateway.Begin(Request("tab.click", {{"ref", snapshot["elements"][0]["ref"]}}))["response"]["error"]["code"] == "STALE_REFERENCE");
  auto selected_config = config; selected_config["targeting"] = "selected"; selected_config["requireAuthorization"] = false;
  kelpie::BrowserGateway selected(selected_config);
  selected.Begin(Request("tab.snapshot"));
  snapshot = SnapshotFor(selected.Advance({{"success", true}, {"activeTab", "a"}}));
  selected.Advance({{"success", true}, {"result", snapshot}});
  selected.Begin(Request("tab.click", {{"ref", snapshot["elements"][0]["ref"]}}));
  const auto step = selected.Advance({{"success", true}, {"activeTab", "b"}});
  assert(step["kind"] == "complete" && step["response"]["error"]["code"] == "STALE_OBSERVATION");
  assert(step["response"]["error"]["mayHaveCompleted"] == false);
}
void R5RequiresObservedActionEffect() {
  kelpie::BrowserGateway gateway({{"nativeMethods", {"click", "type"}}, {"fixedReader", true},
    {"requireAuthorization", false}, {"requireActionEffect", true}});
  for (const auto& method : {"tab.click", "tab.type"}) {
    auto ref = Observe(gateway);
    json params = {{"ref", ref}};
    if (std::string(method) == "tab.type") params["text"] = "fixture";
    auto step = gateway.Begin(Request(method, params));
    const auto expression = step["params"]["expression"].get<std::string>();
    assert(expression.substr(expression.size() - 6) == ",true)");
    step = gateway.Advance({{"success", true}, {"result", {{"ok", true}}}});
    assert(step["response"]["error"]["code"] == "EFFECT_UNVERIFIED");
    assert(step["response"]["error"]["mayHaveCompleted"] == true);
    params["ref"] = Observe(gateway);
    gateway.Begin(Request(method, params));
    step = gateway.Advance({{"success", true}, {"result", {{"ok", true}, {"effectVerified", true}}}});
    assert(step["response"]["ok"] == true);
    assert(step["response"]["value"]["postconditionStatus"] == "VERIFIED_LOCAL_DOM_EFFECT");
    params["ref"] = Observe(gateway);
    gateway.Begin(Request(method, params));
    step = gateway.Advance({{"success", true}, {"result", {{"error", "EFFECT_UNVERIFIED"}, {"mayHaveCompleted", true}}}});
    assert(step["response"]["error"]["code"] == "EFFECT_UNVERIFIED");
    assert(step["response"]["error"]["mayHaveCompleted"] == true);
  }
}
void RendererReadinessIsNotAuthentication() {
  kelpie::BrowserGateway gateway({{"nativeMethods", {"get-tabs", "navigate"}}, {"requireAuthorization", true}});
  for (const auto& method : {"capabilities", "documentation"}) {
    auto step = gateway.Begin({{"version", 1}, {"method", method}, {"params", json::object()}});
    assert(step["kind"] == "authorize");
    step = gateway.Advance({{"success", true}});
    assert(step["kind"] == "complete" && step["response"]["ok"] == true);
  }
  for (const auto& method : {"tabs.list", "tab.goto"}) {
    const bool mutation = std::string(method) == "tab.goto";
    auto step = gateway.Begin(Request(method, mutation ? json{{"url", "https://example.org"}} : json::object()));
    assert(step["kind"] == "authorize");
    step = gateway.Advance({{"success", true}});
    assert(step["kind"] == "dispatch");
    step = gateway.Advance({{"success", false}, {"error", {{"code", "RENDERER_NOT_READY"}, {"message", "SECRET page text"}}}});
    assert(step["response"]["error"]["code"] == "UNAVAILABLE");
    assert(step["response"]["error"]["mayHaveCompleted"] == mutation);
    assert(step.dump().find("SECRET") == std::string::npos);
    assert(step["response"]["error"]["message"].get<std::string>().find("approval is unchanged") != std::string::npos);
  }
  gateway.Begin(Request("tabs.list")); gateway.Advance({{"success", true}});
  auto step = gateway.Advance({{"success", false}, {"error", {{"code", "SECRET unknown error"}, {"message", "SECRET diagnostic"}}}});
  assert(step["response"]["error"]["code"] == "COMMAND_FAILED");
  assert(step.dump().find("SECRET") == std::string::npos);
}
int main() { RendererReadinessIsNotAuthentication(); R5RequiresObservedActionEffect(); McpControlWithoutOwnerGrant(); ObservationMetadata(); NativeReferenceLifetime(); RefusalAndAtomicPublication(); NativeAuthorizationRecheck(); ReadOnlySnapshotLifetime(); Notifications(); Contracts(); Sequencing(); References(); Mcp(); ExtensionReachability(); SelectedAndMapping(); CAbi(); std::cout << "Browser gateway contracts passed\n"; }
