#!/usr/bin/env python3
"""Génère les illustrations flat des plats (SVG -> JPG 1200x800).

Usage : python3 data/images/src/build.py [slug ...]
Nécessite rsvg-convert et Pillow.
"""
import math
import os
import random
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.dirname(HERE)
W, H = 1200, 800

DEFS = """
<defs>
  <radialGradient id="bg" cx="50%" cy="45%" r="70%"><stop offset="0%" stop-color="#FFF4E3"/><stop offset="100%" stop-color="#F0D8B8"/></radialGradient>
  <linearGradient id="plate" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#FFFFFF"/><stop offset="100%" stop-color="#ECE6DC"/></linearGradient>
  <linearGradient id="cheese" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#FFF1B0"/><stop offset="100%" stop-color="#F2C84B"/></linearGradient>
  <linearGradient id="gold" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#F4C65A"/><stop offset="100%" stop-color="#D9932B"/></linearGradient>
  <linearGradient id="brown" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#A3552E"/><stop offset="100%" stop-color="#6E3119"/></linearGradient>
  <linearGradient id="meat" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#9A4A2B"/><stop offset="100%" stop-color="#5E2A14"/></linearGradient>
  <linearGradient id="cream" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#FFFBEF"/><stop offset="100%" stop-color="#F2E6C8"/></linearGradient>
  <linearGradient id="ceramic" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#F7F1E7"/><stop offset="100%" stop-color="#D8CFC0"/></linearGradient>
  <linearGradient id="pot" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#C2452F"/><stop offset="100%" stop-color="#7E2318"/></linearGradient>
  <linearGradient id="potblue" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#4B6FA3"/><stop offset="100%" stop-color="#2E4669"/></linearGradient>
  <linearGradient id="broth" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#E7B45C"/><stop offset="100%" stop-color="#C98A2E"/></linearGradient>
  <linearGradient id="wine" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#6B2A2A"/><stop offset="100%" stop-color="#3E1414"/></linearGradient>
  <filter id="soft" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="14"/></filter>
</defs>
"""

BG = f'<rect width="{W}" height="{H}" fill="url(#bg)"/><g opacity="0.08" fill="#A5602A"><rect x="0" y="0" width="{W}" height="40"/><rect x="0" y="{H-40}" width="{W}" height="40"/></g>'


def plate(cx=600, cy=440, rx=430, ry=245):
    return f'''<ellipse cx="{cx+10}" cy="{cy+30}" rx="{rx}" ry="{ry}" fill="#8B5A2B" opacity="0.28" filter="url(#soft)"/>
<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="url(#plate)"/>
<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="none" stroke="#D9CFC1" stroke-width="4"/>
<ellipse cx="{cx}" cy="{cy}" rx="{rx-65}" ry="{ry-45}" fill="none" stroke="#E6DDD0" stroke-width="3"/>'''


def dish(cx=600, cy=440, w=760, h=300, color="url(#ceramic)", rim="#C9BDA8"):
    """Plat à gratin rectangulaire vu de trois-quarts : face avant + bord."""
    x, y = cx - w / 2, cy - h / 2
    return f'''<rect x="{x+12}" y="{y+40}" width="{w}" height="{h+40}" rx="40" fill="#8B5A2B" opacity="0.3" filter="url(#soft)"/>
<rect x="{x}" y="{y+30}" width="{w}" height="{h+30}" rx="40" fill="#B8A890"/>
<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="40" fill="{color}" stroke="{rim}" stroke-width="5"/>
<rect x="{x+22}" y="{y+22}" width="{w-44}" height="{h-44}" rx="26" fill="#C9A86A"/>
<ellipse cx="{x-6}" cy="{cy+10}" rx="34" ry="22" fill="{color}" stroke="{rim}" stroke-width="5"/>
<ellipse cx="{x+w+6}" cy="{cy+10}" rx="34" ry="22" fill="{color}" stroke="{rim}" stroke-width="5"/>'''


def pot(cx=600, cy=430, rx=330, ry=120, depth=220, color="url(#pot)", inner="url(#broth)"):
    """Cocotte / marmite vue de haut-trois-quarts, intérieur visible."""
    return f'''<ellipse cx="{cx+10}" cy="{cy+depth+30}" rx="{rx}" ry="{ry}" fill="#5A2A1A" opacity="0.35" filter="url(#soft)"/>
<path d="M {cx-rx} {cy} L {cx-rx} {cy+depth} A {rx} {ry} 0 0 0 {cx+rx} {cy+depth} L {cx+rx} {cy} Z" fill="{color}"/>
<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{color}"/>
<ellipse cx="{cx}" cy="{cy}" rx="{rx-28}" ry="{ry-20}" fill="{inner}"/>
<rect x="{cx-rx-70}" y="{cy+30}" width="80" height="34" rx="17" fill="{color}"/>
<rect x="{cx+rx-10}" y="{cy+30}" width="80" height="34" rx="17" fill="{color}"/>
<path d="M {cx-rx+30} {cy-10} Q {cx} {cy-ry-10} {cx+rx-30} {cy-10}" fill="none" stroke="#FFFFFF" stroke-width="6" opacity="0.25" stroke-linecap="round"/>'''


