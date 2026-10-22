import random
from vec import *


def haci_bayram():
    s = Scene('Ankara: Hacı Bayram Veli Mosque beside the walls of the Temple of Augustus, the castle behind.')
    s.sky([(0, '#FF1B2A4E'), (0.5, '#FF7A4E6E'), (0.78, '#FFE6896A'), (1, '#FFF4B37A')], y2=900)
    # Ankara Castle on its hill
    s.fill('#FF3A2E48', smooth_ridge([(900, 760), (1250, 560), (1500, 520), (1750, 560), (1980, 640)]))
    teeth = ''.join(rect(x, 486, 22, 34) for x in range(1300, 1720, 40))
    s.fill('#FF3A2E48', rect(1290, 500, 440, 40) + teeth + rect(1360, 440, 60, 80) + rect(1600, 450, 56, 70))
    s.fill('#FF4A3A40', hills(3, 900, 18, 6))
    # Temple of Augustus: a ruined stone wall with its great doorway
    wall = '#FFB08A6E'
    s.fill(wall, 'M1060,940L1060,610L1100,600L1150,590L1190,610L1240,585L1300,600L1340,575L1400,600L1460,590L1500,620L1500,940Z')
    s.fill('#FF4A2F2A', rect(1210, 700, 100, 240))
    s.fill('#FF9A765C', ''.join(rect(1070, y, 420, 3) for y in range(640, 930, 34)))
    # The mosque: plastered walls, lead hipped roof and the minaret
    s.fill('#FFE8D9C0', rect(400, 690, 560, 250))
    s.fill('#FF7C8B96', poly([(380, 690), (470, 600), (890, 600), (980, 690)]))
    s.fill('#FF5D6A74', poly([(470, 600), (890, 600), (980, 690), (820, 690)]))
    s.fill('#FF5A4A3E', ''.join(arch(440 + i * 80, 800, 34, 70) for i in range(7)) + ''.join(arch(440 + i * 80, 900, 34, 60) for i in range(7)))
    s.fill('#FFC9B79C', rect(1000, 560, 30, 380) + rect(992, 640, 46, 12))
    s.fill('#FF7C8B96', poly([(996, 560), (1015, 470), (1034, 560)]))
    s.fill('#FF2E2630', rect(0, 930, W, 150))
    s.fill('#FF1F2A24', tree_blob(300, 900, 60, 1) + tree_blob(1650, 910, 70, 2) + tree_blob(1820, 920, 55, 3))
    return s


def izmir():
    s = Scene('İzmir: the Konak Clock Tower among palms by the bay at sunset.')
    s.sky([(0, '#FF22305E'), (0.45, '#FFD4637A'), (0.62, '#FFFFA26B'), (0.7, '#FFFFD08A'), (1, '#FFFFD08A')], y2=760)
    s.sun(1450, 700, 60, '#FFFFE9B8', 'FFD08A', 500)
    s.fill('#FF7A5878', hills(4, 640, 40, 6, bottom=730))
    s.fill('#FFF3C9A0', ''.join(circle(x, 650 + (x % 7) * 9, 3) for x in range(40, 1900, 37)))
    s.water(720, [(0, '#FFE0907A'), (0.5, '#FF6E4E78'), (1, '#FF2E2A50')])
    s.reflection(1450, 720, '#99FFD08A', seed=3, rows=5)
    s.fill('#FF2A2238', 'M1180,790L1380,790L1360,812L1200,812Z' + rect(1230, 770, 110, 20) + rect(1270, 755, 30, 15))
    s.fill('#FF3B2C3A', rect(0, 840, W, 240))
    stone, shade, dark = '#FFEBDDC2', '#FFC7B497', '#FF6E5A50'
    cx = 960
    s.fill(stone, rect(cx - 90, 860, 180, 40) + rect(cx - 66, 720, 132, 140) + rect(cx - 54, 610, 108, 110) + rect(cx - 42, 520, 84, 90) + rect(cx - 30, 460, 60, 60))
    s.fill(shade, rect(cx + 30, 720, 36, 140) + rect(cx + 24, 610, 30, 110) + rect(cx + 18, 520, 24, 90))
    s.fill(dark, arch(cx - 40, 860, 34, 80) + arch(cx + 6, 860, 34, 80) + arch(cx - 34, 710, 26, 60) + arch(cx + 8, 710, 26, 60))
    s.fill('#FFFFFFFF', circle(cx, 565, 26))
    s.fill(dark, rect(cx - 2, 548, 4, 18) + rect(cx - 2, 563, 14, 4))
    s.fill(stone, dome(cx, 460, 34, 30) + rect(cx - 3, 400, 6, 26))
    s.fill('#FFF0C080', circle(cx, 398, 7))
    for x, h, lean in [(560, 380, 30), (720, 300, -20), (1220, 330, 25), (1400, 400, -30)]:
        trunk, fronds = palm(x, 850, h, lean)
        s.fill('#FF1E1424', trunk + fronds)
    return s


