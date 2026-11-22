#!/usr/bin/env python3
"""Validate required real AVD captures; visual inspection remains a separate step."""
import os
import struct
import sys
import zlib

names = ['01_permissions_onboarding', '02_home', '03_feed', '04_chat', '05_quarto',
         '06_computer', '07_settings', '08_goals', '09_memory', '10_library',
         '11_permissions', '05b_quarto_stage_dialog']
failed = False
collected = []
for name in names:
    path = f'evidence-android/test-screenshots/{name}.png'
    try:
        data = open(path, 'rb').read()
        if data[:8] != b'\x89PNG\r\n\x1a\n': raise ValueError('invalid signature')
        pos, chunks, image, width, height = 8, [], bytearray(), 0, 0
        while pos < len(data):
            length = struct.unpack('>I', data[pos:pos+4])[0]
            typ = data[pos+4:pos+8]; payload = data[pos+8:pos+8+length]
            crc = struct.unpack('>I', data[pos+8+length:pos+12+length])[0]
            if zlib.crc32(typ+payload) != crc: raise ValueError('CRC mismatch')
            chunks.append(typ)
            if typ == b'IHDR': width, height = struct.unpack('>II', payload[:8])
            if typ == b'IDAT': image.extend(payload)
            pos += 12+length
            if typ == b'IEND': break
        if not width or not height or b'IEND' not in chunks or not zlib.decompress(image): raise ValueError('invalid image')
        collected.append(f'{name} {width}x{height} bytes={len(data)}')
    except (OSError, ValueError, struct.error, zlib.error) as exc:
        failed = True
        print(f'::error title=Screenshot missing or invalid::{name} ({type(exc).__name__})')
if collected:
    print('::notice title=Screenshots collected::' + '%0A'.join(collected))
sys.exit(1 if failed else 0)
