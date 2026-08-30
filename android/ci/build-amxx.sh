#!/usr/bin/env bash
#
# Cross-compiles the CS16Client AMXX core + modules for android arm64 using the
# Android NDK, from CLEAN upstream sources:
#   - alliedmodders/amxmodx@master   (rolling 1.10)
#   - Bots-United/metamod-p@master   (aarch64 port patch)
#   - FWGS/hlsdk-portable@master
#
# All build customizations live as patch files under <repo>/patches/ and are
# applied here; nothing is vendored into the repository.
#
# Produces:
#   $OUT/lib/arm64-v8a/libamxmodx.so
#   $OUT/lib/arm64-v8a/libmetamod.so                        (metamod-p, aarch64)
#   $OUT/lib/arm64-v8a/lib<name>_amxx_amd64.so              (11 modules)
#   $OUT/plugins/*.amxx                                     (64-bit cells, from plugins-src)
#
#   usage: ci/build-amxx.sh <src-root> <ndk-root> <out-dir> [plugins-src]
#
set -euo pipefail

SBIN=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SBIN/../.." && pwd)
PATCHES="$REPO_ROOT/patches"

SRC=$1
NDK=$2
OUT=$3
PLUGINS_SRC=${4:-}

AMXX_REPO=https://github.com/alliedmodders/amxmodx.git
METAMOD_REPO=https://github.com/Bots-United/metamod-p.git

mkdir -p "$SRC" "$OUT/lib/arm64-v8a" "$OUT/plugins"

# ------------------------------------------------------------------ sources
fetch() {
  local name=$1 url=$2 recurse=${3:-}
  if [ ! -d "$SRC/$name/.git" ]; then
    echo "== fetching $name =="
    if [ "$recurse" = yes ]; then
      git clone -q --depth 1 --recurse-submodules --shallow-submodules "$url" "$SRC/$name"
    else
      git clone -q --depth 1 "$url" "$SRC/$name"
    fi
    if [ -f "$SRC/$name/.gitmodules" ] && [ "$recurse" != yes ]; then
      true # extra submodules (eg. hlsdk vgui_support) are not needed
    fi
  fi
}
fetch amxmodx "$AMXX_REPO" yes
fetch metamod-p "$METAMOD_REPO"

apply_patch() {
  local patch=$1 dir=$2 subdir=${3:-}
  local target="$dir/$subdir"
  local mark="$target/.applied-$(basename "$patch")"
  if [ -f "$mark" ]; then
    echo "   patch already applied: $(basename "$patch")"
    return 0
  fi
  (cd "$target" && git apply --check "$patch")
  (cd "$target" && git apply "$patch")
  touch "$mark"
  echo "   patched: $(basename "$patch")"
}

apply_patch "$PATCHES/amxmodx-pawncc-64bit.patch"        "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-android-load-CModule.patch" "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-android-load-modules.patch" "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-CDetour-cell.diff"          "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-64bit-cell-casts.diff"       "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-memtools-dlfcn.diff"         "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-amtl-64bit.diff"             "$SRC/amxmodx" "public/amtl"
apply_patch "$PATCHES/metamod-p-aarch64.patch"            "$SRC/metamod-p"

# ----------------------------------------------------------------- toolchain
HOST=$(uname -s | tr 'A-Z' 'a-z')
if [ "$HOST" = darwin ]; then HOST=mac; fi

TC=$NDK/toolchains/llvm/prebuilt/$HOST-x86_64/bin
TARGET=aarch64-linux-android24
CC=$TC/$TARGET-clang
CXX=$TC/$TARGET-clang++
HOSTCC=${HOSTCC:-gcc}
HOSTCXX=${HOSTCXX:-g++}
SYSROOT_LIB=$NDK/toolchains/llvm/prebuilt/$HOST-x86_64/sysroot/usr/lib/aarch64-linux-android

AMXX=$SRC/amxmodx
HLSDK=$REPO_ROOT/android/hlsdk
METAMOD=$SRC/metamod-p/metamod
MMHLSDK=$SRC/metamod-p/hlsdk

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
  -I"$METAMOD"
  -I"$HLSDK/common" -I"$HLSDK/dlls" -I"$HLSDK/engine"
  -I"$HLSDK/game_shared" -I"$HLSDK/public" -I"$HLSDK/pm_shared"
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
# regex module needs a static arm64 pcre; upstream only ships linux/mac/win
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
# metamod-p: aarch64 (patches/metamod-p-aarch64.patch). Statically links libc++
# so no libc++_shared.so needs to ship in the bundle.
echo "== building metamod (metamod-p, aarch64) =="
MM_INC=(
  -I"$METAMOD"
  -I"$MMHLSDK/engine" -I"$MMHLSDK/common" -I"$MMHLSDK/pm_shared" -I"$MMHLSDK/dlls" -I"$MMHLSDK"
)
MM_DEFS=(
  -D__METAMOD_BUILD__
  -Dstricmp=strcasecmp
  -Dstrnicmp=strncasecmp
  -D_snprintf=snprintf
  -D__BYTE_ORDER=__LITTLE_ENDIAN
)
for f in \
  api_hook.cpp api_info.cpp commands_meta.cpp conf_meta.cpp dllapi.cpp \
  engine_api.cpp engineinfo.cpp game_support.cpp game_autodetect.cpp h_export.cpp \
  linkgame.cpp linkplug.cpp log_meta.cpp meta_eiface.cpp metamod.cpp mlist.cpp \
  mplayer.cpp mplugin.cpp mreg.cpp mutil.cpp osdep.cpp osdep_p.cpp \
  reg_support.cpp sdk_util.cpp studioapi.cpp support_meta.cpp vdate.cpp \
  osdep_linkent_linux.cpp osdep_detect_gamedll_linux.cpp; do
  file="$METAMOD/$f"
  [ -e "$file" ] || continue
  base=$(basename "$file")
  obj="$TMP/metamod/$base.o"
  mkdir -p "$(dirname "$obj")"
  "$CXX" "${FLAGS[@]}" -std=gnu++98 -Wno-reserved-user-defined-literal "${CXXFLAGS[@]}" \
    "${MM_INC[@]}" "${MM_DEFS[@]}" -c "$file" -o "$obj"