def sivas():
    s = Scene('Sivas: the twin tiled minarets and carved portal of Gök Medrese over the snowy steppe.')
    s.sky([(0, '#FF22345E'), (0.55, '#FF6F8FBF'), (1, '#FFE3D3C8')], y2=860)
    s.fill('#FFE8EEF4', hills(3, 760, 50, 5))
    s.fill('#FFC9D4E0', hills(5, 860, 30, 6))
    stone, niche, brick, tile1, tile2 = '#FFC8B49A', '#FF8E765E', '#FFA8664A', '#FF2E8FB8', '#FF59C3D9'
    s.fill('#FFB09C82', rect(420, 640, 1080, 300))
    s.fill(stone, rect(760, 560, 400, 380))
    s.fill(niche, pointed_arch(830, 940, 260, 330))
    s.fill('#FF6E5A46', pointed_arch(880, 940, 160, 220))
    s.fill('#FFB8A286', ''.join(arch(840 + i * 30, 640 + (i % 2) * 14, 24, 30) for i in range(8)))
    for mx in (790, 1130):
        s.fill(brick, rect(mx - 30, 300, 60, 260))
        s.fill(tile1, rect(mx - 30, 330, 60, 18) + rect(mx - 30, 400, 60, 18) + rect(mx - 30, 470, 60, 18))
        s.fill(tile2, rect(mx - 30, 352, 60, 8) + rect(mx - 30, 422, 60, 8) + rect(mx - 30, 492, 60, 8))
        s.fill(stone, rect(mx - 40, 290, 80, 14))
        s.fill(brick, poly([(mx - 24, 290), (mx, 230), (mx + 24, 290)]))
    s.fill('#FFF2F6FA', rect(0, 930, W, 150))
    s.stars(5, 30, 300, '#80FFFFFF')
    return s


def ani():
    s = Scene('Ani: the ruined cathedral and city walls on the plateau above the Arpaçay gorge.')
    s.sky([(0, '#FF3E4A7A'), (0.5, '#FFC9876A'), (0.8, '#FFF2C38B'), (1, '#FFF2C38B')], y2=820)
    s.fill('#FF9A8A6A', hills(2, 640, 30, 5, bottom=800))
    s.fill('#FFA49556', smooth_ridge([(-60, 760), (600, 740), (1200, 760), (1500, 740), (1980, 760)]))
    s.fill('#FF4E3A34', 'M1450,1080L1520,800L1600,760L1700,780L1780,820L1980,800L1980,1080Z')
    s.fill('#FF7A5A48', 'M1500,1080L1560,820L1620,790L1700,820L1680,1080Z')
    s.fill('#FF7C6F3C', hills(8, 900, 20, 6))
    red, shade, dark = '#FFB07A5E', '#FF8E5E46', '#FF4A2E26'
    s.fill(red, 'M720,880L720,560L800,540L840,520L880,470L940,430L1000,470L1030,500L1080,540L1120,540L1160,600L1160,880Z')
    s.fill(shade, 'M1030,500L1080,540L1120,540L1160,600L1160,880L1060,880Z')
    s.fill(dark, rect(900, 700, 70, 180) + rect(790, 640, 20, 70) + rect(1080, 650, 20, 70) + rect(930, 560, 14, 50))
    s.stroke('#99643E30', 4, ''.join(f'M{x},880V640Q{x + 25},600 {x + 50},640V880' for x in (740, 990, 1090)))
    s.fill(red, rect(330, 700, 110, 180) + poly([(320, 700), (385, 610), (450, 700)]))
    s.fill(dark, rect(375, 780, 20, 60))
    s.fill('#FF9A6E55', rect(0, 760, 260, 120) + ''.join(rect(x, 736, 18, 26) for x in range(0, 260, 34)) + circle(250, 780, 40))
    return s


