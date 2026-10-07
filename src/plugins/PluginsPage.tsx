import { useRef, useState } from "react";
import { Page } from "../components/ui/Page";
import { GlassButton } from "../components/ui/Controls";
import { Icon } from "../components/ui/Icons";
import { Sheet } from "../components/ui/Sheet";
import { cx } from "../utils/cx";
import { ui } from "../utils/audio";
import { disablePlugin, enablePlugin, exportPlugin, installFromUrl, installPlugin, pluginSource, removePlugin, usePluginRecords } from "./registry";
import { EXAMPLES, PLUGIN_TEMPLATE } from "./examples";

export function PluginsPage() {
  const records = usePluginRecords();
  const [url, setUrl] = useState("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [source, setSource] = useState("");
  const fileInput = useRef<HTMLInputElement>(null);

  const say = (ok: boolean, text: string) => {
    setStatus({ ok, text });
    window.setTimeout(() => setStatus(null), 5000);
  };

  const install = (raw: string, from: string) => {
    const res = installPlugin(raw);
    if (res.ok) {
      say(true, `${res.name} installed`);
      setEditorOpen(false);
      setSource("");
    } else {
      say(false, `${from}: ${res.error}`);
    }
  };

  const importUrl = async () => {
    if (!url.trim()) return;
    setBusy(true);
    const res = await installFromUrl(url.trim());
    setBusy(false);
    if (res.ok) {
      setUrl("");
      say(true, `${res.name} installed`);
    } else {
      say(false, res.error);
    }
  };

  const download = (id: string) => {
    const json = exportPlugin(id);
    if (!json) return;
    const blob = new Blob([json], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `${id}.plugin.json`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const onFile = (files: FileList | null) => {
    const file = files?.[0];
    if (!file) return;
    if (file.size > 200_000) return say(false, "That file is larger than 200 KB");
    file.text().then((body) => install(body, file.name));
  };

  return (
    <Page title="plugins" sub={`${records.filter((r) => r.enabled).length} active · ${records.length} installed`}>
      <input
        ref={fileInput}
        type="file"
        accept=".json,application/json,.gadgetplugin"
        hidden
        onChange={(e) => {
          onFile(e.target.files);
          e.target.value = "";
        }}
      />

      {status && <p className={status.ok ? "notice" : "notice notice--error"}>{status.text}</p>}

      <div className="plugin-actions">
        <GlassButton variant="primary" icon="plus" onClick={() => { ui.tap(); fileInput.current?.click(); }}>
          import .json
        </GlassButton>
        <GlassButton icon="download" onClick={() => { ui.tap(); setEditorOpen(true); }}>
          paste json
        </GlassButton>
      </div>

      <form
        className="plugin-url"
        onSubmit={(e) => {
          e.preventDefault();
          void importUrl();
        }}
      >
        <input
          className="field-input"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          placeholder="https://example.com/my.plugin.json"
          inputMode="url"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          aria-label="Plugin URL"
        />
        <GlassButton type="submit" disabled={busy || !url.trim()}>
          <span>{busy ? "fetching…" : "fetch"}</span>
        </GlassButton>
      </form>

      <h2 className="group-title">installed</h2>
      {records.length === 0 ? (
        <p className="picker-note">
          No plugins yet. Import a .json file, paste one in, or install an example below. A plugin is
          just data — it can add orbs, add pages and change colours, and nothing it contains is executed.
        </p>
      ) : (
        <ul className="plugin-list">
          {records.map((r) => (
            <li key={r.id} className={cx("plugin", r.enabled && "plugin--on")}>
              <div className="plugin-head">
                <button
                  type="button"
                  className="plugin-toggle"
                  aria-pressed={r.enabled}
                  onClick={() => { ui.tap(); r.enabled ? disablePlugin(r.id) : enablePlugin(r.id); }}
                >
                  <span className="plugin-name">{r.name}</span>
                  <span className="plugin-meta">v{r.version} · {r.author}</span>
                </button>
                <span className={cx("plugin-state", r.enabled && "is-on")}>{r.enabled ? "on" : "off"}</span>
              </div>
              {r.description && <p className="plugin-desc">{r.description}</p>}
              <div className="plugin-actions">
                <GlassButton variant="ghost" onClick={() => download(r.id)}>
                  <Icon name="download" />
                  <span>export</span>
                </GlassButton>
                <GlassButton
                  variant="ghost"
                  onClick={() => {
                    const src = pluginSource(r.id);
                    if (src) {
                      setSource(src);
                      setEditorOpen(true);
                    }
                  }}
                >
                  <Icon name="edit" />
                  <span>edit</span>
                </GlassButton>
                <GlassButton variant="danger" icon="trash" onClick={() => { ui.tap(); removePlugin(r.id); say(true, `${r.name} removed`); }}>
                  <span>remove</span>
                </GlassButton>
              </div>
            </li>
          ))}
        </ul>
      )}

      <h2 className="group-title">examples</h2>
      <ul className="plugin-list">
        {EXAMPLES.map((ex) => {
          const installed = records.find((r) => r.id === ex.id);
          return (
            <li key={ex.id} className={cx("plugin", installed?.enabled && "plugin--on")}>
              <div className="plugin-head">
                <button
                  type="button"
                  className="plugin-toggle"
                  aria-pressed={!!installed?.enabled}
                  onClick={() => {
                    ui.tap();
                    if (installed) installed.enabled ? disablePlugin(ex.id) : enablePlugin(ex.id);
                    else install(ex.source, ex.name);
                  }}
                >
                  <span className="plugin-name">{ex.name}</span>
                  <span className="plugin-meta">{ex.description}</span>
                </button>
                <span className={cx("plugin-state", !!installed?.enabled && "is-on")}>
                  {installed ? (installed.enabled ? "on" : "off") : "add"}
                </span>
              </div>
            </li>
          );
        })}
      </ul>

      {editorOpen && (
        <Sheet title="plugin json" onClose={() => setEditorOpen(false)}>
          <p className="picker-note">
            Paste or edit a plugin document. It is validated before it is applied, and nothing in it is executed.
          </p>
          <textarea
            className="plugin-editor"
            value={source}
            onChange={(e) => setSource(e.target.value)}
            spellCheck={false}
            placeholder={PLUGIN_TEMPLATE}
            rows={14}
          />
          <div className="sheet-actions">
            <GlassButton
              variant="ghost"
              onClick={() => {
                setSource(PLUGIN_TEMPLATE);
                ui.tap();
              }}
            >
              <span>template</span>
            </GlassButton>
            <GlassButton
              variant="primary"
              icon="check"
              onClick={() => install(source, "Pasted plugin")}
            >
              <span>apply</span>
            </GlassButton>
          </div>
        </Sheet>
      )}
    </Page>
  );
}
