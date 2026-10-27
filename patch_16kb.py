#!/usr/bin/env python3
"""
Android 15 & 16 16KB Page Size Alignment Patcher
Scans ELF .so libraries and forces PT_LOAD segment alignment to >= 0x4000 (16KB).
"""
import os
import struct
import sys

def patch_so(filepath):
    with open(filepath, "rb") as f:
        data = bytearray(f.read())
    if len(data) < 4 or data[:4] != b"\x7fELF":
        return False
    cls = data[4]
    endian = "<" if data[5] == 1 else ">"
    patched = False

    if cls == 2: # 64-bit ELF
        phoff = struct.unpack(f"{endian}Q", data[32:40])[0]
        phesz = struct.unpack(f"{endian}H", data[54:56])[0]
        phnum = struct.unpack(f"{endian}H", data[56:58])[0]
        for i in range(phnum):
            ph = phoff + i * phesz
            p_type = struct.unpack(f"{endian}I", data[ph:ph+4])[0]
            if p_type == 1: # PT_LOAD
                align_off = ph + 48
                align = struct.unpack(f"{endian}Q", data[align_off:align_off+8])[0]
                if align < 0x4000:
                    struct.pack_into(f"{endian}Q", data, align_off, 0x4000)
                    patched = True
    elif cls == 1: # 32-bit ELF
        phoff = struct.unpack(f"{endian}I", data[28:32])[0]
        phesz = struct.unpack(f"{endian}H", data[42:44])[0]
        phnum = struct.unpack(f"{endian}H", data[44:46])[0]
        for i in range(phnum):
            ph = phoff + i * phesz
            p_type = struct.unpack(f"{endian}I", data[ph:ph+4])[0]
            if p_type == 1: # PT_LOAD
                align_off = ph + 28
                align = struct.unpack(f"{endian}I", data[align_off:align_off+4])[0]
                if align < 0x4000:
                    struct.pack_into(f"{endian}I", data, align_off, 0x4000)
                    patched = True

    if patched:
        with open(filepath, "wb") as f:
            f.write(data)
        return True
    return False

if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/jniLibs"
    count = 0
    for root, dirs, files in os.walk(target):
        for f in files:
            if f.endswith(".so"):
                fp = os.path.join(root, f)
                if patch_so(fp):
                    print(f"[16KB PATCHED] {fp}")
                    count += 1
                else:
                    print(f"[ALREADY 16KB] {fp}")
    print(f"Finished: {count} libraries updated.")
