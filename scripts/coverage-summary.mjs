import { appendFileSync, existsSync, readFileSync } from "node:fs";

const [kind, reportPath] = process.argv.slice(2);
if (!kind || !reportPath || !["backend", "frontend"].includes(kind)) {
  throw new Error("Usage: node scripts/coverage-summary.mjs <backend|frontend> <report-path>");
}

const title = kind === "backend" ? "Backend JaCoCo coverage" : "Frontend Vitest coverage";
let markdown = `## ${title}\n\n`;

if (!existsSync(reportPath)) {
  markdown += `Coverage report was not produced at \`${reportPath}\`.\n`;
} else {
  const totals = kind === "backend" ? readJacoco(reportPath) : readVitest(reportPath);
  markdown += "| Metric | Covered | Total | Coverage |\n";
  markdown += "| --- | ---: | ---: | ---: |\n";
  for (const metric of totals) {
    markdown += `| ${metric.name} | ${metric.covered} | ${metric.total} | ${metric.percent.toFixed(2)}% |\n`;
  }
}

process.stdout.write(markdown);
if (process.env.GITHUB_STEP_SUMMARY) {
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${markdown}\n`);
}

function readJacoco(path) {
  const xml = readFileSync(path, "utf8");
  const counters = new Map();
  for (const match of xml.matchAll(/<counter type="([A-Z]+)" missed="(\d+)" covered="(\d+)"\/>/g)) {
    counters.set(match[1], { missed: Number(match[2]), covered: Number(match[3]) });
  }
  return ["LINE", "BRANCH", "INSTRUCTION"].map((name) => metric(name, counters.get(name)));
}

function readVitest(path) {
  const total = JSON.parse(readFileSync(path, "utf8")).total;
  return ["lines", "branches", "statements", "functions"].map((name) => ({
    name: capitalize(name),
    covered: total[name].covered,
    total: total[name].total,
    percent: total[name].pct,
  }));
}

function metric(name, counter) {
  if (!counter) throw new Error(`JaCoCo ${name} counter is missing`);
  const total = counter.covered + counter.missed;
  return { name: capitalize(name), covered: counter.covered, total,
    percent: total === 0 ? 100 : counter.covered * 100 / total };
}

function capitalize(value) {
  return value[0].toUpperCase() + value.slice(1).toLowerCase();
}
