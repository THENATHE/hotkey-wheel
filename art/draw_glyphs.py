"""Draws Hotkey Wheel's general-purpose icons as 16x16 GUI sprites.

Each icon is built from simple shapes sampled at pixel centres, so the result is plain pixel art like
Minecraft's own items: a light glyph, a second lighter-grey tone for details, and a dark outline added
around every lit pixel so the glyph reads on the wheel and over the world. Run it from the project root:

    python3 art/draw_glyphs.py            # writes the sprites
    python3 art/draw_glyphs.py --preview  # also writes art/preview.png, every icon at 8x
"""
from pathlib import Path
import math
import struct
import sys
import zlib

SIZE = 16
LIGHT = (242, 242, 242, 255)
GREY = (176, 186, 196, 255)
OUTLINE = (20, 24, 28, 230)
OUTPUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/hotkey_wheel/textures/gui/sprites/icon'


def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def ring(cx, cy, outer, inner):
    return lambda x, y: inner * inner < (x - cx) ** 2 + (y - cy) ** 2 <= outer * outer


def rect(x0, y0, x1, y1):
    return lambda x, y: x0 <= x < x1 and y0 <= y < y1


def segment(x0, y0, x1, y1, width):
    def inside(x, y):
        dx, dy = x1 - x0, y1 - y0
        t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy)))
        return math.hypot(x - (x0 + t * dx), y - (y0 + t * dy)) <= width / 2
    return inside


def polygon(*points):
    def inside(x, y):
        result = False
        for (ax, ay), (bx, by) in zip(points, points[1:] + points[:1]):
            if (ay > y) != (by > y) and x < ax + (y - ay) * (bx - ax) / (by - ay):
                result = not result
        return result
    return inside


def arc(cx, cy, outer, inner, start, end):
    """A ring between two angles in degrees, clockwise from 3 o'clock on screen."""
    def inside(x, y):
        distance = (x - cx) ** 2 + (y - cy) ** 2
        angle = math.degrees(math.atan2(y - cy, x - cx)) % 360
        within = start <= angle <= end if start <= end else angle >= start or angle <= end
        return inner * inner < distance <= outer * outer and within
    return inside


def grid(rows, left=1, top=1):
    """Hand-placed pixels: '#' light, '+' grey, anything else empty."""
    def tone_at(x, y):
        row, column = int(y) - top, int(x) - left
        if 0 <= row < len(rows) and 0 <= column < len(rows[row]):
            return rows[row][column]
        return '.'
    return tone_at


# Each icon is a list of steps applied in order: ('#', shape) and ('+', shape) light pixels, ('-', shape) clears them.
ICONS = {}


def icon(name, *steps):
    ICONS[name] = steps


def cog_teeth(cx, cy, r_outer, r_inner, teeth, tooth_width):
    def inside(x, y):
        distance = math.hypot(x - cx, y - cy)
        if distance <= r_inner:
            return True
        if distance > r_outer:
            return False
        angle = math.atan2(y - cy, x - cx)
        step = 2 * math.pi / teeth
        nearest = round(angle / step) * step
        return abs(math.sin(angle - nearest)) * distance <= tooth_width / 2
    return inside


icon('cog', ('#', cog_teeth(8, 8, 7.4, 4.9, 8, 2.5)), ('-', circle(8, 8, 2.0)))
icon('check', ('#', segment(2.5, 8.5, 6, 12, 2.6)), ('#', segment(6, 12, 13.5, 3.5, 2.6)))
icon('close', ('#', segment(3, 3, 13, 13, 2.8)), ('#', segment(13, 3, 3, 13, 2.8)))
icon('plus', ('#', rect(7, 2, 9, 14)), ('#', rect(2, 7, 14, 9)))
icon('minus', ('#', rect(2, 7, 14, 9)))
icon('bulleted_list',
     *[('#', rect(2, top, 4, top + 2)) for top in (3, 7, 11)],
     *[('#', rect(6, top, 14, top + 2)) for top in (3, 7, 11)])
icon('numbered_list', ('#', grid([
    '.#...........',
    '##...########',
    '.#...########',
    '###..........',
    '.............',
    '##...........',
    '..#..########',
    '.#...########',
    '###..........',
    '.............',
    '##...........',
    '.##..########',
    '..#..########',
    '##...........',
], left=1, top=1)))
icon('star', ('#', polygon(*[(8 + (7 if i % 2 == 0 else 3) * math.sin(math.radians(i * 36)),
                               8.6 - (7 if i % 2 == 0 else 3) * math.cos(math.radians(i * 36))) for i in range(10)])))
