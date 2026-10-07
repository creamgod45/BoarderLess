#!/bin/bash
# macOS + Xcode only. Uses the same Xcode/Gradle integration as local development.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export APP_VERSION="${APP_VERSION:-1.0.0}"
export IOS_BUNDLE_ID="${IOS_BUNDLE_ID:-cg.creamgod.boarderless.BoarderLess}"
export IOS_EXPORT_METHOD="${IOS_EXPORT_METHOD:-release-testing}"
export IOS_TEAM_ID="${IOS_TEAM_ID:-}"

APP_BUILD_NUMBER="$(python3 - <<'PY'
import os, re
v = os.environ['APP_VERSION']
if not re.fullmatch(r'(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})', v):
    raise SystemExit('APP_VERSION must be x.y.z; each component must be 0..999 without leading zeros')
a, b, c = map(int, v.split('.'))
if not 1 <= a <= 255 or b > 255:
    raise SystemExit('Release versions require major 1..255, minor 0..255, patch 0..999')
build = os.environ.get('APP_BUILD_NUMBER') or str(a * 1000000 + b * 1000 + c)
if not build.isdecimal() or not 1 <= int(build) <= 999999999:
    raise SystemExit('APP_BUILD_NUMBER must be 1..999999999')
if not re.fullmatch(r'[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+', os.environ['IOS_BUNDLE_ID']):
    raise SystemExit('IOS_BUNDLE_ID must be a valid bundle identifier')
if os.environ['IOS_EXPORT_METHOD'] not in ('release-testing', 'app-store-connect', 'debugging', 'enterprise'):
    raise SystemExit('Unsupported IOS_EXPORT_METHOD')
print(int(build))
PY
)"
export APP_BUILD_NUMBER
if [ "$(uname -m)" != arm64 ]; then
  echo 'This project targets the arm64 iOS Simulator; run packaging on an Apple Silicon Mac.' >&2
  exit 1
fi
xcodebuild -version
JAVA_HOME="$("$ROOT/iosApp/scripts/resolve-java-home.sh")"
export JAVA_HOME
mkdir -p build/ios-package artifacts
WORK="$(mktemp -d "$ROOT/build/ios-package/run.XXXXXX")"
KEYCHAIN=""
PROFILE_DEST=""
ORIGINAL_KEYCHAINS=()
while IFS= read -r entry; do
  entry="${entry#*\"}"
  entry="${entry%\"*}"
  ORIGINAL_KEYCHAINS+=("$entry")
done < <(security list-keychains -d user)
cleanup() {
  if [ -n "$KEYCHAIN" ]; then
    security list-keychains -d user -s "${ORIGINAL_KEYCHAINS[@]}" || true
    security delete-keychain "$KEYCHAIN" || true
  fi
  if [ -n "$PROFILE_DEST" ]; then
    if [ -f "$WORK/previous.mobileprovision" ]; then
      cp "$WORK/previous.mobileprovision" "$PROFILE_DEST"
    else
      rm -f "$PROFILE_DEST"
    fi
  fi
  # Remove signing material; retain Xcode products/logs for diagnosis.
  rm -f "$WORK/certificate.p12" "$WORK/profile.mobileprovision" "$WORK/profile.plist" "$WORK/previous.mobileprovision"
}
trap cleanup EXIT

SIGNING=(CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO)
SIGNED=false
if [ -n "${IOS_CERTIFICATE_BASE64:-}${IOS_PROVISIONING_PROFILE_BASE64:-}${IOS_CERTIFICATE_PASSWORD:-}" ]; then
  : "${IOS_CERTIFICATE_BASE64:?Set certificate and profile together}"
  : "${IOS_PROVISIONING_PROFILE_BASE64:?Set certificate and profile together}"
  : "${IOS_TEAM_ID:?Set IOS_TEAM_ID for signed builds}"
  export WORK
  python3 - <<'PY'
import base64, os, pathlib
p = pathlib.Path(os.environ['WORK'])
for env, name in [('IOS_CERTIFICATE_BASE64', 'certificate.p12'), ('IOS_PROVISIONING_PROFILE_BASE64', 'profile.mobileprovision')]:
    raw = ''.join(os.environ[env].split())
    target = p / name
    target.write_bytes(base64.b64decode(raw, validate=True))
    target.chmod(0o600)
PY
  security cms -D -i "$WORK/profile.mobileprovision" > "$WORK/profile.plist"
  python3 - <<'PY'
import datetime, fnmatch, os, pathlib, plistlib, re
p = pathlib.Path(os.environ['WORK'])
profile = plistlib.loads((p / 'profile.plist').read_bytes())
team, bundle = os.environ['IOS_TEAM_ID'], os.environ['IOS_BUNDLE_ID']
if not re.fullmatch(r'[A-Z0-9]{10}', team) or team not in profile['TeamIdentifier']:
    raise SystemExit('IOS_TEAM_ID does not match provisioning profile')
uid = profile['UUID']
if not re.fullmatch(r'[A-Fa-f0-9-]{36}', uid):
    raise SystemExit('Invalid provisioning profile UUID')
app = profile['Entitlements']['application-identifier'].split('.', 1)[1]
if not fnmatch.fnmatchcase(bundle, app):
    raise SystemExit('IOS_BUNDLE_ID does not match provisioning profile')
if profile['ExpirationDate'] <= datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None):
    raise SystemExit('Provisioning profile has expired')
