import { useEffect, useState } from "react";

export function snapshotAge(timestamp: string, now: number) {
  const age = now - Date.parse(timestamp);
  if (!Number.isFinite(age)) return "Update time unavailable";
  if (age < -60_000) return "Update time ahead of this device’s clock";
  if (age < 60_000) return "Updated just now";
  const minutes = Math.floor(age / 60_000);
  const [amount, unit] = minutes < 60 ? [minutes, "minute"] : minutes < 1440
    ? [Math.floor(minutes / 60), "hour"] : [Math.floor(minutes / 1440), "day"];
  return `Updated ${amount} ${unit}${amount === 1 ? "" : "s"} ago`;
}

export function SnapshotTime({ timestamp }: { timestamp: string }) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  const parsed = new Date(timestamp);
  if (!Number.isFinite(parsed.getTime())) return <span>Update time unavailable</span>;
  const exact = parsed.toLocaleString();
  return <details className="snapshot-time">
    <summary title={exact}><time dateTime={timestamp}>{snapshotAge(timestamp, now)}</time></summary>
    <span>Snapshot taken {exact}</span>
  </details>;
}
