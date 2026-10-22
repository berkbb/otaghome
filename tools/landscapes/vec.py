"""Small helpers that write Android VectorDrawable XML for 1920x1080 landscape scenes."""
import math
import random

W, H = 1920, 1080
FULL = f'M0,0h{W}v{H}h-{W}z'


def f(v):
    """Compact number formatting keeps the files small."""
    return f'{v:.0f}' if abs(v - round(v)) < 0.05 else f'{v:.1f}'


class Scene:
    def __init__(self, desc):
        self.desc = desc
        self.parts = []

    # --- fills -------------------------------------------------------------------------------
    def fill(self, color, d):
        self.parts.append(f'    <path android:fillColor="{color}" android:pathData="{d}" />\n')

    def stroke(self, color, width, d, cap='round'):
        self.parts.append(
            f'    <path android:strokeColor="{color}" android:strokeWidth="{f(width)}" '
            f'android:strokeLineCap="{cap}" android:fillColor="#00000000" android:pathData="{d}" />\n')

    def linear(self, stops, x1=0, y1=0, x2=0, y2=H, d=FULL):
        items = ''.join(f'                <item android:offset="{o}" android:color="{c}" />\n' for o, c in stops)
        self.parts.append(
            f'    <path android:pathData="{d}">\n        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}" '
            f'android:endX="{f(x2)}" android:endY="{f(y2)}">\n{items}            </gradient>\n'
            f'        </aapt:attr>\n    </path>\n')

    def radial(self, cx, cy, r, stops, d=FULL):
        items = ''.join(f'                <item android:offset="{o}" android:color="{c}" />\n' for o, c in stops)
        self.parts.append(
            f'    <path android:pathData="{d}">\n        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="radial" android:centerX="{f(cx)}" android:centerY="{f(cy)}" '
            f'android:gradientRadius="{f(r)}">\n{items}            </gradient>\n'
            f'        </aapt:attr>\n    </path>\n')

    def xml(self):
        return ('<?xml version="1.0" encoding="utf-8"?>\n'
                f'<!-- {self.desc} -->\n'
                '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    xmlns:aapt="http://schemas.android.com/aapt"\n'
                '    android:width="1920dp"\n    android:height="1080dp"\n'
                '    android:viewportWidth="1920"\n    android:viewportHeight="1080">\n'
                + ''.join(self.parts) + '</vector>\n')

    # --- composites --------------------------------------------------------------------------
    def sky(self, stops, y2=H):
        self.linear(stops, y2=y2)

    def glow(self, x, y, r, color_rgb, alpha='66'):
        self.radial(x, y, r, [(0, f'#{alpha}{color_rgb}'), (1, f'#00{color_rgb}')])

    def sun(self, x, y, r, color='#FFFFF1C1', glow_rgb='FFE2A8', glow_r=None):
        self.glow(x, y, glow_r or r * 6, glow_rgb, '55')
        self.fill(color, circle(x, y, r))

    def stars(self, seed, n, ymax, color='#B3FFFFFF', rmin=1.2, rmax=2.6):
        rng = random.Random(seed)
        self.fill(color, ''.join(circle(rng.randint(10, W - 10), rng.randint(10, ymax), rng.uniform(rmin, rmax))
                                 for _ in range(n)))

    def water(self, y, stops):
        self.linear(stops, y1=y, y2=H, d=f'M0,{f(y)}h{W}V{H}H0z')

    def reflection(self, x, y, color, seed=1, rows=10, spread=1.0):
        """Broken horizontal streaks of light on water under (x, y)."""
        rng = random.Random(seed)
        d = []
        for i in range(rows):
            yy = y + 14 + i * 22
            w = (150 - i * 9) * spread * rng.uniform(0.6, 1.1)
            xx = x + rng.uniform(-30, 30)
            d.append(f'M{f(xx - w / 2)},{f(yy)}h{f(w)}v5h-{f(w)}z')
        self.fill(color, ''.join(d))

    def clouds(self, seed, n, ymin, ymax, color='#40FFFFFF', scale=1.0):
        rng = random.Random(seed)
        d = []
        for _ in range(n):
            x, y = rng.randint(0, W), rng.randint(ymin, ymax)
            w = rng.randint(160, 340) * scale
            # Rounded puffs over a soft flat-bottomed base
            for k, (dx, rr) in enumerate([(-0.28, 0.17), (-0.08, 0.24), (0.14, 0.2), (0.32, 0.14)]):
                r = w * rr * rng.uniform(0.9, 1.1)
                d.append(circle(x + dx * w, y - r * 0.55, r))
            d.append(ellipse(x + 0.02 * w, y, w * 0.48, w * 0.09))
        self.fill(color, ''.join(d))


