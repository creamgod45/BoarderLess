#!/usr/bin/env python3
"""Remove BoarderLess outputs and project caches, never shared dependency caches."""
import argparse
import os
import plistlib
import re
import shutil
import stat
import subprocess
import sys
import tempfile
from pathlib import Path


ISOLATED_BUILDS = (
    "boarderless-ai-reasoning-build",
    "boarderless-verified-build",
    "boarderless-export-verify",
)
PROJECT_OUTPUTS = ("build", ".gradle", ".kotlin", ".cxx", ".externalNativeBuild")


def project_modules(repo):
    settings = (repo / "settings.gradle.kts").read_text()
    if not re.search(r'rootProject\.name\s*=\s*"BoarderLess"', settings):
        raise RuntimeError("不是 BoarderLess 專案，拒絕清理")
    result = [repo, repo / "iosApp"]
    for line in re.findall(r'include\(([^\n]+)\)', settings):
        for name in re.findall(r'"(:[A-Za-z0-9_:-]+)"', line):
            result.append(repo.joinpath(*name.lstrip(":").split(":")))
    return list(dict.fromkeys(result))


def validate_target(path, allowed_root):
    """Reject top-level/ancestor links, foreign ownership and unexpected destinations."""
    if path.is_symlink() or path.resolve() != path:
        raise RuntimeError(f"路徑是 symbolic link 或非 canonical 路徑：{path}")
    if path == allowed_root or allowed_root not in path.parents:
        raise RuntimeError(f"路徑不在核准清理範圍：{path}")
    info = path.stat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid():
        raise RuntimeError(f"路徑不是目前使用者擁有的資料夾：{path}")


def discover(repo, temporary_roots, derived_data_root=None):
    targets = []
    for module in project_modules(repo):
        for name in PROJECT_OUTPUTS:
            path = module / name
            if path.exists() or path.is_symlink():
                validate_target(path, repo)
                targets.append(path)
    packages = repo / "artifacts"
    if packages.exists() or packages.is_symlink():
        validate_target(packages, repo)
        for child in packages.iterdir():
            if not re.fullmatch(r"BoarderLess[-_.A-Za-z0-9+]*\.(apk|aab|ipa|zip|dmg|pkg|exe|msi|deb|rpm|tar\.gz)", child.name):
                raise RuntimeError(f"artifacts 含非已知打包成品，拒絕自動刪除：{child.name}")
        targets.append(packages)
    if derived_data_root is not None and derived_data_root.exists():
        base = derived_data_root.resolve()
        for folder in base.iterdir():
            info = folder / "info.plist"
            if folder.is_symlink() or not info.is_file():
                continue
            with info.open("rb") as source:
                workspace = plistlib.load(source).get("WorkspacePath")
            if isinstance(workspace, str) and repo in Path(workspace).resolve().parents:
                validate_target(folder, base)
                targets.append(folder)
    # These exact roots were created by this project's isolated verification builds. Do not
    # blanket-delete boarderless-*; other temp folders contain QA documents and original media.
    for base in dict.fromkeys(p.resolve() for p in temporary_roots):
        for name in ISOLATED_BUILDS:
            path = base / name
            if path.exists() or path.is_symlink():
                validate_target(path, base)
                if not (path / "shared").is_dir() or not (path / "root").is_dir():
                    raise RuntimeError(f"隔離建置資料夾結構不符：{path}")
                targets.append(path)
        for container in base.glob("boarderless-media-validation.*"):
            if container.is_symlink():
                raise RuntimeError(f"隔離驗證根目錄是 symbolic link：{container}")
            if not (container / "booted-ios-test.gradle").is_file():
                continue
            for name in ("build", "gradle-cache"):
                path = container / name
                if path.exists() or path.is_symlink():
                    validate_target(path, base)
                    targets.append(path)
        for prefix in ("boarderless-brush-panels-qa-", "boarderless-export-ui-qa-", "boarderless-scheme-qa-"):
            for container in base.glob(prefix + "*"):
                if container.is_symlink():
                    raise RuntimeError(f"QA 根目錄是 symbolic link：{container}")
                for path in container.glob("BoarderLess*.app"):
                    validate_target(path, base)
                    with (path / "Contents/Info.plist").open("rb") as source:
                        bundle = plistlib.load(source).get("CFBundleIdentifier", "")
                    if bundle in ("cg.creamgod.boarderless", "cg.creamgod.boarderless.brushpanelsqa", "cg.creamgod.boarderless.exportqa") or isinstance(bundle, str) and bundle.startswith("org.boarderless.qa."):
                        targets.append(path)
    return sorted(set(targets), key=str)


