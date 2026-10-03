"""Harambee Tracker logo: three people standing together + a gold "confirmed" badge.

Draws on Android's adaptive-icon canvas (108 x 108; everything inside the 66-unit safe circle)
and writes the SVG masters plus the Android vector drawables, so the app and store art stay identical.
Run: python3 store/brand/make_logo.py  (from the repo root)
"""
import math, os

GREEN, GOLD, WHITE, INK = "#0B6E4F", "#F5B83D", "#FFFFFF", "#10241C"
C = 54.0

# Base design (unscaled), tuned by eye.
BASE = dict(
    side=[(40.0, 52.0, 5.4, 10.4), (64.0, 52.0, 5.4, 10.4)],   # cx, head_cy, head_r, body_r
    centre=(52.0, 47.0, 7.2, 14.4),
    base_y=71.0, gap=3.0,
    badge=(68.5, 37.0, 8.2, 2.7),                                 # cx, cy, r, ring: "confirmed" badge, top right
)

def extremes(b):
    pts = []
    for cx, hy, hr, br in b["side"] + [b["centre"]]:
        pts += [(cx - br, b["base_y"]), (cx + br, b["base_y"]), (cx, hy - hr)]
    bx, by, r, ring = b["badge"]
    pts += [(bx + (r + ring / 2) * math.cos(a), by + (r + ring / 2) * math.sin(a)) for a in [i * math.pi / 18 for i in range(36)]]
    return pts

def fit(b, radius=32.0):
    """Scale and centre the design so its farthest point sits on the safe-zone radius."""
    pts = extremes(b)
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    mx, my = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2 + 1.5  # optical centre slightly low
    far = max(math.hypot(x - mx, y - my) for x, y in pts)
    s = radius / far
    T = lambda x, y: (C + (x - mx) * s, C + (y - my) * s)
    return T, s

T, S = fit(BASE)  # launcher: inside the 66-unit safe circle

def use_fit(radius):
    global T, S
    T, S = fit(BASE, radius)

def f(v): return f"{v:.2f}"

def shapes():
    """(kind, data, role) in paint order; role is 'fg', 'gold', or 'badgefg'. Coordinates already fitted."""
    out = []
    def person(cx, hy, hr, br, outline):
        x0, yb = T(cx - br, BASE["base_y"]); x1, _ = T(cx + br, BASE["base_y"]); r = br * S
        body = f"M{f(x0)},{f(yb)} A{f(r)},{f(r)} 0 0 1 {f(x1)},{f(yb)} Z"
        hx, hyy = T(cx, hy)
        out.append(("person", (body, hx, hyy, hr * S), outline))
    for p in BASE["side"]:
        person(*p, outline=False)
    person(*BASE["centre"], outline=True)
    bx, by, r, ring = BASE["badge"]
    cx, cy = T(bx, by)
    out.append(("badge", (cx, cy, r * S, ring * S), None))
    return out

def check_path(cx, cy, r):
    k = r / 8.6
    pts = [(-4.3, 0.2), (-1.2, 3.3), (4.6, -2.9)]
    p = [(cx + x * k, cy + y * k) for x, y in pts]
    return f"M{f(p[0][0])},{f(p[0][1])} L{f(p[1][0])},{f(p[1][1])} L{f(p[2][0])},{f(p[2][1])}", 2.7 * k

def svg_mark(bg, fg, gold, badge_fg):
    parts = []
    for kind, d, outline in shapes():
        if kind == "person":
            body, hx, hy, hr = d
            o = f' stroke="{bg}" stroke-width="{f(BASE["gap"] * S)}" paint-order="stroke"' if outline and bg else ""
            parts.append(f'<path d="{body}" fill="{fg}"{o}/><circle cx="{f(hx)}" cy="{f(hy)}" r="{f(hr)}" fill="{fg}"{o}/>')
        else:
            cx, cy, r, ring = d
            ringattr = f' stroke="{bg}" stroke-width="{f(ring)}"' if bg else ""
            parts.append(f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="{gold}"{ringattr}/>')
            path, w = check_path(cx, cy, r)
            parts.append(f'<path d="{path}" stroke="{badge_fg}" stroke-width="{f(w)}" stroke-linecap="round" stroke-linejoin="round" fill="none"/>')
    return "\n".join(parts)

def svg(body, bg=None, size=108, view="0 0 108 108", w=1024, h=1024):
    rect = f'<rect width="{size}" height="{size}" fill="{bg}"/>' if bg else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}" width="{w}" height="{h}">{rect}\n{body}\n</svg>\n'

os.makedirs("store/brand", exist_ok=True)
use_fit(41.0)  # store icon and logos are not cropped by launcher masks, so the mark can be larger
# App icon (full bleed, square; stores and launchers apply their own mask).
open("store/brand/icon.svg", "w").write(svg(svg_mark(GREEN, WHITE, GOLD, GREEN), GREEN))
# Mark on light backgrounds (documents, letterheads, website).
open("store/brand/mark-on-light.svg", "w").write(svg(svg_mark("#FFFFFF", GREEN, GOLD, "#FFFFFF"), None))
# Single colour (stamps, embroidery, Android themed icon).
open("store/brand/mark-mono.svg", "w").write(svg(svg_mark("#FFFFFF", INK, INK, "#FFFFFF"), None))

TAGLINE = "Kila mchango unahesabiwa"  # Kiswahili: "Every contribution is counted" (and so, every contribution counts)

