import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

function LineIcon({ children, ...props }: IconProps) {
  return (
    <svg aria-hidden="true" className="ui-icon" fill="none" viewBox="0 0 24 24"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...props}>
      {children}
    </svg>
  );
}

export function CompassIcon(props: IconProps) {
  return <LineIcon {...props}><circle cx="12" cy="12" r="9" /><path d="m15.5 8.5-2 5-5 2 2-5 5-2Z" /></LineIcon>;
}

export function LibraryIcon(props: IconProps) {
  return <LineIcon {...props}><path d="M4 19V5M9 19V5M14 19V5l6-1v15l-6 1Z" /><path d="M3 19h18" /></LineIcon>;
}

export function HammerIcon(props: IconProps) {
  return <LineIcon {...props}><path d="m14 4 6 6-3 3-6-6 3-3Z" /><path d="m13 8-9 9a2.1 2.1 0 0 0 3 3l9-9" /></LineIcon>;
}

export function SearchIcon(props: IconProps) {
  return <LineIcon {...props}><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></LineIcon>;
}

export function SteeringWheelIcon(props: IconProps) {
  return <LineIcon {...props}><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="2" /><path d="M3.5 10h17M12 14v7M10.6 13.4 6 18M13.4 13.4 18 18" /></LineIcon>;
}

export function ComponentsIcon(props: IconProps) {
  return <LineIcon {...props}><rect x="3" y="3" width="6" height="6" rx="1" /><rect x="15" y="3" width="6" height="6" rx="1" /><rect x="9" y="15" width="6" height="6" rx="1" /><path d="M6 9v2h12V9M12 11v4" /></LineIcon>;
}

export function TagIcon(props: IconProps) {
  return <LineIcon {...props}><path d="M20 13 13 20l-9-9V4h7l9 9Z" /><circle cx="8.5" cy="8.5" r="1" /></LineIcon>;
}

export function SortIcon(props: IconProps) {
  return <LineIcon {...props}><path d="M8 5v14M5 8l3-3 3 3M16 19V5M13 16l3 3 3-3" /></LineIcon>;
}

export function GearIcon(props: IconProps) {
  return <LineIcon {...props}><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z" /></LineIcon>;
}

export function HistoryIcon(props: IconProps) {
  return <LineIcon {...props}><path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5M12 7v5l3 2" /></LineIcon>;
}

// Compact, monochrome Sonic-head mark from the Sonic Team logo, included with user approval.
export function RacerIcon(props: IconProps) {
  return (
    <svg aria-hidden="true" className="ui-icon racer-icon" viewBox="0 0 240 202" fill="currentColor" {...props}>
      <path d="M95.4 100.5c-6.4 1.3-6.4 9-5.2 19.3 1.3 11.6 5.2 19.3 11.6 18 6.5 0 6.5-9 5.2-19.3s-3.9-18-11.6-18Z" />
      <path d="M228.1 113.4s-3.9-18-23.2-33.5c-18-12.9-37.4-11.6-37.4-11.6s5.2-10.3 19.3-18c12.9-6.5 31-7.8 31-7.8s-11.6-14.2-34.8-23.2c-28.4-10.3-51.6-10.3-79.9-1.3-30.9 10.3-52.8 34.8-52.8 34.8s-30.9-9-36.1-1.3c-6.4 7.7 9 37.4 9 37.4s-3.9 9-2.6 25.8c1.3 18 11.6 30.9 11.6 30.9-5.2 1.3-10.3 3.9-10.3 7.7 0 3.9 7.7 7.8 16.8 7.8h3.8c9 9 21.9 20.6 41.2 21.9 24.5 1.3 40-7.7 50.3-10.3 11.6-3.9 28.3-3.9 38.7-3.9 30.9 3.9 43.8 11.6 43.8 11.6s1.3-14.2-11.6-33.5c-10.3-14.2-30.9-19.3-30.9-19.3s10.3-9 23.2-11.6c16.8-3.9 31-2.6 31-2.6ZM32.2 135.3c-2.6-5.1-3.9-10.3-5.1-25.8 0-7.7 2.5-16.7 6.4-18 5.1-1.3 5.1 7.7 7.7 15.5.6 2.1 2.6 6.4 3.9 10.3-1.3-1.3-1.3-1.3-1.3 0-3.9 0-5.1 7.7-2.6 16.7 1.3 5.2 3.9 10.3 6.5 11.6-2.6 0-5.2-1.3-9-1.3 0 0-1.3 0-1.3 1.3-1.3-2.6-3.9-6.5-5.2-10.3Zm79.9 0c-6.4 12.9-16.7 16.8-28.4 16.8-11.6 0-19.3-6.5-30.9-5.2 2.6-2.6 2.6-9 1.3-16.8-1.3-1.3-1.3-2.5-2.6-5.1 3.9 2.6 6.5-1.3 6.5-1.3s1.3-19.3 7.7-32.2c5.2-10.3 12.9-18 24.5-19.3 10.3-2.6 18 5.1 21.9 16.7 3.9 11.6 5.2 33.5 0 46.4Z" />
    </svg>
  );
}
