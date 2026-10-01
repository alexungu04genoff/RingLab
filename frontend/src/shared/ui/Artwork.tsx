import { useState } from "react";

export function Artwork({
  item,
  compact = false,
  portrait = false,
  unavailable = false,
}: {
  item: { name: string; imagePath?: string | null; racingType?: string | null };
  compact?: boolean;
  portrait?: boolean;
  unavailable?: boolean;
}) {
  const [failed, setFailed] = useState(false);
  const imagePath = item.imagePath;
  const local = imagePath?.startsWith("/assets/");
  const imageUrl = imagePath?.startsWith("/assets/racers/")
    ? `${imagePath}?v=left-facing-artwork`
    : imagePath;
  const racingType = "racingType" in item ? item.racingType ?? "unknown" : "gadget";
  return (
    <div
      className={`artwork ${unavailable ? "not-owned-artwork" : ""} ${compact ? "compact" : ""} ${portrait ? "portrait" : ""} type-${racingType.toLowerCase()}`}
      title={unavailable ? `${item.name} — Not owned` : undefined}
    >
      {local && !failed ? (
        <img
          src={imageUrl!}
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