def bowl(cx=600, cy=420, rx=300, ry=95, depth=190, color="url(#ceramic)", inner="url(#broth)"):
    return f'''<ellipse cx="{cx+10}" cy="{cy+depth+28}" rx="{rx}" ry="{ry}" fill="#8B5A2B" opacity="0.3" filter="url(#soft)"/>
<path d="M {cx-rx} {cy} C {cx-rx} {cy+depth} {cx-rx*0.55} {cy+depth+30} {cx} {cy+depth+30} C {cx+rx*0.55} {cy+depth+30} {cx+rx} {cy+depth} {cx+rx} {cy} Z" fill="{color}"/>
<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{color}" stroke="#C9BDA8" stroke-width="4"/>
<ellipse cx="{cx}" cy="{cy}" rx="{rx-26}" ry="{ry-18}" fill="{inner}"/>'''


def fork(x=1030, y=300, rot=35):
    return f'''<g transform="translate({x} {y}) rotate({rot})" fill="#C8CCD2" stroke="#9AA0A8" stroke-width="3">
<rect x="-9" y="-10" width="18" height="300" rx="9"/>
<path d="M -34 -100 L -34 -10 Q -34 10 -14 10 L 14 10 Q 34 10 34 -10 L 34 -100 L 20 -100 L 20 -30 L 8 -30 L 8 -100 L -8 -100 L -8 -30 L -20 -30 L -20 -100 Z"/></g>'''


def knife(x=180, y=560, rot=-150):
    return f'''<g transform="translate({x} {y}) rotate({rot})" fill="#C8CCD2" stroke="#9AA0A8" stroke-width="3">
<rect x="-9" y="-10" width="18" height="200" rx="9"/>
<path d="M -14 -10 L -14 -150 Q -14 -175 10 -175 L 22 -175 Q 26 -120 24 -10 Z"/></g>'''


def spoon(x=1040, y=320, rot=30):
    return f'''<g transform="translate({x} {y}) rotate({rot})" fill="#C8CCD2" stroke="#9AA0A8" stroke-width="3">
<rect x="-9" y="-10" width="18" height="290" rx="9"/><ellipse cx="0" cy="-60" rx="34" ry="52"/></g>'''


def steam(cx=600, y=190, n=3, gap=60):
    s = '<g fill="none" stroke="#FFFFFF" stroke-width="10" stroke-linecap="round" opacity="0.55">'
    for i in range(n):
        x = cx + (i - (n - 1) / 2) * gap
        s += f'<path d="M {x} {y} C {x-20} {y-30} {x+20} {y-50} {x} {y-80}"/>'
    return s + '</g>'


def herbs(points, color="#4E9A3B"):
    s = f'<g fill="{color}">'
    for (x, y, r) in points:
        s += f'<ellipse cx="{x}" cy="{y}" rx="14" ry="7" transform="rotate({r} {x} {y})"/><ellipse cx="{x+14}" cy="{y-6}" rx="11" ry="6" transform="rotate({r+60} {x+14} {y-6})"/>'
    return s + '</g>'


def dots(rng, cx, cy, rx, ry, n, rmin, rmax, colors):
    s = ''
    for _ in range(n):
        a = rng.uniform(0, 2 * math.pi)
        d = math.sqrt(rng.uniform(0, 1))
        x, y = cx + math.cos(a) * rx * d, cy + math.sin(a) * ry * d
        s += f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{rng.uniform(rmin, rmax):.0f}" fill="{rng.choice(colors)}"/>'
    return s


# ---------------------------------------------------------------- plats

def spaghetti_bolognaise():
    rng = random.Random(1)
    s = plate()
    # nid de pâtes
    s += '<ellipse cx="600" cy="445" rx="330" ry="180" fill="#F5D27E"/>'
    s += '<g fill="none" stroke="#F2C86B" stroke-width="14" stroke-linecap="round">'
    for i in range(60):
        a = rng.uniform(0, 2 * math.pi)
        r1, r2 = rng.uniform(120, 330), rng.uniform(120, 330)
        x1, y1 = 600 + math.cos(a) * r1, 440 + math.sin(a) * r1 * 0.55
        x2, y2 = 600 + math.cos(a + rng.uniform(1.5, 3.5)) * r2, 440 + math.sin(a + rng.uniform(1.5, 3.5)) * r2 * 0.55
        cx1, cy1 = 600 + rng.uniform(-250, 250), 440 + rng.uniform(-160, 160)
        cx2, cy2 = 600 + rng.uniform(-250, 250), 440 + rng.uniform(-160, 160)
        s += f'<path d="M {x1:.0f} {y1:.0f} C {cx1:.0f} {cy1:.0f} {cx2:.0f} {cy2:.0f} {x2:.0f} {y2:.0f}"/>'
    s += '</g>'
    s += '<g fill="none" stroke="#FFE9A8" stroke-width="4" stroke-linecap="round" opacity="0.6">'
    for i in range(30):
        a = rng.uniform(0, 2 * math.pi)
        x1, y1 = 600 + math.cos(a) * 240, 440 + math.sin(a) * 130
        s += f'<path d="M {x1:.0f} {y1:.0f} q {rng.uniform(-80,80):.0f} {rng.uniform(-60,60):.0f} {rng.uniform(-120,120):.0f} {rng.uniform(-80,80):.0f}"/>'
    s += '</g>'
    # sauce
    s += '<path d="M 440 430 C 460 360 560 335 640 345 C 740 360 790 400 775 455 C 760 510 660 530 590 520 C 500 510 420 490 440 430 Z" fill="url(#meat)" opacity="0.95"/>'
    s += dots(rng, 610, 435, 150, 75, 26, 6, 13, ["#B0583A", "#C9302C", "#8E3B22"])
    s += dots(rng, 610, 420, 190, 110, 18, 3, 6, ["#FFF6D6"])  # parmesan
    s += herbs([(650, 345, -20), (560, 470, 30)])
    s += steam(600, 230)
    s += fork()
    return s


