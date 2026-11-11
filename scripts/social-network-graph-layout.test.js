"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");

const html = fs.readFileSync("src/main/resources/static/index.html", "utf8");
const css = fs.readFileSync("src/main/resources/static/css/style.css", "utf8");
const graphScript = fs.readFileSync(
    "src/main/resources/static/js/social-network-graph.js",
    "utf8"
);

const workspaceStart = html.indexOf(
    '<div class="social-network-graph-workspace">'
);
const sidebarStart = html.indexOf(
    '<aside class="social-network-graph-sidebar">',
    workspaceStart
);
const graphStart = html.indexOf(
    '<section class="social-network-graph-main card-panel"',
    workspaceStart
);
const sidebarMarkup = html.slice(sidebarStart, graphStart);

assert.ok(workspaceStart >= 0 && sidebarStart > workspaceStart,
    "place graph information panels inside the graph workspace");
assert.ok(graphStart > sidebarStart,
    "render the information panels before the relationship canvas");
assert.equal(
    (sidebarMarkup.match(/class="social-network-graph-side-card card-panel"/g) || [])
        .length,
    3,
    "keep the legend, community, and selected-node cards together"
);
assert.match(css,
    /\.social-network-graph-workspace\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/,
    "use a single full-width workspace column"
);
assert.match(css,
    /\.social-network-graph-sidebar\s*\{[^}]*grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\)/,
    "give the three desktop cards equal-width columns"
);
assert.match(css,
    /\.social-network-graph-side-card\s*\{[^}]*height:\s*280px/,
    "keep the desktop information cards at a consistent height"
);
assert.match(css,
    /\.social-network-graph-community-list\s*\{[^}]*flex:\s*1 1 auto;[^}]*max-height:\s*none;[^}]*overflow:\s*auto/,
    "fill the remaining card height with a scrollable community list"
);
assert.match(css,
    /\.social-network-graph-selected\s*\{[^}]*max-height:\s*126px;[^}]*margin:\s*var\(--sn-space-2\)\s+0 14px;[^}]*overflow:\s*auto/,
    "leave a consistent gap above selected-node details and keep overflow scrollable"
);
assert.match(sidebarMarkup,
    /class="social-network-graph-legend-content"[\s\S]*class="social-network-graph-legend"/,
    "group legend details so they can be centered below the card heading"
);
assert.match(css,
    /\.social-network-graph-legend-content\s*\{[^}]*margin-top:\s*var\(--sn-space-2\)/,
    "leave a consistent gap above the legend details"
);
assert.match(sidebarMarkup,
    /data-network-scope-term[\s\S]*aria-label="查看社群划分说明"[\s\S]*aria-expanded="false"/,
    "provide an accessible, keyboard-focusable community note trigger"
);
assert.match(sidebarMarkup,
    /id="social-network-graph-community-method-tooltip"[\s\S]*role="tooltip"/,
    "show community method details through the shared tooltip pattern"
);
assert.match(sidebarMarkup,
    /id="social-network-graph-community-method"/,
    "keep dynamic community status copy in the tooltip"
);
assert.doesNotMatch(sidebarMarkup,
    /<p id="social-network-graph-community-method"/,
    "avoid reserving card space for the full community method copy"
);
assert.match(sidebarMarkup,
    /id="social-network-graph-user-detail" class="button"/,
    "keep the user detail action in the graph page button system"
);
assert.match(css,
    /\.social-network-graph-side-card > \.page-overline\s*\{[^}]*margin-bottom:\s*0;[^}]*line-height:\s*1\.2/,
    "remove extra margin below card eyebrows for a compact title stack"
);
assert.match(css,
    /\.social-network-graph-community-heading\s*\{[^}]*align-items:\s*flex-start/,
    "align the community title with the other card titles"
);
assert.match(css,
    /\.social-network-graph-community-note:hover[\s\S]*?visibility:\s*visible/,
    "keep the tooltip visible while the pointer moves onto it"
);
assert.match(css,
    /\.social-network-graph-community-note\.is-dismissed[\s\S]*?visibility:\s*hidden/,
    "let Escape close the tooltip while its trigger retains keyboard focus"
);
assert.match(css,
    /\.social-network-graph-page #social-network-graph-user-detail\s*\{[^}]*background:\s*var\(--surface\)/,
    "use the warm surface color for the user detail button"
);
assert.match(graphScript,
    /event\.key !== "Escape"[\s\S]*classList\.add\("is-dismissed"\)[\s\S]*positionCommunityMethodTooltip/,
    "support Escape dismissal and viewport-aware tooltip positioning"
);
assert.match(html, /css\/style\.css\?v=20261111-ranking-bridge-spacing-1/,
    "serve the updated graph layout stylesheet");

console.log("social-network-graph-layout: 23 checks passed");
