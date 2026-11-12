#!/usr/bin/env bash
# Prepares a release in the working tree:
#   * sets mod_version in gradle.properties,
#   * turns the "## [Unreleased]" section of CHANGELOG.md into "## [<version>] - <today>" and starts
#     a new, empty Unreleased section above it,
#   * updates the compare links at the bottom of the changelog,
#   * points the addon template (templates/addon-template/gradle.properties) at this release: the mod
#     version, the API version from gradle.properties and the accepted MCHeli version range.
# It does not commit, tag or push; it prints the commands for that at the end. Review the diff first.
#
#   tools/release/prepare-release.sh 2.1.0
#   tools/release/prepare-release.sh 2.1.0-beta.1
#
# CHANGELOG, PROPERTIES and TEMPLATE_PROPERTIES can point it at other files (used to test the script on copies).

set -euo pipefail

if [ $# -ne 1 ]; then
  echo "usage: $0 <version>   (for example 2.1.0 or 2.1.0-beta.1)" >&2
  exit 2
fi

version=${1#v}
root=$(cd "$(dirname "$0")/../.." && pwd)
changelog=${CHANGELOG:-$root/CHANGELOG.md}
properties=${PROPERTIES:-$root/gradle.properties}
template_properties=${TEMPLATE_PROPERTIES:-$root/templates/addon-template/gradle.properties}
default_repo=https://github.com/IVomens/MCHeli-Revived
today=$(date -u +%Y-%m-%d)

fail() {
  echo "error: $*" >&2
  exit 1
}

semver='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?$'
[[ $version =~ $semver ]] || fail "'$version' is not a semantic version (x.y.z or x.y.z-suffix)"

[ -f "$changelog" ] || fail "$changelog not found"
[ -f "$properties" ] || fail "$properties not found"
grep -q '^## \[Unreleased\]' "$changelog" || fail "$changelog has no '## [Unreleased]' section"
! grep -qF "## [$version]" "$changelog" || fail "$changelog already has a [$version] section"
grep -q '^mod_version=' "$properties" || fail "$properties has no mod_version line"

"$root/tools/release/changelog-section.sh" Unreleased "$changelog" >/dev/null 2>&1 \
  || fail "the Unreleased section is empty; write down what changed first"

# The newest released version, i.e. the first "## [x.y.z]" heading below Unreleased.
previous=$(grep -E '^## \[[0-9]+\.[0-9]+\.[0-9]+' "$changelog" | head -n 1 | sed -E 's/^## \[([^]]+)\].*/\1/' || true)

# Repository URL from the existing Unreleased link, if there is one.
repo=$(grep -E '^\[Unreleased\]: ' "$changelog" | sed -E 's#^\[Unreleased\]: (.*)/(compare|commits|releases)/.*#\1#' || true)
repo=${repo:-$default_repo}

if [ -n "$previous" ]; then
  version_link="[$version]: $repo/compare/v$previous...v$version"
else
  version_link="[$version]: $repo/releases/tag/v$version"
fi
unreleased_link="[Unreleased]: $repo/compare/v$version...HEAD"

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

awk -v version="$version" -v today="$today" \
    -v unreleased_link="$unreleased_link" -v version_link="$version_link" '
  /^## \[Unreleased\]/ && !heading_done {
    print "## [Unreleased]"
    print ""
    print "## [" version "] - " today
    heading_done = 1
    next
  }
  /^\[Unreleased\]: / {
    print unreleased_link
    print version_link
    links_done = 1
    next
  }
  { print }
  END {
    if (!links_done) {
      print ""
      print unreleased_link
      print version_link
    }
  }
' "$changelog" > "$tmp"
cat "$tmp" > "$changelog"

old_version=$(grep '^mod_version=' "$properties" | head -n 1 | cut -d= -f2 | tr -d '\r')
sed "s/^mod_version=.*/mod_version=$version/" "$properties" > "$tmp"
cat "$tmp" > "$properties"

# The addon template builds against the newest release, from the public Maven repository.
api_version=$(grep '^api_version=' "$properties" | head -n 1 | cut -d= -f2 | tr -d '\r')
if [ -f "$template_properties" ]; then
  sed -e "s/^mcheli_version=.*/mcheli_version=$version/" \
      -e "s/^mcheli_api_version=.*/mcheli_api_version=$api_version/" \
      -e "s/^mcheli_version_range=.*/mcheli_version_range=[$version,)/" \
      -e "s/^mcheli_fabric_version_range=.*/mcheli_fabric_version_range=>=$version/" \
      "$template_properties" > "$tmp"
  cat "$tmp" > "$template_properties"
  echo "Addon template: MCHeli $version, API $api_version."
fi

if [ "$old_version" = "$version" ]; then
  echo "mod_version is already $version."
else
  echo "mod_version: $old_version -> $version"
fi
echo "CHANGELOG.md: Unreleased is now [$version] - $today."
echo
echo "Check the result, then commit, tag and push:"
echo
echo "  git diff"
echo "  git commit -am \"Release $version\""
echo "  git tag -a v$version -m \"MCHeli: Revived $version\""
echo "  git push origin HEAD v$version"
echo
echo "Pushing the tag starts the release workflow."
