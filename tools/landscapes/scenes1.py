import math
import random
from vec import *


def cappadocia():
    s = Scene('Cappadocia: fairy chimneys at dawn with hot-air balloons rising.')
    s.sky([(0, '#FF2E3A6E'), (0.45, '#FF9C6B98'), (0.72, '#FFF6A57A'), (1, '#FFFFD7A0')])
    s.sun(1450, 790, 70, '#FFFFF0C8', 'FFE0A8', 700)

    def chimney(x, base, h, w):
        body = (f'M{f(x - w / 2)},{f(base)}C{f(x - w * 0.36)},{f(base - h * 0.5)} {f(x - w * 0.18)},{f(base - h * 0.86)} '
                f'{f(x - w * 0.12)},{f(base - h)}L{f(x + w * 0.12)},{f(base - h)}C{f(x + w * 0.18)},{f(base - h * 0.86)} '
                f'{f(x + w * 0.36)},{f(base - h * 0.5)} {f(x + w / 2)},{f(base)}Z')
        return body, ellipse(x, base - h, w * 0.24, w * 0.1)

    for seed, base, hmin, hmax, wmin, wmax, body_c, cap_c in [
        (3, 840, 110, 200, 70, 110, '#FFC9967C', '#FF9E6E58'),
        (5, 950, 170, 290, 100, 150, '#FFAE6F55', '#FF7E4A3A'),
        (9, 1090, 260, 420, 150, 220, '#FF7E4636', '#FF552B22')]:
        rng = random.Random(seed)
        s.fill(body_c, hills(seed, base - 30, 25, 5))
        bodies, caps = [], []
        x = rng.randint(-40, 40)
        while x < W + 80:
            b, c = chimney(x, base, rng.randint(hmin, hmax), rng.randint(wmin, wmax))
            bodies.append(b); caps.append(c)
            x += rng.randint(wmax, wmax * 2)
        s.fill(body_c, ''.join(bodies))
        s.fill(cap_c, ''.join(caps))

    def balloon(x, y, r, c1, c2):
        env = f'M{f(x)},{f(y + r * 1.2)}C{f(x - r * 1.15)},{f(y + r * 0.55)} {f(x - r * 1.15)},{f(y - r)} {f(x)},{f(y - r)}C{f(x + r * 1.15)},{f(y - r)} {f(x + r * 1.15)},{f(y + r * 0.55)} {f(x)},{f(y + r * 1.2)}Z'
        stripe = f'M{f(x)},{f(y + r * 1.2)}C{f(x - r * 0.45)},{f(y + r * 0.55)} {f(x - r * 0.45)},{f(y - r)} {f(x)},{f(y - r)}C{f(x + r * 0.45)},{f(y - r)} {f(x + r * 0.45)},{f(y + r * 0.55)} {f(x)},{f(y + r * 1.2)}Z'
        s.fill(c1, env)
        s.fill(c2, stripe)
        s.fill('#FF4E342E', rect(x - r * 0.16, y + r * 1.32, r * 0.32, r * 0.24))
        s.stroke('#99332822', max(1.5, r * 0.03), f'M{f(x - r * 0.2)},{f(y + r * 1.15)}L{f(x - r * 0.14)},{f(y + r * 1.33)}M{f(x + r * 0.2)},{f(y + r * 1.15)}L{f(x + r * 0.14)},{f(y + r * 1.33)}')

    for x, y, r, c1, c2 in [(300, 260, 46, '#FFE53935', '#FFFDD835'), (560, 150, 30, '#FF26A69A', '#FFFFF59D'),
                            (820, 330, 60, '#FF7E57C2', '#FFFFB74D'), (1080, 190, 38, '#FFFB8C00', '#FF1E88E5'),
                            (1300, 380, 52, '#FFD81B60', '#FFFFFFFF'), (1560, 230, 34, '#FF43A047', '#FFFFEB3B'),
                            (1760, 120, 24, '#FFE53935', '#FF3949AB'), (180, 520, 22, '#FFFFB300', '#FF8E24AA'),
                            (1700, 470, 28, '#FF1E88E5', '#FFFF7043')]:
        balloon(x, y, r, c1, c2)
    return s


