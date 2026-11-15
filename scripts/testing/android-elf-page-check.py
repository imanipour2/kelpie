#!/usr/bin/env python3
"""Fail closed when an Android APK contains ELF LOAD segments not 16 KiB aligned.

This inspects the *actual* native shared objects inside the final APK. Checking
zipalign -P 16 alone is insufficient: an archive may package 4 KiB ELF segments.
No NDK tools, dependencies, release keystore or internet access are required.
"""

import argparse
import json
import struct
import sys
import zipfile

PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552


def read_program_headers(binary: bytes) -> list[dict]:
    if len(binary) < 64 or binary[:4] != b"\x7fELF":
        raise ValueError("Not an ELF shared object")
    if binary[4] != 2 or binary[5] != 1:
        raise ValueError("Expected 64-bit little-endian ELF")
    offset = struct.unpack_from("<Q", binary, 32)[0]
    stride, count = struct.unpack_from("<HH", binary, 54)
    if stride < 56 or count <= 0 or count > 256:
        raise ValueError("Malformed ELF program header table")
    if offset + stride * count > len(binary):
        raise ValueError("ELF program headers exceed file")
    headers = []
    for index in range(count):
        fields = struct.unpack_from("<IIQQQQQQ", binary, offset + index * stride)
        headers.append(
            {
                "type": fields[0],
                "flags": fields[1],
                "offset": fields[2],
                "vaddr": fields[3],
                "filesz": fields[5],
                "memsz": fields[6],
                "align": fields[7],
            }
        )
    return headers


def verify_apk(path: str, required_page: int = 16_384) -> dict:
    result = {"apk": path, "required_page": required_page, "libraries": [], "errors": []}
    with zipfile.ZipFile(path) as archive:
        names = [name for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so")]
        if not names:
            result["errors"].append("No native .so libraries found")
        for name in names:
            try:
                headers = read_program_headers(archive.read(name))
                loads = [h for h in headers if h["type"] == PT_LOAD]
                if not loads:
                    raise ValueError("No ELF LOAD segments")
                alignments = [h["align"] for h in loads]
                compliant = all(
                    h["align"] >= required_page and
                    h["offset"] % required_page == h["vaddr"] % required_page
                    for h in loads
                )
                relro = [h for h in headers if h["type"] == PT_GNU_RELRO]
                # Conservative policy: GNU_RELRO end must be page aligned.
                # Keep this strict rather than allowing an unverified writable
                # LOAD overlap that could SIGSEGV on 16 KiB devices.
                relro_aligned = all(
                    (h["vaddr"] + h["memsz"]) % required_page == 0
                    for h in relro
                )
                result["libraries"].append(
                    {
                        "name": name,
                        "load_alignments": alignments,
                        "relro_page_aligned": relro_aligned,
                        "pass": compliant and relro_aligned,
                    }
                )
                if not compliant:
                    result["errors"].append(f"{name}: ELF LOAD alignment below {required_page} or offset mismatch")
                if not relro_aligned:
                    result["errors"].append(f"{name}: GNU_RELRO page end not aligned to {required_page}")
            except (ValueError, struct.error) as error:
                result["errors"].append(f"{name}: {error}")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", help="Path to Android application APK")
    arguments = parser.parse_args()
    try:
        report = verify_apk(arguments.apk)
    except (FileNotFoundError, OSError, zipfile.BadZipFile) as error:
        print(json.dumps({"error": str(error)}))
        return 2
    print(json.dumps(report, indent=2))
    return 0 if not report["errors"] else 1


if __name__ == "__main__":
    sys.exit(main())
