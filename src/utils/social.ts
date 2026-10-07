import { useCallback, useRef, useSyncExternalStore } from "react";
import mqtt, { type MqttClient } from "mqtt";
import { readStored, writeStored } from "./storage";

/**
 * Serverless social layer over the free public HiveMQ relay (wss, no account) with EMQX as
 * failover. Every visitor "hosts" under a short code; others add that code to watch them.
 *
 *  - presence  gadget/v1/p/<code>   QoS1, RETAINED + MQTT last-will → a homie's last
 *                                      activity survives even after they go offline
 *  - chat      gadget/v1/c/<a>/<b>  QoS1, both peers subscribe to the same topic
 *
 * Chat is peer-to-peer through the broker relay (no server of our own, no account).
 */

export interface PresenceNow {
  kind: "music" | "game";
  title?: string;
  artist?: string;
  name?: string;
}
export interface Presence {
  v: number;
  t: number;
  up?: boolean; // false = last-will tombstone
  name?: string;
  tagline?: string;
  status?: string;
  avatar?: string | null;
  now?: PresenceNow | null;
  listenedSec?: number;
  games?: number;
  top?: { title: string; artist: string; sec: number }[];
}
export interface ChatMsg {
  id: string;
  from: string;
  name: string;
  text: string;
  t: number;
}

const ROOT = "gadget/v1";
const BROKERS = ["wss://broker.hivemq.com:8884/mqtt", "wss://broker.emqx.io:8084/mqtt"];
const ALPHA = "abcdefghjkmnpqrstuvwxyz23456789";

/* ---------------- host code ---------------- */

let myCode: string | null = readStored<string | null>("hostcode", null);
export function getMyCode(): string {
  if (!myCode) {
    let s = "";
    for (let i = 0; i < 6; i++) s += ALPHA[Math.floor(Math.random() * ALPHA.length)];
    myCode = `hp${s}`;
    writeStored("hostcode", myCode);
  }
  return myCode;
}
export const normalizeCode = (raw: string) => raw.trim().toLowerCase().replace(/\s+/g, "");
export const isValidCode = (c: string) => /^[a-z0-9]{3,16}$/.test(c);

/* ---------------- connection state ---------------- */

export type ConnState = "idle" | "connecting" | "online" | "offline";
let conn: ConnState = "idle";
const connSubs = new Set<() => void>();
function setConn(s: ConnState) {
  if (conn === s) return;
  conn = s;
  connSubs.forEach((f) => f());
}
export function useConn(): ConnState {
  return useSyncExternalStore(
    (f) => {
      connSubs.add(f);
      return () => connSubs.delete(f);
    },
    () => conn,
    () => conn,
  );
}

/* ---------------- presence store ---------------- */

const presMap = new Map<string, Presence>();
const presSubs = new Set<() => void>();
export function usePresence(code: string | null | undefined): Presence | undefined {
  const c = code ?? "";
  const snap = useCallback(() => (c ? presMap.get(c) : undefined), [c]);
  return useSyncExternalStore(
    (f) => {
      presSubs.add(f);
      return () => presSubs.delete(f);
    },
    snap,
    snap,
  );
}

/* ---------------- chat buffers ---------------- */

const chatMap = new Map<string, ChatMsg[]>();
const chatSubs = new Map<string, Set<() => void>>();
function pushChat(peer: string, m: ChatMsg) {
  const list = chatMap.get(peer) ?? [];
  list.push(m);
  if (list.length > 200) list.splice(0, list.length - 200);
  chatMap.set(peer, list);
  chatSubs.get(peer)?.forEach((f) => f());
}
export function useChatBuffer(code: string): ChatMsg[] {
  // getSnapshot must return a stable reference — cache the empty array per peer.
  const empty = useRef<ChatMsg[]>([]);
  const snap = useCallback(() => chatMap.get(code) ?? empty.current, [code]);
  return useSyncExternalStore(
    (f) => {
      let set = chatSubs.get(code);
      if (!set) chatSubs.set(code, (set = new Set()));
      set.add(f);
      return () => {
        set!.delete(f);
      };
    },
    snap,
    snap,
  );
}

/* ---------------- self presence source ---------------- */

let source: (() => Partial<Presence> | null) | null = null;
export function registerSource(fn: (() => Partial<Presence> | null) | null) {
  source = fn;
  publishSoon();
}
let activeGame: string | null = null;
export function setActiveGame(name: string | null) {
  activeGame = name;
  publishSoon(200);
}
export const getActiveGame = () => activeGame;

function selfPresence(): Presence {
  const base = source ? (source() ?? {}) : {};
  return { v: 1, t: Date.now(), up: true, ...base } as Presence;
}
function tombstone(): Presence {
  return { v: 1, t: Date.now(), up: false };
}

/* ---------------- broker ---------------- */

