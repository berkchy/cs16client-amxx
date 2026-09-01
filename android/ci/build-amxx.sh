#!/usr/bin/env bash
#
# Cross-compiles the CS16Client AMXX core + modules for android arm64 using the
# Android NDK. Sources:
#   - alliedmodders/amxmodx@master   (rolling 1.10, fetched from upstream)
#   - android/mm-p                    (vendored Bots-United/metamod-p)
#   - android/hlsdk                   (vendored HLSDK)
#
# All build customizations live as patch files under <repo>/patches/ and are
# applied here. hlsdk + metamod-p are vendored into the repository (git
# submodules cannot be used since the linked repos are not under this account).
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

# vendored_from <src-dir> <dst-dir>: copy a vendored tree from the repo and
# turn it into a git repo so apply_patch() (git apply) works on it.
vendored_from() {
  local src=$1 dst=$2
  if [ ! -d "$dst/.git" ]; then
    echo "== vendoring $src -> $dst =="
    mkdir -p "$dst"
    cp -R "$src/." "$dst/"
    (cd "$dst" && git init -q && git add -A && git -c user.name=ci -c user.email=ci@ci commit -q -m sourced)
  fi
}
# metamod-p stays only as the header source used to compile the AMXX core
# (its meta_api.h ABI suffices); the actual runtime gamemod is metamod-fwgs.
vendored_from "$REPO_ROOT/android/mm-p" "$SRC/metamod-p"
# Runtime metamod: FWGS/metamod-fwgs (CMake), Xash3D-explicit, produces
# libmetamod_android_arm64.so.
fetch metamod-fwgs "https://github.com/FWGS/metamod-fwgs.git" yes

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
apply_patch "$PATCHES/amxmodx-libpawnc-console.patch"      "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-android-load-CModule.patch" "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-android-load-modules.patch" "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-CDetour-cell.diff"          "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-64bit-cell-casts.diff"       "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-memtools-dlfcn.diff"         "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-CTextParsers-quote-underrun.diff" "$SRC/amxmodx"
apply_patch "$PATCHES/amxmodx-amtl-64bit.diff"             "$SRC/amxmodx" "public/amtl"
# AMXX core is still compiled against metamod-p's meta_api.h (METAMOD above),
# which requires this ARM64 shim (cs16_amxx_compat.h + const SET_LOCALINFO).
apply_patch "$PATCHES/metamod-p-aarch64.patch"            "$SRC/metamod-p"
apply_patch "$PATCHES/metamod-fwgs-android.patch"          "$SRC/metamod-fwgs"
# Android native lib: also try libamxxpc32.so (APK lib prefix) when driver is libamxxpc.so
if [ -f "$SRC/amxmodx/compiler/amxxpc/amxxpc.cpp" ]; then
  python3 - "$SRC" <<'PYEOF' || true