def mardin():
    s = Scene('Mardin: stone houses stepping down the hill above the Mesopotamian plain.')
    s.sky([(0, '#FF3B5A8C'), (0.5, '#FFE8A577'), (0.75, '#FFF7D29A'), (1, '#FFF7D29A')], y2=760)
    s.linear([(0, '#FFE6C48E'), (1, '#FFB08A5E')], y1=700, y2=H, d=f'M0,700h{W}V{H}H0z')
    s.fill('#40FFFFFF', ''.join(rect(1100 + i * 60, 760 + i * 18, 900, 3) for i in range(6)))
    s.fill('#FF9C7650', smooth_ridge([(-60, 260), (300, 280), (700, 420), (1100, 700), (1400, 1080)]))
    # The castle on the summit
    s.fill('#FF8A6646', rect(80, 200, 380, 80) + ''.join(rect(x, 180, 22, 26) for x in range(80, 460, 40)))
    rng = random.Random(6)
    walls, tops, wins = [], [], []
    for row in range(9):
        y = 340 + row * 72
        for col in range(14):
            x = col * 82 + rng.randint(-6, 6)
            if y < 260 + x * 0.42 + 60 or x > 1100 + row * 30:
                continue
            w, h = rng.randint(64, 84), rng.randint(56, 72)
            walls.append(rect(x, y - h, w, h))
            tops.append(rect(x - 2, y - h - 6, w + 4, 8))
            wins.append(arch(x + w * 0.2, y - h * 0.25, 12, 22) + arch(x + w * 0.6, y - h * 0.25, 12, 22))
    s.fill('#FFE2C79A', ''.join(walls))
    s.fill('#FFEED8B2', ''.join(tops))
    s.fill('#FF6B4A33', ''.join(wins))
    s.fill('#FFE9D2A8', rect(640, 300, 46, 260) + rect(630, 290, 66, 14) + dome(663, 290, 28, 26))
    s.fill('#FFC9A87A', ''.join(rect(648 + i * 10, 310, 3, 240) for i in range(4)))
    s.fill('#FFE9D2A8', dome(760, 470, 70, 60) + rect(690, 470, 140, 60))
    return s


