/* Bespoke icon set — 24×24, 1.7px stroke, round caps.
   No icon library; every glyph is drawn for this shell. */

import type { CSSProperties } from "react";

const P = {
  home: (
    <>
      <path d="M4 11.2 12 4.5l8 6.7" />
      <path d="M6.2 9.8v9.2a1.5 1.5 0 0 0 1.5 1.5h8.6a1.5 1.5 0 0 0 1.5-1.5V9.8" />
      <path d="M9.8 20.5v-5.4h4.4v5.4" />
    </>
  ),
  layers: (
    <>
      <path d="M12 3.6 20.4 8 12 12.4 3.6 8 12 3.6Z" />
      <path d="m4.8 11.6 7.2 3.9 7.2-3.9" />
      <path d="m4.8 15.4 7.2 3.9 7.2-3.9" />
    </>
  ),
  puzzle: (
    <>
      <path d="M9.2 4.8h5.6v3.1a1.9 1.9 0 1 0 3.4 1.2V6.8a2 2 0 0 0-2-2h-3.4M9.2 4.8v3.4a1.9 1.9 0 1 1-3.4 1V6.8a2 2 0 0 1 2-2h1.4Z" />
      <path d="M12.6 13.2v5.6a2 2 0 0 1-2 2H7.2a2 2 0 0 1-2-2v-3.2h3a1.9 1.9 0 1 1 1.4-3.2v.8h3Z" />
    </>
  ),
  feather: (
    <>
      <path d="M20.2 4.2c-6 .5-10.6 4.8-12.2 10.6l-3 4.6" />
      <path d="M20.2 4.2c-.5 6-4.8 10.6-10.6 12.2" />
      <path d="M8 15.9 16.6 7.3" />
      <path d="m10.4 13.5 3-3" />
    </>
  ),
  users: (
    <>
      <circle cx="9.5" cy="8.4" r="3.4" />
      <path d="M3.8 19.6c.7-3.1 2.9-4.9 5.7-4.9s5 1.8 5.7 4.9" />
      <path d="M15.6 5.4a3.4 3.4 0 0 1 0 6" />
      <path d="M17.4 14.9c1.9.7 3 2.1 3.4 4.1" />
    </>
  ),
  gear: (
    <>
      <circle cx="12" cy="12" r="3.1" />
      <path d="M12 2.9v2.5M12 18.6v2.5M2.9 12h2.5M18.6 12h2.5M5.5 5.5l1.8 1.8M16.7 16.7l1.8 1.8M18.5 5.5l-1.8 1.8M7.3 16.7l-1.8 1.8" />
    </>
  ),
  terminal: (
    <>
      <rect x="3.4" y="4.6" width="17.2" height="14.8" rx="2" />
      <path d="m7.2 9.4 3 2.6-3 2.6M12.4 15h4.4" />
    </>
  ),
  play: <path d="M8 5.4v13.2c0 .9 1 1.5 1.8 1L20 13a1.2 1.2 0 0 0 0-2L9.8 4.4A1.2 1.2 0 0 0 8 5.4Z" />,
  plus: <path d="M12 5.4v13.2M5.4 12h13.2" />,
  download: (
    <>
      <path d="M12 4v9.6M7.8 10l4.2 4.2L16.2 10" />
      <path d="M4.6 16.4v2.2a2 2 0 0 0 2 2h10.8a2 2 0 0 0 2-2v-2.2" />
    </>
  ),
  x: <path d="M6 6l12 12M18 6 6 18" />,
  check: <path d="m5 12.6 4.4 4.4L19 7.4" />,
  chevronDown: <path d="m6 9.4 6 6 6-6" />,
  chevronRight: <path d="m9.4 6 6 6-6 6" />,
  chevronLeft: <path d="m14.6 6-6 6 6 6" />,
  copy: (
    <>
      <rect x="9" y="9" width="11" height="11" rx="2" />
      <path d="M5.4 15h-.9a2 2 0 0 1-2-2V5.5a2 2 0 0 1 2-2H12a2 2 0 0 1 2 2v.9" />
    </>
  ),
  refresh: (
    <>
      <path d="M20 12a8 8 0 1 1-2.34-5.66" />
      <path d="M20 3.6v4h-4" />
    </>
  ),
  trash: (
    <>
      <path d="M4.6 6.6h14.8M9.6 6.4V4.8a1.2 1.2 0 0 1 1.2-1.2h2.4a1.2 1.2 0 0 1 1.2 1.2v1.6" />
      <path d="M6.6 6.8 7.4 19a1.6 1.6 0 0 0 1.6 1.5h6a1.6 1.6 0 0 0 1.6-1.5l.8-12.2" />
      <path d="M10.2 10.6v5.8M13.8 10.6v5.8" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6.2" />
      <path d="m15.6 15.6 4.6 4.6" />
    </>
  ),
  external: (
    <>
      <path d="M10 5.6H6.4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V14" />
      <path d="M14 4.4h5.6V10M19.2 4.8 11.4 12.6" />
    </>
  ),
  minus: <path d="M5.5 12h13" />,
  maximize: <rect x="5.5" y="5.5" width="13" height="13" rx="2.2" />,
  restore: (
    <>
      <rect x="4.5" y="8.5" width="11" height="11" rx="2.2" />
      <path d="M8.6 5.5h8a2.4 2.4 0 0 1 2.4 2.4v8" />
    </>
  ),
  stop: <rect x="6.5" y="6.5" width="11" height="11" rx="1.6" />,
  signal: (
    <>
      <path d="M4.5 16.6v2.4M9 13.4v5.6M13.5 10.2v8.8M18 7v11.4M22 3.8v14.2" transform="translate(-.5)" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.1" />
      <path d="M12 7.4V12l3.2 2" />
    </>
  ),
  server: (
    <>
      <rect x="3.6" y="4.4" width="16.8" height="6.6" rx="1.8" />
      <rect x="3.6" y="13" width="16.8" height="6.6" rx="1.8" />
      <path d="M7 7.7h.01M7 16.3h.01" strokeWidth="2.6" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8.2" r="3.6" />
      <path d="M5.4 20c.8-3.5 3.3-5.4 6.6-5.4s5.8 1.9 6.6 5.4" />
    </>
  ),
  cpu: (
    <>
      <rect x="6" y="6" width="12" height="12" rx="1.8" />
      <rect x="9.6" y="9.6" width="4.8" height="4.8" rx="1" />
      <path d="M9 2.8V6M15 2.8V6M9 18v3.2M15 18v3.2M2.8 9H6M2.8 15H6M18 9h3.2M18 15h3.2" />
    </>
  ),
  ram: (
    <>
      <rect x="3" y="8.4" width="18" height="7.6" rx="1.4" />
      <path d="M7 8.4V12M11 8.4V12M15 8.4V12M19 16v3M5 16v3M9.5 16v2.2M14.5 16v2.2" />
    </>
  ),
  palette: (
    <>
      <path d="M12 3.8a8.2 8.2 0 1 0 0 16.4c1.5 0 2.1-.9 1.6-2-.5-1.2.2-2.4 1.6-2.4h1.6a3.4 3.4 0 0 0 3.4-3.4c0-4.8-4.3-8.6-8.2-8.6Z" />
      <circle cx="8" cy="10" r="1.1" fill="currentColor" stroke="none" />
      <circle cx="12" cy="7.6" r="1.1" fill="currentColor" stroke="none" />
      <circle cx="16" cy="10" r="1.1" fill="currentColor" stroke="none" />
    </>
  ),
  globe: (
    <>
      <circle cx="12" cy="12" r="8.1" />
      <path d="M3.9 12h16.2M12 3.9c-4.8 4.7-4.8 11.5 0 16.2 4.8-4.7 4.8-11.5 0-16.2Z" />
    </>
  ),
  shield: (
    <>
      <path d="M12 3.4 5 6v5.4c0 4.5 2.9 7.6 7 9.2 4.1-1.6 7-4.7 7-9.2V6l-7-2.6Z" />
      <path d="m9 11.8 2.2 2.2 3.8-4" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.1" />
      <path d="M12 11.2v5M12 7.6h.01" />
    </>
  ),
  zap: <path d="M13.4 2.8 5.2 13.4h5.2L9.6 21.2l8.2-10.6h-5.2l.8-7.8Z" />,
  folder: (
    <>
      <path d="M3.6 7.2a2 2 0 0 1 2-2h4l2 2.4h6.8a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2H5.6a2 2 0 0 1-2-2V7.2Z" />
    </>
  ),
  arrowRight: <path d="M4.5 12h15M14 6.5l5.5 5.5-5.5 5.5" />,
  gamepad: (
    <>
      <path d="M7.4 6.8h9.2a5.2 5.2 0 0 1 5.1 6.2l-.6 2.9a2.8 2.8 0 0 1-4.9 1.2L14.6 15H9.4l-1.6 2.1a2.8 2.8 0 0 1-4.9-1.2l-.6-2.9a5.2 5.2 0 0 1 5.1-6.2Z" />
      <path d="M8.4 9.8v3M6.9 11.3h3" />
      <path d="M15.4 10.4h.01M17.6 12.2h.01" strokeWidth="2.4" />
    </>
  ),
  bell: (
    <>
      <path d="M18.2 15.6H5.8c1.1-1.2 1.7-2.4 1.7-4.5v-1.6a4.5 4.5 0 0 1 9 0v1.6c0 2.1.6 3.3 1.7 4.5Z" />
      <path d="M10.2 18.6a1.9 1.9 0 0 0 3.6 0" />
    </>
  ),
  star: <path d="m12 3.6 2.6 5.3 5.8.8-4.2 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8L3.6 9.7l5.8-.8L12 3.6Z" />,
  sun: (
    <>
      <circle cx="12" cy="12" r="3.4" />
      <path d="M12 4.2v1.8M12 18v1.8M4.2 12h1.8M18 12h1.8M6.4 6.4l1.3 1.3M16.3 16.3l1.3 1.3M17.6 6.4l-1.3 1.3M7.7 16.3l-1.3 1.3" />
    </>
  ),
  clips: (
    <>
      <rect x="3.2" y="5.2" width="17.6" height="13.6" rx="2" />
      <path d="M8.2 5.2v13.6M15.8 5.2v13.6" />
      <path d="M5.7 8.2h.01M5.7 12h.01M5.7 15.8h.01M18.3 8.2h.01M18.3 12h.01M18.3 15.8h.01" strokeWidth="2.4" />
    </>
  ),
  command: (
    <>
      <path d="M9 9h6v6H9z" />
      <path d="M9 9H7a2 2 0 1 1 2-2v2ZM15 9h2a2 2 0 1 0-2-2v2ZM9 15H7a2 2 0 1 0 2 2v-2ZM15 15h2a2 2 0 1 1-2 2v-2Z" />
    </>
  ),
} as const;

export type IconName = keyof typeof P;

export function Icon({
  name,
  size = 18,
  strokeWidth = 1.7,
  style,
  className,
}: {
  name: IconName;
  size?: number;
  strokeWidth?: number;
  style?: CSSProperties;
  className?: string;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      className={className}
      style={{ flex: "none", ...style }}
    >
      {P[name]}
    </svg>
  );
}