def istanbul():
    s = Scene('Istanbul: the Bosphorus at sunset, domes and minarets, Galata Tower and the bridge.')
    s.sky([(0, '#FF1D2B53'), (0.33, '#FF6D3A72'), (0.55, '#FFE0685A'), (0.64, '#FFFDB57A'), (1, '#FFFDB57A')])
    s.sun(1180, 630, 80, '#FFFFE3A8', 'FFD08A', 600)
    s.water(660, [(0, '#FF8A4E6E'), (0.25, '#FF3A2A58'), (1, '#FF0E1230')])
    s.reflection(1180, 660, '#88FFC98A', seed=4, rows=14)
    sil = '#FF1A1432'
    s.fill('#FF2D2448', hills(2, 640, 14, 8, bottom=672))
    s.fill(sil, mosque(620, 672, 1.15))
    s.fill(sil, mosque(250, 672, 0.7, minarets=2))
    # Galata Tower
    s.fill(sil, rect(1470, 470, 56, 202) + rect(1462, 470, 72, 16) + poly([(1464, 470), (1498, 380), (1532, 470)]) + rect(0, 650, 1920, 30))
    s.fill('#FF221A3C', ''.join(rect(x, 640 - h, 40, h) for x, h in [(870, 40), (930, 60), (990, 45), (1040, 70), (1110, 50), (1360, 55), (1410, 80), (1550, 60), (1600, 45)]))
    # Bosphorus Bridge
    s.stroke(sil, 8, 'M1560,600L1980,600', 'butt')
    s.fill(sil, rect(1640, 470, 12, 200) + rect(1880, 470, 12, 200))
    s.stroke(sil, 4, 'M1560,520Q1646,600 1646,470Q1766,600 1886,470Q1930,520 1980,540')
    for x, y in [(380, 230), (430, 200), (1010, 280), (1050, 300), (1270, 170)]:
        s.stroke('#CCFFFFFF', 3, gull(x, y))
    return s


def pamukkale():
    s = Scene('Pamukkale: white travertine terraces stepping down the hill, each holding a turquoise pool.')
    s.sky([(0, '#FF5E9CD6'), (0.4, '#FFA9D3F2'), (1, '#FFE6F4FB')], y2=520)
    s.clouds(21, 3, 120, 260, '#66FFFFFF')
    s.fill('#FF8FA6B8', hills(3, 420, 30, 5))
    s.fill('#FF7C9A78', hills(6, 470, 20, 7))
    # The white hill falling from upper left to lower right
    s.fill('#FFEEF3F6', smooth_ridge([(-60, 440), (400, 470), (900, 560), (1400, 700), (1980, 820)]))
    s.fill('#FFDDE6EC', smooth_ridge([(-60, 640), (500, 700), (1000, 800), (1500, 920), (1980, 1000)]))

    def bowl(x, y, w, h):
        rim = f'M{f(x - 10)},{f(y)}L{f(x + w + 10)},{f(y)}Q{f(x + w + 4)},{f(y + h + 22)} {f(x + w / 2)},{f(y + h + 22)}Q{f(x - 4)},{f(y + h + 22)} {f(x - 10)},{f(y)}Z'
        pool = f'M{f(x)},{f(y + 4)}L{f(x + w)},{f(y + 4)}Q{f(x + w * 0.96)},{f(y + h)} {f(x + w / 2)},{f(y + h)}Q{f(x + w * 0.04)},{f(y + h)} {f(x)},{f(y + 4)}Z'
        return rim, pool

    rng = random.Random(4)
    rims, pools, drips = [], [], []
    for row in range(6):
        y = 470 + row * 100
        w = 150 + row * 26
        h = 34 + row * 7
        x = -80 + (row % 2) * w * 0.5 + row * 40
        while x < W:
            ry = y + (x / W) * 140
            r, p = bowl(x, ry, w, h)
            rims.append(r); pools.append(p)
            for k in range(3):
                dx = x + w * (0.25 + 0.25 * k)
                drips.append(rect(dx, ry + h + 18, 4, rng.randint(20, 60)))
            x += w + rng.randint(10, 40)
    s.fill('#FFC9D5DC', ''.join(drips))
    s.fill('#FFF7FAFB', ''.join(rims))
    s.linear([(0, '#FF8EE3EC'), (1, '#FF2FAFC6')], y1=470, y2=1080, d=''.join(pools))
    s.fill('#FF4E7A55', ''.join(tree_blob(x, 470 + x * 0.04, 26, x) for x in range(40, 600, 70)))
    return s


