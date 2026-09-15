#!/usr/bin/env bash
set -euo pipefail

REPO_NAME="${1:-data-limit}"
VISIBILITY="${2:-public}"
REMOTE="${3:-origin}"

echo "▸ Initializing git repository …"
git init
git checkout -b main
git add -A
git commit -m "feat: Data Limit v0.1.0-pre – dashboard, budget, auto-gate"

if ! command -v gh &>/dev/null; then
  echo
  echo "⚠  'gh' (GitHub CLI) is not installed."
  echo "   Install it: https://cli.github.com/"
  echo
  echo "Then create the repo manually:"
  echo "  gh repo create \"$REPO_NAME\" --$VISIBILITY --source=. --push"
  echo "  gh workflow run build.yml"
  exit 0
fi

if ! gh auth status &>/dev/null 2>&1; then
  echo "⚠  Not logged in to GitHub CLI."
  echo "   Run: gh auth login"
  exit 1
fi

echo "▸ Creating GitHub repository \"$REPO_NAME\" …"
gh repo create "$REPO_NAME" --"$VISIBILITY" --source=. --push

echo "▸ Pushing to GitHub …"
git push -u "$REMOTE" main

echo "▸ Triggering the first build workflow …"
gh workflow run build.yml || echo "(trigger manually from the Actions tab if this fails)"

echo
echo "✅ Done. View releases at:"
gh repo view "$REPO_NAME" --web || echo "   https://github.com/$(gh api user -q '.login')/$REPO_NAME/releases"