def gratin_dauphinois():
    rng = random.Random(2)
    s = dish()
    # rondelles de pommes de terre en écailles
    s += '<g>'
    for row in range(4):
        for col in range(9):
            x = 290 + col * 72 + (row % 2) * 36
            y = 345 + row * 48
            s += f'<ellipse cx="{x}" cy="{y}" rx="46" ry="30" fill="#F3D27A" stroke="#D8A84A" stroke-width="3"/><ellipse cx="{x}" cy="{y-4}" rx="30" ry="16" fill="#F9E3A0"/>'
    s += '</g>'
    # gratiné doré par-dessus
    s += '<path d="M 250 335 C 400 300 800 300 950 335 C 940 420 900 500 850 560 L 350 560 C 300 500 260 420 250 335 Z" fill="url(#gold)" opacity="0.55"/>'
    s += dots(rng, 600, 440, 300, 90, 30, 5, 10, ["#B86A1E", "#D9932B", "#FFF1B0"])
    s += '<path d="M 300 340 C 450 320 750 320 900 340" fill="none" stroke="#FFFFFF" stroke-width="8" opacity="0.5" stroke-linecap="round"/>'
    s += steam(600, 250)
    s += spoon(1060, 320, 28)
    return s


def quiche_lorraine():
    rng = random.Random(3)
    s = plate()
    cx, cy = 600, 430
    # pâte (bord crénelé)
    s += f'<ellipse cx="{cx}" cy="{cy+22}" rx="300" ry="165" fill="#B9802C"/>'
    pts = []
    for i in range(48):
        a = i / 48 * 2 * math.pi
        r = 300 if i % 2 == 0 else 282
        pts.append(f'{cx + math.cos(a)*r:.0f},{cy + math.sin(a)*r*0.55:.0f}')
    s += f'<polygon points="{" ".join(pts)}" fill="url(#gold)"/>'
    s += f'<ellipse cx="{cx}" cy="{cy}" rx="262" ry="142" fill="#F6C94A"/>'
    s += f'<ellipse cx="{cx}" cy="{cy-6}" rx="250" ry="130" fill="#F9DA7A"/>'
    s += dots(rng, cx, cy, 230, 115, 22, 7, 13, ["#D98A7A", "#C96A58"])  # lardons
    s += dots(rng, cx, cy, 230, 115, 16, 4, 8, ["#E7A93A", "#B86A1E"])
    # part découpée : coin sombre
    s += f'<path d="M {cx} {cy} L {cx+300} {cy-20} A 300 165 0 0 1 {cx+240} {cy+100} Z" fill="#E8DFD1"/>'
    s += f'<path d="M {cx} {cy} L {cx+300} {cy-20} L {cx+300} {cy+8} L {cx} {cy+28} Z" fill="#B9802C"/>'
    s += f'<path d="M {cx} {cy+28} L {cx+240} {cy+128} L {cx+240} {cy+100} L {cx} {cy} Z" fill="#B9802C"/>'
    s += f'<path d="M {cx+2} {cy+4} L {cx+296} {cy-16} L {cx+296} {cy-4} L {cx+2} {cy+16} Z" fill="#F6C94A"/>'
    s += f'<path d="M {cx+2} {cy+4} L {cx+236} {cy+102} L {cx+236} {cy+114} L {cx+2} {cy+16} Z" fill="#F6C94A"/>'
    s += herbs([(400, 330, -30)])
    s += knife(200, 580, -140)
    return s