def oludeniz():
    s = Scene('Ölüdeniz: the turquoise lagoon under Babadağ, paragliders drifting down.')
    s.sky([(0, '#FF4E9EDC'), (0.5, '#FFBFE3F8'), (1, '#FFE3F3FB')])
    s.clouds(7, 5, 120, 300, '#59FFFFFF')
    s.fill('#FF6E9282', smooth_ridge([(1000, 560), (1250, 470), (1500, 500), (1750, 440), (1980, 480)]))
    s.fill('#FF3E6B4F', smooth_ridge([(-60, 640), (150, 380), (420, 190), (700, 330), (950, 520), (1150, 640)]))
    s.fill('#FF2F5540', smooth_ridge([(300, 640), (520, 420), (760, 470), (1000, 620)]))
    s.linear([(0, '#FF24CFC8'), (0.55, '#FF1BA8C2'), (1, '#FF1559A8')], x1=0, y1=0, x2=W, y2=0, d=f'M0,620h{W}V{H}H0z')
    s.fill('#FFF1DEB4', 'M520,1080C700,860 980,760 1260,720C1500,690 1700,700 1980,690L1980,740C1700,760 1460,770 1260,800C1000,840 820,940 700,1080Z')
    s.fill('#FF3E7A50', ''.join(tree_blob(x, 700 + (x - 1200) * -0.05, 22, x) for x in range(1150, 1900, 60)))
    s.fill('#FF2F6040', smooth_ridge([(-60, 760), (120, 690), (300, 720), (470, 860), (560, 1080)]))
    for x, y, c in [(760, 210, '#FFE53935'), (980, 300, '#FFFDD835'), (1240, 180, '#FF1E88E5'), (1500, 330, '#FFFB8C00')]:
        s.stroke(c, 14, f'M{x - 46},{y + 10}Q{x},{y - 22} {x + 46},{y + 10}')
        s.stroke('#99333333', 1.5, f'M{x - 44},{y + 10}L{x},{y + 62}L{x + 44},{y + 10}')
        s.fill('#FF333333', circle(x, y + 66, 6))
    return s


def ararat():
    s = Scene('Mount Ararat: the snow-capped peaks of Greater and Lesser Ağrı over the plain.')
    s.sky([(0, '#FF16305E'), (0.5, '#FF4E86C4'), (1, '#FFCFE3F2')], y2=820)
    s.clouds(12, 4, 240, 420, '#33FFFFFF', 1.4)
    s.fill('#FF8783A0', poly([(150, 820), (520, 500), (900, 820)]))
    s.fill('#FFF2F5FA', poly([(462, 552), (520, 500), (580, 552), (548, 545), (520, 560), (494, 546)]))
    big = [(540, 840), (820, 560), (1040, 330), (1120, 250), (1200, 300), (1420, 520), (1760, 840)]
    s.fill('#FF7D7A95', smooth_ridge(big, 840))
    s.fill('#FF5E5B78', poly([(1120, 250), (1200, 300), (1420, 520), (1760, 840), (1200, 840), (1160, 500)]))
    s.fill('#FFF5F7FA', 'M1002,372C1060,300 1100,250 1120,250C1150,250 1180,280 1235,330L1260,370L1225,360L1200,390L1170,360L1140,400L1110,368L1080,410L1050,380L1020,400Z')
    s.fill('#FFDCE3EE', 'M1120,250C1150,250 1180,280 1235,330L1260,370L1225,360L1200,390L1170,360L1150,330Z')
    s.fill('#FF8D9B6A', hills(4, 820, 20, 5))
    s.fill('#FF5E6B44', hills(6, 900, 30, 6))
    s.fill('#FF3E4A2E', hills(9, 1000, 25, 7))
    return s


def nemrut():
    s = Scene('Mount Nemrut: the colossal stone heads on the summit terrace at sunrise.')
    s.sky([(0, '#FF2B1B3A'), (0.4, '#FF8E3B5E'), (0.68, '#FFE2704F'), (0.84, '#FFFFC07A'), (1, '#FFFFC07A')])
    s.sun(1540, 720, 110, '#FFFFE2A0', 'FFD08A', 700)
    s.fill('#FF8A6550', smooth_ridge([(500, 860), (900, 560), (1150, 430), (1250, 425), (1500, 560), (1980, 860)]))
    s.fill('#FF6E4E3D', poly([(1250, 425), (1500, 560), (1980, 860), (1300, 860)]))
    s.fill('#FF4A3329', hills(5, 880, 15, 6))
    sil = '#FF2B1E1C'

    def head(x, base, h, hat=True):
        d = [rect(x - h * 0.42, base - h * 0.32, h * 0.84, h * 0.32),
             ellipse(x, base - h * 0.55, h * 0.24, h * 0.3)]
        if hat:
            d.append(f'M{f(x - h * 0.26)},{f(base - h * 0.66)}Q{f(x - h * 0.2)},{f(base - h * 1.12)} {f(x + h * 0.04)},{f(base - h * 1.22)}Q{f(x + h * 0.2)},{f(base - h * 1.0)} {f(x + h * 0.26)},{f(base - h * 0.66)}Z')
        return ''.join(d)

    s.fill(sil, head(230, 980, 300) + head(560, 990, 340) + head(900, 970, 280) + head(1240, 1000, 310, hat=False))
    s.fill(sil, rect(0, 975, W, 120) + poly([(1060, 1000), (1120, 940), (1200, 960), (1180, 1000)]))
    return s


