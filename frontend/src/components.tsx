import { useState } from "react";
import { Link } from "react-router-dom";
import type { Build, Gadget, Machine, Racer } from "./types";
export function ErrorNotice({ message }: { message: string }) {
  return message ? (
    <div className="error" role="alert">
      {message}
    </div>
  ) : null;
}
export function Artwork({
  item,
  compact = false,
}: {
  item: Racer | Machine | Gadget;
  compact?: boolean;
}) {
  const [failed, setFailed] = useState(false);
  const local = item.imagePath?.startsWith("/assets/");
  const racingType = "racingType" in item ? item.racingType : "gadget";
  return (
    <div
      className={`artwork ${compact ? "compact" : ""} type-${racingType.toLowerCase()}`}
    >
      {local && !failed ? (
        <img
          src={item.imagePath!}
          alt={item.name}
          onError={() => setFailed(true)}
        />
      ) : (
        <span aria-hidden="true">
          {item.name
            .split(" ")
            .map((w) => w[0])
            .slice(0, 2)
            .join("")}
        </span>
      )}
    </div>
  );
}
export const date = (value: string) =>
  new Date(value).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
export function BuildCard({ build }: { build: Build }) {
  return (
    <Link to={`/builds/${build.id}`} className="build-card">
      <div className="card-art">
        <Artwork item={build.racer} />
        <span className="type-badge">{build.racer.racingType}</span>
        <span className="score">↑ {build.score}</span>
      </div>
      <div className="card-body">
        <span className="eyebrow">{build.racer.name}</span>
        <h2>{build.title}</h2>
        <p className="machine-name">{build.machine.name}</p>
        <div className="tags">
          {build.gadgets.slice(0, 2).map((g, i) => (
            <span key={`${g.id}-${i}`}>{g.name}</span>
          ))}
          {build.gadgets.length > 2 && <span>+{build.gadgets.length - 2}</span>}
          {build.gadgets.length === 0 && <span>No gadgets</span>}
        </div>
        <div className="card-meta">
          <span>@{build.author.username}</span>
          <time>{date(build.createdAt)}</time>
        </div>
      </div>
    </Link>
  );
}
export function ItemSelect({
  label,
  items,
  value,
  onChange,
  optional = false,
}: {
  label: string;
  items: Array<Racer | Machine>;
  value: string;
  onChange: (value: string) => void;
  optional?: boolean;
}) {
  return (
    <label>
      {label}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={!optional}
      >
        <option value="">
          {optional
            ? `All ${label.toLowerCase()}s`
            : `Choose a ${label.toLowerCase()}`}
        </option>
        {items.map((i) => (
          <option key={i.id} value={i.id}>
            {i.name}
            {` · ${i.racingType.toLowerCase()}`}
          </option>
        ))}
      </select>
    </label>
  );
}