def selimiye():
    s = Scene('Edirne: Selimiye Mosque, its great dome and four slender minarets.')
    s.sky([(0, '#FF2F4D86'), (0.6, '#FF9BB7D9'), (1, '#FFF2D7B6')], y2=880)
    s.clouds(4, 4, 180, 360, '#55FFFFFF')
    stone, lead = '#FFE2D8C6', '#FF7E8C98'
    cx, base = 960, 880
    s.fill(stone, rect(cx - 330, base - 260, 660, 260) + rect(cx - 210, base - 330, 420, 80))
    s.fill(lead, dome(cx, base - 330, 200, 150) + dome(cx - 260, base - 260, 60, 50) + dome(cx + 260, base - 260, 60, 50))
    s.fill(stone, ''.join(rect(cx + dx - 18, base - 400, 36, 80) + poly([(cx + dx - 18, base - 400), (cx + dx, base - 440), (cx + dx + 18, base - 400)])
                          for dx in (-200, 200, -110, 110)))
    s.fill(stone, rect(cx - 6, base - 520, 12, 40))
    s.fill('#FFF0C080', circle(cx, base - 528, 8))
    s.fill(stone, ''.join(minaret(cx + dx, base, 640, 18, 70) for dx in (-380, -330, 330, 380)))
    s.fill(lead, ''.join(poly([(cx + dx - 11, base - 640), (cx + dx, base - 710), (cx + dx + 11, base - 640)]) for dx in (-380, -330, 330, 380)))
    s.fill('#FF8A7E6E', ''.join(arch(cx - 300 + i * 70, base - 40, 30, 70) for i in range(9)))
    s.fill('#FF2F5A3A', rect(0, 860, W, 220) + ''.join(tree_blob(x, 860, 48, x) for x in range(-20, 1960, 90)))
    return s


def maidens_tower():
    s = Scene('Istanbul: the Maiden\'s Tower on its islet at dusk, the old city across the water.')
    s.sky([(0, '#FF0E1A3A'), (0.55, '#FF3A3F7A'), (0.8, '#FFCF7A6E'), (1, '#FFF2A27A')], y2=640)
    s.stars(8, 50, 330)
    s.fill('#FF1F1A3A', hills(3, 600, 10, 6, bottom=650) + mosque(500, 640, 0.55) + mosque(1500, 640, 0.45, minarets=2))
    s.water(640, [(0, '#FF7A5A7A'), (0.3, '#FF2E2E5A'), (1, '#FF0B1230')])
    s.fill('#55FFD49A', ''.join(rect(900 + (i % 3) * 40, 680 + i * 24, 140 - i * 6, 4) for i in range(14)))
    s.fill('#FF3A3448', ellipse(1100, 800, 240, 40))
    tower = '#FFEDE2CF'
    s.fill(tower, rect(950, 700, 300, 100) + rect(1070, 470, 70, 230) + rect(1060, 460, 90, 14) + rect(1080, 420, 50, 40))
    s.fill('#FF9EA8B4', poly([(1076, 420), (1105, 360), (1134, 420)]) + dome(1180, 700, 34, 30))
    s.fill('#FFFFD27A', ''.join(rect(980 + i * 50, 735, 14, 22) for i in range(5)) + rect(1093, 520, 22, 40) + rect(1093, 600, 22, 40))
    return s


def mevlana():
    s = Scene('Konya: the fluted green dome of the Mevlana Museum beside the Selimiye Mosque.')
    s.sky([(0, '#FF3C6FB4'), (0.7, '#FFB8D4EC'), (1, '#FFE7F0F7')], y2=880)
    s.clouds(9, 4, 150, 330, '#66FFFFFF')
    stone, lead = '#FFE4DACA', '#FF8494A2'
    s.fill(stone, mosque(1450, 880, 0.9, minarets=2))
    s.fill(stone, rect(380, 640, 760, 240))
    s.fill(lead, dome(520, 640, 70, 56) + dome(700, 640, 60, 50) + dome(1040, 640, 70, 56))
    green, dark = '#FF31A88F', '#FF1F7F6C'
    s.fill(green, rect(810, 440, 120, 200))
    s.fill(dark, ''.join(rect(818 + i * 18, 446, 4, 190) for i in range(6)))
    s.fill(green, poly([(800, 440), (870, 250), (940, 440)]))
    s.stroke('#FF1F7F6C', 3, ''.join(f'M{870},{250}L{810 + i * 26},{440}' for i in range(6)))
    s.fill('#FFF0C080', circle(870, 238, 9) + rect(866, 200, 8, 30))
    s.fill('#FF7C6450', ''.join(arch(420 + i * 90, 880, 40, 90) for i in range(4)))
    s.fill('#FF3E6B45', rect(0, 860, W, 220) + ''.join(tree_blob(x, 870, 52, x) for x in range(-20, 1960, 100)))
    return s


