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

# Try to find an existing comment that contains our marker.
# Use the Issues API directly (instead of `gh pr view`) so that bot comments
# created via GITHUB_TOKEN are included.  Paginate through all comments.
EXISTING_ID=""
PAGE=1
while :; do
  RESPONSE="$(gh api \
    "repos/{owner}/{repo}/issues/${PR_NUMBER}/comments?per_page=100&page=${PAGE}" \
    2>/dev/null || true)"

  MATCH="$(echo "$RESPONSE" | jq -r ".[] | select(.body | contains(\"$MARK_BEGIN\")) | .id" 2>/dev/null | head -n 1 || true)"
  if [[ -n "$MATCH" ]]; then
    EXISTING_ID="$MATCH"
    break
  fi

  # Stop when we've reached the last page (fewer than 100 results).
  COUNT="$(echo "$RESPONSE" | jq 'length' 2>/dev/null || echo 0)"
  if [[ "$COUNT" -lt 100 ]]; then
    break
  fi
  PAGE=$((PAGE + 1))
done

if [[ -n "$EXISTING_ID" ]]; then
  gh api \
    -X PATCH \
    "repos/{owner}/{repo}/issues/comments/${EXISTING_ID}" \
    -f body="$(cat "$BODY_FILE")" >/dev/null
else
  gh pr comment "$PR_NUMBER" --body-file "$BODY_FILE" >/dev/null
fi