import sys, os
src = sys.argv[1]
p = os.path.join(src, "amxmodx/compiler/amxxpc/amxxpc.cpp")
data = open(p, encoding="utf-8").read()
old = '\tHINSTANCE lib = NULL;\n\tdlopen("libm.so", RTLD_NOW | RTLD_GLOBAL);\n\tif (FileExists("./amxxpc32.so"))\n\t\tlib = dlmount("./amxxpc32.so");\n\telse\n\t\tlib = dlmount("amxxpc32.so");'
new = (
    '\tHINSTANCE lib = NULL;\n'
    '\tdlopen("libm.so", RTLD_NOW | RTLD_GLOBAL);\n'
    '\t{\n'
    '\t\t/* Android: resolve library path relative to this binary */\n'
    '\t\tchar selfpath[4096] = "./";\n'
    '\t\tssize_t len = readlink("/proc/self/exe", selfpath, sizeof(selfpath) - 1);\n'
    '\t\tif (len > 0) {\n'
    '\t\t\tselfpath[len] = \'\\0\';\n'
    '\t\t\tfor (int i = len - 1; i > 0; i--)\n'
    '\t\t\t\tif (selfpath[i] == \'/\') { selfpath[i] = \'\\0\'; break; }\n'
    '\t\t}\n'
    '\t\t/* Try: <bindir>/libamxxpc32.so, <bindir>/amxxpc32.so, ./libamxxpc32.so, ./amxxpc32.so */\n'
    '\t\tchar fullpath[4096];\n'
    '\t\tsnprintf(fullpath, sizeof(fullpath), "%s/libamxxpc32.so", selfpath);\n'
    '\t\tif (FileExists(fullpath))\n'
    '\t\t\tlib = dlmount(fullpath);\n'
    '\t\telse {\n'
    '\t\t\tsnprintf(fullpath, sizeof(fullpath), "%s/amxxpc32.so", selfpath);\n'
    '\t\t\tif (FileExists(fullpath))\n'
    '\t\t\t\tlib = dlmount(fullpath);\n'
    '\t\t\telse if (FileExists("./libamxxpc32.so"))\n'
    '\t\t\t\tlib = dlmount("./libamxxpc32.so");\n'
    '\t\t\telse if (FileExists("./amxxpc32.so"))\n'
    '\t\t\t\tlib = dlmount("./amxxpc32.so");\n'
    '\t\t\telse\n'
    '\t\t\t\tlib = dlmount("amxxpc32.so");\n'
    '\t\t}\n'
    '\t}\n'
)
if old in data:
    open(p, "w", encoding="utf-8").write(data.replace(old, new))
    print("patched amxxpc for lib prefix")
else:
    if 'lib = dlmount("./amxxpc32.so");' in data and 'libamxxpc32.so' not in data:
        data = data.replace('lib = dlmount("./amxxpc32.so");', 'lib = dlmount("./amxxpc32.so");\n\telse if (FileExists("./libamxxpc32.so"))\n\t\tlib = dlmount("./libamxxpc32.so");\n\telse if (FileExists("libamxxpc32.so"))\n\t\tlib = dlmount("libamxxpc32.so");')
        open(p, "w", encoding="utf-8").write(data)
        print("patched amxxpc (fallback)")
PYEOF
fi

# MemoryUtils: skip the ELF-parsing fallback in ResolveSymbol on Android.
# dlmap->l_name is NULL/invalid on bionic, causing SIGSEGV in open().
# dlsym() is reliable on Android, so the fallback is unnecessary.
if [ -f "$SRC/amxmodx/public/memtools/MemoryUtils.cpp" ]; then
  MF="$SRC/amxmodx/public/memtools/MemoryUtils.cpp"
  if ! grep -q '__ANDROID__' "$MF"; then
    echo "   patching MemoryUtils.cpp for Android"
    python3 -c "
import sys
p = sys.argv[1]
d = open(p).read()
d = d.replace(
    'void *addr = dlsym(handle, symbol);\n\n\tif (addr)\n\t{\n\t\treturn addr;\n\t}\n\n\tstruct link_map',
    'void *addr = dlsym(handle, symbol);\n#if defined(__ANDROID__)\n\t/* On Android, dlsym is reliable.\n\t   The manual ELF fallback dereferences dlmap->l_name which\n\t   can be NULL on bionic, causing SIGSEGV. */\n\treturn addr;\n#else\n\n\tif (addr)\n\t{\n\t\treturn addr;\n\t}\n\n\tstruct link_map'
)
d = d.replace(
    '\treturn symbol_entry ? symbol_entry->address : NULL;\n\n#elif defined(__APPLE__)',
    '\treturn symbol_entry ? symbol_entry->address : NULL;\n#endif /* !__ANDROID__ */\n\treturn NULL;\n\n#elif defined(__APPLE__)'
)
open(p,'w').write(d)
print('   MemoryUtils patched for Android')
" "$MF"
  else
    echo "   MemoryUtils already patched"
  fi
fi

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