def tracked_files(repo, targets):
    relative = [str(p.relative_to(repo)) for p in targets if repo in p.parents]
    if not relative:
        return []
    result = subprocess.run(
        ["git", "-C", str(repo), "ls-files", "-z", "--", *relative],
        check=True, capture_output=True,
    )
    return [p.decode("utf-8") for p in result.stdout.split(b"\0") if p]


def processes():
    output = subprocess.check_output(["ps", "-A", "-o", "pid=,command="], text=True)
    result = []
    for line in output.splitlines():
        parts = line.strip().split(None, 1)
        if len(parts) == 2 and int(parts[0]) != os.getpid():
            result.append((int(parts[0]), parts[1]))
    return result


def check_busy(targets):
    build_tokens = ("GradleWrapperMain", "org.gradle.launcher.GradleMain", "xcodebuild ")
    for pid, command in processes():
        if any(token in command for token in build_tokens):
            raise RuntimeError(f"偵測到建置程序 PID {pid}，請等建置結束再清理")
        # Include /tmp and its macOS /private/tmp spelling, without printing command contents.
        spellings = (s for p in targets for s in (str(p), str(p).removeprefix("/private")))
        if any(re.search(re.escape(s) + r"(?=$|[/\s\"'])", command) for s in spellings):
            raise RuntimeError(f"PID {pid} 正在使用清理範圍內的路徑，請先結束該程序")


def gradle_environment():
    env = os.environ.copy()
    if env.get("JAVA_HOME"):
        return env
    # macOS double-click launches with a minimal PATH. Reuse an installed Java runtime.
    candidates = (
        Path.home() / "Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home",
        Path("/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"),
        Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home"),
    )
    for path in candidates:
        if (path / "bin/java").is_file():
            env["JAVA_HOME"] = str(path)
            break
    return env


def stop_idle_gradle(repo):
    env = gradle_environment()
    command = [str(repo / "gradlew"), "--console=plain"]
    status = subprocess.run(command + ["--status"], cwd=repo, env=env, capture_output=True, text=True, timeout=45)
    if status.returncode != 0:
        raise RuntimeError("無法確認 Gradle daemon 狀態；請設定可用的 JAVA_HOME 後重試")
    if re.search(r"\bBUSY\b", status.stdout):
        raise RuntimeError("Gradle daemon 正在建置，拒絕清理")
    stopped = subprocess.run(command + ["--stop"], cwd=repo, env=env, capture_output=True, text=True, timeout=45)
    if stopped.returncode != 0:
        raise RuntimeError("無法停止閒置 Gradle daemon，拒絕清理")


def disk_bytes(path):
    # du counts allocated space and does not follow links into other projects.
    output = subprocess.check_output(["du", "-sk", str(path)], text=True)
    return int(output.split(None, 1)[0]) * 1024


def human_size(size):
    for unit in ("B", "KiB", "MiB", "GiB", "TiB"):
        if size < 1024 or unit == "TiB":
            return f"{size:.1f} {unit}"
        size /= 1024


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true", help="實際刪除，包括最後 APP/APK/Web/iOS 成品")
    mode.add_argument("--dry-run", action="store_true", help="僅列出清理計畫（預設）")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    if not (repo / "gradlew").is_file() or not (repo / ".git").exists():
        raise RuntimeError("缺少 Gradle/Git 專案標記，拒絕清理")
    roots = [Path("/tmp"), Path(tempfile.gettempdir())]
    derived = Path.home() / "Library/Developer/Xcode/DerivedData"
    targets = discover(repo, roots, derived)
    tracked = tracked_files(repo, targets)
    if tracked:
        raise RuntimeError(f"清理範圍含 Git 追蹤檔案，拒絕刪除：{tracked[0]}")
    total = 0
    print("清理計畫（含最後 verified 成品）：", flush=True)
    for path in targets:
        size = disk_bytes(path)
        total += size
        print(f"  {human_size(size):>12}  {path}", flush=True)
    print(f"合計約 {human_size(total)}。共用 ~/.gradle/caches、wrapper、jdks、~/.konan、SDK、Docker 與 APP 使用者資料均保留。", flush=True)
    if not args.apply:
        print("預覽完成。加 --apply 實際清理，或雙擊專案根目錄的 Clean BoarderLess Build.command。")
        return
    check_busy(targets)
    stop_idle_gradle(repo)
    # Gradle status/stop may create project cache directories. Discover and validate again.
    targets = discover(repo, roots, derived)
    if tracked_files(repo, targets):
        raise RuntimeError("清理前發現 Git 追蹤檔案，拒絕刪除")
    check_busy(targets)
    for path in targets:
        shutil.rmtree(path)  # Internal symlinks are unlinked, never followed.
        print(f"已清理 {path}", flush=True)
    print(f"完成。清理前估計可回收 {human_size(total)}；下次建置會重建輸出與專案快取。")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as error:
        print(f"清理未完成：{error}", file=sys.stderr)
        sys.exit(1)
