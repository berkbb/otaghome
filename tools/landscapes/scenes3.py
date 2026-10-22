import random
from vec import *


def safranbolu():
    s = Scene('Safranbolu: Ottoman houses with white upper floors and red-tiled roofs on an autumn hillside.')
    s.sky([(0, '#FF4E7FB8'), (0.7, '#FFC6DCEE'), (1, '#FFF1E6D6')], y2=700)
    s.fill('#FF8FA06E', hills(3, 420, 40, 5))
    s.fill('#FFB98A4A', ''.join(tree_blob(x, 430 + (x % 5) * 8, 44, x) for x in range(0, 1960, 160)))
    s.fill('#FF7A6A48', smooth_ridge([(-60, 520), (500, 500), (1000, 540), (1500, 520), (1980, 560)]))
    rng = random.Random(3)
    walls, frames, roofs, bases, wins = [], [], [], [], []
    for row in range(3):
        base = 680 + row * 170
        x = -60 + (row % 2) * 140
        while x < W:
            w = rng.randint(200, 250)
            bases.append(rect(x + 10, base - 50, w - 20, 50))
            walls.append(rect(x, base - 140, w, 92))
            frames.append(''.join(rect(x + 8 + i * (w - 16) / 4, base - 140, 4, 92) for i in range(5)) + rect(x, base - 96, w, 4))
            roofs.append(poly([(x - 18, base - 140), (x + w * 0.2, base - 190), (x + w * 0.8, base - 190), (x + w + 18, base - 140)]))
            wins.append(''.join(rect(x + 22 + i * (w - 16) / 4, base - 128, 18, 26) for i in range(4)))
            x += w + rng.randint(90, 180)
    s.fill('#FFB8A890', ''.join(bases))
    s.fill('#FFF7F1E6', ''.join(walls))
    s.fill('#FF6E4A30', ''.join(frames) + ''.join(wins))
    s.fill('#FFB5523F', ''.join(roofs))
    s.fill('#FFE09A3A', ''.join(tree_blob(x, 1060, 50, x + 1) for x in range(-20, 1960, 260)))
    return s


def salda():
    s = Scene('Salda Lake: white shores and turquoise water ringed by pine-covered hills.')
    s.sky([(0, '#FF3F8AD0'), (0.6, '#FFAED7F2'), (1, '#FFE8F5FC')], y2=560)
    s.clouds(5, 4, 120, 280, '#80FFFFFF')
    s.fill('#FFC3D3E2', peaks(2, 470, 110, 9, jitter=0.4))
    s.fill('#FF4E7A5A', hills(4, 500, 40, 6))
    s.fill('#FF2E5A3E', pine_row(6, 560, 40, 80, 30, ground=False))
    s.linear([(0, '#FF2BB3C8'), (0.6, '#FF5ED9DC'), (1, '#FF9EF0E6')], y1=560, y2=900, d=f'M0,560h{W}v380H0z')
    s.fill('#55FFFFFF', ''.join(rect(200 + i * 230, 620 + i * 34, 300, 3) for i in range(7)))
    s.fill('#FFF8F6F0', 'M-20,1080L-20,880C300,840 700,900 1000,870C1300,840 1600,820 1940,860L1940,1080Z')
    s.fill('#FFE8E2D6', 'M-20,1080L-20,960C400,930 800,980 1200,950C1500,930 1700,940 1940,950L1940,1080Z')
    return s


def van_akdamar():
    s = Scene('Lake Van: the church on Akdamar Island, snowy Süphan beyond the deep blue water.')
    s.sky([(0, '#FF2A5C9E'), (0.7, '#FFA9CBE8'), (1, '#FFE6EFF5')], y2=620)
    s.fill('#FFB4BCD0', poly([(900, 620), (1400, 330), (1900, 620)]))
    s.fill('#FFF4F6FA', poly([(1330, 372), (1400, 330), (1470, 372), (1440, 368), (1400, 386), (1360, 368)]))
    s.fill('#FF8C8EA8', hills(3, 600, 30, 6, bottom=640))
    s.linear([(0, '#FF2D6FB0'), (1, '#FF0E3A70')], y1=620, y2=H, d=f'M0,620h{W}V{H}H0z')
    s.fill('#40FFFFFF', ''.join(rect(100 + i * 260, 700 + i * 40, 260, 3) for i in range(7)))
    s.fill('#FF8E8270', 'M620,860C700,760 860,730 1000,740C1150,750 1280,800 1340,860Z')
    s.fill('#FF6E8A4E', ''.join(tree_blob(x, 780 + abs(x - 980) * 0.12, 26, x) for x in range(700, 1300, 60)))
    stone = '#FFC79F7E'
    s.fill(stone, rect(890, 640, 200, 130) + rect(950, 590, 80, 60) + rect(960, 520, 60, 80))
    s.fill('#FFA87E5E', poly([(952, 520), (990, 450), (1028, 520)]) + poly([(880, 640), (990, 600), (1100, 640)]))
    s.fill('#FF5A3E2E', arch(975, 770, 30, 60) + rect(983, 545, 14, 30))
    return s


