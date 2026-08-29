#!/data/data/com.termux/files/usr/bin/env bash
# Compile a Pawn plugin with the 64-bit-cell amxxpc and deploy it to the
# installed game's plugins dir.
#
# Usage:
#   compile_plugin.sh <plugin.sma> [plugin_name.amxx]
# Installs into /storage/emulated/0/xash/cstrike/addons/amxmodx/plugins/
# and appends it to configs/plugins.ini if not already listed.
set -e

ROOT=/data/data/com.termux/files/usr/tmp/amxx-bld
SMA="$(readlink -f "$1")"
[ -n "$SMA" ] || { echo "usage: $0 <plugin.sma>"; exit 2; }
[ -f "$SMA" ] || { echo "no such file: $SMA"; exit 2; }

OUT="$2"
if [ -z "$OUT" ]; then
  OUT="$(basename "${SMA%.sma}").amxx"
fi

PLUG="$ROOT/build/plugins/amxxpc"
INC="$ROOT/src/amxmodx/plugins/include"

STORE=/storage/emulated/0/xash/cstrike/addons/amxmodx
PDIR="$STORE/plugins"
CONF="$STORE/configs/plugins.ini"

echo ">> compiling $SMA -> $OUT"
cd "$ROOT/build/plugins"
"$PLUG" -i"$INC" -h "$SMA" -o"$OUT"

echo ">> installing $OUT"
cp "$OUT" "$PDIR/$OUT"
grep -qxF "$(basename "$OUT")" "$CONF" || printf '\n%s\n' "$(basename "$OUT")" >> "$CONF"

echo ">> done. cellsize field:"
stat -c '%s bytes' "$PDIR/$OUT"
echo "Restart the game (or type 'amx_plugins' in console after a map is loaded)."