def poulet_roti():
    rng = random.Random(4)
    s = plate(600, 450, 440, 250)
    # pommes de terre autour
    for (x, y, r) in [(300, 470, -20), (360, 540, 15), (860, 420, 25), (900, 500, -10), (820, 560, 40), (430, 580, 60), (740, 600, -30)]:
        s += f'<g transform="rotate({r} {x} {y})"><path d="M {x-50} {y} C {x-45} {y-40} {x+45} {y-40} {x+50} {y} C {x+45} {y+28} {x-45} {y+28} {x-50} {y} Z" fill="#E8B35A" stroke="#C48A2E" stroke-width="3"/><path d="M {x-30} {y-14} C {x-10} {y-26} {x+15} {y-26} {x+30} {y-16}" fill="none" stroke="#FFF1B0" stroke-width="5" stroke-linecap="round"/></g>'
    # corps du poulet
    s += '<ellipse cx="610" cy="470" rx="210" ry="118" fill="#8B5A2B" opacity="0.3" filter="url(#soft)"/>'
    s += '<path d="M 400 440 C 400 330 500 290 600 300 C 720 310 820 360 820 440 C 820 530 720 560 600 560 C 480 560 400 520 400 440 Z" fill="url(#brown)"/>'
    s += '<path d="M 430 420 C 450 350 540 330 600 335 C 690 340 760 380 770 420 C 700 390 540 380 430 420 Z" fill="#D98A3B" opacity="0.8"/>'
    s += '<path d="M 470 380 C 520 355 640 355 720 380" fill="none" stroke="#FFF1B0" stroke-width="9" opacity="0.55" stroke-linecap="round"/>'
    # cuisses
    for (x, y, flip) in [(470, 520, 1), (740, 520, -1)]:
        s += f'<g transform="translate({x} {y}) scale({flip} 1)"><path d="M -10 -60 C 40 -70 80 -30 70 20 C 60 60 20 70 -20 50 C -40 30 -40 -40 -10 -60 Z" fill="url(#brown)"/><path d="M -20 50 L -60 85" stroke="#D98A3B" stroke-width="18" stroke-linecap="round"/><path d="M -60 85 l -14 -14 m 14 14 l -2 -20 m 2 20 l 16 -8" stroke="#F3E3C5" stroke-width="8" stroke-linecap="round"/></g>'
    s += herbs([(600, 310, 0), (880, 380, 30)], "#3F7F2E")
    s += '<g fill="#C9302C"><circle cx="560" cy="580" r="8"/><circle cx="650" cy="585" r="7"/></g>'
    s += steam(610, 250)
    s += fork(1040, 290, 35)
    return s


def boeuf_bourguignon():
    rng = random.Random(5)
    s = pot(600, 400, 330, 120, 230, "url(#pot)", "url(#wine)")
    # morceaux
    for (x, y, r) in [(470, 400, 10), (560, 380, -15), (660, 395, 25), (740, 385, -5), (600, 430, 5), (520, 440, -20), (690, 440, 15)]:
        s += f'<g transform="rotate({r} {x} {y})"><rect x="{x-44}" y="{y-28}" width="88" height="56" rx="18" fill="url(#meat)"/><rect x="{x-34}" y="{y-22}" width="52" height="12" rx="6" fill="#B0583A" opacity="0.8"/></g>'
    # carottes et champignons
    for (x, y) in [(430, 445), (620, 470), (760, 440), (540, 345)]:
        s += f'<circle cx="{x}" cy="{y}" r="22" fill="#F08A24" stroke="#D16A10" stroke-width="3"/><circle cx="{x}" cy="{y}" r="9" fill="#FFB85C"/>'
    for (x, y) in [(500, 350), (720, 350), (660, 480)]:
        s += f'<path d="M {x-26} {y+6} A 26 22 0 0 1 {x+26} {y+6} Z" fill="#E9D8BE" stroke="#C9A978" stroke-width="3"/><rect x="{x-8}" y="{y+6}" width="16" height="14" rx="4" fill="#D8C4A4"/>'
    s += dots(rng, 600, 410, 260, 60, 14, 4, 8, ["#8E3B22", "#B0583A"])
    s += herbs([(430, 370, -40), (700, 330, 20)])
    s += steam(600, 210, 3, 70)
    s += '<rect x="880" y="470" width="200" height="22" rx="11" fill="#7A4A22"/><rect x="900" y="400" width="160" height="76" rx="22" fill="#D8A86A" stroke="#B0804A" stroke-width="4"/><g fill="none" stroke="#B0804A" stroke-width="3" opacity="0.6"><path d="M 915 420 h 130 M 915 440 h 130 M 915 460 h 130"/></g>'
    return s