# --- shapes ------------------------------------------------------------------------------------
# Circles and ellipses wind the same way as rect() (clockwise on screen); with the default
# non-zero fill rule, opposite windings would punch holes where shapes overlap.
def circle(x, y, r):
    return f'M{f(x)},{f(y)}m-{f(r)},0a{f(r)},{f(r)} 0,1 1,{f(2 * r)} 0a{f(r)},{f(r)} 0,1 1,-{f(2 * r)} 0'


def ellipse(x, y, rx, ry):
    return f'M{f(x - rx)},{f(y)}a{f(rx)},{f(ry)} 0,1 1,{f(2 * rx)} 0a{f(rx)},{f(ry)} 0,1 1,-{f(2 * rx)} 0'


def rect(x, y, w, h):
    return f'M{f(x)},{f(y)}h{f(w)}v{f(h)}h-{f(w)}z'


def poly(points):
    return 'M' + 'L'.join(f'{f(x)},{f(y)}' for x, y in points) + 'Z'


def smooth_ridge(points, bottom=H):
    """Closed shape under a smooth line through [points] (Catmull-Rom turned into cubic Béziers)."""
    p = points
    d = [f'M{f(p[0][0])},{f(bottom)}L{f(p[0][0])},{f(p[0][1])}']
    for i in range(len(p) - 1):
        p0 = p[i - 1] if i > 0 else p[i]
        p1, p2 = p[i], p[i + 1]
        p3 = p[i + 2] if i + 2 < len(p) else p2
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d.append(f'C{f(c1[0])},{f(c1[1])} {f(c2[0])},{f(c2[1])} {f(p2[0])},{f(p2[1])}')
    d.append(f'L{f(p[-1][0])},{f(bottom)}Z')
    return ''.join(d)


def hills(seed, y, amp, n=6, bottom=H):
    rng = random.Random(seed)
    xs = [-60 + i * (W + 120) / n for i in range(n + 1)]
    return smooth_ridge([(x, y - amp * rng.uniform(-1, 1)) for x in xs], bottom)


def peaks(seed, y, amp, n=14, bottom=H, jitter=0.5):
    """Jagged mountain ridge."""
    rng = random.Random(seed)
    pts = [(-40, y)]
    for i in range(1, n):
        x = i * W / n + rng.uniform(-30, 30)
        up = i % 2 == 1
        pts.append((x, y - (amp * rng.uniform(0.6, 1.0) if up else amp * rng.uniform(0, jitter))))
    pts.append((W + 40, y))
    return poly([(pts[0][0], bottom)] + pts + [(pts[-1][0], bottom)])


def dome(cx, base, r, h=None):
    """Half-ellipse dome sitting on [base]."""
    h = h or r
    return f'M{f(cx - r)},{f(base)}C{f(cx - r)},{f(base - h * 1.33)} {f(cx + r)},{f(base - h * 1.33)} {f(cx + r)},{f(base)}Z'


def minaret(x, base, h, w=12, cone=44):
    """Ottoman pencil minaret: shaft, balcony and pointed cap."""
    top = base - h
    return (rect(x - w / 2, top, w, h) + rect(x - w * 0.95, top + h * 0.28, w * 1.9, 6)
            + poly([(x - w * 0.62, top), (x, top - cone), (x + w * 0.62, top)]))


def mosque(cx, base, s, domes=True, minarets=4):
    """A classical Ottoman mosque silhouette scaled by [s]."""
    d = [rect(cx - 170 * s, base - 120 * s, 340 * s, 120 * s),
         rect(cx - 70 * s, base - 170 * s, 140 * s, 50 * s),
         dome(cx, base - 165 * s, 95 * s, 85 * s),
         dome(cx - 120 * s, base - 120 * s, 50 * s, 42 * s),
         dome(cx + 120 * s, base - 120 * s, 50 * s, 42 * s),
         dome(cx - 190 * s, base - 70 * s, 28 * s),
         dome(cx + 190 * s, base - 70 * s, 28 * s),
         rect(cx - 220 * s, base - 70 * s, 440 * s, 70 * s)]
    spots = [-240, 240, -205, 205][:minarets]
    heights = [330, 330, 290, 290]
    for i, dx in enumerate(spots):
        d.append(minaret(cx + dx * s, base, heights[i] * s, 13 * s, 48 * s))
    return ''.join(d)


