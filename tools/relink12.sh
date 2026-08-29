#!/data/data/com.termux/files/usr/bin/env bash
set -e
ROOT=/data/data/com.termux/files/usr/tmp
NDK=$ROOT/opencode/android-ndk-r25c
SYSROOT=$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android
CXX=/data/data/com.termux/files/usr/bin/c++
L=libc++_static.a
ABI=libc++abi.a
WRAP="-Wl,--wrap=__assert2 -Wl,--wrap=__assert_fail"
SHIM=$ROOT/opencode/shims/assert_shim.o

relink_cxx() {
    # $1 output, $2... objects
    local out="$1"; shift
    $CXX -fPIC -O3 -shared -nostdlib++ -o "$out" "$@" "$SHIM" \
        $WRAP \
        -Wl,--whole-archive "$SYSROOT/$L" -Wl,--no-whole-archive \
        "$SYSROOT/$ABI" \
        -ldl -lm -pthread
}

# amxmodx core
relink_cxx $ROOT/amxx-bld/build/amxmodx/amxmodx/amxmodx.so.plt \
    $ROOT/amxx-bld/build/amxmodx/amxmodx/*.o \
    $ROOT/amxx-bld/build/third_party/hashing/hashinglib/*.o \
    $ROOT/amxx-bld/build/third_party/zlib/zlib/*.o \
    $ROOT/amxx-bld/build/third_party/utf8rewind/utf8rewind/*.o \
    $ROOT/opencode/shims/libcpp_verbose_abort.o

# modules
declare -A MODS=(
  [cstrike]=cstrike/cstrike/cstrike
  [csx]=cstrike/csx/csx
  [engine]=engine/engine
  [fakemeta]=fakemeta/fakemeta
  [fun]=fun/fun
  [geoip]=geoip/geoip
  [json]=json/json
  [nvault]=nvault/nvault
  [regex]=regex/regex
  [sockets]=sockets/sockets
  [sqlite]=sqlite/sqlite
)
for name in "${!MODS[@]}"; do
    d=$ROOT/amxx-bld/build/modules/${MODS[$name]}
    relink_cxx "$d/$name.so" "$d"/*.o
done

# metamod
MM=$ROOT/amxx-bld/mm-fwgs/build-aarch64/metamod
relink_cxx $MM/libmetamod_android_arm64.so $MM/CMakeFiles/metamod.dir/src/*.o $MM/../library-suffix/liblibrary_suffix.a $ROOT/opencode/shims/libcpp_verbose_abort.o

echo ALL_RELINKED