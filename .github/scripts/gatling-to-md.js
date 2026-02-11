#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");

function usage() {
  console.error("Usage: gatling-to-md.js <gatling_simulation_log>");
}

// Strip ANSI escape codes from text
function stripAnsi(str) {
  // eslint-disable-next-line no-control-regex
  return str.replace(/\x1B\[[0-9;]*[a-zA-Z]/g, "");
}

function parseKey(lines, regex) {
  for (const l of lines) {
    const m = l.match(regex);
    if (m) return m;
  }
  return null;
}

function parseGatlingSimulationLog(text) {
  // simulation.log is a binary format for Gatling 3.14.x; we can still extract a few
  // useful fields by searching for known UTF-8 strings inside it.
  const ascii = text.toString("utf8");
  const lines = ascii.split(/\r?\n/);

  // Best-effort parsing from possible embedded stdout (not present for binary simulation.log).
  const ok = parseKey(lines, /^>\s+OK\s+([0-9]+)/);
  const ko = parseKey(lines, /^>\s+KO\s+([0-9]+)/);

  let totalRequests = null;
  const reqCount = parseKey(lines, /^>\s+request count\s+\|\s+([0-9]+)/);
  if (reqCount) totalRequests = Number(reqCount[1]);

  // For the binary simulation.log, at least the simulation class name is present.
  const simClassMatch = ascii.match(/simulations\.[A-Za-z0-9_$.]+/);
  const simulationClass = simClassMatch ? simClassMatch[0] : null;

  let koCount = ko ? Number(ko[1]) : null;
  let okCount = ok ? Number(ok[1]) : null;

  return {
    okCount,
    koCount,
    totalRequests,
    assertions: [],
    simulationClass,
    simulationId: null,
  };
}

function renderMd({ title, parsed, reportDir, consoleSummary }) {
  const now = new Date().toISOString();

  let out = "";
  out += `## ${title}\n`;
  out += `Generated: ${now}\n\n`;

  if (parsed.simulationClass) {
    out += `Simulation: \`${parsed.simulationClass}\`\n\n`;
  }

  if (parsed.koCount === 0) {
    out += "Status: PASS\n\n";
  } else if (parsed.koCount != null) {
    out += `Status: FAIL (KO=${parsed.koCount})\n\n`;
  } else {
    out += "Status: Completed (KO/OK summary unavailable from binary simulation.log)\n\n";
  }

  if (consoleSummary && consoleSummary.trim()) {
    out += "Console summary:\n\n";
    out += "```\n";
    out += consoleSummary.trimEnd() + "\n";
    out += "```\n\n";
  }

  out += "| Metric | Value |\n";
  out += "|---|---:|\n";
  if (parsed.totalRequests != null) out += `| Total requests | ${parsed.totalRequests} |\n`;
  if (parsed.okCount != null) out += `| OK | ${parsed.okCount} |\n`;
  if (parsed.koCount != null) out += `| KO | ${parsed.koCount} |\n`;
  out += "\n";

  if (reportDir) {
    const idx = path.join(reportDir, "index.html");
    out += `Report (artifact path): \`${idx}\`\n`;
    const artifactNote = process.env.GATLING_ARTIFACT_NAME;
    if (artifactNote) {
      out += `Artifact: \`${artifactNote}\` (download from this workflow run)\n`;
    }
  }

  return out;
}

function main() {
  const input = process.argv[2];
  if (!input) {
    usage();
    process.exit(2);
  }

  const buf = fs.readFileSync(input);
  const parsed = parseGatlingSimulationLog(buf);

  // reportDir is the directory containing simulation.log
  const reportDir = path.dirname(path.resolve(input));

  // If a Gatling console output file exists alongside, include its final summary lines.
  // In CI we pass it explicitly by setting GATLING_CONSOLE_OUT.
  let consoleSummary = "";
  const consoleOutPath = process.env.GATLING_CONSOLE_OUT;
  if (consoleOutPath && fs.existsSync(consoleOutPath)) {
    const consoleText = stripAnsi(fs.readFileSync(consoleOutPath, "utf8"));
    const consoleLines = consoleText.split(/\r?\n/);
    // Take the last N lines and keep the ones that usually contain the summary.
    const tail = consoleLines.slice(Math.max(0, consoleLines.length - 200));
    const keep = tail.filter((l) =>
      l.includes("Requests") ||
      l.includes("OK") ||
      l.includes("KO") ||
      l.includes("Global:") ||
      l.includes("Simulation") ||
      l.includes("Reports generated")
    );
    consoleSummary = keep.join("\n");
  }

  process.stdout.write(renderMd({ title: "Gatling", parsed, reportDir, consoleSummary }));
}

main();