def abant():
    s = Scene('Abant Lake in autumn: golden and red trees reflected in still water.')
    s.sky([(0, '#FF6E8FB8'), (0.6, '#FFD7DCE0'), (1, '#FFF1E6D6')], y2=600)
    s.fill('#FF4E6A4A', pine_row(2, 520, 90, 170, 70, ground=False))
    rng = random.Random(4)
    colors = ['#FFE09A3A', '#FFD2622E', '#FFB8422A', '#FFF0C050', '#FF8FA040']
    for k, c in enumerate(colors):
        s.fill(c, ''.join(tree_blob(x, 560 + rng.randint(-20, 20), rng.randint(46, 64), x * 7 + k) for x in range(k * 44, 1960, 230)))
    s.fill('#FF5A4632', rect(0, 600, W, 20))
    s.linear([(0, '#FF6E7A6A'), (1, '#FF2A3A3A')], y1=620, y2=H, d=f'M0,620h{W}V{H}H0z')
    for k, c in enumerate(colors):
        s.fill('#55' + c[3:], ''.join(ellipse(x, 690 + (x % 3) * 12, 64, 26) for x in range(k * 44, 1960, 230)))
    s.fill('#40FFFFFF', ''.join(rect(300 + i * 280, 760 + i * 46, 200, 3) for i in range(6)))
    s.fill('#60E8F0EC', rect(0, 600, W, 30))
    return s


def uludag():
    s = Scene('Uludağ: snowy slopes and snow-laden pines under the chairlift.')
    s.sky([(0, '#FF1E5AA8'), (0.7, '#FF8EC0EA'), (1, '#FFDCEEFA')], y2=700)
    s.fill('#FFE6EEF6', peaks(3, 560, 300, 7, jitter=0.4))
    s.fill('#FFC9D6E4', poly([(780, 560), (1080, 260), (1300, 560)]))
    s.fill('#FFF4F8FC', smooth_ridge([(-60, 640), (400, 560), (900, 620), (1400, 700), (1980, 780)]))
    trees = pine_row(5, 760, 140, 240, 70, ground=False)
    s.fill('#FF2E4A3A', trees)
    rng = random.Random(9)
    snow = ''.join(poly([(x - 16, 760 - h * 0.62), (x, 760 - h * 0.72), (x + 16, 760 - h * 0.62)]) + poly([(x - 26, 760 - h * 0.28), (x, 760 - h * 0.36), (x + 26, 760 - h * 0.28)])
                   for x, h in [(rng.randint(0, W), rng.randint(140, 240)) for _ in range(30)])
    s.fill('#E6FFFFFF', snow)
    s.fill('#FFF6F9FC', smooth_ridge([(-60, 800), (600, 770), (1200, 820), (1980, 790)]))
    s.stroke('#FF3A3A48', 3, 'M-20,300L1940,620', 'butt')
    s.fill('#FF3A3A48', ''.join(rect(x, 300 + (x + 20) * 0.1667 - 2, 4, 40) + rect(x - 18, 300 + (x + 20) * 0.1667 + 36, 40, 10) for x in range(120, 1900, 260)))
    s.fill('#FF4A4A58', rect(400, 360, 14, 420) + rect(1300, 510, 14, 280))
    return s


