# Stage definitions for the shared verified-release-kit runner.
LOCAL_CI_NAME="forge-android"
LOCAL_CI_STAGES=(verify)
LOCAL_CI_DEFAULT_STAGES=(verify)

LOCAL_CI_OPTIONS_HELP="  --publish     upload the verified APK and checksum to the release"
PUBLISH=0
local_ci_option() { case "$1" in --publish) PUBLISH=1 ;; *) return 1 ;; esac; }

REPO=RuiCostafrg/forge-android

stage_verify() {
  : "${RELEASE_TAG:?set RELEASE_TAG, e.g. RELEASE_TAG=0.0.2}"
  : "${BLOB_SHA:?set BLOB_SHA — git blob SHA of the APK (git hash-object Forge-Android-<TAG>.apk)}"
  : "${EXPECTED_SHA256:?set EXPECTED_SHA256 — sha256sum of the verified APK}"
  require base64 ""
  [[ "$RELEASE_TAG" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || _fail "invalid RELEASE_TAG"
  local scratch ASSET_NAME
  scratch="$(mktemp -d)"
  trap "rm -rf -- $(printf '%q' "$scratch")" EXIT
  cd -- "$scratch"
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