icon('heart', ('#', circle(5, 5.6, 3.2)), ('#', circle(11, 5.6, 3.2)),
     ('#', polygon((1.9, 6.6), (14.1, 6.6), (8, 13.8))))
icon('home', ('#', polygon((8, 1.5), (15, 8.5), (1, 8.5))), ('#', rect(3, 8, 13, 14)),
     ('-', rect(6.5, 10, 9.5, 14)))
icon('folder', ('#', rect(2, 3, 7, 5)), ('#', rect(2, 4, 14, 13)), ('+', rect(2, 6, 14, 7)))
icon('search', ('#', ring(6.5, 6.5, 5, 2.8)), ('#', segment(10, 10, 13.5, 13.5, 2.8)))
icon('pencil', ('#', segment(4.5, 11.5, 11.5, 4.5, 3.4)), ('+', segment(11, 5, 12.5, 3.5, 3.4)),
     ('#', polygon((2, 14), (2.6, 10.6), (5.4, 13.4))))
icon('trash', ('#', rect(3, 3, 13, 5)), ('#', rect(6, 1, 10, 3)), ('#', rect(4, 5, 12, 14)),
     ('+', rect(6, 7, 7, 12)), ('+', rect(9, 7, 10, 12)))
icon('refresh', ('#', arc(8, 8, 6, 3.6, 40, 320)), ('#', polygon((10.2, 1.6), (14.6, 6.4), (9.2, 7.2))))
icon('arrow_up', ('#', polygon((8, 1.5), (14, 7.5), (2, 7.5))), ('#', rect(6.5, 7, 9.5, 14)))
icon('arrow_down', ('#', polygon((8, 14.5), (14, 8.5), (2, 8.5))), ('#', rect(6.5, 2, 9.5, 9)))
icon('arrow_left', ('#', polygon((1.5, 8), (7.5, 2), (7.5, 14))), ('#', rect(7, 6.5, 14, 9.5)))
icon('arrow_right', ('#', polygon((14.5, 8), (8.5, 2), (8.5, 14))), ('#', rect(2, 6.5, 9, 9.5)))
icon('play', ('#', polygon((4, 2), (13.5, 8), (4, 14))))
icon('pause', ('#', rect(3.5, 2.5, 6.5, 13.5)), ('#', rect(9.5, 2.5, 12.5, 13.5)))
icon('lock', ('#', ring(8, 6.5, 4.6, 2.4)), ('-', rect(0, 7, 16, 16)), ('#', rect(3, 7, 13, 14)),
     ('-', rect(7.5, 9, 8.5, 12)))
icon('eye', ('#', polygon((1, 8), (4, 4.5), (8, 3.2), (12, 4.5), (15, 8), (12, 11.5), (8, 12.8), (4, 11.5))),
     ('-', circle(8, 8, 3.2)), ('+', circle(8, 8, 1.8)))
icon('info', ('#', circle(8, 8, 6.8)), ('-', rect(7, 4, 9, 6)), ('-', rect(7, 7, 9, 12)))
icon('warning', ('#', polygon((8, 1.2), (15.2, 14.2), (0.8, 14.2))), ('-', rect(7, 5, 9, 10)), ('-', rect(7, 11, 9, 13)))
icon('question', ('#', grid([
    '...######...',
    '..########..',
    '.###....###.',
    '.###....###.',
    '.......###..',
    '......###...',
    '.....###....',
    '.....###....',
    '............',
    '.....###....',
    '.....###....',
], left=2, top=2)))
icon('bookmark', ('#', polygon((3.5, 1.5), (12.5, 1.5), (12.5, 14.5), (8, 10.5), (3.5, 14.5))))
icon('flag', ('#', rect(3, 1.5, 4.8, 14.5)), ('#', polygon((4.5, 2), (13.5, 2), (11, 5.5), (13.5, 9), (4.5, 9))))
icon('map_pin', ('#', circle(8, 6, 5)), ('#', polygon((3.6, 7.6), (12.4, 7.6), (8, 14.8))), ('-', circle(8, 6, 1.9)))
icon('clock', ('#', circle(8, 8, 6.9)), ('-', rect(7.5, 3.5, 8.5, 8.5)), ('-', rect(7.5, 7.5, 11.5, 8.5)))
icon('sun', ('#', circle(8, 8, 3.4)),
     *[('#', segment(8 + 5 * math.cos(math.radians(a)), 8 + 5 * math.sin(math.radians(a)),
                     8 + 6.6 * math.cos(math.radians(a)), 8 + 6.6 * math.sin(math.radians(a)), 1.8)) for a in range(0, 360, 45)])
