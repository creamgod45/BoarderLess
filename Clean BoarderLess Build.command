#!/bin/bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "$0")" && pwd -P)"
python3 "$ROOT/scripts/clean-build-artifacts.py" --apply
if [[ -t 0 ]]; then
    read -r -p "清理完成，按 Enter 關閉視窗。" _reply
fi
