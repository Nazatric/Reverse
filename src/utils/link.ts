/** Add https:// to bare domains; leave real schemes (tel:, steam://, intent://) alone. */
export function normaliseLink(raw: string) {
  const v = raw.trim();
  if (!v) return "";
  return /^[a-z][a-z0-9+.-]*:/i.test(v) ? v : `https://${v}`;
}

export function openLink(url: string) {
  if (!url) return;
  if (/^https?:/i.test(url)) window.open(url, "_blank", "noopener,noreferrer");
  else window.location.href = url;
}

export function ago(ts: number | null) {
  if (!ts) return "never";
  const s = Math.max(1, Math.round((Date.now() - ts) / 1000));
  if (s < 60) return "just now";
  const m = Math.round(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.round(h / 24);
  if (d < 30) return `${d}d ago`;
  return new Date(ts).toLocaleDateString();
}

export function duration(sec: number) {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m`;
  return `${Math.floor(sec)}s`;
}
