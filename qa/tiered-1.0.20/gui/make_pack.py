#!/usr/bin/env python3
"""Deterministic artificial GUI resource pack; no third-party assets."""
import json, struct, zlib, zipfile
from pathlib import Path

def png(width, height, pixel):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    rows = b"".join(b"\0" + bytes(v for x in range(width) for v in pixel(x, y)) for y in range(height))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")

def chest(x, y):
    if x < 4 or 172 <= x < 176 or y < 4 or 218 <= y < 222:
        return (255, 130, 30, 255)
    return (105, 195, 230, 255)

def slot(x, y):
    return (210, 30, 200, 255) if x in (0, 17) or y in (0, 17) else (215, 235, 245, 255)

out = Path(__file__).with_name("qa-vanilla-container-sprites.zip")
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as pack:
    pack.writestr("pack.mcmeta", json.dumps({"pack": {"description": "QA only: vanilla chest and slot sprite path probe", "min_format": [97, 1], "max_format": [97, 1]}}))
    pack.writestr("assets/minecraft/textures/gui/container/generic_54.png", png(256, 256, chest))
    pack.writestr("assets/minecraft/textures/gui/sprites/container/slot.png", png(18, 18, slot))
print(out.resolve())
