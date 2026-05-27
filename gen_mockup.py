#!/usr/bin/env python3
"""Generate an SVG mockup of the population-density / income feature screen.
Pure stdlib. Matches the existing front-end design tokens (App.css)."""
import random, math

random.seed(42)

W, H = 1320, 840
PANEL = 320

# ── design tokens (from web/src/App.css) ───────────────────────────────
INK = "#2d3748"; MUTE = "#718096"; BORDER = "#e2e8f0"; LIGHT = "#f7fafc"
ACCENT = "#2b6cb0"; ACCENT_BG = "#ebf8ff"
DOT_PASSES = "#3498db"; DOT_DEP = "#2ecc71"; DOT_ARR = "#e74c3c"
# YlOrRd choropleth ramp (low -> high density)
RAMP = ["#ffffb2", "#fecc5c", "#fd8d3c", "#f03b20", "#bd0026"]

S = []  # svg fragments
def add(x): S.append(x)
def esc(t): return (t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"))
def txt(x,y,t,size=13,fill=INK,weight="400",anchor="start",ff="system-ui,-apple-system,'Segoe UI',Roboto,sans-serif",op=1):
    add(f'<text x="{x}" y="{y}" font-family="{ff}" font-size="{size}" fill="{fill}" '
        f'font-weight="{weight}" text-anchor="{anchor}" opacity="{op}">{esc(t)}</text>')
def rect(x,y,w,h,fill,rx=0,stroke=None,sw=1,op=1):
    s=f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ""
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}"{s} opacity="{op}"/>')