def uzungol():
    s = Scene('Uzungöl: misty Black Sea mountains, the lake and a white mosque on the shore.')
    s.sky([(0, '#FFD9E6E1'), (1, '#FFAFC8BE')], y2=700)
    for seed, y, amp, c in [(2, 320, 120, '#FF9DBDB0'), (4, 440, 140, '#FF6D9C82'), (6, 560, 130, '#FF3F6E57'), (8, 660, 90, '#FF27493B')]:
        s.fill(c, hills(seed, y, amp, 5, 780))
        s.linear([(0, '#00E8F0EC'), (1, '#70E8F0EC')], y1=y - 40, y2=y + 120, d=rect(0, y - 40, W, 160))
    s.fill('#FF1E3C31', pine_row(14, 770, 60, 120, 38))
    s.water(770, [(0, '#FF6E9D93'), (1, '#FF2E5751')])
    s.fill('#55FFFFFF', ''.join(rect(x, y, w, 3) for x, y, w in [(200, 820, 300), (900, 860, 420), (1400, 830, 260), (600, 930, 380), (1200, 980, 300)]))
    s.fill('#FFF4F4F0', rect(860, 700, 110, 70) + dome(915, 700, 42, 36) + minaret(1000, 770, 190, 12, 40))
    s.fill('#FF7C8C96', dome(915, 700, 30, 26))
    for x, y in [(300, 760), (420, 748), (1220, 742), (1340, 756), (1500, 750)]:
        walls, roof = house(x, y, 46, 34)
        s.fill('#FFEDE6DA', walls)
        s.fill('#FFB5523F', roof)
    return s


def ephesus():
    s = Scene('Ephesus: the two-storey facade of the Library of Celsus at golden hour.')
    s.sky([(0, '#FF5B3A6E'), (0.45, '#FFE0855E'), (0.8, '#FFF8C27E'), (1, '#FFF8C27E')])
    s.fill('#FFA06E57', hills(3, 600, 60, 5))
    s.fill('#FFC9A27A', rect(0, 960, W, 120))
    stone, shade, dark = '#FFE9C9A0', '#FFC29A70', '#FF6E4532'
    x0, x1 = 520, 1400
    s.fill(shade, rect(x0, 380, x1 - x0, 580))
    s.fill(dark, arch(640, 900, 110, 210) + arch(905, 900, 110, 250) + arch(1170, 900, 110, 210))
    s.fill(dark, rect(650, 470, 90, 120) + rect(915, 450, 90, 140) + rect(1180, 470, 90, 120))
    s.fill(stone, rect(x0 - 40, 900, x1 - x0 + 80, 30) + rect(x0 - 70, 930, x1 - x0 + 140, 30))
    cols = []
    for px in (560, 800, 1030, 1270):
        cols.append(rect(px - 22, 860, 134, 40))
        for cx in (px, px + 70):
            cols.append(rect(cx, 620, 34, 240) + rect(cx - 6, 612, 46, 12))
    s.fill(stone, ''.join(cols))
    s.fill(stone, rect(x0 - 30, 585, x1 - x0 + 60, 30))
    up = []
    for px in (680, 920, 1160):
        up.append(rect(px - 14, 560, 112, 26))
        for cx in (px, px + 64):
            up.append(rect(cx, 400, 28, 160) + rect(cx - 5, 392, 38, 10))
        up.append(poly([(px - 20, 392), (px + 46, 340), (px + 112, 392)]))
    s.fill(stone, ''.join(up) + rect(x0 + 40, 380, x1 - x0 - 80, 14))
    s.fill('#FFF6DFBE', ''.join(rect(cx + 4, 625, 6, 230) for px in (560, 800, 1030, 1270) for cx in (px, px + 70)))
    return s


