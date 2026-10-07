import type { ReactNode } from "react";
import { cx } from "../../utils/cx";

export type IconName =
  | "play" | "pause" | "next" | "prev" | "shuffle" | "repeat" | "repeat-one" | "search" | "plus" | "close"
  | "trash" | "edit" | "link" | "folder" | "volume" | "star" | "star-fill" | "check" | "playlist-add"
  | "image" | "fullscreen" | "exit-fullscreen" | "phone" | "chat" | "external" | "note" | "disc"
  | "list" | "playlists" | "chevron" | "download" | "user" | "gamepad" | "people";

const S = { fill: "none", stroke: "currentColor" } as const;

const PATHS: Record<IconName, ReactNode> = {
  play: <path d="M8 5.5v13l11-6.5z" fill="currentColor" stroke="none" />,
  pause: <path d="M7 5h4v14H7zM13 5h4v14h-4z" fill="currentColor" stroke="none" />,
  next: <path d="M6 5.5v13l9-6.5zM17 5h2.2v14H17z" fill="currentColor" stroke="none" />,
  prev: <path d="M18 5.5v13l-9-6.5zM5 5h2.2v14H5z" fill="currentColor" stroke="none" />,
  shuffle: <path {...S} d="M3 7h4l10 10h4M3 17h4l10-10h4M18 4l3 3-3 3M18 14l3 3-3 3" />,
  repeat: <path {...S} d="M4 10V8a3 3 0 013-3h12M20 14v2a3 3 0 01-3 3H5M16 2l3 3-3 3M8 22l-3-3 3-3" />,
  "repeat-one": <path {...S} d="M4 10V8a3 3 0 013-3h12M20 14v2a3 3 0 01-3 3H5M16 2l3 3-3 3M8 22l-3-3 3-3M11 10l1.6-1.2V15" />,
  search: <path {...S} d="M10.5 16a5.5 5.5 0 100-11 5.5 5.5 0 000 11zM15 15l5 5" />,
  plus: <path {...S} d="M12 5v14M5 12h14" />,
  close: <path {...S} d="M6 6l12 12M18 6L6 18" />,
  trash: <path {...S} d="M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12M10 11v5M14 11v5" />,
  edit: <path {...S} d="M4 20l1-4L16 5l3 3L8 19zM14 7l3 3" />,
  link: <path {...S} d="M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1" />,
  folder: <path {...S} d="M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />,
  volume: <path {...S} d="M4 9.5v5h3.5L12 18V6L7.5 9.5zM15.5 9a4 4 0 010 6M18 6.5a8 8 0 010 11" />,
  star: <path {...S} d="M12 4l2.4 5 5.4.7-4 3.8 1 5.4L12 16.3 7.2 18.9l1-5.4-4-3.8 5.4-.7z" />,
  "star-fill": <path d="M12 4l2.4 5 5.4.7-4 3.8 1 5.4L12 16.3 7.2 18.9l1-5.4-4-3.8 5.4-.7z" fill="currentColor" stroke="currentColor" />,
  check: <path {...S} d="M5 12.5l4.5 4.5L19 7.5" />,
  "playlist-add": <path {...S} d="M4 7h11M4 12h11M4 17h6M17 14v6M14 17h6" />,
  image: <path {...S} d="M6 5h12a2 2 0 012 2v10a2 2 0 01-2 2H6a2 2 0 01-2-2V7a2 2 0 012-2zM9 10.5a1.5 1.5 0 100-3 1.5 1.5 0 000 3zM5 17l5-4 4 3 3-2 3 3" />,
  fullscreen: <path {...S} d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5" />,
  "exit-fullscreen": <path {...S} d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5" />,
  phone: <path {...S} d="M6 4h3l1.5 4-2 1.5a11 11 0 006 6l1.5-2 4 1.5v3a2 2 0 01-2 2A15 15 0 014 6a2 2 0 012-2z" />,
  chat: <path {...S} d="M4 6a2 2 0 012-2h12a2 2 0 012 2v8a2 2 0 01-2 2H10l-4 4v-4a2 2 0 01-2-2z" />,
  external: <path {...S} d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 01-1 1H5a1 1 0 01-1-1V7a1 1 0 011-1h5" />,
  note: <path {...S} d="M9 18V6l10-2v12M9 18a3 3 0 11-6 0 3 3 0 016 0zM19 16a3 3 0 11-6 0 3 3 0 016 0z" />,
  disc: <path {...S} d="M12 20a8 8 0 100-16 8 8 0 000 16zM12 14.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z" />,
  list: <path {...S} d="M5 7h14M5 12h14M5 17h9" />,
  playlists: <path {...S} d="M4 7h12M4 12h12M4 17h7M18 10v7a2 2 0 11-2-2" />,
  chevron: <path {...S} d="M9 5l7 7-7 7" />,
  download: <path {...S} d="M12 4v11M7 11l5 5 5-5M5 20h14" />,
  user: <path {...S} d="M12 12a4 4 0 100-8 4 4 0 000 8zM4 20c1-4 4-6 8-6s7 2 8 6" />,
  gamepad: <path {...S} d="M7 8h10a4 4 0 014 4v2.5a2.5 2.5 0 01-4.3 1.7L15 14.5H9l-1.7 1.7A2.5 2.5 0 013 14.5V12a4 4 0 014-4zM8 10.5v3M6.5 12h3M15.5 11.5h.01M17.5 13h.01" />,
  people: <path {...S} d="M9 11a3 3 0 100-6 3 3 0 000 6zM3 19c.6-3 3-4.5 6-4.5s5.4 1.5 6 4.5M16.5 11a2.6 2.6 0 100-5.2M17 14.6c2 .3 3.5 1.7 4 4.4" />,
};

export function Icon({ name, className }: { name: IconName; className?: string }) {
  return (
    <svg className={cx("ico", className)} viewBox="0 0 24 24" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      {PATHS[name]}
    </svg>
  );
}
