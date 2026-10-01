import { Artwork } from "../../shared/ui/Artwork";
import { isExcluded, useCollection, type CollectionCategory } from "./Collection";
import { gadgetArtworkById } from "./gadgetArtwork";
import type { Racer, Machine, Gadget } from "../../shared/types";

/** Adds catalog fallbacks and current collection ownership to generic artwork. */
export function CollectionArtwork({ item, compact = false, portrait = false, category }: {
  item: Racer | Machine | Gadget;
  compact?: boolean;
  portrait?: boolean;
  category?: CollectionCategory;
}) {
  const collection = useCollection();
  const itemCategory = category ?? ("slotCost" in item ? "GADGET" : portrait ? "RACER" : "MACHINE");
  const unavailable = collection.status === "ready" && isExcluded(collection.data, itemCategory, item.id);
  const imagePath = item.imagePath ?? ("slotCost" in item ? gadgetArtworkById[item.id] : undefined);
  return <Artwork item={{ ...item, imagePath }} compact={compact} portrait={portrait} unavailable={unavailable} />;
}
