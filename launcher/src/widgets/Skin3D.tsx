import { lazyComponent } from "../components/lazyComponent";
import type { Skin3DProps } from "./Skin3DViewer";

/* three.js and skinview3d are the heaviest thing the launcher ships, and the first screen has no
   figure on it, so the viewer is its own chunk. The placeholder is the canvas's own box. */
const Skin3D = lazyComponent<Skin3DProps>(
  () => import("./Skin3DViewer"),
  ({ width = 260, height = 380 }) => <div className="skin3d" style={{ width, height, display: "block" }} />,
);

export default Skin3D;
