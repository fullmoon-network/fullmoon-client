import { useEffect, useState } from "react";
import { Icon } from "./Icon";
import { useStore } from "../state/store";
import { isRealCore } from "../core/client";
import { useT } from "../i18n";
import BRAND from "../brand";

declare const __APP_VERSION__: string;
const APP_VERSION = typeof __APP_VERSION__ !== "undefined" ? __APP_VERSION__ : "dev";

/** The seal: 月 on its square, the one mark the launcher keeps. The glyph is drawn, because no
 *  face the launcher ships carries the Han character and a fallback font is not ours to rely on. */
export function Seal() {
  return (
    <span className="seal" aria-hidden>
      <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="1.1" strokeLinecap="square">
        <path d="M3 1.2v5.3c0 1.3-.5 2.3-1.6 3" />
        <path d="M3 1.2h4.6v7.6c0 .5-.3.8-.9.7" />
        <path d="M3 3.9h4.6M3 6.3h4.6" />
      </svg>
    </span>
  );
}

/* In the shell the buttons drive the real window; in the browser they fall
   back to fullscreen so the same chrome stays usable in vite dev. */
async function win() {
  const { getCurrentWindow } = await import("@tauri-apps/api/window");
  return getCurrentWindow();
}

export function TitleBar() {
  const { toast } = useStore();
  const { t } = useT();
  const [maximized, setMaximized] = useState(false);

  useEffect(() => {
    if (!isRealCore) return;
    let off: (() => void) | null = null;
    void (async () => {
      const w = await win();
      setMaximized(await w.isMaximized());
      off = await w.onResized(async () => setMaximized(await w.isMaximized()));
    })();
    return () => off?.();
  }, []);

  const minimize = () => {
    if (!isRealCore) return toast("info", t("titlebar.desktopOnly"));
    void win().then((w) => w.minimize());
  };

  const toggleMax = () => {
    if (!isRealCore) {
      if (document.fullscreenElement) void document.exitFullscreen();
      else void document.documentElement.requestFullscreen().catch(() => {});
      return;
    }
    void win().then((w) => w.toggleMaximize());
  };

  const close = () => {
    if (!isRealCore) return toast("info", t("titlebar.desktopOnly"));
    void win().then((w) => w.close());
  };

  return (
    <header className="titlebar" data-tauri-drag-region>
      <Seal />
      <span className="titlebar-name" data-tauri-drag-region>{BRAND.name}</span>
      <span className="titlebar-tag num" data-tauri-drag-region>{APP_VERSION}</span>
      <div className="titlebar-controls">
        <button className="winbtn" aria-label={t("titlebar.minimize")} onClick={minimize}>
          <Icon name="minus" size={12} strokeWidth={1.4} />
        </button>
        <button className="winbtn" aria-label={t(maximized ? "titlebar.restore" : "titlebar.maximize")} onClick={toggleMax}>
          <Icon name={maximized ? "restore" : "maximize"} size={11} strokeWidth={1.4} />
        </button>
        <button className="winbtn winbtn-close" aria-label={t("titlebar.close")} onClick={close}>
          <Icon name="x" size={12} strokeWidth={1.4} />
        </button>
      </div>
    </header>
  );
}
