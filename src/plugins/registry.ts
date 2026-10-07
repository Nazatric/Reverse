import { useSyncExternalStore } from "react";
import { readStored, writeStored } from "../utils/storage";
import { parsePlugin, type ActivePlugin, type PluginDocument, type PluginRecord } from "./schema";

/**
 * Plugin store. Each installed plugin is a validated JSON document; this file owns
 * the list, notifies React, and computes one merged view of everything the UI renders.
 * Applying a plugin only ever writes CSS custom properties and adds typed hub
 * nodes/pages — no code from the plugin is executed.
 */
const KEY = "plugins";

let records: PluginRecord[] = readStored<PluginRecord[]>(KEY, []);
const subs = new Set<() => void>();
let snapshot: PluginRecord[] = records;

function commit(next: PluginRecord[]) {
  records = next;
  snapshot = next;
  writeStored(KEY, next);
  rebuild();
  subs.forEach((f) => f());
}

function subscribe(cb: () => void) {
  subs.add(cb);
  return () => subs.delete(cb);
}

export function usePluginRecords(): PluginRecord[] {
  return useSyncExternalStore(subscribe, () => snapshot, () => snapshot);
}

/* ------------------------------------------------- merged active plugin view */

let activeSnapshot: { nodes: ActivePlugin[]; theme: NonNullable<PluginDocument["theme"]> } = { nodes: [], theme: {} };
const activeSubs = new Set<() => void>();

function rebuild() {
  const enabled = records.filter((r) => r.enabled);
  const nodes: ActivePlugin[] = [];
  const theme: PluginDocument["theme"] = {};
  for (const r of enabled) {
    const doc = r.doc ?? { id: r.id, name: r.name };
    if (doc.theme) Object.assign(theme, doc.theme);
    if (doc.nodes?.length) nodes.push({ id: r.id, doc, nodes: doc.nodes, pages: doc.pages ?? [], theme: doc.theme ?? {} });
  }
  activeSnapshot = { nodes, theme };
  activeSubs.forEach((f) => f());
}
rebuild();

export function useActivePlugins(): { nodes: ActivePlugin[]; theme: NonNullable<PluginDocument["theme"]> } {
  return useSyncExternalStore(
    (cb) => {
      activeSubs.add(cb);
      return () => activeSubs.delete(cb);
    },
    () => activeSnapshot,
    () => activeSnapshot,
  );
}

/** Flat list of every plugin-declared page, in install order. */
export function usePluginPages(): { pluginId: string; page: NonNullable<PluginDocument["pages"]>[number] }[] {
  const { nodes } = useActivePlugins();
  return nodes.flatMap((p) => (p.pages ?? []).map((page) => ({ pluginId: p.id, page })));
}

/* ------------------------------------------------------------- installation */

export function installPlugin(raw: string): { ok: true; id: string; name: string } | { ok: false; error: string } {
  let record: PluginRecord;
  try {
    record = parsePlugin(raw);
  } catch (err) {
    return { ok: false, error: err instanceof Error ? err.message : "This plugin couldn't be read" };
  }
  const existing = records.find((r) => r.id === record.id);
  const next = existing
    ? records.map((r) => (r.id === record.id ? { ...record, enabled: r.enabled, installed: r.installed } : r))
    : [...records, record];
  commit(next);
  return { ok: true, id: record.id, name: record.name };
}

export function installFromUrl(url: string): Promise<{ ok: true; id: string; name: string } | { ok: false; error: string }> {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return Promise.resolve({ ok: false, error: "That isn't a valid URL" });
  }
  if (!/^https?:$/.test(parsed.protocol)) return Promise.resolve({ ok: false, error: "Only http and https URLs are supported" });

  return fetch(parsed.toString(), { mode: "cors" })
    .then((res) => {
      if (!res.ok) return { ok: false as const, error: `The server answered ${res.status}` };
      return res.text().then((body) => installPlugin(body));
    })
    .catch(() => ({ ok: false as const, error: "The plugin couldn't be fetched. Check the address and that it allows cross-origin requests." }));
}

export function enablePlugin(id: string) {
  commit(records.map((r) => (r.id === id ? { ...r, enabled: true } : r)));
}

export function disablePlugin(id: string) {
  commit(records.map((r) => (r.id === id ? { ...r, enabled: false } : r)));
}

export function removePlugin(id: string) {
  commit(records.filter((r) => r.id !== id));
}

/** Export a plugin back out as pretty JSON so people can share and edit it. */
export function exportPlugin(id: string): string | null {
  const record = records.find((r) => r.id === id);
  if (!record) return null;
  const { source, ...meta } = record;
  void meta;
  try {
    return JSON.stringify(JSON.parse(record.source), null, 2);
  } catch {
    return record.source;
  }
}

export function pluginSource(id: string): string | null {
  return records.find((r) => r.id === id)?.source ?? null;
}

/** Restore any plugins that were installed previously. */
export function bootPlugins() {
  for (const r of [...records]) {
    if (r.enabled && !r.doc) {
      try {
        const parsed = parsePlugin(r.source);
        records = records.map((x) => (x.id === parsed.id ? { ...parsed, enabled: x.enabled, installed: x.installed } : x));
      } catch {
        records = records.map((x) => (x.id === r.id ? { ...x, enabled: false } : x));
      }
    }
  }
  writeStored(KEY, records);
  rebuild();
}