# Horizontal logo: rounded icon + wordmark (+ optional Kiswahili tagline underneath).
def lockup(dark, tagline=False):
    bg = INK if dark else "#FFFFFF"
    text = "#FFFFFF" if dark else INK
    sub = "#9FD9C2" if dark else GREEN
    icon = svg_mark(GREEN, WHITE, GOLD, GREEN)
    font = "Poppins, Montserrat, Roboto, Arial, sans-serif"
    h = 176 if tagline else 140
    icon_y = (h - 108) / 2
    top = 10 if tagline else 0   # text block shifts down slightly when the tagline is under it
    tag = (f'<line x1="146" y1="{112 + top}" x2="520" y2="{112 + top}" stroke="{GOLD}" stroke-width="2"/>'
           f'<text x="146" y="{140 + top}" font-family="{font}" font-style="italic" font-weight="500" font-size="23" fill="{text}">{TAGLINE}</text>'
           ) if tagline else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 560 {h}" width="1680" height="{h * 3}">'
            f'<rect width="560" height="{h}" fill="{bg}"/>'
            f'<g transform="translate(16,{icon_y})"><svg viewBox="0 0 108 108" width="108" height="108">'
            f'<clipPath id="r"><rect width="108" height="108" rx="26"/></clipPath><g clip-path="url(#r)">'
            f'<rect width="108" height="108" fill="{GREEN}"/>{icon}</g></svg></g>'
            f'<text x="146" y="{(62 if tagline else 74) + top}" font-family="{font}" font-weight="700" font-size="46" fill="{text}">Harambee</text>'
            f'<text x="148" y="{(96 if tagline else 108) + top}" font-family="{font}" font-weight="600" font-size="22" letter-spacing="5" fill="{sub}">TRACKER</text>'
            + tag + '</svg>\n')

open("store/brand/logo-horizontal.svg", "w").write(lockup(False))
open("store/brand/logo-horizontal-dark.svg", "w").write(lockup(True))
open("store/brand/logo-tagline.svg", "w").write(lockup(False, tagline=True))
open("store/brand/logo-tagline-dark.svg", "w").write(lockup(True, tagline=True))

# ---- Android vector drawables ----
def vd(paths, w=108):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by store/brand/make_logo.py; edit there. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="{w}dp" android:height="{w}dp"\n'
            f'    android:viewportWidth="108" android:viewportHeight="108">\n{paths}</vector>\n')

def circle_d(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 Z"

def vd_paths(fg, gold, badge_fg, bg, mono=False):
    """VectorDrawable has no stroke-behind-fill, so separation gaps are drawn as background-coloured shapes first."""
    out = []
    gap = BASE["gap"] * S
    for kind, d, outline in shapes():
        if kind == "person":
            body, hx, hy, hr = d
            if outline and bg:
                out.append(f'    <path android:fillColor="{bg}" android:strokeColor="{bg}" android:strokeWidth="{f(gap)}" android:pathData="{body}"/>\n')
                out.append(f'    <path android:fillColor="{bg}" android:pathData="{circle_d(hx, hy, hr + gap / 2)}"/>\n')
            out.append(f'    <path android:fillColor="{fg}" android:pathData="{body}"/>\n')
            out.append(f'    <path android:fillColor="{fg}" android:pathData="{circle_d(hx, hy, hr)}"/>\n')
        else:
            cx, cy, r, ring = d
            path, w = check_path(cx, cy, r)
            if mono:
                # One colour: the badge becomes an outlined circle with the check inside.
                out.append(f'    <path android:strokeColor="{fg}" android:strokeWidth="{f(w * 0.8)}" android:pathData="{circle_d(cx, cy, r - w * 0.4)}"/>\n')
                out.append(f'    <path android:strokeColor="{fg}" android:strokeWidth="{f(w)}" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{path}"/>\n')
                continue
            if bg:
                out.append(f'    <path android:fillColor="{bg}" android:pathData="{circle_d(cx, cy, r + ring / 2)}"/>\n')
            out.append(f'    <path android:fillColor="{gold}" android:pathData="{circle_d(cx, cy, r)}"/>\n')
            out.append(f'    <path android:strokeColor="{badge_fg}" android:strokeWidth="{f(w)}" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{path}"/>\n')
    return "".join(out)

use_fit(32.5)
res = "app/src/main/res/drawable"
# Launcher foreground on transparent: gaps must cut through to the green background layer.
open(f"{res}/ic_launcher_foreground.xml", "w").write(vd(vd_paths(WHITE, GOLD, GREEN, GREEN)))
# Themed (monochrome) icon: Android tints a single colour, so there are no background-coloured gaps.
open(f"{res}/ic_launcher_monochrome.xml", "w").write(vd(vd_paths("#FF000000", "#FF000000", "#FF000000", None, mono=True)))
# Notification icon: white silhouette on 24dp.
def notif():
    paths = vd_paths("#FFFFFFFF", "#FFFFFFFF", "#FFFFFFFF", None, mono=True)
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by store/brand/make_logo.py; edit there. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp" android:height="24dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            '    <group android:scaleX="1.45" android:scaleY="1.45" android:pivotX="54" android:pivotY="54">\n'
            + paths.replace("    <path", "        <path") + '    </group>\n</vector>\n')
open(f"{res}/ic_notification.xml", "w").write(notif())
print("scale", round(S, 3))