def blanquette_de_veau():
    rng = random.Random(6)
    s = plate()
    # riz
    s += '<path d="M 640 380 C 720 330 860 360 880 440 C 890 510 800 550 700 540 C 620 530 600 440 640 380 Z" fill="#FFFDF5" stroke="#E6DDD0" stroke-width="3"/>'
    s += '<g fill="#F1EAD9">'
    for i in range(40):
        x, y = rng.uniform(650, 860), rng.uniform(370, 530)
        s += f'<ellipse cx="{x:.0f}" cy="{y:.0f}" rx="9" ry="4" transform="rotate({rng.uniform(0,180):.0f} {x:.0f} {y:.0f})"/>'
    s += '</g>'
    # sauce crème
    s += '<path d="M 320 430 C 320 340 420 300 520 310 C 630 320 690 380 670 460 C 650 540 540 570 450 550 C 360 530 320 500 320 430 Z" fill="url(#cream)" stroke="#E3D5B5" stroke-width="3"/>'
    for (x, y, r) in [(440, 400, 10), (530, 430, -15), (400, 480, 20), (560, 500, 5), (470, 510, -10)]:
        s += f'<rect x="{x-38}" y="{y-26}" width="76" height="52" rx="14" fill="#E2C9A4" stroke="#C8A878" stroke-width="3" transform="rotate({r} {x} {y})"/>'
    for (x, y) in [(380, 420), (600, 440)]:
        s += f'<circle cx="{x}" cy="{y}" r="18" fill="#F08A24" stroke="#D16A10" stroke-width="3"/>'
    for (x, y) in [(500, 350), (420, 540)]:
        s += f'<path d="M {x-22} {y+4} A 22 18 0 0 1 {x+22} {y+4} Z" fill="#EFE1C8" stroke="#C9A978" stroke-width="3"/>'
    s += herbs([(600, 360, 30), (360, 470, -40)])
    s += steam(480, 240)
    s += fork()
    return s


def croque_monsieur():
    s = plate()
    cx, cy = 590, 420
    s += f'<rect x="{cx-240}" y="{cy-130}" width="480" height="320" rx="26" fill="#8B5A2B" opacity="0.3" transform="skewX(-12) translate(70 0)" filter="url(#soft)"/>'
    # pain du bas
    s += f'<g transform="skewX(-12) translate(80 0)"><rect x="{cx-240}" y="{cy+40}" width="480" height="70" rx="18" fill="#D9A45A" stroke="#B8802E" stroke-width="4"/>'
    s += f'<rect x="{cx-240}" y="{cy+40}" width="480" height="26" rx="12" fill="#F3E1B8"/>'
    # jambon + fromage qui coulent
    s += f'<rect x="{cx-250}" y="{cy+8}" width="500" height="36" rx="12" fill="#F0A8A0" stroke="#D88A82" stroke-width="3"/>'
    s += f'<path d="M {cx-255} {cy-16} L {cx+255} {cy-16} L {cx+255} {cy+12} C {cx+230} {cy+40} {cx+200} {cy+14} {cx+180} {cy+12} C {cx+120} {cy+10} {cx+90} {cy+50} {cx+60} {cy+12} C {cx} {cy+10} {cx-40} {cy+44} {cx-70} {cy+12} C {cx-150} {cy+10} {cx-200} {cy+38} {cx-255} {cy+12} Z" fill="url(#cheese)"/>'
    # pain du haut
    s += f'<rect x="{cx-240}" y="{cy-100}" width="480" height="90" rx="18" fill="#D9A45A" stroke="#B8802E" stroke-width="4"/>'
    s += f'<rect x="{cx-240}" y="{cy-100}" width="480" height="28" rx="12" fill="#F3E1B8"/>'
    # gratiné sur le dessus
    s += f'<path d="M {cx-230} {cy-96} C {cx-100} {cy-130} {cx+150} {cy-130} {cx+232} {cy-96} L {cx+232} {cy-70} L {cx-230} {cy-70} Z" fill="url(#cheese)"/>'
    s += f'<g fill="#C8841E" opacity="0.8"><circle cx="{cx-120}" cy="{cy-100}" r="9"/><circle cx="{cx+20}" cy="{cy-110}" r="11"/><circle cx="{cx+160}" cy="{cy-100}" r="8"/><circle cx="{cx-40}" cy="{cy-88}" r="6"/></g>'
    s += '</g>'
    # salade
    s += '<g fill="#5FAE45" stroke="#3F8A2E" stroke-width="3"><path d="M 300 520 C 320 470 380 470 400 510 C 430 480 480 500 470 540 C 420 570 340 570 300 520 Z"/><path d="M 820 520 C 840 470 900 470 920 510 C 950 480 1000 500 990 540 C 940 570 860 570 820 520 Z"/></g>'
    s += '<g fill="#C9302C"><circle cx="440" cy="560" r="16"/><circle cx="860" cy="560" r="16"/></g>'
    s += steam(590, 240)
    s += fork()
    return s


def hachis_parmentier():
    rng = random.Random(8)
    s = dish()
    # couche viande visible sur la tranche avant
    s += '<rect x="242" y="430" width="716" height="120" rx="20" fill="url(#meat)"/>'
    s += dots(rng, 600, 490, 340, 45, 24, 4, 9, ["#B0583A", "#C9302C"])
    # purée
    s += '<path d="M 242 330 C 300 300 420 290 600 295 C 780 300 900 310 958 335 L 958 445 C 900 470 300 470 242 445 Z" fill="#F7E7B0"/>'
    s += '<path d="M 242 330 C 300 300 420 290 600 295 C 780 300 900 310 958 335 C 900 370 300 370 242 330 Z" fill="url(#gold)" opacity="0.7"/>'
    # stries de fourchette
    s += '<g fill="none" stroke="#D9932B" stroke-width="6" stroke-linecap="round" opacity="0.8">'
    for i in range(9):
        x = 300 + i * 75
        s += f'<path d="M {x} 330 C {x+20} 350 {x-20} 380 {x+10} 410"/>'
    s += '</g>'
    s += dots(rng, 600, 330, 320, 20, 14, 5, 9, ["#B86A1E", "#C8841E"])
    s += herbs([(420, 300, -20), (780, 300, 20)])
    s += steam(600, 250)
    s += spoon(1060, 320, 28)
    return s


