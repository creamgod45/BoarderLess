#!/usr/bin/env python3
"""Destructive cleanup guards tested only with disposable fixtures."""
import importlib.util
import os
import plistlib
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("clean", Path(__file__).with_name("clean-build-artifacts.py"))
clean = importlib.util.module_from_spec(spec)
spec.loader.exec_module(clean)


class CleanupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name).resolve()
        self.repo = self.base / "repo"
        self.repo.mkdir()
        (self.repo / "settings.gradle.kts").write_text('rootProject.name = "BoarderLess"\ninclude(":shared", ":nested:module")\n')
        self.outside = self.base / "outside"
        self.outside.mkdir()
        (self.outside / "keep").write_text("original")

    def tearDown(self):
        self.temp.cleanup()

    def test_removes_outputs_and_final_packages_but_keeps_sources_and_user_data(self):
        for name in ("build/verified/final.apk", "shared/build/output", ".gradle/cache", ".kotlin/cache", "nested/module/build/output"):
            p = self.repo / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("generated")
        for name in ("shared/src/test/build/original", "gradle/wrapper/gradle-wrapper.jar", "user/document"):
            p = self.repo / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("keep")
        for path in clean.discover(self.repo, []):
            shutil.rmtree(path)
        self.assertFalse((self.repo / "build").exists())
        self.assertTrue((self.repo / "shared/src/test/build/original").exists())
        self.assertTrue((self.repo / "gradle/wrapper/gradle-wrapper.jar").exists())
        self.assertTrue((self.repo / "user/document").exists())

    def test_only_identified_legacy_karma_outputs_are_selected(self):
        ours = self.base / "_karma_webpack_123"
        other = self.base / "_karma_webpack_456"
        suspicious = self.base / "_karma_webpack_789"
        for folder in (ours, other, suspicious):
            folder.mkdir()
        marker = '/***/ "./kotlin/BoarderLess-shared-test.js"; cg.creamgod.boarderless'
        (ours / "commons.js").write_text(marker)
        (ours / "runtime.js").write_text("generated")
        (ours / "123.wasm").write_bytes(b"generated")
        (other / "commons.js").write_text('"./kotlin/Other-shared-test.js"')
        (suspicious / "commons.js").write_text(marker)
        (suspicious / "private.p12").write_bytes(b"keep")
        paths = clean.discover(self.repo, [self.base])
        self.assertIn(ours, paths)
        self.assertNotIn(other, paths)
        self.assertNotIn(suspicious, paths)
        for command in ("java -jar /repo/gradle-wrapper.jar", "node /build/karma.conf.js"):
            with patch.object(clean, "processes", return_value=[(42, command)]):
                with self.assertRaises(RuntimeError):
                    clean.check_busy([ours])

    def test_external_output_symlink_rejected(self):
        (self.repo / "build").symlink_to(self.outside)
        with self.assertRaises(RuntimeError):
            clean.discover(self.repo, [])
        self.assertEqual("original", (self.outside / "keep").read_text())

    def test_internal_symlink_does_not_delete_target(self):
        (self.repo / "build").mkdir()
        (self.repo / "build/link").symlink_to(self.outside)
        for path in clean.discover(self.repo, []):
            shutil.rmtree(path)
        self.assertEqual("original", (self.outside / "keep").read_text())

    def test_only_known_isolated_outputs_and_cache_are_selected(self):
        root = self.base / clean.ISOLATED_BUILDS[0]
        (root / "shared").mkdir(parents=True)
        (root / "root").mkdir()
        unrelated = self.base / "boarderless-important-documents"
        unrelated.mkdir()
        container = self.base / "boarderless-media-validation.fixture"
        container.mkdir()
        (container / "booted-ios-test.gradle").write_text("fixture")
        (container / "build").mkdir()
        (container / "gradle-cache").mkdir()
        paths = clean.discover(self.repo, [self.base])
        self.assertIn(root, paths)
        self.assertIn(container / "gradle-cache", paths)
        self.assertNotIn(unrelated, paths)
        self.assertNotIn(container, paths)

    def test_unrecognized_project_and_isolated_structure_rejected(self):
        root = self.base / clean.ISOLATED_BUILDS[0]
        root.mkdir()
        with self.assertRaises(RuntimeError):
            clean.discover(self.repo, [self.base])
        (self.repo / "settings.gradle.kts").write_text('rootProject.name = "Other"')
        with self.assertRaises(RuntimeError):
            clean.discover(self.repo, [])

    def test_qa_app_copy_only_not_qa_documents(self):
        container = self.base / "boarderless-scheme-qa-fixture"
        app = container / "BoarderLess Scheme QA.app"
        (app / "Contents").mkdir(parents=True)
        with (app / "Contents/Info.plist").open("wb") as f:
            plistlib.dump({"CFBundleIdentifier": "org.boarderless.qa.fixture"}, f)
        (container / "user").mkdir()
        paths = clean.discover(self.repo, [self.base])
        self.assertEqual([app], paths)

    def test_tracked_output_detected(self):
        subprocess.run(["git", "init", "-q", str(self.repo)], check=True)
        output = self.repo / "build/source.txt"
        output.parent.mkdir()
        output.write_text("tracked source")
        subprocess.run(["git", "-C", str(self.repo), "add", "build/source.txt"], check=True)
        self.assertEqual(["build/source.txt"], clean.tracked_files(self.repo, [output.parent]))

    def test_release_packages_selected_but_unrecognized_artifact_refuses_cleanup(self):
        packages = self.repo / "artifacts"
        packages.mkdir()
        (packages / "BoarderLess-1.2.3-ios-unsigned-archive.zip").write_bytes(b"generated")
        self.assertIn(packages, clean.discover(self.repo, []))
        (packages / "private.p12").write_bytes(b"keep")
        with self.assertRaises(RuntimeError):
            clean.discover(self.repo, [])
        self.assertTrue((packages / "private.p12").exists())

    def test_xcode_cache_workspace_must_belong_to_this_project(self):
        derived = self.base / "DerivedData"
        for name, workspace in (("ours", self.repo / "iosApp/iosApp.xcodeproj/project.xcworkspace"), ("other", self.outside / "project.xcworkspace")):
            folder = derived / name
            folder.mkdir(parents=True)
            with (folder / "info.plist").open("wb") as f:
                plistlib.dump({"WorkspacePath": str(workspace)}, f)
        self.assertEqual([derived / "ours"], clean.discover(self.repo, [], derived))

    def test_build_and_live_output_processes_rejected_but_idle_daemon_allowed(self):
        for command in ("java GradleWrapperMain", "xcodebuild -project app", str(self.repo / "build/verified/app")):
            with patch.object(clean, "processes", return_value=[(42, command)]):
                with self.assertRaises(RuntimeError):
                    clean.check_busy([self.repo / "build"])
        with patch.object(clean, "processes", return_value=[(42, "java org.gradle.launcher.daemon.bootstrap.GradleDaemon")]):
            clean.check_busy([self.repo / "build"])
        with patch.object(clean, "processes", return_value=[(42, str(self.repo / "build.init.gradle"))]):
            clean.check_busy([self.repo / "build"])

    def test_build_wrapper_disables_output_cache_cleans_after_failure_and_preserves_exit_code(self):
        scripts = self.repo / "scripts"
        scripts.mkdir()
        wrapper = scripts / "build-and-clean.sh"
        shutil.copyfile(Path(__file__).with_name("build-and-clean.sh"), wrapper)
        fake_gradle = self.repo / "gradlew"
        fake_gradle.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "$BOARDERLESS_TEST_ARG_FILE"\nexit 7\n')
        fake_gradle.chmod(0o700)
        tools = self.base / "tools"
        tools.mkdir()
        python = tools / "python3"
        python.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "$BOARDERLESS_TEST_CLEAN_FILE"\n')
        python.chmod(0o700)
        env = dict(os.environ, PATH=str(tools) + os.pathsep + os.environ.get("PATH", ""),
                   BOARDERLESS_TEST_ARG_FILE=str(self.base / "args"), BOARDERLESS_TEST_CLEAN_FILE=str(self.base / "cleanup"))
        result = subprocess.run(["/bin/bash", str(wrapper), ":shared:jvmTest"], env=env, capture_output=True)
        self.assertEqual(7, result.returncode)
        self.assertEqual(["--no-build-cache", ":shared:jvmTest"], (self.base / "args").read_text().splitlines())
        self.assertEqual([str(scripts / "clean-build-artifacts.py"), "--apply"], (self.base / "cleanup").read_text().splitlines())


if __name__ == "__main__":
    unittest.main()
