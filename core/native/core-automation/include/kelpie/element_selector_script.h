#pragma once

#include <string>

namespace kelpie {

inline std::string ElementSelectorBuilderScript() {
  return R"JS(
function kelpieEscapeIdentifier(value) {
  if (window.CSS && typeof window.CSS.escape === 'function') {
    return window.CSS.escape(String(value));
  }
  return String(value).replace(/([ !"#$%&'()*+,./:;<=>?@[\\\]^`{|}~])/g, '\\$1');
}
function kelpieEscapeAttribute(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}
function kelpieSafeTag(node) {
    var tag = node && node.tagName ? node.tagName.toLowerCase() : '';
    var known = 'a abbr address area article aside audio b base bdi bdo blockquote body br button canvas caption cite code '+
        'col colgroup data datalist dd del details dfn dialog div dl dt em embed fieldset figcaption figure footer form '+
        'h1 h2 h3 h4 h5 h6 head header hgroup hr html i iframe img input ins kbd label legend li link main map mark menu '+
        'meta meter nav noscript object ol optgroup option output p picture pre progress q rp rt ruby s samp script search '+
        'section select slot small source span strong style sub summary sup table tbody td template textarea tfoot th thead '+
        'time title tr track u ul var video wbr svg path g rect circle ellipse line polyline polygon text tspan defs symbol use';
    return known.split(' ').indexOf(tag) >= 0 ? tag : 'generic';
}
function kelpieBuildSelector(node, structuralOnly) {
  if (!node || node.nodeType !== 1) return null;
  function identifiesNode(candidate) {
    if (candidate.length > 2000) return false;
    try {
      var matches = document.querySelectorAll(candidate);
      return matches.length === 1 && matches[0] === node;
    } catch (error) { return false; }
  }
  if (!structuralOnly && node.id) {
    var id = '#' + kelpieEscapeIdentifier(node.id);
    if (identifiesNode(id)) return id;
  }
  var segments = [];
  var current = node;
  while (current && current.nodeType === 1) {
    var tag = current.tagName.toLowerCase();
    if (structuralOnly && kelpieSafeTag(current) === 'generic') return null;
    var segment = tag;
    if (!structuralOnly && current.id) {
      var identified = [tag + '#' + kelpieEscapeIdentifier(current.id)].concat(segments).join(' > ');
      if (identifiesNode(identified)) return identified;
    }
    var name = !structuralOnly && current.getAttribute && current.getAttribute('name');
    if (!structuralOnly && name) {
      var named = tag + '[name="' + kelpieEscapeAttribute(name) + '"]';
      var namedPath = [named].concat(segments).join(' > ');
      if (identifiesNode(namedPath)) return namedPath;
    }
    var parent = current.parentElement;
    if (parent) {
      var index = 1;
      var sibling = current;
      while ((sibling = sibling.previousElementSibling)) {
        if (sibling.tagName === current.tagName) index += 1;
      }
      segment += ':nth-of-type(' + index + ')';
    }
    segments.unshift(segment);
    var candidate = segments.join(' > ');
    if (identifiesNode(candidate)) return candidate;
    current = parent;
  }
  return null;
}
)JS";
}

}  // namespace kelpie
