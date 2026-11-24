#!/bin/sh
# The release notes ("What's new" in the app's update page) for a new release: the first line of each commit since this app's previous release, oldest first.
#   release-notes.sh <tag prefix> <commit>     prefix "sunflower-v" (the play app) or "v" (the test app); writes Markdown to stdout
# Runs in the release workflows with GH_TOKEN and GITHUB_REPOSITORY set; reads the releases and the comparison through the API (no deep clone).
set -eu
prefix=$1 sha=$2
prev=$(gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" --jq ".[].tag_name | select(startswith(\"$prefix\"))" | head -n 1)   # newest first
if [ -z "$prev" ]; then echo "The first release."; exit 0; fi
lines=$(gh api "repos/$GITHUB_REPOSITORY/compare/$prev...$sha" --jq '.commits[].commit.message | split("\n")[0]' | sed -e 's/^Test app: //' | awk '{ print "- " toupper(substr($0, 1, 1)) substr($0, 2) }')
if [ -z "$lines" ]; then echo "No changes since $prev: the same app, built again."; exit 0; fi
printf "## What's new\n\n%s\n\nSince %s.\n" "$lines" "$prev"
