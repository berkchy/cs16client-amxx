#!/usr/bin/env python3
"""Pack the CI build output into release bundle artifacts.

  gen-bundle.py <libdir> <out-bundle.zip> [<pluginsdir> <out-plugins.zip>]

The bundle manifest schema mirrors com.pickle.patcher.lib.BundleManifest so the
patched APK injects exactly these payload entries.
"""
import json
import os
import sys
import zipfile

VERSION = os.environ.get("RELEASE_VERSION", "1.10.0-dev")

MODULES = [
    "cstrike", "csx", "engine", "fakemeta", "fun", "geoip",
    "json", "nvault", "regex", "sockets", "sqlite",
]


def sha256(path):
    import hashlib
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 16), b""):
            h.update(block)
    return h.hexdigest()


def main():
    libdir, bundle_out = sys.argv[1], sys.argv[2]
    plugins_dir, plugins_out = (sys.argv[3], sys.argv[4]) if len(sys.argv) > 3 else (None, None)

    entries = []
    core = os.path.join(libdir, "libamxmodx.so")
    assert os.path.exists(core), f"missing {core}"
    entries.append({
        "source": "lib/arm64-v8a/libamxmodx.so",
        "target": "lib/arm64-v8a/libamxmodx.so",
        "method": "STORED",
        "required": True,
        "description": "AMX Mod X core",
    })
    metamod = os.path.join(libdir, "libmetamod.so")
    if os.path.exists(metamod):
        entries.append({
            "source": "lib/arm64-v8a/libmetamod.so",
            "target": "lib/arm64-v8a/libmetamod.so",
            "method": "STORED",
            "required": True,
            "description": "Metamod HL1",
        })
    for mod in MODULES:
        p = os.path.join(libdir, f"lib{mod}_amxx_amd64.so")
        if os.path.exists(p):
            entries.append({
                "source": f"lib/arm64-v8a/lib{mod}_amxx_amd64.so",
                "target": f"lib/arm64-v8a/lib{mod}_amxx_amd64.so",
                "method": "STORED",
                "required": True,
                "description": f"{mod} module",
            })
        else:
            print(f"WARN: missing module {mod}, skipping")

    manifest = {
        "version": VERSION,
        "game": "cs16client",
        "entries": entries,
    }

    with zipfile.ZipFile(bundle_out, "w", zipfile.ZIP_STORED) as z:
        z.writestr("bundle.json", json.dumps(manifest, ensure_ascii=False, separators=(",", ":")))
        for e in entries:
            z.write(os.path.join(libdir, os.path.basename(e["source"])), e["source"])

    print(f"bundle: {bundle_out} ({os.path.getsize(bundle_out)} bytes, {len(entries)} entries)")

    if plugins_dir and plugins_out:
        with zipfile.ZipFile(plugins_out, "w", zipfile.ZIP_DEFLATED) as z:
            for name in sorted(os.listdir(plugins_dir)):
                if name.endswith(".amxx"):
                    z.write(os.path.join(plugins_dir, name), f"plugins/{name}")
        print(f"plugins: {plugins_out} ({os.path.getsize(plugins_out)} bytes)")


if __name__ == "__main__":
    main()