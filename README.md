# Xash3D AMXX for Android (aarch64)

Runs **AMX Mod X 1.10.0-manual** inside the **CS16Client (Xash3D FWGS)** Android build by
injecting a **Metamod** runtime, then building AMXX as a metamod *metaplugin*.

Everything here was built and verified on a real aarch64 Android device (Termux + NDK r25c).
No Android Studio, no Gradle: the engine APK is repacked directly with `python3`/`zipalign`/`apksigner`.

## Why 64-bit cells

Official AMXX ships **32-bit cells** (`PAWN_CELL_SIZE=32`). That is fine on 32-bit CPUs, but on
aarch64 pointers are 8 bytes and don't fit in a 4-byte cell. `amx_BrowseRelocate` writes the
relocated opcode *(a function pointer)* into each code cell, so the bytecode **must** be built so
that `sizeof(cell) == sizeof(void*)`. This tree therefore **forces 64-bit cells** everywhere:
core, all modules, the compiler and all shipped plugins. 32-bit `.amxx` files are rejected at load
(clean "section not found", never a crash).

## Repository layout

```
amxmodx/          patched AMX Mod X 1.10.0-manual source (64-bit cell build)
hlsdk/            Half-Life SDK headers (needed to compile AMXX)
metamod-hl1/      classic Metamod 1.19 headers (AMXX-style metaplugin ABI)
metamod-fwgs/     vendored FWGS/metamod-fwgs source (@ d80b2fe, unmodified)
tools/            relink + repack scripts, assert shim sources
```

## How the pieces fit

1. **Engine APK** (`su.xash.engine.test`) is the unmodified Xash3D FWGS CS16Client build.
   Android extracts `lib/<abi>/lib*.so` entries into the app native dir.
2. **Metamod** is `libyapb_android_arm64.so` — a metamod-fwgs build whose `GiveFnptrsToDll` hook
   is wired through the game DLL. It is *not* loaded by the engine; it loads the game DLL itself
   and transparently forwards engine calls.
3. **AMXX core** `libamxmodx.so` + the 11 module `.so` files are loaded by metamod as a
   *metaplugin* (`addons/amxmodx/configs/metamod/plugins.ini`, paths unquoted).
4. All AMXX libs are **relinked** with static `libc++` (Android has no `libstdc++.so`) and a
   link-time **`--wrap=__assert2/__assert_fail`** shim so the engine never aborts on a Pawn
   `assert()`; the shim logs to `/storage/emulated/0/xash/assert.txt` and continues.

## Storage layout (installed app)

```
/storage/emulated/0/xash/cstrike/addons/amxmodx/
  configs/plugins.ini      plugin list (one name per line, no paths)
  plugins/*.amxx           64-bit-cell plugins
  data/lang/*.txt          multilingual dictionaries
  data/gamedata/           game configs (see Known issues)
  logs/                    amxmodx + error logs
```

CDLL launch: CS16Client runs `cstrike/addons/metamod/metamod.ini` configured with
`gamedll "addons/amxmodx/libyapb_android_arm64.so"`.

## Building (Termux)

```bash
# 1. ambuild the patched source (compiler, core, modules, plugins)
cd build && ambuild --no-color

# 2. relink core + 11 modules + metamod with static libc++ and the assert shim
bash ../tools/relink12.sh        # produces *.so.plt / module .so with wraps

# 3. repack the engine APK (python-only; keeps per-entry compression like the reference APK)
python3 tools/pack13.py          # -> CS16Client-AMXX13-unsigned.apk

# 4. align + sign (create your own key first!)
keytool -genkey -keystore debug.keystore -alias androiddebugkey -storepass android \
        -keypass android -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -validity 10000
zipalign -f -p 4 CS16Client-AMXX13-unsigned.apk CS16Client-AMXX13-aligned.apk
apksigner sign --ks debug.keystore --ks-key-alias androiddebugkey \
        --ks-pass pass:android --key-pass pass:android \
        --out CS16Client-AMXX13-signed.apk CS16Client-AMXX13-aligned.apk
```

Prerequisites: AMBuild 2 (`pip install ambuild2`), Android NDK r25c, `python3`, `zipalign`,
`apksigner`, a `aarch64-linux-android-strip` binary, and a reference APK named `REF.apk`
(pack scripts copy its compression map so Android tooling stays happy).

## Known issues

- **SMC parser (gamedata)**: `ParseStream_SMCE` crashes for files larger than the 4 KiB internal
  buffer (`amxmodx/amxmodx/CTextParsers.cpp`, multi-chunk relocation path). Bypassed by shipping
  an empty 81-byte `data/gamedata/common.games/master.games.txt` (original backed up as `.bak`).
  The real parser fix is future work.
- **adminslots.amxx** logs one `Invalid CVAR pointer` / runtime error at map start (cvar creation
  timing); gameplay and admin features are unaffected.
- **Second launch black screen**: occasionally the app relaunch produces no `engine.log` writes at
  all — that failure happens in the Android/SDL layer *before* the native engine starts, unrelated
  to metamod/AMXX. Swipe the app away and relaunch.
- `libvgui_support.so` is missing from the stock APK too; the engine warning is benign.