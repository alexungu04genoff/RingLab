"""Generate the ignored, offline gadget artwork review sheet from canonical CSVs.

Run: python scripts/gadget-review.py
No catalog files or artwork are modified. Output embeds local bytes for easy review.
"""
import base64
import csv
import html
import json
import mimetypes
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / ".tools/gadget-clarity/review.html"


def rows(path):
    with (ROOT / path).open(encoding="utf-8", newline="") as source:
        return list(csv.DictReader(source))


def main():
    gadgets = rows("game-data/catalog/gadgets.csv")
    rules = rows("game-data/versions/1.4.1/passive-gadget-rules.csv")
    scenarios = rows("game-data/versions/1.4.1/scenario-gadget-rules.csv")
    inventory = json.loads((ROOT / "game-data/review/gadget-artwork.json").read_text(encoding="utf-8"))
    artwork = {item["id"]: item for item in inventory}
    labels = {"PASSIVE": "Passive stats", "CONDITIONAL": "Race condition", "NON_STAT": "Utility", "UNSUPPORTED": "Unsupported"}
    groups = {label: [] for label in [*labels.values(), "Festival reward"]}
    for gadget in gadgets:
        kinds = {rule["kind"] for rule in rules if rule["gadget_id"] == gadget["id"]}
        group = "Unsupported" if "UNSUPPORTED" in kinds else "Festival reward" if gadget["acquisition_kind"] == "FESTIVAL_REWARD" else next(labels[k] for k in ["PASSIVE", "CONDITIONAL", "NON_STAT"] if k in kinds)
        icon = ROOT / "frontend/public" / gadget["image_path"].lstrip("/")
        data = base64.b64encode(icon.read_bytes()).decode("ascii")
        src = f"data:{mimetypes.guess_type(icon)[0]};base64,{data}"
        badges = [f'<span class="gadget-effect-badge gadget-effect-{kind.lower()}">{"Effect unsupported" if kind == "UNSUPPORTED" else labels[kind]}</span>' for kind in labels if kind in kinds]
        if any(rule["gadget_id"] == gadget["id"] for rule in scenarios):
            badges.append('<span class="gadget-effect-badge gadget-effect-scenario">Scenario modeled</span>')
        event = gadget["acquisition_label"]
        if event:
            badges.append(f'<span class="gadget-effect-badge gadget-festival-badge" title="Originally awarded during the {html.escape(event)}.">Festival reward</span>')
        entry = artwork[gadget["id"]]
        name = html.escape(gadget["name"])
        groups[group].append(f'''<article data-name="{name.lower()}"><img src="{src}" alt="{name}">
<div><h3>{name}</h3><div class="gadget-metadata"><span class="gadget-effect-badge">{gadget["slot_cost"]} {"slot" if gadget["slot_cost"] == "1" else "slots"}</span>{"".join(badges)}</div>
<p>{html.escape(event)}</p><small>{html.escape(entry["sourceIdentifier"])}</small>
<details><summary>Source and identity</summary><p>{gadget["id"]}</p><p>{gadget["image_path"]}</p>
<p>{html.escape(entry["attribution"])}</p><p>{html.escape(entry["sourcePage"])}</p></details></div></article>''')
    sections = "".join(f'<section><h2>{name} · {len(cards)}</h2><div class="cards">{"".join(cards)}</div></section>' for name, cards in groups.items())
    shared_css = (ROOT / "frontend/src/styles/gadget-metadata.css").read_text(encoding="utf-8")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>RingLab · 117 gadget artwork review</title><style>{shared_css}
*{{box-sizing:border-box}}body{{margin:0;background:#101720;color:#edf3fb;font:16px system-ui;--muted:#aab8cc;padding:24px}}
main{{max-width:1440px;margin:auto}}h1{{font-size:30px}}p,small{{color:#aab8cc}}input{{padding:12px;width:min(100%,500px);background:#202b3a;color:white;border:1px solid #526174;border-radius:8px}}
.cards{{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,340px),1fr));gap:14px}}article{{background:#1a2534;border:1px solid #34465b;border-radius:12px;padding:16px;display:grid;grid-template-columns:80px minmax(0,1fr);gap:14px}}
article img{{width:80px;height:80px;object-fit:contain}}h3{{margin:0 0 10px;font-size:16px}}article small,article p{{overflow-wrap:anywhere;font-size:12px}}details{{font-size:12px;margin-top:10px}}section{{margin-top:32px}}[hidden]{{display:none}}
@media(max-width:480px){{body{{padding:14px}}article{{grid-template-columns:56px minmax(0,1fr)}}article img{{width:56px;height:56px}}}}
</style><main><h1>Gadget clarity & artwork review</h1><p>117 / 117 canonical local icons · 18 festival rewards · reviewed 2026-10-09</p>
<p>Each gadget appears once. Unsupported effects take grouping priority, followed by festival history; all applicable badges remain visible. Icons retain source bytes and proportions.</p>
<label>Find a gadget <input id="search" type="search" placeholder="Name…"></label>{sections}</main>
<script>document.querySelector('#search').addEventListener('input',e=>{{for(const card of document.querySelectorAll('article'))card.hidden=!card.dataset.name.includes(e.target.value.trim().toLowerCase());}});</script></html>''', encoding="utf-8")
    print(OUTPUT)


if __name__ == "__main__":
    main()
