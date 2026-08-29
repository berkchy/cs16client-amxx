# Changes vs upstream

Every change made to get AMXX 1.10.0-manual running as a **64-bit-cell** build inside
CS16Client (Xash3D FWGS) on **aarch64 Android**.

## 64-bit cell enablement

The upstream fork defaults to `PAWN_CELL_SIZE=32` and the build system injects
`-DPAWN_CELL_SIZE=32` on the command line. On aarch64 the engine aborts in
`amx_BrowseRelocate` (a function pointer cannot be stored in a 4-byte cell). All of the
following force 64-bit cells on the whole toolchain:

- `amxmodx/amxmodx/amx.h`
  - `#undef PAWN_CELL_SIZE` + `#define PAWN_CELL_SIZE 64` (overrides the `-D` on the command line)
  - guarantee `HAVE_I64` right after the cell typedef (matters when `HAVE_STDINT_H` made the
    platform typedef block build with `<stdint.h>`, where the old `HAVE_I64` was never set)
- `compiler/libpc300/amx.h` — same two edits (compiler side)
- `compiler/amxxpc/amx.h` — same two edits (driver side)
- `compiler/libpc300/libpawnc.c`
  - `pc_printf`: export it (`visibility("default")`) and give it the real `vprintf` body even in
    the 64-bit compile (`#if PAWN_CELL_SIZE==32` had made it a silent `return 1` stub)
  - `pc_error`: print messages unconditionally (was `#if PAWN_CELL_SIZE==32`-gated)
- `compiler/amxxpc/amxxpc.cpp`
  - `dlsym("Compile64")` first, falling back to `"Compile32"` (the library exports `Compile64`
    under 64-bit cells)
  - write `pl32.cellsize = (char)sizeof(cell)` (was hardcoded `4`, which made the runtime reject
    every plugin with "section not found")
- `plugins` recompiled with the 64-bit `amxxpc` so the shipped `.amxx` payloads and their
  `cellsize` field are consistent with the 64-bit core

## Portability fixes required by the 64-bit cell build / aarch64

- `amxmodx/public/resdk/engine/rehlds_api.h`
  - `FORCE_STACK_ALIGN` was `__attribute__((force_align_arg_pointer))` for every non-Windows
    target; clang rejects that attribute on aarch64. Now only defined on `__i386__`/`__x86_64__`.
- `amxmodx/public/memtools/CDetour/detours.h`
  - its own `typedef int32_t cell` collided with the 64-bit `cell` from `amx.h`; guarded with
    `#ifndef AMX_H_INCLUDED`
- `amxmodx/amxmodx/string.cpp`
  - `ke::Min(params[arg_inputsize], inputLength)` — cast `inputLength` to `cell` (template
    deduction otherwise breaks with mixed `long`/`int`)
- `amxmodx/amxmodx/file.cpp`
  - `ke::Max(0, params[2])` — cast the literals to `cell` (two call sites)

## Android / runtime integration (tools/)

- `tools/shims/assert_shim.c`
  - link-time `-Wl,--wrap=__assert2 -Wl,--wrap=__assert_fail`. The 64-bit AMXX build still ships
    live `assert()`s (no `NDEBUG`); instead of aborting the engine (`signal 6`) the shim appends a
    line to `/storage/emulated/0/xash/assert.txt` and returns, so the loader soft-fails.
- `tools/relink12.sh`
  - relinks the ambuild output with static `libc++` (`-nostdlib++` +
    `libc++_static.a`/`libc++abi.a`), the assert wraps, and `libcpp_verbose_abort.o`.
    Android ships no `libstdc++.so`, so without this step none of the AMXX libs would load.
- `tools/pack11.py` / `tools/pack13.py`
  - Python-only APK repack: copies every entry from the engine APK, replaces the
    `lib/arm64-v8a/*.so` with the relinked AMXX/metamod libs **prefixed with `lib`**
    (`libamxmodx.so`, `lib<cstrike|csx|engine|fakemeta|fun|geoip|json|nvault|regex|sockets|sqlite>_amxx_i386.so`,
    `libyapb_android_arm64.so` — the Android ROM only extracts `lib*.so` names into the native
    dir), and preserves the reference APK's per-entry compression type so extract happens in place.

## Config workarounds (deployed on device, not in source)

- `data/gamedata/common.games/master.games.txt` — replaced with an 81-byte empty master because
  `ParseStream_SMCE` (`CTextParsers.cpp`) SIGSEGVs on any file larger than its 4 KiB internal
  buffer (multi-chunk relocation bug). The full 61,527-byte original is kept next to it as
  `master.games.txt.bak`. **Real parser fix is still pending.**
- `configs/plugins.ini` — plain plugin names (AMXX resolves them against `plugins/`); the metamod
  plugin requires the `linux` platform token in its own `configs/metamod/plugins.ini`.
- Removed stale `mm_dbg.txt` before each build so diagnostics markers stay clean.

## Known residual issues

- `adminslots.amxx` logs a single `Invalid CVAR pointer` runtime error at map start (cvar
  creation timing), non-fatal.
- Second-launch black screen is a pre-engine Android/SDL layer failure (no `engine.log` writes);
  swipe the app away and relaunch.
- `libvgui_support.so` is absent from the stock APK too (benign engine warning).
- 32-bit `.amxx` files are intentionally rejected by the 64-bit core.