import { Link } from "react-router-dom";
import { useAuth } from "../features/auth/auth";

const srcGadgetBuilderUrl = "https://www.srcgadgetbuilder.com/";
const sonicFandomCrossWorldsUrl = "https://sonic.fandom.com/wiki/Sonic_Racing:_CrossWorlds";
const japaneseCrossWorldsWikiUrl = "https://w.atwiki.jp/sonicracingcw/";

export function GuidePage() {
  const { user } = useAuth();
  return <article className="guide-page">
    <header className="guide-hero">
      <div className="eyebrow accent">ABOUT &amp; GUIDE</div>
      <h1>How RingLab works</h1>
      <p className="guide-tagline"><strong>Explore builds. Make them your own.</strong></p>
      <p>RingLab helps you discover, share, and experiment with Sonic Racing: CrossWorlds builds.</p>
      <nav className="guide-section-nav" aria-label="Guide sections">
        <a href="#why-ringlab">Why use RingLab?</a><a href="#finding-builds">Finding builds</a><a href="#recommendations">Recommendations</a>
        <a href="#your-collection">Your collection</a><a href="#understanding-stats">Understanding stats</a>
        <a href="#about-credits">About &amp; credits</a>
      </nav>
    </header>

    <section id="why-ringlab" className="guide-section">
      <h2>Why use RingLab?</h2>
      <p>You find a popular build, but it uses gadgets you haven’t unlocked. Another looks fast, but you don’t want to give up your favourite racer or sacrifice all your handling.</p>
      <p>That’s where RingLab helps. Borrow an idea from the community, keep the things you enjoy, and explore changes that fit your preferences.</p>
      <p className="guide-callout"><strong>You choose what matters. RingLab helps you find a setup worth trying.</strong></p>
    </section>

    <section id="finding-builds" className="guide-section">
      <h2>Finding and sorting builds</h2>
      <p>Looking for a Tails setup? Choose him in the racer filter. Have a particular track in mind? Look for builds their authors recommend for that map.</p>
      <p><strong>Best rated</strong> highlights builds with strong community approval. <strong>Highest score</strong> sorts by upvotes minus downvotes. <strong>Newest first</strong> shows recently shared setups.</p>
      <p>The <strong>Top 3</strong> shows overall community favourites and stays the same when you change your filters.</p>
      <p><strong>Save</strong> a build for later, <Link to="/compare"><strong>Compare</strong></Link> two setups to see their differences, or <strong>Remix</strong> one to make your own version. The original stays untouched.</p>
      <p><strong>Public</strong> builds are eligible for community discovery and engagement. Newest uses the first publication date; editing or republishing does not move a build to the top.</p>
      <p><Link to="/my-builds">My Builds</Link> includes your public and private builds, with All, Public and Private filters. Its Newest order uses creation time. <strong>Private</strong> means only you can access the persisted build through RingLab. You can edit, compare and remix it.</p>
      <p>Choose visibility in the editor. Making a build private hides its comments, votes and bookmarks while retaining them for republication. A saved public build that becomes private disappears from Saved Builds until it is public again.</p>
      <p>Previously public copies already viewed or shared outside RingLab cannot be recalled. A public remix can remain public after its source becomes private; the private source attribution is hidden.</p>
    </section>

    <section id="recommendations" className="guide-section">
      <h2>Recommendations: build around your preferences</h2>
      <p>Choose what you want to keep and which stats matter to you. RingLab suggests a setup around those choices, and you decide whether to use it.</p>
      <p>Open the <Link to="/builds/new">build editor</Link> on any screen size and choose a patch and machine type. Select the items you like, then use their lock icons to keep them. You can also start empty.</p>
      <p>You can lock your racer, individual machine parts, any of your selected gadgets, or your entire machine setup. You can combine these locks—you’re not limited to keeping just one thing.</p>
      <p>For example, lock your racer, a front part, and two favourite gadgets. RingLab will keep those selections while exploring the remaining choices. Unlocked items may be kept or replaced.</p>
      <p>Selecting a racer or part alone does not lock it. Locking the entire machine setup keeps its currently selected parts; it does not lock your racer or gadgets.</p>
      <p><strong>Keep current</strong> is the default gadget scope: recommend the racer and machine around your selected gadgets, including an empty plate. Their passive effects are recalculated for the proposed types.</p>
      <p>Choose <strong>Optimize unlocked gadgets</strong> to allow additions, removals and replacements. Locked gadgets remain. This optimizes reviewed passive stat points; utility, scenario effects and race strategy are not valued.</p>
      <p><strong>Strict</strong> follows your exact priority order. The first differing stat decides between builds; a lower priority never outweighs a higher one.</p>
      <p><strong>Balanced</strong> considers your priorities in order. For each stat, it keeps builds within the loss you allow, then evaluates the next priority. You can start with an empty setup or just a locked racer or part.</p>
      <p className="guide-callout">For example, if the best remaining build has <strong>100 Boost</strong>, a <strong>5% maximum sacrifice</strong> keeps builds with at least <strong>95 Boost</strong>. The next stat is compared only among those builds. <strong>0%</strong> keeps only the best value at that stage.</p>
      <p>Not bothered about Power? Choose <strong>Ignore</strong>. It has no effect on the recommendation, even to break ties. If several builds survive every stage, your active priority order decides; exact ties prefer fewer changes, fewer added gadgets, then lower slot cost. Your current stats are shown for comparison only.</p>
      <p>Conflicting locks or locked items marked as unavailable can prevent a recommendation until you change those choices.</p>
      <p>Select <strong>Calculate recommendation</strong> to see the proposed changes. Your locked items stay in place. Choose <strong>Apply to draft</strong> to use the suggestion, or <strong>Cancel</strong> to leave your setup untouched. Saving or publishing is still your choice.</p>
      <p>Results label selections as Added, Changed or Kept. Complete setups also show Current and Recommended side by side. Balanced details show how each allowed sacrifice narrowed the remaining builds.</p>
      <p><strong>Best setup found so far</strong> means the search reached its limit: the build is usable, but a better match may exist. If no build was found before that limit, it does not mean your choices are impossible.</p>
    </section>

    <section id="your-collection" className="guide-section">
      <h2>Your collection: recommend things you can use</h2>
      <p>Sign in and open <Link to="/game-data"><strong>Game Collection</strong></Link>. Everything starts marked as <strong>Owned</strong>. Uncheck racers, machines, or gadgets you don’t have.</p>
      <p><strong>You can still browse them, but recommendations won’t use them.</strong> Unchecking a machine also excludes its parts.</p>
      <p>Remixing a build with missing items keeps them visible so you can choose replacements. Mark those items as Owned before recommending an alternative that uses them.</p>
      <p>These are settings you manage yourself; RingLab doesn’t read your game account. <strong>While logged out, everything is treated as available.</strong></p>
      <p>Unowned items never block saving or publishing a build. Ownership badges describe your own collection, independently of a build’s visibility and calculation coverage.</p>
      {!user && <p className="guide-account-note"><Link to="/login" state={{ from: "/guide#your-collection" }}>Log in to manage your collection.</Link></p>}
    </section>

    <section id="understanding-stats" className="guide-section">
      <h2>Understanding stats: numbers aren’t the whole race</h2>
      <p>Stats help you compare setups, but they don’t capture every useful gadget effect. A gadget can help with items, recovery, or landing boosts without adding points to the displayed totals.</p>
      <p>Some gadgets depend on what is happening during a race. <strong>Scenario Preview</strong> lets you try supported conditions without changing the saved build. It does not affect recommendations, which use base and always-active passive stats only.</p>
      <p><strong>Keep current</strong> preserves your gadgets. When optimizing unlocked gadgets, lock the ones you enjoy using.</p>
      <p>The 23 reviewed numerical passive effects are modeled with signed addition, supported by the community calculator. This stacking model is not independently verified official in-game behavior. Unknown effects remain unsupported.</p>
      <p>Recommended maps are the author’s suggestions. <strong>All maps</strong> simply means they haven’t picked particular tracks.</p>
      <p>Use a recommendation as a starting point, take it racing, and see how it feels. Higher numbers alone don’t guarantee a better lap.</p>
    </section>

    <section id="about-credits" className="guide-section guide-about">
      <h2>About &amp; credits</h2>
      <p>RingLab is a fan-made community build-sharing project created for educational purposes. It is not affiliated with SEGA.</p>
      <p>Special thanks to <strong>Meohong of <a href={srcGadgetBuilderUrl} target="_blank" rel="noreferrer">SRC Gadget Builder</a></strong> for guidance on stats and gadgets, and to the <a href={sonicFandomCrossWorldsUrl} target="_blank" rel="noreferrer"><strong>Sonic Fandom</strong></a> and <a href={japaneseCrossWorldsWikiUrl} target="_blank" rel="noreferrer"><strong>Japanese CrossWorlds</strong></a> wikis for catalog references.</p>
    </section>
  </article>;
}