icon('moon', ('#', circle(8, 8, 6.4)), ('-', circle(11, 5.6, 5)))
icon('bell', ('#', circle(8, 6.5, 4.6)), ('#', polygon((3.4, 6.5), (12.6, 6.5), (14, 12), (2, 12))),
     ('#', rect(6.5, 12, 9.5, 14.5)), ('#', rect(7, 1, 9, 3)))
icon('key', ('#', ring(5, 8, 3.9, 1.5)), ('#', rect(8, 7, 15, 9.5)), ('#', rect(12, 9, 14, 12)), ('#', rect(9.5, 9, 11, 11)))
icon('lightning', ('#', polygon((9.5, 1), (3, 9), (7.5, 9), (6, 15), (13, 6.5), (8.5, 6.5), (10.5, 1))))
icon('power', ('#', arc(8, 8.5, 6.2, 3.8, 300, 240)), ('#', rect(7, 1, 9, 8.5)))
icon('person', ('#', circle(8, 4.8, 3.2)), ('#', polygon((2.5, 15), (3.5, 10.5), (6, 8.8), (10, 8.8), (12.5, 10.5), (13.5, 15))))
icon('chat', ('#', rect(1.5, 2.5, 14.5, 11)), ('#', polygon((4, 10.5), (8, 10.5), (4, 14.5))),
     ('+', rect(4, 5, 12, 6)), ('+', rect(4, 7.5, 10, 8.5)))
icon('puzzle', ('#', rect(1.5, 5, 11, 14.5)), ('#', circle(6.25, 2.9, 1.9)), ('#', rect(5.25, 4, 7.25, 5)),
     ('#', circle(13.1, 9.75, 1.9)), ('#', rect(11, 8.75, 12, 10.75)), ('-', circle(1.5, 9.75, 1.6)))


def render(steps):
    pixels = [[None] * SIZE for _ in range(SIZE)]
    for tone, shape in steps:
        for y in range(SIZE):
            for x in range(SIZE):
                value = shape(x + 0.5, y + 0.5)
                if isinstance(value, str):
                    if value in '#+':
                        pixels[y][x] = LIGHT if value == '#' else GREY
                    continue
                if value:
                    pixels[y][x] = None if tone == '-' else LIGHT if tone == '#' else GREY
    # The outline belongs to the margin and to holes, never over a lit pixel.
    image = [row[:] for row in pixels]
    for y in range(SIZE):
        for x in range(SIZE):
            if pixels[y][x] is not None:
                continue
            if any(0 <= x + dx < SIZE and 0 <= y + dy < SIZE and pixels[y + dy][x + dx] is not None
                   for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                image[y][x] = OUTLINE
    return image


def write_png(path, image):
    width, height = len(image[0]), len(image)
    raw = b''.join(b'\x00' + b''.join(bytes(pixel or (0, 0, 0, 0)) for pixel in row) for row in image)

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def preview(images, scale=8, columns=8):
    cell = SIZE * scale + 8
    rows = (len(images) + columns - 1) // columns
    canvas = [[(38, 48, 56, 255)] * (columns * cell) for _ in range(rows * cell)]
    for index, image in enumerate(images):
        ox, oy = index % columns * cell + 4, index // columns * cell + 4
        for y in range(SIZE * scale):
            for x in range(SIZE * scale):
                pixel = image[y // scale][x // scale]
                if pixel is not None:
                    alpha = pixel[3] / 255
                    base = canvas[oy + y][ox + x]
                    canvas[oy + y][ox + x] = tuple(round(pixel[c] * alpha + base[c] * (1 - alpha)) for c in range(3)) + (255,)
    return canvas


if __name__ == '__main__':
    OUTPUT.mkdir(parents=True, exist_ok=True)
    images = []
    for name, steps in ICONS.items():
        image = render(steps)
        images.append(image)
        write_png(OUTPUT / f'{name}.png', image)
    if '--preview' in sys.argv:
        write_png(Path(__file__).resolve().parent / 'preview.png', preview(images))
    print(f'Wrote {len(ICONS)} icons to {OUTPUT}')