add(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">')
add(f'<rect width="{W}" height="{H}" fill="#ffffff"/>')

# ════════════════════════════════════════════════════════════════════════
#  MAP AREA (choropleth)
# ════════════════════════════════════════════════════════════════════════
mx0, my0, mx1, my1 = PANEL, 0, W, H
rect(mx0, my0, mx1-mx0, my1-my0, "#eaf0f5")
# faint grid
for gx in range(PANEL, W, 46):
    add(f'<line x1="{gx}" y1="0" x2="{gx}" y2="{H}" stroke="#dde6ee" stroke-width="1"/>')
for gy in range(0, H, 46):
    add(f'<line x1="{PANEL}" y1="{gy}" x2="{W}" y2="{gy}" stroke="#dde6ee" stroke-width="1"/>')

# jittered lattice -> irregular neighbourhood polygons
COLS, ROWS = 11, 9
m = 40
ax0, ay0, ax1, ay1 = mx0+m, my0+m, mx1-m, my1-m
cw = (ax1-ax0)/COLS; ch=(ay1-ay0)/ROWS
pts=[[None]*(COLS+1) for _ in range(ROWS+1)]
for r in range(ROWS+1):
    for c in range(COLS+1):
        jx = 0 if c in (0,COLS) else random.uniform(-0.34,0.34)*cw
        jy = 0 if r in (0,ROWS) else random.uniform(-0.34,0.34)*ch
        pts[r][c]=(ax0+c*cw+jx, ay0+r*ch+jy)

# dense "urban core" focus -> density falls off radially + noise
fx, fy = ax0+(ax1-ax0)*0.46, ay0+(ay1-ay0)*0.44
maxd = math.hypot(ax1-ax0, ay1-ay0)/2
def density_at(x,y):
    d = math.hypot(x-fx,y-fy)/maxd
    base = max(0.0, 1-d)**1.6
    return max(0.0, min(1.0, base + random.uniform(-0.18,0.18)))

# secondary dense pocket (to look organic)
fx2, fy2 = ax0+(ax1-ax0)*0.78, ay0+(ay1-ay0)*0.70

cells=[]
for r in range(ROWS):
    for c in range(COLS):
        p=[pts[r][c],pts[r][c+1],pts[r+1][c+1],pts[r+1][c]]
        cxp=sum(q[0] for q in p)/4; cyp=sum(q[1] for q in p)/4
        v=density_at(cxp,cyp)
        d2=math.hypot(cxp-fx2,cyp-fy2)/maxd
        v=max(v,max(0.0,1-d2)**1.7*0.85 + random.uniform(-0.1,0.1))
        v=max(0.0,min(1.0,v))
        cells.append((p,v,cxp,cyp))

def ramp_color(v):
    i=min(len(RAMP)-1,int(v*len(RAMP)))
    return RAMP[i]

# highlighted cell = the densest near core
hl=max(cells,key=lambda cc: cc[1])
for p,v,cxp,cyp in cells:
    pth=" ".join(f"{x:.1f},{y:.1f}" for x,y in p)
    is_hl = (p is hl[0])
    add(f'<polygon points="{pth}" fill="{ramp_color(v)}" fill-opacity="0.82" '
        f'stroke="{"#1a202c" if is_hl else "#ffffff"}" stroke-width="{2.4 if is_hl else 0.8}"/>')

# bus lines (selected, serving dense bairros) — blue polylines through core
def busline(waypoints,color=ACCENT,w=4):
    d="M "+" L ".join(f"{x:.0f} {y:.0f}" for x,y in waypoints)
    add(f'<path d="{d}" fill="none" stroke="#ffffff" stroke-width="{w+2.5}" stroke-linecap="round" stroke-linejoin="round" opacity="0.85"/>')
    add(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>')
busline([(ax0+10,ay0+220),(fx-120,fy-30),(fx,fy),(fx+180,fy+120),(ax1-30,ay1-90)])
busline([(fx-40,ay0+8),(fx+10,fy-60),(fx,fy),(fx-60,fy+160),(ax0+120,ay1-10)], color="#2f855a")
busline([(ax0+8,fy+40),(fx-90,fy+50),(fx,fy),(fx2-40,fy2-30),(fx2,fy2),(ax1-10,fy2+60)], color="#c05621")

# stop dots along core
for sx,sy in [(fx,fy),(fx-120,fy-30),(fx+180,fy+120),(fx2,fy2),(fx-60,fy+160)]:
    add(f'<circle cx="{sx:.0f}" cy="{sy:.0f}" r="4.5" fill="#fff" stroke="{INK}" stroke-width="1.6"/>')

# ── map title pill ──
rect(mx0+22,22,430,40,"#ffffff",rx=8,stroke=BORDER)
txt(mx0+40,47,"Densidade populacional por bairro — Belo Horizonte",15,INK,"600")

# ── zoom control ──
rect(mx1-58,22,36,72,"#ffffff",rx=8,stroke=BORDER)
txt(mx1-40,52,"+",22,MUTE,"600","middle"); add(f'<line x1="{mx1-52}" y1="58" x2="{mx1-28}" y2="58" stroke="{BORDER}"/>')
txt(mx1-40,86,"−",22,MUTE,"600","middle")

# ── popup on highlighted bairro ──
hx,hy=hl[2],hl[3]
pw,ph=246,118; px=min(hx+14, mx1-pw-20); py=max(hy-ph-14, 70)
add(f'<rect x="{px}" y="{py}" width="{pw}" height="{ph}" rx="10" fill="#ffffff" stroke="{BORDER}" stroke-width="1.2"/>')
add(f'<polygon points="{px+30},{py+ph} {px+46},{py+ph} {px+34},{py+ph+12}" fill="#ffffff" stroke="{BORDER}" stroke-width="1"/>')
txt(px+16,py+26,"Cabana do Pai Tomás",14,INK,"600")
add(f'<rect x="{px+16}" y="{py+36}" width="116" height="22" rx="11" fill="{RAMP[4]}"/>')
txt(px+24,py+51,"18.420 hab/km²",12,"#ffffff","600")
add(f'<rect x="{px+138}" y="{py+36}" width="78" height="22" rx="11" fill="#fef2f2" stroke="#fecaca"/>')
txt(px+148,py+51,"Renda: D",12,"#b91c1c","600")
txt(px+16,py+80,"12 linhas atendem este bairro",12.5,INK,"500")
for i,(lab,col) in enumerate([("7 passam",DOT_PASSES),("3 partem",DOT_DEP),("2 chegam",DOT_ARR)]):
    bx=px+16+i*78
    add(f'<circle cx="{bx+4}" cy="{py+98}" r="4" fill="{col}"/>')
    txt(bx+13,py+102,lab,11,MUTE)

# ── attribution ──
rect(mx1-300,my1-30,288,22,"#ffffff",rx=4,op=0.9)
txt(mx1-292,my1-15,"© OpenFreeMap · IBGE Censo 2022 · PBH",11,MUTE)

# ════════════════════════════════════════════════════════════════════════
#  LEFT PANEL
# ════════════════════════════════════════════════════════════════════════
rect(0,0,PANEL,H,"#ffffff")
add(f'<line x1="{PANEL}" y1="0" x2="{PANEL}" y2="{H}" stroke="{BORDER}" stroke-width="1"/>')
y=0
# header
rect(0,0,PANEL,64,"#ffffff",stroke=BORDER)
txt(16,30,"BH Bus Lines",18,"#1a202c","700")
txt(16,50,"Densidade · Renda · Cobertura de linhas",12.5,MUTE)

# segmented toggle (layer switch)
ty=80
rect(16,ty,PANEL-32,38,LIGHT,rx=8,stroke=BORDER)
rect(20,ty+4,(PANEL-40)/2,30,ACCENT,rx=6)
txt(20+(PANEL-40)/4,ty+24,"Densidade",13,"#ffffff","600","middle")
txt(20+(PANEL-40)*3/4,ty+24,"Renda A/B/C/D",13,MUTE,"500","middle")

# legend
ly=140
txt(16,ly,"DENSIDADE (hab/km²)",11,MUTE,"600")
labels=["< 3.000","3.000 – 6.000","6.000 – 10.000","10.000 – 15.000","> 15.000"]
for i,(col,lab) in enumerate(zip(RAMP,labels)):
    ry=ly+14+i*26
    rect(16,ry,26,18,col,rx=4,stroke="#ffffff",sw=1)
    txt(52,ry+14,lab,13,INK)

# filters card
fy0=300
rect(16,fy0,PANEL-32,160,LIGHT,rx=10,stroke=BORDER)
txt(30,fy0+26,"FILTROS",11,MUTE,"600")
# density slider
txt(30,fy0+52,"Densidade mínima",13,INK,"500")
add(f'<line x1="30" y1="{fy0+70}" x2="{PANEL-30}" y2="{fy0+70}" stroke="{BORDER}" stroke-width="6" stroke-linecap="round"/>')
add(f'<line x1="30" y1="{fy0+70}" x2="{206}" y2="{fy0+70}" stroke="{ACCENT}" stroke-width="6" stroke-linecap="round"/>')
add(f'<circle cx="206" cy="{fy0+70}" r="9" fill="#ffffff" stroke="{ACCENT}" stroke-width="3"/>')
txt(PANEL-30,fy0+52,"8.000",12,ACCENT,"600","end")
# income class chips
txt(30,fy0+104,"Classe de renda",13,INK,"500")
chips=[("A",False),("B",False),("C",True),("D",True)]
cxp=30
for lab,on in chips:
    bg=ACCENT if on else "#ffffff"; fg="#ffffff" if on else MUTE
    rect(cxp,fy0+114,52,30,bg,rx=8,stroke=ACCENT if on else BORDER)
    txt(cxp+26,fy0+134,lab,13,fg,"600","middle")
    cxp+=62

# divider + results header
dy=480
txt(16,dy,"LINHAS NOS BAIRROS FILTRADOS",11,MUTE,"600")
add(f'<rect x="232" y="{dy-13}" width="72" height="18" rx="9" fill="{ACCENT_BG}"/>')
txt(268,dy,"34 linhas",11.5,ACCENT,"600","middle")

# line list
lines=[
    ("9250","Cabana / Centro via Amazonas",DOT_PASSES),
    ("8201","Barreiro / Estação Vilarinho",DOT_PASSES),
    ("64","Aglomerado / Savassi",DOT_DEP),
    ("SC01","Circular Centro-Sul",DOT_PASSES),
    ("5102","Taquaril / Centro",DOT_ARR),
    ("9550","Vila Pinho / Hospitais",DOT_DEP),
    ("3051","Granja de Freitas / Centro",DOT_PASSES),
]
lyy=dy+18
for i,(num,name,dot) in enumerate(lines):
    row=lyy+i*42
    sel = i==0
    if sel: rect(8,row,PANEL-16,38,ACCENT_BG,rx=6)
    add(f'<circle cx="22" cy="{row+19}" r="5" fill="{dot}"/>')
    txt(38,row+24,num,13,ACCENT if sel else INK,"700")
    txt(86,row+24,name,12.5,ACCENT if sel else "#4a5568","500" if sel else "400")

add('</svg>')
open("/home/user/uai-bus-lines-map/density-preview.svg","w").write("\n".join(S))
print("wrote density-preview.svg")
