#!/usr/bin/env node

const fs = require("node:fs");

function usage() {
  console.error("Usage: jmh-to-md.js <jmh_stdout_file>");
}

// Strip ANSI escape codes from text
function stripAnsi(str) {
  // eslint-disable-next-line no-control-regex
  return str.replace(/\x1B\[[0-9;]*[a-zA-Z]/g, "");
}

function parseJmhSummary(text) {
  // Strip ANSI codes that sbt may emit in CI environments
  const cleanText = stripAnsi(text);
  const lines = cleanText.split(/\r?\n/);

  // We parse the final summary table like:
  // Benchmark ... Mode  Cnt     Score   Error  Units
  // BlobBenchmarks.blobSizeCheck avgt 0,767 ns/op
  // We keep it robust: split by 2+ spaces.
  const hdrIdx = lines.findIndex((l) => /^\[info\]\s+Benchmark\s+Mode\s+/.test(l.trim()) || /^Benchmark\s+Mode\s+/.test(l.trim()));
  if (hdrIdx === -1) {
    return { rows: [], warning: "Could not find JMH summary table (Benchmark Mode Cnt Score Error Units)." };
  }

  const rows = [];
  for (let i = hdrIdx + 1; i < lines.length; i++) {
    const line = lines[i].trim().replace(/^\[info\]\s+/, "");
    if (!line) continue;

    // Stop if we hit sbt prompt-like noise (rare) or something clearly not a row.
    if (/^\[/.test(line)) break;

    const parts = line.split(/\s{2,}/).filter(Boolean);
    if (parts.length < 4) continue;

    // Depending on whether Error column exists, we can have:
    // [benchmark, mode, cnt, score, error, units] OR [benchmark, mode, score, units]
    let benchmark = parts[0];
    let mode = parts[1];

    let cnt = "";
    let score = "";
    let units = "";

    if (parts.length >= 6) {
      // Benchmark, Mode, Cnt, Score, Error, Units
      cnt = parts[2];
      score = parts[3];
      units = parts[5];
    } else {
      // Some JMH configs omit Cnt/Error or formatting collapses; best effort.
      // Try to interpret last as units and second-to-last as score.
      units = parts[parts.length - 1];
      score = parts[parts.length - 2];
      cnt = parts.length >= 5 ? parts[2] : "";
    }

    rows.push({ benchmark, mode, cnt, score, units });
  }

  return { rows, warning: null };
}

function renderMd({ title, rows, warning }) {
  const now = new Date().toISOString();

  let out = "";
  out += `## ${title}\n`;
  out += `Generated: ${now}\n\n`;

  if (warning) {
    out += `> ${warning}\n\n`;
  }

  if (!rows.length) {
    out += "No benchmark rows parsed.\n";
    return out;
  }

  out += "| Benchmark | Mode | Cnt | Score | Units |\n";
  out += "|---|---:|---:|---:|---|\n";

  for (const r of rows) {
    out += `| ${r.benchmark} | ${r.mode} | ${r.cnt || ""} | ${r.score} | ${r.units} |\n`;
  }

  return out;
}

function main() {
  const input = process.argv[2];
  if (!input) {
    usage();
    process.exit(2);
  }

  const text = fs.readFileSync(input, "utf8");
  const parsed = parseJmhSummary(text);
  process.stdout.write(renderMd({ title: "Benchmarks (JMH)", ...parsed }));
}

main();