def koprulu_kanyon():
    s = Scene('Köprülü Canyon: a Roman stone bridge over the turquoise river between rock walls.')
    s.sky([(0, '#FF3A86CF'), (1, '#FFCFE6F5')], y2=500)
    s.fill('#FFB98E68', 'M-20,1080L-20,120C120,160 260,260 380,340C500,420 580,520 640,620L700,1080Z')
    s.fill('#FF9A6E4E', 'M-20,1080L-20,300C140,360 300,460 420,560C520,640 580,760 600,1080Z')
    s.fill('#FFB98E68', 'M1940,1080L1940,80C1800,140 1640,240 1540,340C1440,440 1360,540 1300,640L1240,1080Z')
    s.fill('#FF8A6046', 'M1940,1080L1940,280C1780,340 1620,440 1520,560C1430,660 1380,780 1360,1080Z')
    s.fill('#FF4E7A45', ''.join(tree_blob(x, y, 30, x) for x, y in [(200, 210), (320, 300), (1650, 180), (1560, 280), (120, 420), (1780, 380), (480, 470), (1450, 450)]))
    s.linear([(0, '#FF5ED9DC'), (1, '#FF1E9FB8')], y1=780, y2=H, d='M560,1080C620,920 700,820 820,790C960,760 1100,780 1240,800C1320,820 1360,940 1380,1080Z')
    s.fill('#55FFFFFF', rect(760, 860, 200, 4) + rect(1000, 930, 180, 4) + rect(820, 1000, 260, 4))
    bridge = '#FFD9C3A0'
    s.fill(bridge, 'M640,790L640,600L1300,600L1300,790L1180,790C1180,680 1080,640 970,640C860,640 760,680 760,790Z')
    s.fill('#FFBFA680', rect(640, 590, 660, 16))
    s.fill('#FFB09670', ''.join(rect(660 + i * 40, 600, 2, 30) for i in range(16)))
    return s


def gobeklitepe():
    s = Scene('Göbeklitepe: T-shaped stone pillars of the ancient circle under the steppe sunset.')
    s.sky([(0, '#FF3A2E5E'), (0.5, '#FFB8607A'), (0.75, '#FFF2A07A'), (1, '#FFF8CF9A')], y2=800)
    s.sun(1500, 720, 70, '#FFFFE8B8', 'FFD49A', 500)
    s.fill('#FFB08A5E', hills(3, 760, 25, 6))
    s.fill('#FF8E6A44', 'M-20,1080L-20,820C400,780 900,790 1300,800C1600,810 1800,820 1940,830L1940,1080Z')
    s.fill('#FF6E5034', ellipse(960, 930, 760, 110))
    stone, shade, carve = '#FFD7BE94', '#FFB09670', '#FF9A8060'
    for x, base, h, w in [(560, 960, 300, 70), (820, 930, 360, 80), (1100, 930, 360, 80), (1360, 960, 300, 70), (400, 1000, 240, 60), (1520, 1000, 240, 60)]:
        top = base - h
        s.fill(stone, rect(x - w / 2, top + 50, w, h - 50) + rect(x - w * 1.3, top, w * 2.6, 54))
        s.fill(shade, rect(x + w * 0.2, top + 54, w * 0.3, h - 54) + rect(x - w * 1.3, top + 44, w * 2.6, 10))
        s.stroke(carve, 3, f'M{f(x - w * 0.3)},{f(top + 90)}l{f(w * 0.2)},{f(30)}l{f(w * 0.25)},-{f(20)}M{f(x - w * 0.25)},{f(top + 160)}h{f(w * 0.4)}')
    s.stroke('#FF3A2E2A', 6, 'M200,560Q960,420 1720,560', 'butt')
    return s


def sumela():
    s = Scene('Sümela Monastery clinging to a sheer cliff above the misty forest.')
    s.sky([(0, '#FFB8CCC6'), (1, '#FFE2EBE6')], y2=600)
    s.fill('#FF6E8A7E', 'M600,1080L760,120L1000,40L1260,80L1420,260L1560,1080Z')
    s.fill('#FF4E6A60', 'M1000,40L1260,80L1420,260L1560,1080L1200,1080L1150,400Z')
    s.fill('#FF3A5248', 'M760,120L700,500L860,540L900,300Z')
    s.fill('#FF3E3A40', rect(820, 470, 360, 60))
    stone, roof, dark = '#FFEDE2CC', '#FF9A6E58', '#FF4A3A34'
    s.fill(stone, rect(830, 380, 340, 100) + rect(870, 330, 120, 60) + rect(1040, 350, 100, 40))
    s.fill(roof, rect(826, 372, 348, 12) + poly([(864, 330), (930, 300), (996, 330)]))
    s.fill(dark, ''.join(arch(850 + i * 40, 460, 20, 36) for i in range(8)) + ''.join(rect(880 + i * 30, 345, 12, 20) for i in range(3)))
    for y, a in [(560, '88'), (700, '99'), (840, 'AA')]:
        s.linear([(0, f'#00F0F4F2'), (0.5, f'#{a}F0F4F2'), (1, '#00F0F4F2')], y1=y - 60, y2=y + 60, d=rect(0, y - 60, W, 120))
    s.fill('#FF2E5A3E', pine_row(3, 780, 160, 280, 50, ground=False))
    s.fill('#FF1E4030', pine_row(7, 960, 220, 380, 70))
    return s


