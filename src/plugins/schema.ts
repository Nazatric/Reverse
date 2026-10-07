/**
 * The Gadget plugin format.
 *
 * A plugin is a JSON document. Nothing is evaluated, so a plugin can never run code —
 * it can only describe changes to known UI targets. That keeps the customization system
 * powerful (new orbs, new pages, icon and colour overrides) while staying safe.
 *
 * Minimal example:
 *
 * {
 *   "id": "ac.hub",
 *   "name": "Accent Hub",
 *   "version": "1.0.0",
 *   "author": "you",
 *   "description": "Adds an extra orb that opens the player.",
 *   "theme": { "accent": "#62BDF1", "orbScale": 1.1 },
 *   "nodes": [
 *     { "id": "ac.play", "label": "play", "x": 10, "y": 45, "d": 96, "icon": "music", "page": "now" }
 *   ]
 * }
 *
 * Load it from Plugins → Import, from a URL, or drop a `gadget.plugin.json` next to the app.
 */

export interface PluginNodeSpec {
  id: string;
  label: string;
  /** 0–100, percentage of the hub stage */
  x: number;
  y: number;
  /** diameter in reference pixels (736-unit canvas) */
  d: number;
  /** gap between orb and label, reference pixels */
  ly?: number;
  /** built-in glyph name: music | games | homies | config | account | play | note | star */
  icon?: string;
  /** an existing internal page to open, or a `page` id declared by this plugin */
  page?: string;
}

export interface PluginPageSpec {
  id: string;
  label: string;
  /** heading shown at the top of the page */
  title?: string;
  /** short line under the heading */
  subtitle?: string;
  /** ordered list of blocks rendered on the page */
  blocks: PluginBlock[];
}

export type PluginBlock =
  | { type: "text"; value: string }
  | { type: "note"; value: string }
  | { type: "header"; value: string }
  | { type: "link"; label: string; url: string }
  | { type: "button"; label: string; page?: string; url?: string }
  | { type: "tiles"; items: { label: string; icon?: string; page?: string; url?: string }[] };

export interface PluginThemeSpec {
  /** hex colour, e.g. #62BDF1 */
  accent?: string;
  /** hex colours for the orb body, highlight first */
  orb?: [string, string, string, string];
  /** 0.7–1.5 */
  orbScale?: number;
  /** 0.7–1.5 */
  hubScale?: number;
  /** 0.5–2 */
  chainScale?: number;
  /** 0.7–1.7 */
  labelScale?: number;
  /** 0.3–1.8 */
  glow?: number;
}

export interface PluginDocument {
  id: string;
  name: string;
  version?: string;
  author?: string;
  description?: string;
  theme?: PluginThemeSpec;
  nodes?: PluginNodeSpec[];
  pages?: PluginPageSpec[];
}

export interface PluginRecord {
  id: string;
  name: string;
  version: string;
  author: string;
  description: string;
  /** pretty-printed JSON source, stored verbatim */
  source: string;
  doc: PluginDocument;
  enabled: boolean;
  installed: number;
}

/** Everything the renderer needs to know, already validated and flattened. */
export interface ActivePlugin {
  id: string;
  doc: PluginDocument;
  nodes: PluginNodeSpec[];
  pages: PluginPageSpec[];
  theme: PluginThemeSpec;
}

const GLYPHS = ["music", "games", "homies", "config", "account", "play", "note", "star"];
const PAGES = ["music", "albums", "songs", "playlists", "search", "now", "games", "2048", "homies", "account", "settings"];
const HEX = /^#[0-9a-f]{6}$/i;
const MAX_SOURCE = 200_000;

export class PluginError extends Error {}

function fail(message: string): never {
  throw new PluginError(message);
}

function str(value: unknown, what: string, max = 80): string {
  if (typeof value !== "string" || !value.trim()) fail(`${what} is required`);
  if (value.length > max) fail(`${what} is too long (max ${max} characters)`);
  return value.trim();
}

function id(value: unknown, what: string): string {
  const v = str(value, what, 60);
  if (!/^[a-z0-9][a-z0-9._-]{1,59}$/i.test(v)) fail(`${what} must use letters, numbers, dots, dashes or underscores`);
  return v;
}

function num(value: unknown, what: string, min: number, max: number): number {
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n)) fail(`${what} must be a number`);
  if (n < min || n > max) fail(`${what} must be between ${min} and ${max}`);
  return n;
}

