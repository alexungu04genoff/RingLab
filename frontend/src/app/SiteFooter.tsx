import { Link } from "react-router-dom";
import { GuideIcon, LibraryIcon } from "../shared/ui/icons";

const srcGadgetBuilderUrl = "https://www.srcgadgetbuilder.com/";
const sonicFandomCrossWorldsUrl = "https://sonic.fandom.com/wiki/Sonic_Racing:_CrossWorlds";
const japaneseCrossWorldsWikiUrl = "https://w.atwiki.jp/sonicracingcw/";

export function SiteFooter() {
  return (
    <footer>
      <span className="brand footer-brand">
        RING<span>LAB</span>
      </span>
      <span className="footer-project">A CrossWorlds community build lab · Educational project</span>
      <span className="footer-thanks">
        With thanks to <strong>Meohong</strong> — <a href={srcGadgetBuilderUrl} target="_blank" rel="noreferrer">SRC Gadget Builder</a>
      </span>
      <details className="footer-credits">
        <summary>Data sources &amp; credits</summary>
        <div className="footer-credit-content">
          <p>
            Special thanks to Meohong, creator of <a href={srcGadgetBuilderUrl} target="_blank" rel="noreferrer">SRC Gadget Builder</a>, for explaining character and machine stat calculations, clarifying how gadget effects are handled, and sharing guidance on data sources.
          </p>
          <p>
            RingLab&apos;s catalog and artwork references include the <a href={sonicFandomCrossWorldsUrl} target="_blank" rel="noreferrer">Sonic Fandom Wiki — Sonic Racing: CrossWorlds</a>.
          </p>
          <p>
            Community reference: <a href={japaneseCrossWorldsWikiUrl} target="_blank" rel="noreferrer">Japanese Sonic Racing: CrossWorlds Wiki</a>.
          </p>
        </div>
      </details>
      <span className="footer-links"><Link to="/guide"><GuideIcon /> Guide</Link>
        <Link to="/game-data"><LibraryIcon /> Game collection ↗</Link></span>
    </footer>
  );
}
