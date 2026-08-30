#!/usr/bin/env bash
#
# Cross-compiles the CS16Client AMXX core + modules for android arm64 using the
# Android NDK. Replicates, flag-for-flag, the hand-tuned build that was validated
# on-device (see the project notes). Produces:
#   $OUT/lib/arm64-v8a/libamxmodx.so
#   $OUT/lib/arm64-v8a/lib<name>_amxx_amd64.so   (11 modules)
#   $OUT/plugins/*.amxx                          (compiled from ./plugins-src, if any)
#
#   usage: ci/build-amxx.sh <amxx-src-root> <ndk-root> <out-dir> [plugins-src]
#
set -euo pipefail

SBIN=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SRC=$1
NDK=$2
OUT=$3
PLUGINS_SRC=${4:-}

HOST=$(uname -s | tr 'A-Z' 'a-z')
if [ "$HOST" = darwin ]; then HOST=mac; fi

# The fork (berkchy/cs16client-amxx@amxx-addons) omits stable public sources
# (MemoryUtils, CDetour, resdk, various headers) — they are gitignored there.
# Vendor copies live under modsrc-amxx/ inside this repo.
REPO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
VENDOR="$REPO_ROOT/modsrc-amxx"
if [ -d "$VENDOR/public/memtools" ]; then
  mkdir -p "$SRC/amxmodx/public/memtools" "$SRC/amxmodx/public/resdk" "$SRC/amxmodx/public/amtl" "$SRC/amxmodx/public/sdk"
  cp -r "$VENDOR/public/memtools/"* "$SRC/amxmodx/public/memtools/"
  cp -r "$VENDOR/public/resdk/"* "$SRC/amxmodx/public/resdk/"
  cp -r "$VENDOR/public/amtl/"* "$SRC/amxmodx/public/amtl/"
  cp -r "$VENDOR/public/sdk/"* "$SRC/amxmodx/public/sdk/"
fi
for f in "$VENDOR/public/"*.h; do
  [ -f "$f" ] && cp -f "$f" "$SRC/amxmodx/public/$(basename "$f")"
done
for d in common dlls engine game_shared public pm_shared; do
  if [ -d "$VENDOR/hlsdk/$d" ]; then
    mkdir -p "$SRC/hlsdk/$d"
    cp -r "$VENDOR/hlsdk/$d/"* "$SRC/hlsdk/$d/"
  fi
done
if [ -d "$VENDOR/metamod-hl1/metamod" ]; then
  mkdir -p "$SRC/metamod-hl1/metamod"
  cp -r "$VENDOR/metamod-hl1/metamod/"* "$SRC/metamod-hl1/metamod/"
fi
find "$VENDOR/modules" -name 'moduleconfig.h' 2>/dev/null | while read f; do
  rel="${f#$VENDOR/modules/}"
  mkdir -p "$(dirname "$SRC/amxmodx/modules/$rel")"
  cp -f "$f" "$SRC/amxmodx/modules/$rel"
done
if [ -d "$VENDOR/modules" ]; then
  find "$VENDOR/modules" -maxdepth 1 -type d | while read d; do
    modname=$(basename "$d")
    [ "$modname" = "modules" ] && continue
    mkdir -p "$SRC/amxmodx/modules/$modname"
    cp -r "$VENDOR/modules/$modname/"* "$SRC/amxmodx/modules/$modname/" 2>/dev/null
  done
fi
if [ -d "$VENDOR/third_party" ]; then
  mkdir -p "$SRC/amxmodx/third_party"
  cp -r "$VENDOR/third_party/"* "$SRC/amxmodx/third_party/"
fi
for d in compiler amxmodx; do
  if [ -d "$VENDOR/$d" ]; then
    mkdir -p "$SRC/amxmodx/$d"
    cp -r "$VENDOR/$d/"* "$SRC/amxmodx/$d/"
  fi
done

TC=$NDK/toolchains/llvm/prebuilt/$HOST-x86_64/bin
TARGET=aarch64-linux-android24
CC=$TC/$TARGET-clang
CXX=$TC/$TARGET-clang++
SYSROOT_LIB=$NDK/toolchains/llvm/prebuilt/$HOST-x86_64/sysroot/usr/lib/aarch64-linux-android

AMXX=$SRC/amxmodx
mkdir -p "$OUT/lib/arm64-v8a" "$OUT/plugins"