def aspendos():
    s = Scene('Aspendos: the tiers of the Roman theatre and its stage wall in warm evening light.')
    s.sky([(0, '#FF3E5A8C'), (0.6, '#FFE6A27A'), (1, '#FFF6D2A2')], y2=520)
    s.fill('#FFA07A5A', hills(3, 500, 30, 6))
    stone, shade, dark = '#FFE2B98A', '#FFC09468', '#FF7A5038'
    rows = ''.join(f'M{f(960 - 900 + i * 46)},{f(1080 - i * 34)}A{f(900 - i * 46)},{f(420 - i * 30)} 0,0 1 {f(960 + 900 - i * 46)},{f(1080 - i * 34)}' for i in range(14))
    s.fill(shade, f'M60,1080A900,420 0,0 1 1860,1080Z')
    s.stroke(stone, 14, rows, 'butt')
    s.fill('#FFD9AE7E', f'M440,1080A520,180 0,0 1 1480,1080Z')
    s.fill(stone, rect(500, 560, 920, 300))
    s.fill(dark, ''.join(arch(560 + i * 110, 780, 46, 90) for i in range(8)) + ''.join(arch(560 + i * 110, 660, 46, 70) for i in range(8)) + rect(900, 760, 120, 100))
    s.fill(shade, rect(500, 540, 920, 22))
    return s


def hasankeyf():
    s = Scene('Hasankeyf: cliffs with cave dwellings, the old bridge piers and the El Rızk minaret by the Tigris.')
    s.sky([(0, '#FF2E3A6E'), (0.5, '#FFD08070'), (0.75, '#FFF6B880'), (1, '#FFF6B880')], y2=760)
    s.fill('#FFC9A27A', 'M-20,1080L-20,300L300,280L520,330L700,420L820,560L860,760L880,1080Z')
    s.fill('#FFA87E58', 'M-20,1080L-20,520L400,540L700,620L820,760L840,1080Z')
    rng = random.Random(5)
    s.fill('#FF5A3E2E', ''.join(arch(rng.randint(20, 700), rng.randint(360, 820), rng.randint(20, 36), rng.randint(30, 50)) for _ in range(26)))
    s.fill('#FF8A6A4E', rect(30, 250, 260, 50) + ''.join(rect(x, 230, 20, 26) for x in range(30, 290, 36)))
    s.water(760, [(0, '#FF9A8A7A'), (0.4, '#FF4E5A6E'), (1, '#FF22304A')])
    s.fill('#40FFE0B0', ''.join(rect(900 + i * 70, 800 + i * 30, 300, 3) for i in range(8)))
    s.fill('#FFB08A64', rect(1060, 560, 90, 260) + rect(1340, 600, 80, 220) + poly([(1040, 560), (1105, 500), (1170, 560)]))
    s.fill('#FF8E6E4E', rect(1120, 560, 30, 260) + rect(1390, 600, 30, 220))
    s.fill('#FFC9A27A', rect(1640, 380, 60, 420) + rect(1630, 450, 80, 14) + dome(1670, 380, 30, 26))
    s.fill('#FFA87E58', rect(1600, 780, 200, 40))
    s.fill('#FF9A8A6A', smooth_ridge([(1500, 820), (1700, 780), (1980, 760)], 840))
    return s


SCENES = dict(safranbolu=safranbolu, salda=salda, van_akdamar=van_akdamar, abant=abant, uludag=uludag,
              koprulu_kanyon=koprulu_kanyon, gobeklitepe=gobeklitepe, sumela=sumela, aspendos=aspendos, hasankeyf=hasankeyf)
