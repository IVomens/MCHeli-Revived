#!/usr/bin/env bash
# Prints the body of one version's section of CHANGELOG.md, without its "## [x.y.z]" heading and
# without leading or trailing blank lines. Fails if the section is missing or empty.
#
#   tools/release/changelog-section.sh 2.1.0 [path/to/CHANGELOG.md]
#
# The release workflow uses it for the GitHub release notes and the Modrinth and CurseForge
# changelogs. "Unreleased" works as a version too.

set -euo pipefail

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
  echo "usage: $0 <version> [changelog]" >&2
  exit 2
fi

version=$1
changelog=${2:-$(cd "$(dirname "$0")/../.." && pwd)/CHANGELOG.md}

if [ ! -f "$changelog" ]; then
  echo "error: $changelog not found" >&2
  exit 1
fi

section=$(awk -v heading="## [$version]" '
  index($0, heading) == 1 { found = 1; next }
  found && /^## / { exit }
  found && /^\[[^]]+\]: / { exit }
  found { print }
' "$changelog" | sed -e '/./,$!d')
# Command substitution has already dropped the trailing blank lines.

if [ -z "$section" ]; then
  if grep -qF "## [$version]" "$changelog"; then
    echo "error: the [$version] section of $changelog is empty" >&2
  else
    echo "error: $changelog has no '## [$version]' section" >&2
  fi
  exit 1
fi

printf '%s\n' "$section"