# ---------------------------------------------------------------- flags
DEFS=(
  -Dstricmp=strcasecmp
  -Dstrnicmp=strncasecmp
  -DAMX_NOPROPLIST
  -DPAWN_CELL_SIZE=64
  -DAMXMODX_BUILD
  -DAMXX_USE_VERSIONLIB
  -DHAVE_STDINT_H
  -DHAVE_I64
  -D_snprintf=snprintf
  -D__BYTE_ORDER=__LITTLE_ENDIAN
)
FLAGS=(
  -fPIC -O2 -fno-strict-aliasing -Wall -Wno-uninitialized -Wno-unused
  -Wno-switch -Wno-format -Wno-format-security -fsigned-char -fvisibility=hidden
)
CXXFLAGS=(
  -Wno-narrowing -Wno-invalid-offsetof -std=c++14 -fvisibility-inlines-hidden
  -Wno-delete-non-virtual-dtor -Wno-implicit-exception-spec-mismatch
  -Wno-tautological-compare -Wno-deprecated-register -fno-exceptions -fno-rtti
)
INC=(
  -I"$AMXX/public" -I"$AMXX/public/sdk" -I"$AMXX/public/amtl"
  -I"$AMXX/public/memtools" -I"$AMXX/public/resdk"
  -I"$AMXX/third_party" -I"$AMXX/third_party/hashing" -I"$AMXX/third_party/zlib"
  -I"$AMXX/third_party/sqlite"   -I"$AMXX/third_party/utf8rewind"
  -I"$AMXX/amxmodx"
  -I"$SRC/metamod-hl1/metamod"
  -I"$SRC/hlsdk/common" -I"$SRC/hlsdk/dlls" -I"$SRC/hlsdk/engine"
  -I"$SRC/hlsdk/game_shared" -I"$SRC/hlsdk/public" -I"$SRC/hlsdk/pm_shared"
)

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cmd_shim="$TMP/assert_shim.o"
"$CC" -O2 -fPIC -c "$SBIN/assert_shim.c" -o "$cmd_shim"

compile_one() {
  local stage="$1" file="$2" extra_inc="$3" extra_defs="$4"
  local base obj
  base=$(basename "$file")
  obj="$TMP/$stage/$base.o"
  mkdir -p "$(dirname "$obj")"
  if [[ "$file" == *.c ]]; then
    "$CC" "${FLAGS[@]}" "${INC[@]}" $extra_inc "${DEFS[@]}" $extra_defs -c "$file" -o "$obj"
  else
    "$CXX" "${FLAGS[@]}" "${CXXFLAGS[@]}" "${INC[@]}" $extra_inc "${DEFS[@]}" $extra_defs -c "$file" -o "$obj"
  fi
}

relink() {
  local out="$1"; shift
  "$CXX" -fPIC -O2 -shared -nostdlib++ -o "$out" "$@" "$cmd_shim" \
    -Wl,--wrap=__assert2 -Wl,--wrap=__assert_fail \
    -Wl,--whole-archive "$SYSROOT_LIB/libc++_static.a" -Wl,--no-whole-archive \
    "$SYSROOT_LIB/libc++abi.a" -ldl -lm -pthread
}