# ARM64: provide C implementations for the dynamic native helpers that are
# only available as x86/amd64 NASM assembly in the upstream source.
# These define: amxx_DynaInit, amxx_DynaMake, amxx_DynaCodesize, amxx_CpuSupport
cat > "$AMXX/amxmodx/natives-arm64.c" << 'NATIVES_EOF'
#include <stdint.h>
#include <string.h>

static void *g_gate = 0;

void amxx_DynaInit(void *ptr) {
    g_gate = ptr;
}

int amxx_DynaCodesize(void) {
    return 52;
}

typedef int (*dyna_cb_t)(int, void*, void*);

/* ARM64 trampoline (52 bytes):
 *   stp  x29, x30, [sp, #-16]!
 *   mov  x29, sp
 *   mov  x2, x1          ; params -> arg3
 *   mov  x1, x0          ; amx    -> arg2
 *   movz x0,  #id_lo16   ; patched
 *   movk x0,  #id_hi16, lsl #16
 *   movz x16, #cb_lo16   ; patched
 *   movk x16, #cb_16,  lsl #16
 *   movk x16, #cb_32,  lsl #32
 *   movk x16, #cb_48,  lsl #48
 *   blr  x16
 *   ldp  x29, x30, [sp], #16
 *   ret
 */
static const uint32_t tpl[] = {
    0xA9BE7BFD,  /* stp x29,x30,[sp,#-16]!  */
    0x910003FD,  /* mov x29, sp             */
    0xAA0103E2,  /* mov x2, x1              */
    0xAA0003E1,  /* mov x1, x0              */
    0xD2800000,  /* movz x0, #0   (id lo)   */
    0xF2A00000,  /* movk  x0, #0, lsl#16    */
    0xD2E00010,  /* movz x16, #0  (cb lo)   */
    0xF2C00010,  /* movk x16,#0, lsl#16     */
    0xF2E00010,  /* movk x16,#0, lsl#32     */
    0xF3000010,  /* movk x16,#0, lsl#48     */
    0xD63F0200,  /* blr  x16                */
    0xA8C27BFD,  /* ldp x29,x30,[sp],#16    */
    0xD65F03C0,  /* ret                     */
};

void amxx_DynaMake(char *buf, int id) {
    uint32_t code[13];
    memcpy(code, tpl, sizeof(code));
    uintptr_t cb = (uintptr_t)g_gate;
    code[4] |= ((uint32_t)(id & 0xFFFF)) << 5;
    code[5] |= ((uint32_t)((id >> 16) & 0xFFFF)) << 5;
    code[6]  |= ((uint32_t)(cb & 0xFFFF)) << 5;
    code[7]  |= ((uint32_t)((cb >> 16) & 0xFFFF)) << 5;
    code[8]  |= ((uint32_t)((cb >> 32) & 0xFFFF)) << 5;
    code[9]  |= ((uint32_t)((cb >> 48) & 0xFFFF)) << 5;
    memcpy(buf, code, sizeof(code));
}

int amxx_CpuSupport(void) {
    return 1;
}
NATIVES_EOF
echo "   created natives-arm64.c"

