import { MascotFace } from "./MascotFace";
import { cx } from "../utils/cx";

export type MascotId = "grin";

/**
 * The one mascot: the chrome logo. Everything that shows a face (hub, pill, account,
 * constellation centre) uses this, always behind the same glass gloss so nothing looks
 * out of place next to the orbs.
 */
export function Mascot({
  variant = "yellow",
  className,
  bare = false,
  animated = true,
}: {
  variant?: "yellow" | "mono";
  className?: string;
  bare?: boolean;
  animated?: boolean;
}) {
  return (
    <span className={cx("mascot", `mascot--${variant}`, !animated && "is-still", className)}>
      <MascotFace variant={variant} bare={bare} className="mascot-body" />
    </span>
  );
}