let client: MqttClient | null = null;
let brokerIdx = 0;
let errors = 0;
let started = false;
const watching = new Set<string>();

function pTopic(c: string) {
  return `${ROOT}/p/${c}`;
}
function chatTopic(a: string, b: string) {
  return `${ROOT}/c/${a < b ? a + "/" + b : b + "/" + a}`;
}

function publish() {
  if (!client || !client.connected) return;
  try {
    client.publish(pTopic(getMyCode()), JSON.stringify(selfPresence()), { qos: 1, retain: true });
  } catch {
    /* broker hiccup — next tick */
  }
}
let pubTimer: number | undefined;
function publishSoon(ms = 350) {
  window.clearTimeout(pubTimer);
  pubTimer = window.setTimeout(publish, ms);
}

let presVersion = 0;
const versionSubs = new Set<() => void>();
export function usePresenceVersion(): number {
  return useSyncExternalStore(
    (f) => {
      versionSubs.add(f);
      return () => versionSubs.delete(f);
    },
    () => presVersion,
    () => presVersion,
  );
}
export function peekPresence(code: string): Presence | undefined {
  return presMap.get(code);
}

function route(topic: string, payload: Uint8Array) {
  try {
    const msg = JSON.parse(new TextDecoder().decode(payload));
    const parts = topic.slice(ROOT.length + 1).split("/"); // ["p", code] | ["c", a, b]
    if (parts[0] === "p") {
      const code = parts[1] ?? "";
      if (watching.has(code) && msg && typeof msg === "object" && msg.v === 1) {
        presMap.set(code, msg as Presence);
        presVersion++;
        presSubs.forEach((f) => f());
      }
    } else if (parts[0] === "c") {
      const [a, b] = [parts[1], parts[2]];
      const mine = getMyCode();
      const p = a === mine ? b : a;
      if (msg && typeof msg === "object" && msg.id && msg.text) {
        pushChat(p, {
          id: String(msg.id),
          from: String(msg.from ?? a),
          name: String(msg.name ?? ""),
          text: String(msg.text),
          t: Number(msg.t) || Date.now(),
        });
      }
    }
  } catch {
    /* malformed frame */
  }
}

function resubscribe() {
  if (!client || !client.connected) return;
  const topics: string[] = [];
  watching.forEach((c) => topics.push(pTopic(c)));
  const mine = getMyCode();
  watching.forEach((c) => topics.push(chatTopic(mine, c)));
  if (topics.length) void client.subscribe(topics, { qos: 1 }, () => undefined);
}

function dropClient() {
  try {
    client?.end(true);
  } catch {
    /* already dead */
  }
  client = null;
}

/** Called whenever the watch list or hosting state changes. Safe to call often. */
export function ensureConnection(watchCodes: string[]) {
  watching.clear();
  watchCodes.filter((c) => c && c !== getMyCode()).forEach((c) => watching.add(c));
  const need = watching.size > 0;
  if (!need) {
    dropClient();
    setConn("idle");
    return;
  }
  if (!started) {
    started = true;
    window.setInterval(publish, 25000);
  }
  if (client) {
    if (client.connected) {
      resubscribe();
      publish();
    }
    return;
  }
  setConn("connecting");
  const url = BROKERS[brokerIdx % BROKERS.length];
  try {
    client = mqtt.connect(url, {
      clientId: `hpw${Math.random().toString(36).slice(2, 12)}`,
      keepalive: 30,
      clean: true,
      reconnectPeriod: 4000,
      connectTimeout: 9000,
      will: { topic: pTopic(getMyCode()), payload: JSON.stringify(tombstone()), qos: 1, retain: true },
    });
  } catch {
    setConn("offline");
    return;
  }
  client.on("connect", () => {
    errors = 0;
    setConn("online");
    resubscribe();
    publish();
  });
  client.on("reconnect", () => setConn("connecting"));
  client.on("close", () => setConn("connecting"));
  client.on("offline", () => setConn("connecting"));
  client.on("message", (t, p) => route(t, p));
  client.on("error", () => {
    errors++;
    if (errors > 4) {
      errors = 0;
      brokerIdx++;
      dropClient();
      setConn("offline");
      window.setTimeout(() => ensureConnection([...watching]), 6000);
    }
  });
}

/** Send a chat line to a peer. Also echoes into the local buffer. */
export function sendChat(peer: string, text: string) {
  const body = text.trim().slice(0, 500);
  if (!body) return;
  const msg: ChatMsg = { id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`, from: getMyCode(), name: selfPresence().name ?? "", text: body, t: Date.now() };
  pushChat(peer, msg);
  if (client && client.connected) {
    try {
      client.publish(chatTopic(getMyCode(), peer), JSON.stringify(msg), { qos: 1, retain: false });
    } catch {
      /* will retry on next message */
    }
  }
}