# ------------------------------------------------------------------- core
echo "== building core =="
for f in "$AMXX/amxmodx"/*.c "$AMXX/amxmodx"/*.cpp; do
  [ -e "$f" ] || continue
  compile_one core "$f" "" ""
done
compile_one core "$AMXX/public/memtools/MemoryUtils.cpp" "" ""
compile_one core "$AMXX/public/memtools/CDetour/detours.cpp" "" ""
compile_one core "$AMXX/public/memtools/CDetour/asm/asm.c" "" ""
compile_one core "$AMXX/public/resdk/mod_rehlds_api.cpp" "" ""
for f in "$AMXX/third_party/hashing/hashinglib/"*.{c,cpp}; do
  [ -e "$f" ] || continue
  compile_one core "$f" "" ""
done
for f in "$AMXX/third_party/zlib/"*.c; do
  [ -e "$f" ] || continue
  compile_one core "$f" "" ""
done
for f in "$AMXX/third_party/utf8rewind/utf8rewind/"*.cpp; do
  [ -e "$f" ] || continue
  compile_one core "$f" "" ""
done

relink "$OUT/lib/arm64-v8a/libamxmodx.so" "$TMP"/core/*.o
echo "   core -> $(ls -l "$OUT/lib/arm64-v8a/libamxmodx.so" | awk '{print $5}') bytes"

# ------------------------------------------------------------------ pcre
# regex module needs a static arm64 pcre; the repo only ships linux/mac/win
# prebuilts, so build it here (deterministic, source-based).
PCRE_VER=8.45
if [[ ! -f "$TMP/libpcre.a" ]]; then
  echo "== building pcre $PCRE_VER (arm64) =="
  curl -fsSL "https://downloads.sourceforge.net/project/pcre/pcre/$PCRE_VER/pcre-$PCRE_VER.tar.gz" -o "$TMP/pcre.tar.gz"
  tar -xzf "$TMP/pcre.tar.gz" -C "$TMP"
  (
    cd "$TMP/pcre-$PCRE_VER"
    CC="$CC" CXX="$CXX" CFLAGS="-O2 -fPIC" CXXFLAGS="-O2 -fPIC" \
      ./configure --host=aarch64-linux-android --disable-shared --enable-static \
      --enable-utf8 --enable-unicode-properties --disable-cpp --prefix="$TMP/pcre-inst" \
      >/dev/null
    make -j"$(nproc)" >/dev/null
    make install >/dev/null
  )
fi
PCRE_A="$TMP/pcre-inst/lib/libpcre.a"

# ------------------------------------------------------------- metamod
echo "== building metamod =="
METAMOD="$SRC/metamod-hl1/metamod"
for f in "$METAMOD"/*.cpp; do
  [ -e "$f" ] || continue
  compile_one metamod "$f" "" ""
done
relink "$OUT/lib/arm64-v8a/libmetamod.so" "$TMP"/metamod/*.o
echo "   metamod -> $(ls -l "$OUT/lib/arm64-v8a/libmetamod.so" | awk '{print $5}') bytes"

# ----------------------------------------------------------------- modules
build_module() {
  local name="$1" modsub="$2" extra_inc="$3" extra_defs="$4"
  shift 4
  echo "== building module $name =="
  local M="$AMXX/modules/$modsub"
  local MOD_INC="-I$M"
  for f in "$@"; do
    if [[ "$f" == /* ]]; then
      compile_one "mod-$name" "$f" "$MOD_INC $extra_inc" "$extra_defs"
    else
      compile_one "mod-$name" "$M/$f" "$MOD_INC $extra_inc" "$extra_defs"
    fi
  done
  relink "$OUT/lib/arm64-v8a/lib$name"_amxx_amd64.so "$TMP/mod-$name"/*.o
  echo "   $name -> $(ls -l "$OUT/lib/arm64-v8a/lib$name"_amxx_amd64.so | awk '{print $5}') bytes"
}

P="$AMXX/public"
SDK="$AMXX/public/sdk"

build_module engine engine "" "" \
  "$SDK/amxxmodule.cpp" "amxxapi.cpp" "engine.cpp" "entity.cpp" "globals.cpp" \
  "forwards.cpp" "$P/memtools/MemoryUtils.cpp" "$P/memtools/CDetour/detours.cpp" \
  "$P/memtools/CDetour/asm/asm.c"

build_module fakemeta fakemeta "" "" \
  "$SDK/amxxmodule.cpp" "$P/memtools/MemoryUtils.cpp" "$P/resdk/mod_regamedll_api.cpp" \
  "dllfunc.cpp" "engfunc.cpp" "fakemeta_amxx.cpp" "pdata.cpp" "pdata_entities.cpp" \
  "pdata_gamerules.cpp" "forward.cpp" "fm_tr.cpp" "pev.cpp" "glb.cpp" "fm_tr2.cpp" "misc.cpp"

build_module fun fun "" "" \
  "$SDK/amxxmodule.cpp" "$P/memtools/MemoryUtils.cpp" "fun.cpp"

build_module geoip geoip \
  "-I$AMXX/amxmodx -I$AMXX/third_party/libmaxminddb" "" \
  "$SDK/amxxmodule.cpp" "$AMXX/third_party/libmaxminddb/data-pool.c" \
  "$AMXX/third_party/libmaxminddb/maxminddb.c" "geoip_main.cpp" "geoip_natives.cpp" \
  "geoip_util.cpp"

build_module json json "-I$AMXX/third_party/parson" "" \
  "$SDK/amxxmodule.cpp" "$AMXX/third_party/parson/parson.c" "JsonMngr.cpp" "JsonNatives.cpp"

build_module nvault nvault "" "" \
  "$SDK/amxxmodule.cpp" "amxxapi.cpp" "Binary.cpp" "Journal.cpp" "NVault.cpp"

build_module regex regex "" "-DPCRE_STATIC" \
  "$SDK/amxxmodule.cpp" "module.cpp" "CRegEx.cpp" "utils.cpp"

build_module sockets sockets "" "" \
  "$SDK/amxxmodule.cpp" "sockets.cpp"

build_module sqlite sqlite \
  "-I$AMXX/modules/sqlite/sqlitepp -I$AMXX/modules/sqlite/thread -I$AMXX/third_party/sqlite" \
  "-DSM_DEFAULT_THREADER" \
  "basic_sql.cpp" "handles.cpp" "module.cpp" "threading.cpp" "$SDK/amxxmodule.cpp" \
  "oldcompat_sql.cpp" "thread/BaseWorker.cpp" "thread/ThreadWorker.cpp" \
  "sqlitepp/SqliteQuery.cpp" "sqlitepp/SqliteResultSet.cpp" \
  "sqlitepp/SqliteDatabase.cpp" "sqlitepp/SqliteDriver.cpp" \
  "$AMXX/third_party/sqlite/sqlite3.c"

build_module cstrike cstrike/cstrike "" "" \
  "$SDK/amxxmodule.cpp" "CstrikeMain.cpp" "CstrikePlayer.cpp" "CstrikeNatives.cpp" \
  "CstrikeHacks.cpp" "CstrikeUtils.cpp" "CstrikeUserMessages.cpp" "CstrikeItemsInfos.cpp" \
  "$P/memtools/MemoryUtils.cpp" "$P/memtools/CDetour/detours.cpp" \
  "$P/memtools/CDetour/asm/asm.c" "$P/resdk/mod_rehlds_api.cpp" "$P/resdk/mod_regamedll_api.cpp"

build_module csx cstrike/csx "" "" \
  "$SDK/amxxmodule.cpp" "CRank.cpp" "CMisc.cpp" "meta_api.cpp" "rank.cpp" "usermsg.cpp"

# link regex against freshly built pcre
"$CXX" -fPIC -O2 -shared -nostdlib++ -o "$OUT/lib/arm64-v8a/libregex_amxx_amd64.so" \
  "$TMP"/mod-regex/*.o "$cmd_shim" "$PCRE_A" \
  -Wl,--wrap=__assert2 -Wl,--wrap=__assert_fail \
  -Wl,--whole-archive "$SYSROOT_LIB/libc++_static.a" -Wl,--no-whole-archive \
  "$SYSROOT_LIB/libc++abi.a" -ldl -lm -pthread

# ----------------------------------------------------------------- plugins
echo "== building host pawncc =="
PAWNCC_OK=false
if [ -f "$AMXX/compiler/libpc300/CMakeLists.txt" ]; then
  LIBPC_BUILD="$TMP/libpc300"
  mkdir -p "$LIBPC_BUILD" && cd "$LIBPC_BUILD"
  cmake "$AMXX/compiler/libpc300" -DCMAKE_BUILD_TYPE=Release >/dev/null 2>&1 && \
    make -j"$(nproc)" >/dev/null 2>&1 && PAWNCC_OK=true
fi
if [ "$PAWNCC_OK" = true ]; then
  AMXXPC_SRC="$AMXX/compiler/amxxpc"
  g++ -O2 -std=c++14 -I"$AMXX/compiler/libpc300" -I"$AMXXPC_SRC" \
    -o "$TMP/amxxpc" "$AMXXPC_SRC"/amxxpc.cpp "$AMXXPC_SRC"/Binary.cpp \
    "$LIBPC_BUILD/libpawnc.so" 2>/dev/null && AMXXPC="$TMP/amxxpc" || PAWNCC_OK=false
fi
if [ "$PAWNCC_OK" = false ]; then
  echo "   pawncc build failed, skipping plugin compilation"
  AMXXPC=""
fi

if [[ -n "$PLUGINS_SRC" && -d "$PLUGINS_SRC" && -n "$AMXXPC" ]]; then
  echo "== compiling plugins =="
  for f in "$PLUGINS_SRC"/*.sma; do
    [ -e "$f" ] || continue
    echo "   $(basename "$f")"
    "$AMXXPC" -i"$AMXX/plugins/include" -i"$PLUGINS_SRC/include" -o"$OUT/plugins/$(basename "${f%.sma}.amxx")" "$f" >/dev/null
  done
fi

echo "ALL_BUILT"
ls -l "$OUT/lib/arm64-v8a/" "$OUT/plugins"/