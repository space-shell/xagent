#!/usr/bin/env python3
"""Verify that all ELF shared libraries in an APK are 16 KB page-size
compatible (every PT_LOAD segment aligned to >= 16384 bytes), and that
the entries are stored uncompressed so the loader can map them directly.

Usage: check-16kb.py <apk>
Exit code 0 = compatible, 1 = incompatible or no libraries found.
"""
import struct
import sys
import zipfile

PAGE_SIZE_16K = 16384
PT_LOAD = 1


def check_lib(data: bytes) -> list[int]:
    """Return the PT_LOAD alignments of an ELF image."""
    if data[:4] != b"\x7fELF":
        return []
    is_64 = data[4] == 2
    if is_64:
        e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
        e_phentsize = struct.unpack_from("<H", data, 0x36)[0]
        e_phnum = struct.unpack_from("<H", data, 0x38)[0]
        p_align_off = 0x30
    else:
        e_phoff = struct.unpack_from("<I", data, 0x1C)[0]
        e_phentsize = struct.unpack_from("<H", data, 0x2A)[0]
        e_phnum = struct.unpack_from("<H", data, 0x2C)[0]
        p_align_off = 0x1C
    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        if struct.unpack_from("<I", data, off)[0] == PT_LOAD:
            aligns.append(struct.unpack_from("<Q" if is_64 else "<I", data, off + p_align_off)[0])
    return aligns


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 1
    apk = sys.argv[1]
    failures = []
    with zipfile.ZipFile(apk) as z:
        libs = [i for i in z.infolist() if i.filename.startswith("lib/") and i.filename.endswith(".so")]
        if not libs:
            print("FAIL: no native libraries found in APK (nothing to check, or packaging is wrong)")
            return 1
        for info in libs:
            aligns = check_lib(z.read(info.filename))
            bad = [a for a in aligns if a < PAGE_SIZE_16K]
            compress = "stored" if info.compress_type == zipfile.ZIP_STORED else "COMPRESSED"
            if bad or compress != "stored":
                failures.append(info.filename)
                tag = f"align fail {bad}" if bad else compress
                print(f"FAIL {info.filename}: {tag}")
            else:
                print(f"OK   {info.filename}: {len(aligns)} PT_LOAD segments, >=16KB aligned, stored")
    if failures:
        print(f"\n{len(failures)} lib(s) are not 16 KB compatible")
        return 1
    print(f"\nAll {len(libs)} native libraries are 16 KB page-size compatible")
    return 0


if __name__ == "__main__":
    sys.exit(main())