method = os.environ['IOS_EXPORT_METHOD']
development = profile['Entitlements'].get('get-task-allow', False)
devices = bool(profile.get('ProvisionedDevices'))
enterprise = profile.get('ProvisionsAllDevices', False)
if ((method == 'debugging' and not development) or
    (method == 'release-testing' and (development or not devices or enterprise)) or
    (method == 'app-store-connect' and (development or devices or enterprise)) or
    (method == 'enterprise' and not enterprise)):
    raise SystemExit('Provisioning profile type does not match IOS_EXPORT_METHOD')
certificate = 'Apple Development' if method == 'debugging' else 'Apple Distribution'
(p / 'certificate-name.txt').write_text(certificate)
(p / 'profile-uuid.txt').write_text(uid)
options = dict(method=method, destination='export', teamID=team, signingStyle='manual',
               signingCertificate=certificate, provisioningProfiles={bundle: uid},
               manageAppVersionAndBuildNumber=False)
(p / 'ExportOptions.plist').write_bytes(plistlib.dumps(options))
PY
  PROFILE_UUID="$(cat "$WORK/profile-uuid.txt")"
  CERTIFICATE_NAME="$(cat "$WORK/certificate-name.txt")"
  KEYCHAIN="$WORK/signing.keychain-db"
  KEYCHAIN_PASSWORD="$(openssl rand -hex 32)"
  security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
  security set-keychain-settings -lut 21600 "$KEYCHAIN"
  security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
  security import "$WORK/certificate.p12" -P "${IOS_CERTIFICATE_PASSWORD:-}" -k "$KEYCHAIN" -t cert -f pkcs12 -T /usr/bin/codesign -T /usr/bin/security
  security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN" >/dev/null
  security list-keychains -d user -s "$KEYCHAIN" "${ORIGINAL_KEYCHAINS[@]}"
  PROFILE_DIR="$HOME/Library/MobileDevice/Provisioning Profiles"
  mkdir -p "$PROFILE_DIR"
  PROFILE_DEST="$PROFILE_DIR/$PROFILE_UUID.mobileprovision"
  if [ -f "$PROFILE_DEST" ]; then cp "$PROFILE_DEST" "$WORK/previous.mobileprovision"; fi
  cp "$WORK/profile.mobileprovision" "$PROFILE_DEST"
  SIGNING=(CODE_SIGNING_ALLOWED=YES CODE_SIGN_STYLE=Manual "DEVELOPMENT_TEAM=$IOS_TEAM_ID" "CODE_SIGN_IDENTITY=$CERTIFICATE_NAME" "PROVISIONING_PROFILE_SPECIFIER=$PROFILE_UUID" "OTHER_CODE_SIGN_FLAGS=--keychain $KEYCHAIN")
  SIGNED=true
fi

COMMON=(-project "$ROOT/iosApp/iosApp.xcodeproj" -scheme iosApp -configuration Release
  "MARKETING_VERSION=$APP_VERSION" "CURRENT_PROJECT_VERSION=$APP_BUILD_NUMBER"
  "PRODUCT_BUNDLE_IDENTIFIER=$IOS_BUNDLE_ID")
# Optional overrides use Xcode build settings; unset values retain local development defaults.
for field in SCHEME HOST PORT; do
  name="IOS_BACKEND_$field"
  if [ -n "${!name:-}" ]; then COMMON+=("BOARDERLESS_BACKEND_$field=${!name}"); fi
done

xcodebuild "${COMMON[@]}" -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$WORK/simulator" CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build \
  2>&1 | tee "$WORK/simulator.log"
SIM_APP="$WORK/simulator/Build/Products/Release-iphonesimulator/BoarderLess.app"
test -d "$SIM_APP"
ditto -c -k --sequesterRsrc --keepParent "$SIM_APP" "artifacts/BoarderLess-$APP_VERSION-ios-simulator-arm64.zip"

ARCHIVE="$WORK/BoarderLess.xcarchive"
xcodebuild "${COMMON[@]}" -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath "$WORK/device" -archivePath "$ARCHIVE" "${SIGNING[@]}" archive \
  2>&1 | tee "$WORK/archive.log"
test -d "$ARCHIVE/Products/Applications/BoarderLess.app"
if [ "$SIGNED" = true ]; then
  xcodebuild -exportArchive -archivePath "$ARCHIVE" -exportPath "$WORK/export" \
    -exportOptionsPlist "$WORK/ExportOptions.plist" 2>&1 | tee "$WORK/export.log"
  test -f "$WORK/export/BoarderLess.ipa"
  cp "$WORK/export/BoarderLess.ipa" "artifacts/BoarderLess-$APP_VERSION-ios-$IOS_EXPORT_METHOD.ipa"
  ARCHIVE_SUFFIX=archive
else
  ARCHIVE_SUFFIX=unsigned-archive
  echo 'Apple signing credentials are unset: archive is unsigned and cannot be installed on an iPhone.'
fi
ditto -c -k --sequesterRsrc --keepParent "$ARCHIVE" "artifacts/BoarderLess-$APP_VERSION-ios-$ARCHIVE_SUFFIX.zip"
echo "iOS artifacts: $ROOT/artifacts (build logs: $WORK)"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  printf '### iOS %s\n- Build: %s\n- Signed: %s\n- Simulator: arm64\n' "$APP_VERSION" "$APP_BUILD_NUMBER" "$SIGNED" >> "$GITHUB_STEP_SUMMARY"
fi