def anitkabir():
    s = Scene('Ankara: Anıtkabir at dusk with the Turkish flag.')
    s.sky([(0, '#FF0F2043'), (0.5, '#FF3B4F8C'), (0.8, '#FFE79A62'), (0.9, '#FFF4B97E'), (1, '#FFF4B97E')])
    s.stars(31, 40, 360, '#99FFFFFF')
    s.fill('#FF2E3550', hills(2, 830, 20, 6))
    stone, gap = '#FFE3D3B4', '#FF9C8668'
    s.fill(stone, rect(380, 860, 1160, 40) + rect(330, 900, 1260, 40) + rect(280, 940, 1360, 140))
    s.fill(gap, rect(560, 615, 800, 245))
    s.fill(stone, ''.join(rect(568 + i * 57, 615, 34, 245) for i in range(14)))
    s.fill(stone, rect(540, 580, 840, 40) + rect(620, 548, 680, 36) + rect(700, 520, 520, 32))
    s.fill('#FFF2E6CE', ''.join(rect(574 + i * 57, 620, 8, 235) for i in range(14)))
    s.fill('#FF14233A', cypress(260, 960, 260, 34) + cypress(1660, 960, 260, 34) + cypress(200, 980, 200, 28) + cypress(1720, 980, 200, 28))
    s.stroke('#FFE0E0E0', 6, 'M1560,250L1560,900', 'butt')
    fx, fy, fw, fh = 1563, 255, 210, 140
    s.fill('#FFE30A17', f'M{fx},{fy}C{fx + 70},{fy - 14} {fx + 140},{fy + 14} {fx + fw},{fy}L{fx + fw},{fy + fh}C{fx + 140},{fy + fh + 14} {fx + 70},{fy + fh - 14} {fx},{fy + fh}Z')
    s.fill('#FFFFFFFF', circle(fx + 72, fy + 70, 35))
    s.fill('#FFE30A17', circle(fx + 81, fy + 70, 28))
    s.fill('#FFFFFFFF', star5(fx + 122, fy + 70, 17, rot=180))
    return s


def kas():
    s = Scene('Kaş: a Mediterranean bay at sunset, a gulet and the island across the water.')
    s.sky([(0, '#FF2C3E70'), (0.42, '#FFB65A72'), (0.58, '#FFF57C5B'), (0.62, '#FFFFD49A'), (1, '#FFFFD49A')])
    s.sun(1020, 640, 70, '#FFFFE9B8', 'FFD49A', 520)
    s.fill('#FF5A4B6E', smooth_ridge([(1200, 670), (1380, 600), (1560, 580), (1760, 610), (1980, 650)], 680))
    s.water(660, [(0, '#FFE88A6E'), (0.2, '#FF8A5070'), (0.6, '#FF2A3E6E'), (1, '#FF0B2441')])
    s.reflection(1020, 660, '#AAFFD49A', seed=9, rows=13)
    s.fill('#FF4A3C58', smooth_ridge([(-60, 380), (200, 420), (420, 520), (600, 640), (720, 760)], H))
    rng = random.Random(5)
    homes, roofs, windows = [], [], []
    for row in range(6):
        for col in range(7 - row):
            x = 20 + col * 64 + row * 34 + rng.randint(-8, 8)
            y = 470 + row * 46 + x * 0.32
            w, h = rng.randint(36, 50), rng.randint(24, 32)
            homes.append(rect(x, y - h, w, h))
            roofs.append(rect(x - 3, y - h - 4, w + 6, 5))
            windows.append(rect(x + w * 0.2, y - h * 0.65, 6, 8) + rect(x + w * 0.62, y - h * 0.65, 6, 8))
    s.fill('#FFF3E9DC', ''.join(homes))
    s.fill('#FFD9C9B6', ''.join(roofs))
    s.fill('#FF5A4B6E', ''.join(windows))
    s.fill('#FF2E5A3E', ''.join(tree_blob(30 + i * 90, 760 + i * 30, 26, i) for i in range(6)))
    hull = 'M1240,880L1500,880L1470,915L1270,915Z'
    s.fill('#FF20162A', hull + rect(1300, 862, 120, 18))
    s.stroke('#FF20162A', 4, 'M1330,862L1330,700M1430,862L1430,730M1330,700L1480,876', 'butt')
    s.fill('#CCF3E9DC', poly([(1334, 712), (1334, 852), (1400, 852)]))
    return s


SCENES = dict(cappadocia=cappadocia, istanbul=istanbul, pamukkale=pamukkale, oludeniz=oludeniz, ararat=ararat,
              nemrut=nemrut, uzungol=uzungol, ephesus=ephesus, anitkabir=anitkabir, kas=kas)