function optNum(value: unknown, what: string, min: number, max: number): number | undefined {
  if (value === undefined || value === null) return undefined;
  return num(value, what, min, max);
}

function hex(value: unknown, what: string): string {
  if (typeof value !== "string" || !HEX.test(value)) fail(`${what} must be a hex colour like #62BDF1`);
  return value.toLowerCase();
}

function checkPage(value: unknown, own: Set<string>, what: string): string {
  const v = str(value, what, 60);
  if (!PAGES.includes(v) && !own.has(v)) fail(`${what} must be a built-in page or one declared by this plugin`);
  return v;
}

/** Parse and validate plugin JSON. Throws PluginError with a readable message. */
export function parsePlugin(raw: string): PluginRecord {
  if (!raw.trim()) fail("The plugin file is empty");
  if (raw.length > MAX_SOURCE) fail("The plugin file is too large (200 KB max)");

  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch (err) {
    fail(`Invalid JSON — ${(err as Error).message}`);
  }
  if (typeof data !== "object" || data === null || Array.isArray(data)) fail("The plugin must be a JSON object");

  const o = data as Record<string, unknown>;
  const pluginId = id(o.id, "id");
  const name = str(o.name, "name", 60);
  const version = o.version === undefined ? "1.0.0" : str(o.version, "version", 24);
  const author = o.author === undefined ? "unknown" : str(o.author, "author", 60);
  const description = o.description === undefined ? "" : str(o.description, "description", 240);

  const pagesRaw = o.pages;
  const ownPages = new Set<string>();
  if (pagesRaw !== undefined) {
    if (!Array.isArray(pagesRaw)) fail("pages must be an array");
    for (const p of pagesRaw) ownPages.add(id((p as PluginPageSpec)?.id, "page id"));
  }

  const pages: PluginPageSpec[] = [];
  for (const entry of (pagesRaw ?? []) as unknown[]) {
    if (typeof entry !== "object" || entry === null) fail("each page must be an object");
    const p = entry as Record<string, unknown>;
    const pageId = id(p.id, "page id");
    const label = str(p.label, "page label", 40);
    const title = p.title === undefined ? label : str(p.title, "page title", 60);
    const subtitle = p.subtitle === undefined ? "" : str(p.subtitle, "page subtitle", 120);
    if (!Array.isArray(p.blocks)) fail(`page ${pageId} needs a blocks array`);
    if (p.blocks.length > 40) fail(`page ${pageId} has too many blocks (40 max)`);

    const blocks: PluginBlock[] = p.blocks.map((rawBlock, bi) => {
      if (typeof rawBlock !== "object" || rawBlock === null) fail(`page ${pageId} block ${bi + 1} must be an object`);
      const b = rawBlock as Record<string, unknown>;
      const where = `page ${pageId} block ${bi + 1}`;
      switch (b.type) {
        case "text":
        case "note":
          return { type: b.type, value: str(b.value, `${where} value`, 600) };
        case "header":
          return { type: "header", value: str(b.value, `${where} value`, 80) };
        case "link": {
          const link = str(b.url, `${where} url`, 400);
          if (!/^https?:\/\//i.test(link)) fail(`${where} url must start with http:// or https://`);
          return { type: "link", label: str(b.label, `${where} label`, 60), url: link };
        }
        case "button": {
          const label2 = str(b.label, `${where} label`, 60);
          const hasTarget = b.page !== undefined || b.url !== undefined;
          if (!hasTarget) fail(`${where} needs a page or a url`);
          if (b.page !== undefined && b.url !== undefined) fail(`${where} can't have both a page and a url`);
          return {
            type: "button",
            label: label2,
            page: b.page === undefined ? undefined : checkPage(b.page, ownPages, `${where} page`),
            url: b.url === undefined ? undefined : str(b.url, `${where} url`, 400),
          };
        }
        case "tiles": {
          if (!Array.isArray(b.items)) fail(`${where} needs an items array`);
          if (b.items.length === 0) fail(`${where} needs at least one item`);
          if (b.items.length > 12) fail(`${where} has too many items (12 max)`);
          return {
            type: "tiles",
            items: b.items.map((rawItem, ti) => {
              if (typeof rawItem !== "object" || rawItem === null) fail(`${where} item ${ti + 1} must be an object`);
              const item = rawItem as Record<string, unknown>;
              const itemLabel = str(item.label, `${where} item ${ti + 1} label`, 40);
              const icon = item.icon === undefined ? undefined : str(item.icon, `${where} item ${ti + 1} icon`, 20);
              if (icon && !GLYPHS.includes(icon)) fail(`${where} item ${ti + 1} icon must be one of: ${GLYPHS.join(", ")}`);
              if (item.page === undefined && item.url === undefined) fail(`${where} item ${ti + 1} needs a page or a url`);
              return {
                label: itemLabel,
                icon,
                page: item.page === undefined ? undefined : checkPage(item.page, ownPages, `${where} item ${ti + 1} page`),
                url: item.url === undefined ? undefined : str(item.url, `${where} item ${ti + 1} url`, 400),
              };
            }),
          };
        }
        default:
          fail(`${where} has an unknown type. Use text, note, header, link, button or tiles`);
      }
    });
    pages.push({ id: pageId, label, title, subtitle, blocks });
  }

  const nodes: PluginNodeSpec[] = [];
  if (o.nodes !== undefined) {
    if (!Array.isArray(o.nodes)) fail("nodes must be an array");
    const seen = new Set<string>();
    for (const rawNode of o.nodes) {
      if (typeof rawNode !== "object" || rawNode === null) fail("each node must be an object");
      const n = rawNode as Record<string, unknown>;
      const nodeId = id(n.id, "node id");
      if (seen.has(nodeId)) fail(`node id ${nodeId} is used twice`);
      if (PAGES.includes(nodeId)) fail(`node id ${nodeId} clashes with a built-in page`);
      seen.add(nodeId);
      if (!/^-?\d+(\.\d+)?$/.test(String(n.x ?? ""))) fail(`node ${nodeId} x must be a number`);
      if (!/^-?\d+(\.\d+)?$/.test(String(n.y ?? ""))) fail(`node ${nodeId} y must be a number`);
      const x = num(n.x, `node ${nodeId} x`, -10, 110);
      const y = num(n.y, `node ${nodeId} y`, -10, 110);
      const d = num(n.d ?? 96, `node ${nodeId} d`, 56, 260);
      const ly = optNum(n.ly, `node ${nodeId} ly`, 4, 60) ?? 14;
      const icon = n.icon === undefined ? undefined : str(n.icon, `node ${nodeId} icon`, 20);
      if (icon && !GLYPHS.includes(icon)) fail(`node ${nodeId} icon must be one of: ${GLYPHS.join(", ")}`);
      nodes.push({
        id: nodeId,
        label: str(n.label, `node ${nodeId} label`, 24),
        x,
        y,
        d,
        ly,
        icon,
        page: n.page === undefined ? undefined : checkPage(n.page, ownPages, `node ${nodeId} page`),
      });
    }
    if (nodes.length > 12) fail("too many nodes (12 max)");
  }

  const theme: PluginThemeSpec = {};
  if (o.theme !== undefined) {
    if (typeof o.theme !== "object" || o.theme === null) fail("theme must be an object");
    const t = o.theme as Record<string, unknown>;
    if (t.accent !== undefined) theme.accent = hex(t.accent, "theme accent");
    if (t.orb !== undefined) {
      if (!Array.isArray(t.orb) || t.orb.length !== 4) fail("theme orb must be an array of 4 hex colours");
      theme.orb = t.orb.map((c, i) => hex(c, `theme orb colour ${i + 1}`)) as [string, string, string, string];
    }
    theme.orbScale = optNum(t.orbScale, "theme orbScale", 0.7, 1.5);
    theme.hubScale = optNum(t.hubScale, "theme hubScale", 0.7, 1.5);
    theme.chainScale = optNum(t.chainScale, "theme chainScale", 0.5, 2);
    theme.labelScale = optNum(t.labelScale, "theme labelScale", 0.7, 1.7);
    theme.glow = optNum(t.glow, "theme glow", 0.3, 1.8);
  }

  if (!nodes.length && !pages.length && !Object.keys(theme).length) {
    fail("This plugin has nothing to apply. Add nodes, pages or a theme.");
  }

  return {
    id: pluginId,
    name,
    version,
    author,
    description,
    source: raw.trim(),
    doc: { id: pluginId, name, version, author, description, theme, nodes, pages },
    enabled: true,
    installed: Date.now(),
  };
}
