import { useEffect, useRef } from "react";
import { SkinViewer, WalkingAnimation, IdleAnimation } from "skinview3d";

type Props = {
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
}: Props) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const viewer = useRef<SkinViewer | null>(null);

  useEffect(() => {
    if (!canvas.current) return;
    const v = new SkinViewer({ canvas: canvas.current, width, height, skin });
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
      v.dispose();
      viewer.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    if (cape) v.loadCape(cape);
    else v.resetCape();
  }, [cape]);

  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    v.animation = walk ? new WalkingAnimation() : stillness() ? null : new IdleAnimation();
  }, [walk]);

  return <canvas ref={canvas} className="skin3d" style={{ width, height, display: "block" }} />;
}
