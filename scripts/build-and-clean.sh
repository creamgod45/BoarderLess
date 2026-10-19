#!/bin/bash
# Local verification only: outputs (including final packages) are removed after the build.
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd -P)"
cd "$ROOT"
cleanup() {
    local build_status=$?
    trap - EXIT
    if python3 "$ROOT/scripts/clean-build-artifacts.py" --apply; then
        exit "$build_status"
    else
        local clean_status=$?
        echo "建置結束，但清理未完成；請排除上述原因後重跑清理腳本。" >&2
        if [[ "$build_status" -ne 0 ]]; then exit "$build_status"; fi
        exit "$clean_status"
    fi
}
trap cleanup EXIT
"$ROOT/gradlew" --no-build-cache "$@"
