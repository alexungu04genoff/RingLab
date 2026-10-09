import type { Gadget, GadgetRulesCatalog, ScenarioRulesCatalog } from "../../shared/types";
import { acquisitionDescription, effectBadges, gadgetPresentation } from "./gadgetPresentation";
import "../../styles/gadget-metadata.css";

export function GadgetMetadata({ gadget, rules, scenarios }: {
  gadget: Gadget; rules?: GadgetRulesCatalog; scenarios?: ScenarioRulesCatalog;
}) {
  const metadata = gadgetPresentation(gadget, rules, scenarios);
  const kinds = metadata.effectKinds;
  if (kinds.length === 0 && metadata.acquisitionKind !== "FESTIVAL_REWARD") return null;
  return <span className="gadget-metadata">
    {kinds.map(kind => <span key={kind}
      className={`gadget-effect-badge gadget-effect-${kind.toLowerCase()}`} title={effectBadges[kind].explanation}>
      {effectBadges[kind].label}
    </span>)}
    {metadata.acquisitionKind === "FESTIVAL_REWARD" && <span className="gadget-effect-badge gadget-festival-badge"
      title={acquisitionDescription(gadget)} aria-label={`Festival reward. ${acquisitionDescription(gadget)}`}>Festival reward</span>}
  </span>;
}

export function GadgetMetadataLegend() {
  return <details className="gadget-metadata-legend"><summary>Gadget badge guide</summary>
    <dl>{Object.values(effectBadges).map(badge => <div key={badge.label}><dt>{badge.label}</dt><dd>{badge.explanation}</dd></div>)}
      <div><dt>Festival reward</dt><dd>Originally awarded during a festival. Other acquisition methods may also exist.</dd></div>
    </dl>
  </details>;
}

export function GadgetAcquisitionDetails({ gadget }: { gadget: Gadget }) {
  return <details className="gadget-acquisition-details"><summary>Acquisition details</summary>
    <p>{acquisitionDescription(gadget)}</p>
    {gadget.acquisitionKind === "FESTIVAL_REWARD" && <p>This is acquisition history; other unlock methods may also exist.</p>}
  </details>;
}