def ishak_pasha():
    s = Scene('Doğubayazıt: İshak Paşa Palace on its rocky slope, Ağrı rising behind.')
    s.sky([(0, '#FF2A5C9E'), (0.7, '#FFA9CBE8'), (1, '#FFE6EFF5')], y2=900)
    s.fill('#FF9A98B2', poly([(900, 700), (1400, 260), (1900, 700)]))
    s.fill('#FFF4F6FA', poly([(1310, 340), (1400, 260), (1490, 340), (1450, 330), (1400, 360), (1350, 332)]))
    s.fill('#FF8E7A6E', smooth_ridge([(-60, 420), (200, 360), (500, 460), (800, 560), (1200, 680), (1980, 760)]))
    s.fill('#FF6E5A50', smooth_ridge([(-60, 560), (300, 520), (700, 640), (1100, 760), (1980, 860)]))
    stone, shade, dark = '#FFD8B58A', '#FFB08E66', '#FF5A3E2E'
    s.fill(stone, rect(500, 640, 760, 200) + rect(680, 560, 300, 80))
    s.fill(shade, rect(1000, 640, 260, 200))
    s.fill('#FFB8C4CE', dome(830, 560, 90, 70))
    s.fill(stone, rect(1120, 380, 44, 260) + rect(1110, 450, 64, 10))
    s.fill('#FFB5523F', ''.join(rect(1120, y, 44, 14) for y in (400, 470, 540, 600)))
    s.fill(stone, poly([(1116, 380), (1142, 330), (1168, 380)]))
    s.fill(dark, pointed_arch(760, 840, 110, 170) + ''.join(arch(560 + i * 60, 720, 24, 40) for i in range(3)) + ''.join(arch(1030 + i * 60, 720, 24, 40) for i in range(3)))
    s.fill('#FF5E4A3E', hills(7, 900, 25, 6))
    return s


def erzurum():
    s = Scene('Erzurum: the twin brick minarets of the Çifte Minareli Medrese against snowy Palandöken.')
    s.sky([(0, '#FF1E3A6E'), (0.6, '#FF7FA8D6'), (1, '#FFDCE8F2')], y2=860)
    s.fill('#FFE6EDF5', peaks(3, 700, 260, 9, jitter=0.4))
    s.fill('#FFC4D1E0', peaks(5, 760, 160, 11, jitter=0.5))
    stone, niche, brick, tile = '#FFC9B595', '#FF8A735A', '#FFA85A42', '#FF2E8FB8'
    s.fill(stone, rect(720, 520, 480, 400))
    s.fill(niche, pointed_arch(800, 920, 320, 360))
    s.fill('#FF6A5440', pointed_arch(860, 920, 200, 250))
    for mx in (770, 1150):
        s.fill(brick, rect(mx - 34, 180, 68, 340))
        s.stroke('#FF8A4532', 4, ''.join(f'M{mx - 28 + i * 14},190V510' for i in range(5)), 'butt')
        s.fill(tile, rect(mx - 34, 230, 68, 14) + rect(mx - 34, 330, 68, 14) + rect(mx - 34, 430, 68, 14))
        s.fill(stone, rect(mx - 44, 170, 88, 14))
        s.fill(brick, poly([(mx - 26, 170), (mx, 120), (mx + 26, 170)]))
    s.fill('#FFF2F6FA', rect(0, 910, W, 170))
    s.fill('#FFDCE4EE', hills(4, 930, 15, 6))
    return s


SCENES = dict(haci_bayram=haci_bayram, izmir=izmir, sivas=sivas, ani=ani, mardin=mardin, selimiye=selimiye,
              maidens_tower=maidens_tower, mevlana=mevlana, ishak_pasha=ishak_pasha, erzurum=erzurum)
