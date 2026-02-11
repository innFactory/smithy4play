#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: post-pr-comment.sh <pr_number> <tag> <markdown_file>"
  exit 2
fi

PR_NUMBER="$1"
TAG="$2"
MD_FILE="$3"

if [[ ! -f "$MD_FILE" ]]; then
  echo "Markdown file not found: $MD_FILE"
  exit 2
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI not found in PATH"
  exit 2
fi

MARK_BEGIN="<!-- smithy4play-ci:${TAG}:begin -->"
MARK_END="<!-- smithy4play-ci:${TAG}:end -->"

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

{
  echo "$MARK_BEGIN"
  cat "$MD_FILE"
  echo
  echo "$MARK_END"
} >"$BODY_FILE"

# Try update an existing comment that contains our marker.
EXISTING_ID="$(gh pr view "$PR_NUMBER" --json comments --jq ".comments[] | select(.body | contains(\"$MARK_BEGIN\")) | .databaseId" | head -n 1 || true)"

if [[ -n "$EXISTING_ID" ]]; then
  gh api \
    -X PATCH \
    "repos/{owner}/{repo}/issues/comments/${EXISTING_ID}" \
    -f body="$(cat "$BODY_FILE")" >/dev/null
else
  gh pr comment "$PR_NUMBER" --body-file "$BODY_FILE" >/dev/null
fi
