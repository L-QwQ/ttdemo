"""彩票球图标生成器：SVG + 多尺寸 PNG + ICO（零第三方依赖）"""
import math
import os
import struct
import zlib

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(SCRIPT_DIR), 'public')

BG_A = (0xE4, 0x38, 0x3B)   # 红
BG_B = (0xD6, 0x92, 0x2A)   # 金
BALL = (0xFF, 0xFD, 0xF8)
BAND_A = (0xD6, 0x92, 0x2A)
BAND_B = (0xE4, 0x38, 0x3B)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sd_rbox(x, y, cx, cy, hw, hh, r):
    qx = abs(x - cx) - (hw - r)
    qy = abs(y - cy) - (hh - r)
    return math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - r


def sd_box(x, y, cx, cy, hw, hh):
    qx = abs(x - cx) - hw
    qy = abs(y - cy) - hh
    return math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0)


def sd_circle(x, y, cx, cy, r):
    return math.hypot(x - cx, y - cy) - r


def render(size, sub=4):
    step = 1.0 / (size * sub)
    rows = []
    half_bg, r_bg = 0.484, 0.25
    for py in range(size):
        row = bytearray()
        for px in range(size):
            tr = tg = tb = 0
            ta = 0
            for sy in range(sub):
                y = (py + (sy + 0.5) / sub) / size
                for sx in range(sub):
                    x = (px + (sx + 0.5) / sub) / size
                    if sd_rbox(x, y, 0.5, 0.5, half_bg, half_bg, r_bg) > 0:
                        continue
                    d = sd_box(x, y, 0.5, 0.5, 0.5, 0.125)
                    if max(d, sd_circle(x, y, 0.5, 0.5, 0.297)) <= 0:
                        c = lerp(BAND_A, BAND_B, y)
                    elif sd_circle(x, y, 0.5, 0.5, 0.297) <= 0:
                        c = BALL
                    else:
                        c = lerp(BG_A, BG_B, (x + y) / 2)
                    tr += c[0]
                    tg += c[1]
                    tb += c[2]
                    ta += 1
            n = sub * sub
            if ta:
                row += bytes((tr // ta, tg // ta, tb // ta, round(ta * 255 / n)))
            else:
                row += b'\x00\x00\x00\x00'
        rows.append(bytes(row))
    return rows


def png_bytes(size):
    rows = render(size)
    raw = b''.join(b'\x00' + r for r in rows)

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))


def build_ico(sizes):
    images = [(s, png_bytes(s)) for s in sizes]
    head = struct.pack('<HHH', 0, 1, len(images))
    offset = 6 + 16 * len(images)
    entries, blobs = b'', b''
    for s, data in images:
        entries += struct.pack('<BBBBHHII', s, s, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
        blobs += data
    return head + entries + blobs


if __name__ == '__main__':
    sizes = [16, 32, 48]
    ico = build_ico(sizes)
    with open(os.path.join(OUT, 'favicon.ico'), 'wb') as f:
        f.write(ico)
    for s in (180, 192, 512):
        with open(os.path.join(OUT, f'icon-{s}.png'), 'wb') as f:
            f.write(png_bytes(s))
    print('ico sizes', sizes, '->', len(ico), 'bytes')
    for s in (180, 192, 512):
        print(f'icon-{s}.png', os.path.getsize(os.path.join(OUT, f'icon-{s}.png')), 'bytes')
