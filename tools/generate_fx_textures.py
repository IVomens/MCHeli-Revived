# Generates the modern particle sprite sheets (8 frames in a row, 64 px each).
import math, random
from PIL import Image, ImageFilter
import os
OUT=os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources', 'assets', 'mcheli', 'textures', 'fx') + os.sep
S=64; N=8
random.seed(7)

def value_noise(size, cells, seed):
    rnd=random.Random(seed)
    grid=[[rnd.random() for _ in range(cells+1)] for _ in range(cells+1)]
    img=Image.new('L',(size,size))
    px=img.load()
    for y in range(size):
        for x in range(size):
            fx=x/size*cells; fy=y/size*cells
            ix,iy=int(fx),int(fy); tx,ty=fx-ix,fy-iy
            tx=tx*tx*(3-2*tx); ty=ty*ty*(3-2*ty)
            a=grid[iy][ix]*(1-tx)+grid[iy][ix+1]*tx
            b=grid[iy+1][ix]*(1-tx)+grid[iy+1][ix+1]*tx
            px[x,y]=int(255*(a*(1-ty)+b*ty))
    return img

def fbm(size, seed):
    acc=[0.0]*(size*size); amp=1.0; tot=0
    for o,c in enumerate((3,6,12,24)):
        n=list(value_noise(size,c,seed*31+o).getdata())
        for i in range(len(acc)): acc[i]+=n[i]/255*amp
        tot+=amp; amp*=0.5
    return [a/tot for a in acc]

def sheet(frame_fn, name, blur=0.8):
    out=Image.new('RGBA',(S*N,S),(0,0,0,0))
    for f in range(N):
        im=frame_fn(f/(N-1), f)
        if blur: im=im.filter(ImageFilter.GaussianBlur(blur))
        out.paste(im,(f*S,0))
    out.save(OUT+name)

def puff(t, f, seed_base, tint):
    noise=fbm(S, seed_base+f)
    im=Image.new('RGBA',(S,S))
    px=im.load(); c=S/2
    radius=0.62+0.3*t
    for y in range(S):
        for x in range(S):
            d=math.hypot(x-c+0.5,y-c+0.5)/(S/2)
            n=noise[y*S+x]
            edge=radius*(0.75+0.5*n)
            a=max(0.0,1-(d/edge)**2)
            a*=0.55+0.45*n
            shade=0.78+0.22*n
            r,g,b=(int(tint[i]*shade) for i in range(3))
            px[x,y]=(r,g,b,int(255*min(1,a)))
    return im

sheet(lambda t,f: puff(t,f,10,(255,255,255)),'smoke.png',1.2)
sheet(lambda t,f: puff(t,f,40,(222,200,160)),'dust.png',1.2)

def flame(t,f):
    noise=fbm(S, 90+f)
    im=Image.new('RGBA',(S,S)); px=im.load(); c=S/2
    for y in range(S):
        for x in range(S):
            d=math.hypot(x-c+0.5,y-c+0.5)/(S/2)
            n=noise[y*S+x]
            a=max(0.0,1-d/(0.55+0.45*n))*(1-0.6*t)
            heat=max(0,min(1,(1-d)*1.4*(1-0.5*t)+0.2*n))
            r=255; g=int(55+170*heat**1.5); b=int(10+140*heat**4)
            px[x,y]=(r,g,b,int(255*min(1,a*1.3)))
    return im
sheet(flame,'flame.png',1.0)

def spark(t,f):
    im=Image.new('RGBA',(S,S)); px=im.load(); c=S/2
    for y in range(S):
        for x in range(S):
            dx=(x-c+0.5)/(S/2); dy=(y-c+0.5)/(S/2)
            a=max(0,1-abs(dx)*1.1)*max(0,1-abs(dy)*7)
            core=max(0,1-math.hypot(dx*1.6,dy*9))
            px[x,y]=(255,int(200+55*core),int(120+135*core),int(255*min(1,a+core)))
    return im
sheet(spark,'spark.png',0.6)

def debris(t,f):
    rnd=random.Random(200+f)
    im=Image.new('RGBA',(S,S),(0,0,0,0))
    from PIL import ImageDraw
    d=ImageDraw.Draw(im); c=S/2
    pts=[]
    k=rnd.randint(5,8)
    for i in range(k):
        ang=i/k*2*math.pi+rnd.uniform(-0.3,0.3)
        r=rnd.uniform(10,24)
        pts.append((c+math.cos(ang)*r,c+math.sin(ang)*r))
    g=rnd.randint(40,80)
    d.polygon(pts,fill=(g,g-4,g-8,255),outline=(g+30,g+26,g+20,255))
    return im
sheet(debris,'debris.png',0.5)

def ring(t,f):
    im=Image.new('RGBA',(S,S)); px=im.load(); c=S/2
    rr=0.35+0.6*t; w=0.10+0.05*(1-t)
    for y in range(S):
        for x in range(S):
            d=math.hypot(x-c+0.5,y-c+0.5)/(S/2)
            a=max(0,1-abs(d-rr)/w)*(1-t)
            px[x,y]=(255,240,220,int(255*a))
    return im
sheet(ring,'shockwave.png',0.6)

def flash(t,f):
    im=Image.new('RGBA',(S,S)); px=im.load(); c=S/2
    for y in range(S):
        for x in range(S):
            d=math.hypot(x-c+0.5,y-c+0.5)/(S/2)
            a=max(0,1-d)**2*(1-t)
            px[x,y]=(255,int(230-60*d),int(170-120*d),int(255*a))
    return im
sheet(flash,'flash.png',0.4)
print('ok')