def ratatouille():
    s = dish(600, 440, 720, 300, "url(#ceramic)")
    cols = ["#7A3E8C", "#4FA64A", "#E3432C", "#F2B233"]
    rng = random.Random(9)
    s += '<rect x="262" y="310" width="676" height="256" rx="26" fill="#A6341E"/>'
    for row in range(3):
        for col in range(11):
            x = 300 + col * 60 + (row % 2) * 30
            y = 350 + row * 72
            c = cols[(col + row) % 4]
            s += f'<ellipse cx="{x}" cy="{y}" rx="34" ry="30" fill="{c}" stroke="#6B2A1A" stroke-width="2"/><ellipse cx="{x}" cy="{y}" rx="22" ry="19" fill="{c}" opacity="0.6" stroke="#FFFFFF" stroke-opacity="0.5" stroke-width="3"/>'
    s += herbs([(600, 300, 0), (900, 330, 40), (320, 560, -30)], "#2F7A2A")
    s += '<g fill="#C9302C" opacity="0.6">' + dots(rng, 600, 440, 320, 110, 14, 3, 5, ["#F7D27A"]) + '</g>'
    s += spoon(1060, 320, 28)
    return s


def tartiflette():
    rng = random.Random(10)
    s = dish()
    for row in range(4):
        for col in range(9):
            x = 290 + col * 72 + (row % 2) * 36
            y = 345 + row * 48
            s += f'<ellipse cx="{x}" cy="{y}" rx="44" ry="28" fill="#F0CF78" stroke="#D0A245" stroke-width="3"/>'
    s += dots(rng, 600, 430, 320, 100, 26, 7, 12, ["#D9847A", "#B85E55"])  # lardons
    s += dots(rng, 600, 430, 320, 100, 18, 5, 9, ["#F3E3C5"])  # oignons
    # demi-reblochons fondus
    for (x, y) in [(420, 390), (620, 370), (800, 400), (520, 470), (720, 480)]:
        s += f'<path d="M {x-70} {y} A 70 36 0 0 1 {x+70} {y} L {x+70} {y+14} A 70 36 0 0 1 {x-70} {y+14} Z" fill="#E6B646"/><path d="M {x-70} {y} A 70 36 0 0 1 {x+70} {y} A 70 36 0 0 1 {x-70} {y} Z" fill="url(#cheese)"/><path d="M {x-40} {y-8} C {x-20} {y-20} {x+20} {y-20} {x+40} {y-8}" fill="none" stroke="#FFFFFF" stroke-width="5" opacity="0.6" stroke-linecap="round"/>'
    s += dots(rng, 600, 430, 300, 90, 16, 4, 8, ["#B86A1E"])
    s += steam(600, 250)
    s += spoon(1060, 320, 28)
    return s


def soupe_oignon():
    rng = random.Random(11)
    s = bowl(600, 400, 290, 95, 200, "#9B5A2E", "url(#broth)")
    # croûton
    s += '<ellipse cx="560" cy="400" rx="150" ry="52" fill="#D9A45A" stroke="#B8802E" stroke-width="4"/>'
    # chapeau de fromage gratiné, débordant
    s += '<path d="M 330 395 C 360 320 520 300 620 302 C 760 305 870 340 870 400 C 870 440 850 470 830 500 C 815 520 800 490 790 460 C 770 500 740 540 730 500 C 700 470 690 450 670 470 C 650 510 600 540 590 500 C 575 470 560 460 540 475 C 510 500 470 520 460 480 C 450 450 430 440 410 460 C 390 480 350 470 340 440 C 334 420 330 410 330 395 Z" fill="url(#cheese)"/>'
    s += dots(rng, 600, 380, 220, 50, 22, 5, 11, ["#C8841E", "#E0A33A"])
    s += '<path d="M 400 360 C 480 330 620 325 760 345" fill="none" stroke="#FFFFFF" stroke-width="9" opacity="0.6" stroke-linecap="round"/>'
    s += herbs([(500, 330, -20), (720, 340, 30)])
    s += steam(600, 240, 4, 60)
    s += spoon(1050, 330, 30)
    return s


