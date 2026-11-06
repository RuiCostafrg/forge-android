#!/usr/bin/env bash
# forge-android local CI — Linux only, powered by basecamp/gh-signoff.
# Local replacement for .github/workflows/publish-release-asset.yml: verifies the
# signed-APK blob checksum off the released blob. Uploads only with --publish.
# Usage:
#   RELEASE_TAG=0.0.2 BLOB_SHA=<sha1> EXPECTED_SHA256=<hex> \
#   scripts/local-ci.sh               verify checksums, post signoff/verify
#   scripts/local-ci.sh --no-signoff  verify only, no statuses
#   ... --publish                     same + upload with gh release upload --clobber
# BLOB_SHA is the git blob SHA of the APK:
#   git hash-object Forge-Android-<tag>.apk
set -euo pipefail

AVAILABLE_STAGES=(verify)
DEFAULT_STAGES=(verify)

_fail()   { printf '✗ %s\n' "$*" >&2; exit 1; }
_log()    { printf '▸ %s\n' "$*"; }
_ok()     { printf '✓ %s\n' "$*"; }
require() { command -v "$1" >/dev/null 2>&1 || _fail "missing '$1'${2:+ — $2}"; }

case "$(uname -s)" in
  Linux) ;;
  *) _fail "Linux-only local CI; refusing to run on $(uname -s)." ;;
esac

NO_SIGNOFF=0
PUBLISH=0
SELECTED=()
while [ $# -gt 0 ]; do
  case "$1" in
    --no-signoff) NO_SIGNOFF=1 ;;
    --publish) PUBLISH=1 ;;
    --list) printf '%s\n' "stages: ${AVAILABLE_STAGES[*]}" ;;
    -h|--help) sed -n '2,14p' "$0" ;;
    -*) _fail "unknown option: $1 (try --help)" ;;
    *) SELECTED+=("$1") ;;
  esac
  shift
done
[ ${#SELECTED[@]} -gt 0 ] || SELECTED=("${DEFAULT_STAGES[@]}")

if [ "$NO_SIGNOFF" = 0 ]; then
  require gh ""
  gh extension list 2>/dev/null | grep -qi "gh-signoff" \
    || _fail "gh-signoff extension missing — run: gh extension install basecamp/gh-signoff (or pass --no-signoff)"
fi

REPO=RuiCostafrg/forge-android

stage_verify() {
  : "${RELEASE_TAG:?set RELEASE_TAG, e.g. RELEASE_TAG=0.0.2}"
  : "${BLOB_SHA:?set BLOB_SHA — git blob SHA of the APK (git hash-object Forge-Android-<TAG>.apk)}"
  : "${EXPECTED_SHA256:?set EXPECTED_SHA256 — sha256sum of the verified APK}"
  require base64 ""
  ASSET_NAME="Forge-Android-${RELEASE_TAG}.apk"
  gh api "repos/${REPO}/git/blobs/${BLOB_SHA}" --jq .content | base64 --decode > "$ASSET_NAME"
  printf '%s  %s\n' "$EXPECTED_SHA256" "$ASSET_NAME" > "${ASSET_NAME}.sha256"
  sha256sum --check "${ASSET_NAME}.sha256"
  if [ "$PUBLISH" = 1 ]; then
    gh release upload "$RELEASE_TAG" "$ASSET_NAME" "${ASSET_NAME}.sha256" --repo "$REPO" --clobber
    _ok "published ${ASSET_NAME} to release ${RELEASE_TAG}"
  else
    _log "verified ${ASSET_NAME} locally — pass --publish (trusted CI runner/machine with contents write) to upload"
  fi
}

# ── runner ────────────────────────────────────────────────────────────────────

for s in "${SELECTED[@]}"; do
  declare -F "stage_$s" >/dev/null || _fail "unknown stage '$s' (stages: ${AVAILABLE_STAGES[*]})"
  _log "stage: $s"
  if ! ( set -e; "stage_$s" ); then
    [ "$NO_SIGNOFF" = 0 ] && gh signoff fail verify --description "verify failed locally" >/dev/null 2>&1 || true
    _fail "stage '$s' failed"
  fi
  _ok "$s passed"
  if [ "$NO_SIGNOFF" = 0 ]; then
    gh signoff "$s" >/dev/null 2>&1 \
      || printf '⚠ could not post signoff status for %s — is HEAD pushed? gh authorized?\n' "$s" >&2
  fi
done
_log "all done${NO_SIGNOFF:+ — no signoff statuses posted (was --no-signoff)}."
