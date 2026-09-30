#!/bin/sh

set -eu

is_java_home() {
  [ -n "$1" ] && [ -x "$1/bin/java" ] && "$1/bin/java" -version >/dev/null 2>&1
}

if is_java_home "${JAVA_HOME:-}"; then
  printf '%s\n' "$JAVA_HOME"
  exit 0
fi

system_java_home=$(/usr/libexec/java_home 2>/dev/null || true)
if is_java_home "$system_java_home"; then
  printf '%s\n' "$system_java_home"
  exit 0
fi

for candidate in \
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" \
  "/Applications/JetBrains Toolbox.app/Contents/jre/Contents/Home"
do
  if is_java_home "$candidate"; then
    printf '%s\n' "$candidate"
    exit 0
  fi
done

java_command=$(command -v java 2>/dev/null || true)
if [ -n "$java_command" ] && "$java_command" -version >/dev/null 2>&1; then
  java_bin_dir=$(CDPATH= cd -- "$(dirname "$java_command")" && pwd)
  printf '%s\n' "$(dirname "$java_bin_dir")"
  exit 0
fi

printf '%s\n' \
  "BoarderLess could not find a JDK. Install JDK 17+ or Android Studio, then run the iOS app again." >&2
exit 1
