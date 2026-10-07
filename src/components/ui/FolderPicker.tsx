import { useRef } from "react";
import { GlassButton } from "./Controls";
import type { MusicPlayer } from "../../hooks/useMusicPlayer";

/**
 * Folder selection shared by Music and Config.
 * Chrome/Edge desktop: native folder picker, remembered across visits.
 * Android/Safari/Firefox: directory or multi-file input (browsers don't persist access there).
 */
export function FolderPicker({ player, compact }: { player: MusicPlayer; compact?: boolean }) {
  const dirInput = useRef<HTMLInputElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const loading = player.status === "loading";
  const has = player.library.length > 0;

  return (
    <div className={compact ? "picker picker--compact" : "picker"}>
      {player.status === "needs-permission" && (
        <GlassButton variant="primary" icon="folder" onClick={() => void player.reconnect()}>
          reconnect “{player.folderName}”
        </GlassButton>
      )}

      {player.canPickFolder ? (
        <GlassButton variant={has ? undefined : "primary"} icon="folder" disabled={loading} onClick={() => void player.chooseFolder()}>
          {loading ? "scanning…" : has ? "change folder" : "choose music folder"}
        </GlassButton>
      ) : (
        <>
          <GlassButton variant={has ? undefined : "primary"} icon="folder" onClick={() => dirInput.current?.click()}>
            {has ? "change folder" : "choose music folder"}
          </GlassButton>
          <GlassButton icon="note" onClick={() => fileInput.current?.click()}>
            pick files
          </GlassButton>
          <input
            ref={dirInput}
            type="file"
            hidden
            multiple
            {...({ webkitdirectory: "", directory: "" } as Record<string, string>)}
            onChange={(e) => {
              if (e.target.files?.length) player.loadFiles(e.target.files);
              e.target.value = "";
            }}
          />
          <input
            ref={fileInput}
            type="file"
            hidden
            multiple
            accept="audio/*,.mp3,.m4a,.flac,.wav,.ogg,.opus,.aac"
            onChange={(e) => {
              if (e.target.files?.length) player.loadFiles(e.target.files);
              e.target.value = "";
            }}
          />
        </>
      )}

      {has && (
        <GlassButton variant="ghost" icon="close" onClick={() => void player.clearLibrary()}>
          forget
        </GlassButton>
      )}

      {!compact && (
        <p className="picker-note">
          {player.canPickFolder
            ? "Files are read in place and never uploaded. Your folder is remembered on this browser."
            : "Files are read in place and never uploaded. This browser can’t remember folder access, so pick it again next visit."}
        </p>
      )}
      {player.error && <p className="notice">{player.error}</p>}
    </div>
  );
}
