# Local CI — `gh-signoff` (Linux only)

Local replacement for `publish-release-asset.yml` ([basecamp/gh-signoff](https://github.com/basecamp/gh-signoff)).
The workflow fetches a signed APK from a git blob, checks its SHA-256 and uploads
it to a release; the script does the same on a Linux machine you control and
posts a green `signoff/verify` commit status when the checksum matches.

GitHub Actions stays available as the hosted fallback — this lane covers **Linux only**.

## Setup (Linux)

```bash
gh auth login                       # token needs repo (contents write) for --publish
gh extension install basecamp/gh-signoff
```

## Usage

```bash
# Local verification + signoff/verify status (nothing is uploaded):
RELEASE_TAG=0.0.2 \
BLOB_SHA=<git blob sha of the signed APK> \
EXPECTED_SHA256=<expected checksum> \
scripts/local-ci.sh

# Same, and also upload to the release (like the workflow's publish job):
... scripts/local-ci.sh --publish

# Without posting statuses:
... scripts/local-ci.sh --no-signoff
```

Hints:
- `BLOB_SHA`: `git hash-object Forge-Android-<TAG>.apk` (the blob uploaded to main
  or to the release); verify against `Forge-Android-<TAG>.apk.sha256` in the repo.
- `--publish` mirrors the workflow exactly (`gh release upload … --clobber`).
- A failed checksum posts a red `signoff/verify` failure status automatically.