for f in "$AMXX/amxmodx"/*.c "$AMXX/amxmodx"/*.cpp; do
  [ -e "$f" ] || continue
  compile_one core "$f" "" ""
done
compile_one core "$AMXX/public/memtools/MemoryUtils.cpp" "" ""
compile_one core "$AMXX/public/memtools/CDetour/detours.cpp" "" ""
compile_one core "$AMXX/public/memtools/CDetour/asm/asm.c" "" ""
compile_one core "$AMXX/public/resdk/mod_rehlds_api.cpp" "" ""
for f in "$AMXX/third_party/hashing/"*.cpp "$AMXX/third_party/hashing/hashers/"*.cpp; do
  [ -e "$f" ] || continue
  compile_one core "$f" "" ""
done
for f in "$AMXX/third_party/zlib/"*.c; do
  [ -e "$f" ] || continue
  compile_one core "$f" "" ""
done
for f in "$AMXX/third_party/utf8rewind/"*.c "$AMXX/third_party/utf8rewind/internal/"*.c; do
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
# fwgs metamod (FWGS/metamod-fwgs): Xash3D-explicit, builds
# libmetamod_android_arm64.so via CMake with the NDK toolchain. Renamed to
# libmetamod.so for the bundle (the yapb alias still resolves to the same file).
echo "== building metamod (metamod-fwgs, aarch64) =="
MMBUILD=$TMP/metamod-fwgs-build
cmake -S "$SRC/metamod-fwgs" -B "$MMBUILD" \
  -GNinja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-24 \
  -DANDROID_STL=c++_static \
  -DUSE_STATIC_RUNTIME=ON \
  -DCMAKE_BUILD_TYPE=Release
cmake --build "$MMBUILD" --target metamod -j"$(nproc)"
cp "$MMBUILD/metamod/libmetamod_android_arm64.so" "$OUT/lib/arm64-v8a/libmetamod.so"
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
PC_COMMON="-std=gnu17 -O0 -fPIC -DPAWN_CELL_SIZE=64 -DHAVE_I64 -DLINUX \
  -DHAVE_UNISTD_H -DHAVE_INTTYPES_H -DHAVE_STDINT_H -DHAVE_ALLOCA_H -I$LIBPC"
# Mirror upstream AMBuilder's amxxpc32 source list exactly; NO_MAIN on every
# unit strips main()s (sc1.c, pawncc.c, prefix.c, ...), PAWNC_DLL selects the
# exported sp_Compile/LibCompile ABI, sp_symhash.c provides NewHashTable.
for s in sc1 sc2 sc3 sc4 sc5 sc6 sc7 scvars scmemfil scstate sclist sci18n \
         pawncc libpawnc prefix memfile sp_symhash; do
  f="$LIBPC/$s.c"
  [ -e "$f" ] || continue
  "$HOSTCC" $PC_COMMON -DNO_MAIN -DPAWNC_DLL -D_GNU_SOURCE \
    -c "$f" -o "$PC_BUILD/obj/$s.o"
done
"$HOSTCC" -shared -o "$PC_BUILD/amxxpc32.so" "$PC_BUILD"/obj/*.o -lm -lpthread
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
    local_out="$OUT/plugins/$(basename "${f%.sma}.amxx")"
    set +e
    out=$( ( cd "$PC_BUILD" && LC_ALL=C.UTF-8 LANG=C.UTF-8 stdbuf -oL -eL \
             "$PAWNCC" "${extra_inc[@]}" -o"$local_out" "$f" ) 2>&1 )
    rc=$?
    set -e
    if [ $rc -ne 0 ]; then
      echo "   FAILED: $f (rc=$rc)" >&2
      printf '%s\n' "$out" >&2
      # lib-only sanity (same lib, no driver): does Compile64 succeed alone?
      if command -v python3 >/dev/null 2>&1; then
        LC_ALL=C.UTF-8 python3 - "$f" "$local_out" "$AMXX/plugins/include" "$PC_BUILD" <<'PY' >&2 || true
import ctypes, os, sys
sma, outfile, inc, build = sys.argv[1:5]
os.chdir(build)
lib = ctypes.CDLL("./amxxpc32.so")
f = lib.Compile64
f.restype = ctypes.c_int
f.argtypes = [ctypes.c_int, ctypes.POINTER(ctypes.c_char_p)]
args = ["amxxpc", "-i" + inc, "-o" + outfile, sma]
argv = (ctypes.c_char_p * len(args))(*(a.encode() for a in args))
rc = f(len(args), argv)
print(f"[lib-only] Compile64 rc={rc} file_exists={os.path.exists(outfile)}", file=sys.stderr)
PY
      fi
      exit 1
    fi
    echo "   $(basename "$f") OK"
  done
fi

# ------------------------------------------------------------------- amxxpc (arm64)
# Same compiler sources as the host pawncc above, but cross-compiled for Android
# arm64 so the patcher app can compile plugins on-device straight out of the
# bundle. Layout mirrors the AMBuilder targets:
#   OUT/compiler/amxxpc          driver (amxx.cpp + amxxpc.cpp + Binary.cpp + zlib)
#   OUT/compiler/amxxpc32.so     libpc300 kernel (libpawnc + sc*), PAWN_CELL_SIZE=64
# The driver dlopens/amxxpc32.so at runtime, so both ship together. libc++ is
# linked statically (libc++_static + libc++abi, whole-archive) to avoid having to
# bundle libc++_shared.so and juggle LD_LIBRARY_PATH on-device.
echo "== building arm64 amxxpc (embedded) =="
PC_A64="$TMP/amxxpc-arm64"
rm -rf "$PC_A64"
mkdir -p "$PC_A64"
PC_A64_COMMON="-std=gnu17 -O2 -fPIC -DPAWN_CELL_SIZE=64 -DHAVE_I64 -DLINUX \
  -DHAVE_UNISTD_H -DHAVE_INTTYPES_H -DHAVE_STDINT_H -DHAVE_ALLOCA_H \
  -D__BYTE_ORDER=__LITTLE_ENDIAN -D__LITTLE_ENDIAN -I$LIBPC"
for s in sc1 sc2 sc3 sc4 sc5 sc6 sc7 scvars scmemfil scstate sclist sci18n \
         pawncc libpawnc prefix memfile sp_symhash; do
  f="$LIBPC/$s.c"
  [ -e "$f" ] || continue
  "$CC" $PC_A64_COMMON -DNO_MAIN -DPAWNC_DLL -D_GNU_SOURCE -c "$f" -o "$PC_A64/$s.o"
done
"$CXX" -shared -static-libstdc++ -o "$PC_A64/amxxpc32.so" "$PC_A64"/*.o -lm -ldl \
  -Wl,--whole-archive "$SYSROOT_LIB/libc++_static.a" -Wl,--no-whole-archive "$SYSROOT_LIB/libc++abi.a"
mkdir -p "$PC_A64/zobj"
for f in "$AMXX/third_party/zlib"/*.c; do
  [ -e "$f" ] || continue
  "$CC" -O2 -fPIC -c "$f" -o "$PC_A64/zobj/$(basename "${f%.c}").o"
done
"$CXX" -O2 -std=c++14 -DPAWN_CELL_SIZE=64 -DHAVE_I64 -DHAVE_STDINT_H \
  -DLINUX -DAMX_ANSIONLY -D__BYTE_ORDER=__LITTLE_ENDIAN -D__LITTLE_ENDIAN \
  -I"$LIBPC" -I"$AMXX/public" -I"$AMXX/compiler/amxxpc" -I"$AMXX/third_party" \
  -o "$PC_A64/amxxpc" "$AMXX/compiler/amxxpc"/amxxpc.cpp \
  "$AMXX/compiler/amxxpc"/Binary.cpp "$AMXX/compiler/amxxpc"/amx.cpp \
  "$PC_A64"/zobj/*.o \
  -static-libstdc++ -static-libgcc \
  -Wl,--whole-archive "$SYSROOT_LIB/libc++_static.a" -Wl,--no-whole-archive \
  "$SYSROOT_LIB/libc++abi.a" -ldl -lm -pthread
mkdir -p "$OUT/compiler"
cp "$PC_A64/amxxpc" "$PC_A64/amxxpc32.so" "$OUT/compiler/"
echo "   amxxpc -> $(ls -l "$OUT/compiler/amxxpc" | awk '{print $5}') bytes"

echo "ALL_BUILT"
ls -l "$OUT/lib/arm64-v8a/" "$OUT/plugins" "$OUT/compiler"