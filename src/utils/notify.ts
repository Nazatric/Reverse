import { useEffect, useState } from "react";

/**
 * Real PWA notification permission for Android + iOS.
 *
 * The order matters and is the whole reason this works:
 *  1. the permission request runs inside the tap handler *before any await*
 *     (a suspended/awaited async fn loses Chrome's transient activation on Android);
 *  2. we leave fullscreen first when possible — Chrome drops or hides prompts in fullscreen;
 *  3. the actual notification is shown through the service worker
 *     (`new Notification()` throws on Android Chrome);
 *  4. iOS (Safari 16.4+) can only grant it for an app installed to the Home Screen.
 */
export type NotifyState = "granted" | "denied" | "default" | "unsupported" | "needs-install";
export type MediaPayload = { title: string; sub: string; playing: boolean };

export const isStandalone = () =>
  window.matchMedia("(display-mode: standalone)").matches ||
  window.matchMedia("(display-mode: fullscreen)").matches ||
  (navigator as unknown as { standalone?: boolean }).standalone === true;

const isIOS = () =>
  /iphone|ipad|ipod/i.test(navigator.userAgent) ||
  (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

export function notifyState(): NotifyState {
  if (typeof window === "undefined") return "unsupported";
  if (!("Notification" in window)) {
    return isIOS() ? "needs-install" : "unsupported";
  }
  const perm = Notification.permission;
  if (perm === "default" && isIOS() && !isStandalone()) return "needs-install";
  return perm as NotifyState;
}

async function serviceWorker(): Promise<ServiceWorkerRegistration | null> {
  if (!("serviceWorker" in navigator)) return null;
  try {
    return (await navigator.serviceWorker.register("./sw.js")) ?? null;
  } catch {
    try {
      return await navigator.serviceWorker.ready;
    } catch {
      return null;
    }
  }
}

const baseOptions = () => ({
  icon: "./images/mascot.png",
  badge: "./images/mascot.png",
  tag: "gadget-media",
  renotify: true,
  data: { url: "./" },
  silent: true,
} as NotificationOptions & { renotify?: boolean });

/** Show / update the media notification through the service worker (Android-safe). */
export async function showMediaNotification(payload: MediaPayload | null) {
  const reg = await serviceWorker();
  if (!reg || !("showNotification" in reg)) return false;
  if (Notification.permission !== "granted") return false;
  try {
    if (!payload) {
      const existing = await reg.getNotifications({ tag: "gadget-media" });
      existing.forEach((n) => n.close());
      return true;
    }
    const maxActions = (Notification as unknown as { maxActions?: number }).maxActions ?? 0;
    const actions = maxActions ? [{ action: "toggle", title: payload.playing ? "Pause" : "Play" }, { action: "next", title: "Next" }] : [];
    await reg.showNotification(payload.title, {
      ...baseOptions(),
      body: payload.sub,
      actions,
      requireInteraction: payload.playing,
    } as NotificationOptions);
    return true;
  } catch {
    return false;
  }
}

/**
 * Ask for real notification permission. MUST be awaited directly from a click/tap handler.
 * Returns the permission state after the request.
 */
export async function requestNotify(): Promise<NotifyState> {
  if (notifyState() === "needs-install" || notifyState() === "unsupported") return notifyState();

  // 0. Leave fullscreen first — without awaiting — so the prompt isn't hidden,
  //    then ask in the very same task so the Android gesture is still alive.
  if (document.fullscreenElement) {
    void document.exitFullscreen?.().catch(() => undefined);
  }

  // 1. Ask while we still own the user's gesture.
  const ask = (): Promise<NotificationPermission> =>
    new Promise((resolve) => {
      try {
        const p = Notification.requestPermission(resolve);
        if (p && typeof (p as Promise<NotificationPermission>).then === "function") {
          (p as Promise<NotificationPermission>).then(resolve, () => resolve(Notification.permission));
        }
      } catch {
        resolve(Notification.permission);
      }
    });
  const permission = await ask();

  try {
    localStorage.setItem("gadget:notify-asked", "1");
  } catch {
    /* private mode */
  }

  // 2. Get the worker ready and post a confirmation so the permission is visibly real.
  if (permission === "granted") {
    const reg = await serviceWorker();
    try {
      await reg?.showNotification("The Gadget is ready", {
        icon: "./images/mascot.png",
        badge: "./images/mascot.png",
        tag: "gadget-setup",
        silent: true,
      });
    } catch {
      /* silent failure is fine */
    }
  }
  return permission as NotifyState;
}

export function alreadyAsked() {
  try {
    return localStorage.getItem("gadget:notify-asked") === "1";
  } catch {
    return false;
  }
}

export function markAsked() {
  try {
    localStorage.setItem("gadget:notify-asked", "1");
  } catch {
    /* ignore */
  }
}

/** Live permission state (refreshes when the user changes it in site settings). */
export function useNotifyState(): [NotifyState, (s: NotifyState) => void] {
  const [state, setState] = useState<NotifyState>(notifyState);
  useEffect(() => {
    let status: PermissionStatus | null = null;
    const sync = () => setState(notifyState());
    navigator.permissions
      ?.query({ name: "notifications" as PermissionName })
      .then((s) => {
        status = s;
        s.onchange = sync;
      })
      .catch(() => undefined);
    document.addEventListener("visibilitychange", sync);
    window.addEventListener("focus", sync);
    return () => {
      if (status) status.onchange = null;
      document.removeEventListener("visibilitychange", sync);
      window.removeEventListener("focus", sync);
    };
  }, []);
  return [state, setState];
}

export const NOTIFY_HELP: Record<NotifyState, string> = {
  granted: "on — now playing shows on your lock screen",
  default: "tap to allow notifications",
  denied: "blocked — allow The Gadget in this site's settings",
  unsupported: "this browser doesn't support notifications",
  "needs-install": "add the app to your Home Screen first (iOS 16.4+)",
};