def steak_frites():
    rng = random.Random(12)
    s = plate(600, 450, 440, 250)
    # frites
    s += '<g stroke="#C48A2E" stroke-width="3" stroke-linejoin="round">'
    for (x, y, r) in [(760, 470, -70), (800, 450, -60), (840, 480, -75), (880, 440, -55), (820, 420, -65), (900, 480, -80), (780, 500, -50), (860, 510, -70), (920, 450, -60)]:
        s += f'<rect x="{x-14}" y="{y-110}" width="28" height="220" rx="8" fill="#F4C65A" transform="rotate({r} {x} {y})"/>'
    s += '</g>'
    # salade
    s += '<g fill="#5FAE45" stroke="#3F8A2E" stroke-width="3"><path d="M 320 360 C 340 300 420 300 440 350 C 470 310 540 330 520 380 C 560 400 530 450 480 440 C 450 480 370 470 350 430 C 300 430 290 380 320 360 Z"/></g>'
    s += '<g fill="#8BC96B"><ellipse cx="400" cy="380" rx="28" ry="16"/><ellipse cx="470" cy="410" rx="24" ry="14"/></g>'
    s += '<g fill="#C9302C"><circle cx="350" cy="420" r="16"/><circle cx="500" cy="365" r="14"/></g>'
    # steak haché
    s += '<ellipse cx="560" cy="510" rx="190" ry="105" fill="#8B5A2B" opacity="0.3" filter="url(#soft)"/>'
    s += '<path d="M 390 500 C 390 430 470 400 560 400 C 660 400 740 440 740 500 C 740 570 660 600 560 600 C 460 600 390 570 390 500 Z" fill="url(#meat)"/>'
    s += '<path d="M 400 490 C 410 440 480 415 560 415 C 650 415 720 445 728 490 C 660 470 470 470 400 490 Z" fill="#B0583A" opacity="0.6"/>'
    s += '<g stroke="#3E1A0C" stroke-width="10" stroke-linecap="round" opacity="0.8"><path d="M 440 470 L 560 560 M 500 440 L 640 540 M 580 430 L 700 500"/></g>'
    s += '<rect x="520" y="430" width="80" height="26" rx="8" fill="#F7D96B"/>'
    s += herbs([(620, 420, 0)])
    s += steam(560, 300, 2, 70)
    s += fork(1040, 290, 35)
    return s


def omelette():
    rng = random.Random(13)
    s = plate()
    s += '<ellipse cx="610" cy="470" rx="300" ry="120" fill="#8B5A2B" opacity="0.3" filter="url(#soft)"/>'
    # omelette roulée
    s += '<path d="M 320 440 C 320 370 420 330 560 330 C 720 330 880 360 890 430 C 900 500 800 540 640 545 C 480 550 320 520 320 440 Z" fill="#E5A83C"/>'
    s += '<path d="M 330 430 C 340 370 440 340 570 340 C 720 340 860 370 870 425 C 850 440 760 455 620 455 C 470 455 350 450 330 430 Z" fill="#F7C74C"/>'
    s += '<path d="M 350 410 C 420 365 560 350 680 352 C 760 354 830 370 850 395" fill="none" stroke="#FFE28A" stroke-width="12" stroke-linecap="round" opacity="0.9"/>'
    s += '<path d="M 400 470 C 500 500 700 505 820 470" fill="none" stroke="#C98A2A" stroke-width="5" opacity="0.6" stroke-linecap="round"/>'
    # fines herbes
    s += '<g fill="#2F7A2A">'
    for i in range(30):
        x, y = rng.uniform(380, 830), rng.uniform(355, 500)
        s += f'<rect x="{x:.0f}" y="{y:.0f}" width="16" height="5" rx="2" transform="rotate({rng.uniform(0,180):.0f} {x:.0f} {y:.0f})"/>'
    s += '</g>'
    s += '<g fill="#6DB35A" stroke="#3F8A2E" stroke-width="3"><path d="M 760 540 C 780 500 840 500 860 540 C 890 520 930 540 920 570 C 870 590 800 590 760 540 Z"/></g>'
    s += '<circle cx="880" cy="580" r="14" fill="#C9302C"/>'
    s += steam(600, 260)
    s += fork()
    return s


