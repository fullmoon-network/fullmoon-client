import { useEffect, useRef } from "react";
import { SkinViewer, WalkingAnimation, IdleAnimation } from "skinview3d";
import { createRenderGate } from "../core/renderGate";
import { useStore } from "../state/store";

export type Skin3DProps = {
  skin?: string;
  cape?: string | null;
  width?: number;
  height?: number;
  walk?: boolean;
  rotate?: boolean;
  /** initial y rotation, radians. PI shows the player's back — where capes are. */
  angle?: number;
  zoom?: number;
};

/* A figure that turns on its own is motion the player did not ask for, so reduced motion stills
   the turntable and the idle sway; a walk the player picks still walks. */
const stillness = () =>
  typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches;

export default function Skin3D({
  skin = "/skins/blackcow.png",
  cape = null,
  width = 260,
  height = 380,
  walk = false,
  rotate = true,
  angle = 0,
  zoom = 0.82,
}: Skin3DProps) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const viewer = useRef<SkinViewer | null>(null);
  const { game } = useStore();
  const inGame = game.state === "starting" || game.state === "running";

  /* The figure draws every frame, forever; the game running beside it wants that GPU. It draws
     only while the window is up, the canvas is on screen and no game is live (see renderGate). */
  const gate = useRef<ReturnType<typeof createRenderGate> | null>(null);
  useEffect(() => gate.current?.set({ inGame }), [inGame]);

  useEffect(() => {
    const el = canvas.current;
    if (!el) return;
    const v = new SkinViewer({ canvas: el, width, height });
    const g = createRenderGate(v, { tab: !document.hidden, inGame });
    gate.current = g;
    const loaded = () => g.set({ loaded: true });
    v.loadSkin(skin).then(loaded, loaded);
    const onVisibility = () => g.set({ tab: !document.hidden });
    document.addEventListener("visibilitychange", onVisibility);
    const seen =
      typeof IntersectionObserver === "function"
        ? new IntersectionObserver((entries) =>
            g.set({ onScreen: entries[entries.length - 1]?.isIntersecting ?? true }),
          )
        : null;
    seen?.observe(el);
    const still = stillness();
    v.autoRotate = rotate && !still;
    v.autoRotateSpeed = 0.55;
    v.playerWrapper.rotation.y = angle;
    v.zoom = zoom;
    v.fov = 42;
    v.animation = walk ? new WalkingAnimation() : still ? null : new IdleAnimation();
    v.controls.enableZoom = false;
    v.controls.enablePan = false;
    viewer.current = v;
    return () => {
      document.removeEventListener("visibilitychange", onVisibility);
      seen?.disconnect();
      gate.current = null;
      v.dispose();
      viewer.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    const redraw = () => v.renderPaused && v.render();
    if (cape) v.loadCape(cape).then(redraw, redraw);
    else {
      v.resetCape();
      redraw();
    }
  }, [cape]);

  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    v.animation = walk ? new WalkingAnimation() : stillness() ? null : new IdleAnimation();
  }, [walk]);

  return <canvas ref={canvas} className="skin3d" style={{ width, height, display: "block" }} />;
}
