#!/usr/bin/env bash
# Prints the release notes for a tag, or for the next release (HEAD) without one: the Conventional
# Commits since the previous v* tag, grouped like Tokio's, without merges and chore(release) commits.
# Usage: scripts/release-notes.sh [tag]
set -euo pipefail
cd "$(dirname "$0")/.."

tag=${1:-}
to=${tag:-HEAD}
# The previous tag: the latest one before the tag, or the latest one for the next release.
if [ -n "$tag" ]; then ref="$tag^"; else ref=HEAD; fi
from=$(git describe --tags --abbrev=0 --match 'v*' "$ref" 2>/dev/null || true)
url="https://github.com/${GITHUB_REPOSITORY:-tokio-app/minecraft-mod}"
minecraft=$(sed -n 's/^minecraft_version=//p' gradle.properties | tr -d '\r')
if [ -n "$from" ]; then
	changelog="[\`$from...$to\`]($url/compare/$from...$to)"
else
	changelog="[\`$to\`]($url/commits/$to)"
fi

git log --no-merges --format='%H%x1f%h%x1f%an%x1f%B%x1e' "${from:+$from..}$to" |
	awk -v url="$url" -v minecraft="$minecraft" -v changelog="$changelog" '
	BEGIN {
		RS = "\036"; FS = "\037"
		split("Breaking Changes|New Features|Fixes|Performance|Other Changes", titles, "|")
	}
	{
		sub(/^\n+/, "")
		if (NF < 4) next
		subject = $4; sub(/\n.*/, "", subject)
		type = ""; scope = ""; bang = 0; text = subject
		if (match(subject, /^[A-Za-z]+(\([^)]+\))?!?: /)) {
			head = substr(subject, 1, RLENGTH - 2); text = substr(subject, RLENGTH + 1)
			if (head ~ /!$/) { bang = 1; head = substr(head, 1, length(head) - 1) }
			if (match(head, /\(/)) { scope = substr(head, RSTART + 1, length(head) - RSTART - 1); head = substr(head, 1, RSTART - 1) }
			type = tolower(head)
		}
		if (type == "chore" && scope == "release") next
		s = (bang || $4 ~ /(^|\n)BREAKING[ -]CHANGE:/) ? 1 : type == "feat" ? 2 : type == "fix" ? 3 : type == "perf" ? 4 : 5
		items[s] = items[s] "- [ [`" $2 "`](" url "/commit/" $1 ") ] - **" toupper(substr(text, 1, 1)) substr(text, 2) "** _by_ " $3 "\n"
		n++
	}
	END {
		print "## Tokio for Minecraft\n"
		print "The Fabric mod that connects your Minecraft: Java Edition " minecraft " world to Tokio. Put the jar in your `mods` folder, with Fabric Loader and Fabric API.\n"
		print "## What'\''s Changed\n"
		for (i = 1; i <= 5; i++) if (items[i] != "") print "## " titles[i] "\n\n" items[i]
		if (!n) print "No changes.\n"
		print "---\n\n**Full Changelog**: " changelog
	}'