def pot_au_feu():
    rng = random.Random(14)
    s = pot(600, 400, 340, 125, 230, "url(#potblue)", "url(#broth)")
    for (x, y, r) in [(480, 400, 5), (600, 380, -10), (720, 405, 15), (560, 440, 0)]:
        s += f'<g transform="rotate({r} {x} {y})"><rect x="{x-52}" y="{y-32}" width="104" height="64" rx="18" fill="url(#meat)"/><rect x="{x-40}" y="{y-24}" width="60" height="12" rx="6" fill="#B0583A" opacity="0.7"/></g>'
    # os à moelle
    s += '<circle cx="660" cy="450" r="30" fill="#F3E3C5" stroke="#D8C4A4" stroke-width="4"/><circle cx="660" cy="450" r="14" fill="#E9C9A0"/>'
    # carottes entières
    for (x1, y1, x2, y2) in [(420, 350, 560, 330), (650, 340, 790, 360)]:
        s += f'<path d="M {x1} {y1} L {x2} {y2}" stroke="#F08A24" stroke-width="30" stroke-linecap="round"/><path d="M {x1+20} {y1-2} L {x2-20} {y2-2}" stroke="#FFB85C" stroke-width="6" stroke-linecap="round" opacity="0.8"/>'
    # poireau
    s += '<path d="M 400 445 L 540 470" stroke="#EAF3C8" stroke-width="28" stroke-linecap="round"/><path d="M 540 470 L 640 486" stroke="#5FAE45" stroke-width="28" stroke-linecap="round"/><path d="M 410 440 L 530 462" stroke="#FFFFFF" stroke-width="5" opacity="0.6" stroke-linecap="round"/>'
    # navet et pommes de terre
    s += '<circle cx="800" cy="455" r="34" fill="#F3EBE0" stroke="#C9A4C4" stroke-width="4"/><circle cx="800" cy="455" r="14" fill="#B47CB0" opacity="0.6"/>'
    s += '<ellipse cx="470" cy="330" rx="40" ry="28" fill="#F0CF78" stroke="#D0A245" stroke-width="3"/>'
    s += dots(rng, 600, 400, 280, 70, 16, 4, 8, ["#E09A30", "#F5D47A"])
    s += herbs([(720, 320, 20), (520, 490, -30)], "#2F7A2A")
    s += steam(600, 210, 4, 70)
    s += '<rect x="880" y="470" width="200" height="22" rx="11" fill="#7A4A22"/><rect x="900" y="400" width="160" height="76" rx="22" fill="#D8A86A" stroke="#B0804A" stroke-width="4"/>'
    return s


def crepes():
    s = plate(600, 460, 430, 240)
    # pile de crêpes
    for i in range(6):
        y = 470 - i * 22
        s += f'<ellipse cx="560" cy="{y}" rx="250" ry="92" fill="#E8B657" stroke="#C8912E" stroke-width="3"/><ellipse cx="560" cy="{y-6}" rx="230" ry="76" fill="#F5CF7A"/>'
        s += f'<g fill="#C8912E" opacity="0.5"><circle cx="{470+i*20}" cy="{y-10}" r="7"/><circle cx="{620-i*15}" cy="{y-20}" r="9"/><circle cx="{540+i*10}" cy="{y+10}" r="5"/></g>'
    # crêpe pliée en triangle devant
    s += '<path d="M 640 540 L 900 430 L 940 560 Z" fill="#F0C262" stroke="#C8912E" stroke-width="3"/><path d="M 660 538 L 880 445 L 900 500 Z" fill="#F7D98A"/><path d="M 700 530 L 870 460 L 880 490 Z" fill="#E8B657" opacity="0.6"/>'
    # sucre / citron
    s += '<g fill="#FFFFFF" opacity="0.85"><circle cx="500" cy="350" r="3"/><circle cx="540" cy="340" r="4"/><circle cx="600" cy="355" r="3"/><circle cx="650" cy="345" r="4"/><circle cx="560" cy="365" r="3"/><circle cx="620" cy="335" r="3"/></g>'
    s += '<path d="M 300 560 A 60 60 0 0 1 360 500 L 360 560 Z" fill="#F9E05A" stroke="#D9B720" stroke-width="4"/><path d="M 310 556 A 50 50 0 0 1 356 510 L 356 556 Z" fill="#FFF6A8"/>'
    s += '<g stroke="#F9E05A" stroke-width="3"><path d="M 356 556 L 322 528 M 356 556 L 340 514"/></g>'
    s += '<circle cx="820" cy="330" r="14" fill="#C9302C"/><circle cx="845" cy="345" r="12" fill="#C9302C"/><ellipse cx="832" cy="322" rx="10" ry="5" fill="#4E9A3B" transform="rotate(-20 832 322)"/>'
    s += fork(1040, 290, 35)
    return s


DISHES = {
    "spaghetti-bolognaise": spaghetti_bolognaise,
    "gratin-dauphinois": gratin_dauphinois,
    "quiche-lorraine": quiche_lorraine,
    "poulet-roti-pommes-de-terre": poulet_roti,
    "boeuf-bourguignon": boeuf_bourguignon,
    "blanquette-de-veau": blanquette_de_veau,
    "croque-monsieur": croque_monsieur,
    "hachis-parmentier": hachis_parmentier,
    "ratatouille": ratatouille,
    "tartiflette": tartiflette,
    "soupe-oignon-gratinee": soupe_oignon,
    "steak-hache-frites-salade": steak_frites,
    "omelette-fines-herbes": omelette,
    "pot-au-feu": pot_au_feu,
    "crepes": crepes,
}


def build(slug):
    body = DISHES[slug]()
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">{DEFS}{BG}{body}</svg>'
    svg_path = os.path.join(HERE, slug + ".svg")
    with open(svg_path, "w") as f:
        f.write(svg)
    png = os.path.join(HERE, slug + ".png")
    subprocess.run(["rsvg-convert", "-w", str(W), "-h", str(H), svg_path, "-o", png], check=True)
    Image.open(png).convert("RGB").save(os.path.join(OUT, slug + ".jpg"), quality=86, optimize=True)
    os.remove(png)


if __name__ == "__main__":
    for slug in (sys.argv[1:] or DISHES):
        build(slug)
        print("ok", slug)
