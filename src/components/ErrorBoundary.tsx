import { Component, type ErrorInfo, type ReactNode } from "react";

interface State {
  error: Error | null;
}

/**
 * Last line of defence: if anything throws during render we show a readable
 * panel instead of a silent black screen. "Reload" recovers the session;
 * "Reset data" wipes local state if a saved value is what broke it.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Keep the details visible for debugging without a console.
    try {
      localStorage.setItem("gadget:crash", `${error.stack || error.message}\n${info.componentStack || ""}`);
    } catch {
      /* ignore */
    }
    console.error("[the-gadget]", error, info);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <div className="crash">
        <div className="crash-card">
          <p className="crash-kicker">the gadget</p>
          <h1 className="crash-title">something broke</h1>
          <p className="crash-msg">{error.message || String(error)}</p>
          <div className="crash-actions">
            <button
              type="button"
              className="btn btn--primary"
              onClick={() => {
                location.reload();
              }}
            >
              <span>reload</span>
            </button>
            <button
              type="button"
              className="btn btn--ghost"
              onClick={() => {
                try {
                  Object.keys(localStorage)
                    .filter((k) => k.startsWith("gadget:") || k.startsWith("gadget-legacy:"))
                    .forEach((k) => localStorage.removeItem(k));
                  sessionStorage.clear();
                } catch {
                  /* ignore */
                }
                location.reload();
              }}
            >
              <span>reset data</span>
            </button>
          </div>
        </div>
      </div>
    );
  }
}