def pine(x, y, h, w=None):
    w = w or h * 0.42
    return poly([(x, y - h), (x + w * 0.25, y - h * 0.62), (x + w * 0.12, y - h * 0.62), (x + w * 0.4, y - h * 0.28),
                 (x + w * 0.2, y - h * 0.28), (x + w * 0.5, y), (x - w * 0.5, y), (x - w * 0.2, y - h * 0.28),
                 (x - w * 0.4, y - h * 0.28), (x - w * 0.12, y - h * 0.62), (x - w * 0.25, y - h * 0.62)])


def pine_row(seed, base, hmin, hmax, step, ground=True):
    rng = random.Random(seed)
    d = [rect(0, base - 12, W, H - base + 12)] if ground else []
    x = -40
    while x < W + 40:
        h = rng.randint(hmin, hmax)
        d.append(pine(x, base + rng.randint(-6, 6), h))
        x += rng.randint(int(step * 0.7), int(step * 1.3))
    return ''.join(d)


def tree_blob(x, y, r, seed=0):
    """A rounded deciduous tree crown."""
    rng = random.Random(seed)
    return ''.join(circle(x + rng.uniform(-r, r) * 0.6, y + rng.uniform(-r, r) * 0.4, r * rng.uniform(0.55, 0.8))
                   for _ in range(5))


def cypress(x, base, h, w=26):
    return f'M{f(x)},{f(base - h)}C{f(x + w)},{f(base - h * 0.6)} {f(x + w * 0.8)},{f(base)} {f(x)},{f(base)}C{f(x - w * 0.8)},{f(base)} {f(x - w)},{f(base - h * 0.6)} {f(x)},{f(base - h)}Z'


def palm(x, base, h, lean=30):
    """Trunk and fronds of a palm; returns (trunk, fronds) paths for stroking/filling."""
    top = (x + lean, base - h)
    trunk = f'M{f(x - 7)},{f(base)}Q{f(x + lean * 0.2)},{f(base - h * 0.5)} {f(top[0] - 4)},{f(top[1])}L{f(top[0] + 4)},{f(top[1])}Q{f(x + lean * 0.3 + 10)},{f(base - h * 0.5)} {f(x + 7)},{f(base)}Z'
    fronds = []
    for ang in (-160, -125, -90, -55, -20, 15, 200):
        a = math.radians(ang)
        ex, ey = top[0] + math.cos(a) * 120, top[1] + math.sin(a) * 60 + 40
        mx, my = top[0] + math.cos(a) * 60, top[1] - 30 + math.sin(a) * 20
        fronds.append(f'M{f(top[0])},{f(top[1])}Q{f(mx)},{f(my)} {f(ex)},{f(ey)}Q{f(mx)},{f(my + 14)} {f(top[0])},{f(top[1] + 6)}Z')
    return trunk, ''.join(fronds)


def house(x, base, w, h, roof=0.35):
    """Box with a pitched roof; returns (walls, roof)."""
    return rect(x, base - h, w, h), poly([(x - 6, base - h), (x + w / 2, base - h - w * roof), (x + w + 6, base - h)])


def arch(x, base, w, h):
    """Round-topped opening."""
    r = w / 2
    return f'M{f(x)},{f(base)}V{f(base - h + r)}A{f(r)},{f(r)} 0,0 1 {f(x + w)},{f(base - h + r)}V{f(base)}Z'


def pointed_arch(x, base, w, h):
    return f'M{f(x)},{f(base)}V{f(base - h * 0.62)}Q{f(x)},{f(base - h * 0.9)} {f(x + w / 2)},{f(base - h)}Q{f(x + w)},{f(base - h * 0.9)} {f(x + w)},{f(base - h * 0.62)}V{f(base)}Z'


def star5(cx, cy, r, rot=-90):
    pts = []
    for i in range(10):
        rr = r if i % 2 == 0 else r * 0.4
        a = math.radians(rot + i * 36)
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return poly(pts)


def gull(x, y, s=1.0):
    return f'M{f(x - 16 * s)},{f(y)}q{f(8 * s)},{f(-9 * s)} {f(16 * s)},0q{f(8 * s)},{f(-9 * s)} {f(16 * s)},0'
