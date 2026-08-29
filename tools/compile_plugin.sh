#!/data/data/com.termux/files/usr/bin/env bash
# Compile a Pawn plugin with the 64-bit-cell amxxpc and deploy it to the
# installed game's plugins dir.
#
# Sources normally live in the game's own scripting/ dir:
#   /storage/emulated/0/xash/cstrike/addons/amxmodx/scripting/
#
# Usage:
#   compile_plugin.sh <plugin.sma> [plugin_name.amxx]
#   compile_plugin.sh hello          (no extension -> hello.sma from scripting/)
#
# Installs the .amxx into addons/amxmodx/plugins/ and appends it to
# configs/plugins.ini if not already listed.
set -e

ROOT=/data/data/com.termux/files/usr/tmp/amxx-bld
STORE=/storage/emulated/0/xash/cstrike/addons/amxmodx
SCRIPTS="$STORE/scripting"

SMA="$1"
# strip extension so "hello" == "hello.sma"
case "$SMA" in *.sma) : ;; *) SMA="$SMA.sma" ;; esac

if [ -f "$SMA" ]; then
  SMA="$(readlink -f "$SMA")"
elif [ -f "$SCRIPTS/$SMA" ]; then
  SMA="$SCRIPTS/$SMA"
else
  echo "no such file: $1 (also tried $SCRIPTS/$1)"
  exit 2
fi

OUT="$2"
if [ -z "$OUT" ]; then
  OUT="$(basename "${SMA%.sma}").amxx"
fi

PLUG="$ROOT/build/plugins/amxxpc"
# prefer the include set shipped with the installed game (same fork as the core)
INC="$SCRIPTS/include"
[ -d "$INC" ] || INC="$ROOT/src/amxmodx/plugins/include"

PDIR="$STORE/plugins"
CONF="$STORE/configs/plugins.ini"

echo ">> compiling $SMA -> $OUT"
cd "$ROOT/build/plugins"
"$PLUG" -i"$INC" -h "$SMA" -o"$OUT"

echo ">> installing $OUT"
cp "$OUT" "$PDIR/$OUT"
grep -qxF "$(basename "$OUT")" "$CONF" || printf '\n%s\n' "$(basename "$OUT")" >> "$CONF"

echo ">> done. installed:"
ls -l "$PDIR/$OUT"
echo "Restart the game (map change also works: 'amx_plugins' in console)."