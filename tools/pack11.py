import zipfile, os

SRC = "/data/data/com.termux/files/usr/tmp/opencode/apk11"
REF = "/storage/emulated/0/Download/CS16Client-AMXX10-signed.apk"
OUT = "/data/data/com.termux/files/usr/tmp/opencode/CS16Client-AMXX11-unsigned.apk"

ref = zipfile.ZipFile(REF)
refmap = {i.filename: i.compress_type for i in ref.infolist()}

count = 0
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for root, dirs, files in os.walk(SRC):
        dirs[:] = [d for d in dirs if d != "META-INF"]
        for name in files:
            full = os.path.join(root, name)
            arc = os.path.relpath(full, SRC)
            if arc.startswith("META-INF/"):
                continue
            z.write(full, arc, compress_type=refmap.get(arc, zipfile.ZIP_DEFLATED))
            count += 1
print(f"packed {count} entries")
z2 = zipfile.ZipFile(OUT)
print("dupes:", sorted([n for n in set(refmap) if n in z2.namelist() and n not in set(os.path.relpath(os.path.join(r0,f),SRC) for r0,_,fs in os.walk(SRC) for f in fs if not os.path.relpath(os.path.join(r0,f),SRC).startswith('META-INF/'))]))