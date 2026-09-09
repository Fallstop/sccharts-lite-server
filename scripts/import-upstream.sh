#!/usr/bin/env bash
# Copies the kept plugins from the upstream checkout into modules/. Run once per upstream bump.
# Only source, generated source, service registrations and runtime resources are taken;
# Tycho/OSGi metadata (MANIFEST.MF, build.properties, plugin.xml, about.*) is left behind.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
# Existing modules carry source edits; re-importing would throw them away. Pass --force to do it anyway
# (then re-apply the edits from git history).
force=${1:-}
skip() { [ -d "$1/src" ] && [ "$force" != "--force" ]; }
up="$root/upstream"
modules="core annotations annotations.ide kexpressions kexpressions.ide kicool kicool.ide kicool.ui scg scg.klighd scl scl.ide sccharts sccharts.ide sccharts.ui simulation simulation.ide"
for m in $modules; do
  src="$up/plugins/de.cau.cs.kieler.$m"
  dst="$root/modules/$m"
  if skip "$dst"; then echo "kept $m (already imported)"; continue; fi
  mkdir -p "$dst"
  for d in src src-gen resources system; do
    [ -d "$src/$d" ] && rm -rf "$dst/$d" && cp -r "$src/$d" "$dst/$d"
  done
  if [ -d "$src/META-INF/services" ]; then
    mkdir -p "$dst/services" && rm -rf "$dst/services/"* && cp -r "$src/META-INF/services/." "$dst/services/"
  fi
done
# KLighD's language server bundle, from the KLighD checkout (kieler/KLighD, release-2025-04-3.1.0).
src="$root/upstream-klighd/plugins/de.cau.cs.kieler.klighd.lsp"
dst="$root/modules/klighd.lsp"
if skip "$dst"; then echo "kept klighd.lsp (already imported)"; else
mkdir -p "$dst"
rm -rf "$dst/src" && cp -r "$src/src" "$dst/src"
mkdir -p "$dst/services" && rm -rf "$dst/services/"* && cp -r "$src/META-INF/services/." "$dst/services/"
fi

# The language server itself is already a plain Maven module upstream.
src="$up/language-server/de.cau.cs.kieler.language.server"
dst="$root/modules/language.server"
if skip "$dst"; then echo "kept language.server (already imported)"; else
mkdir -p "$dst"
rm -rf "$dst/src" && cp -r "$src/src" "$dst/src"
mkdir -p "$dst/services" && cp -r "$src/META-INF/services/." "$dst/services/"
fi
echo "imported from $(git -C "$up" rev-parse HEAD)"