done
"$CXX" -fPIC -O2 -shared -static-libstdc++ -o "$OUT/lib/arm64-v8a/libmetamod.so" \
  "$TMP"/metamod/*.o -ldl -lm
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
# Host pawncc. libpc300 is compiled from source with 64-bit PAWN cells
# (PAWN_CELL_SIZE=64) and exported as Compile64; the amxxpc driver prefers
# Compile64 and writes cellsize = sizeof(cell), so plugins are 64-bit and
# loadable by the 64-bit AMXX core. NOTE: the CMakeLists of libpc300 is stale
# (missing files / cmake_minimum_required), so we compile it by hand.
echo "== building host pawncc (64-bit cells) =="
LIBPC="$AMXX/compiler/libpc300"
# -DLINUX turns on sclinux.h (stricmp/strnicmp, unistd.h) in the libpc300
# sources. Upstream has no compiler/linux/{prefix.c,prefix.h} (the fork vendored
# it), and sc1.c only pulls <prefix.h> under that same guard, so our
# amxmodx-pawncc-64bit.patch drops the prefix.h include.
PC_BUILD="$TMP/libpc300"
mkdir -p "$PC_BUILD/obj"
PC_COMMON="-std=gnu17 -O2 -fPIC -DPAWN_CELL_SIZE=64 -DHAVE_I64 -DLINUX \
  -DHAVE_UNISTD_H -DHAVE_INTTYPES_H -DHAVE_STDINT_H -DHAVE_ALLOCA_H -I$LIBPC"
for f in "$LIBPC"/sc*.c "$LIBPC"/libpawnc.c; do
  [ -e "$f" ] || continue
  extra=""
  [[ "$(basename "$f")" == sc1.c ]] && extra="-DNO_MAIN"
  "$HOSTCC" $PC_COMMON -DPAWNC_DLL $extra -c "$f" -o "$PC_BUILD/obj/$(basename "${f%.c}").o"
done
"$HOSTCC" -shared -o "$PC_BUILD/amxxpc32.so" "$PC_BUILD"/obj/*.o
cp "$PC_BUILD/amxxpc32.so" "$PC_BUILD/amxxpc.so"

# Host zlib for the amxxpc driver. amxxpc.cpp includes "zlib/zlib.h" and calls
# compress/compressBound, which upstream resolves by putting third_party/ on the
# include path (the .vcxproj does exactly that) and linking the tree's own
# (intermediate, cdecl) zlib into the binary.
Z_DIR="$AMXX/third_party/zlib"
for f in "$Z_DIR"/*.c; do
  [ -e "$f" ] || continue
  "$HOSTCC" -O2 -fPIC -c "$f" -o "$PC_BUILD/obj/zlib-$(basename "${f%.c}").o"
done

PAWNCC=""
if command -v "$HOSTCXX" >/dev/null 2>&1 || [ -x "$HOSTCXX" ]; then
  # -DPAWN_CELL_SIZE=64 + HAVE_I64 keep cell 64-bit (amx_AlignCell -> amx_Align64,
  # pointer<->cell casts in amx.cpp don't lose precision); AMX_ANSIONLY drops the
  # wide-char paths so wcslen isn't needed; LINUX pulls in sclinux.h; HAVE_STDINT_H
  # lets libpawnc skip its own int32_t typedefs; -I third_party resolves
  # "zlib/zlib.h".
  "$HOSTCXX" -O2 -std=c++14 -DPAWN_CELL_SIZE=64 -DHAVE_I64 -DHAVE_STDINT_H \
    -DLINUX -DAMX_ANSIONLY \
    -I"$LIBPC" -I"$AMXX/public" -I"$AMXX/compiler/amxxpc" -I"$AMXX/third_party" \
    -o "$PC_BUILD/amxxpc" "$AMXX/compiler/amxxpc"/amxxpc.cpp \
    "$AMXX/compiler/amxxpc"/Binary.cpp "$AMXX/compiler/amxxpc"/amx.cpp \
    "$PC_BUILD"/obj/zlib-*.o
  PAWNCC="$PC_BUILD/amxxpc"
else
  echo "   host $HOSTCXX not found, skipping plugin compilation"
fi

if [[ -n "$PAWNCC" && -n "$PLUGINS_SRC" && -d "$PLUGINS_SRC" ]]; then
  echo "== compiling plugins (64-bit cells) =="
  extra_inc=(-i"$AMXX/plugins/include")
  [ -d "$PLUGINS_SRC/include" ] && extra_inc+=(-i"$PLUGINS_SRC/include")
  for f in "$PLUGINS_SRC"/*.sma; do
    [ -e "$f" ] || continue
    echo "   $(basename "$f")"
    ( cd "$PC_BUILD" && "$PAWNCC" "${extra_inc[@]}" \
        -o"$OUT/plugins/$(basename "${f%.sma}.amxx")" "$f" >/dev/null )
    if [[ $? -ne 0 ]]; then
        echo "   FAILED: $f" >&2
        exit 1
    fi
  done
fi

echo "ALL_BUILT"
ls -l "$OUT/lib/arm64-v8a/" "$OUT/plugins"