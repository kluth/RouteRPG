import os, subprocess, sys, io
from PIL import Image
ROOT=os.environ.get('SPRITE_WORK','/tmp/r3d')
OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','app','src','main','assets','figures3d')
FRAMES=36; RENDER=384; FINAL=192; ELEV=28
MODELS=[ # id, datei, yaw-Korrektur
 ('sedan','car/sedan.glb',0),('taxi','car/taxi.glb',0),('suv','car/suv.glb',0),('race','car/race.glb',0),
 ('police','car/police.glb',0),('ambulance','car/ambulance.glb',0),('firetruck','car/firetruck.glb',0),('van','car/van.glb',0),
 ('pickup','car/truck-flat.glb',0),('delivery','car/delivery.glb',0),('garbage','car/garbage-truck.glb',0),('tractor','car/tractor.glb',0),
 ('train_bullet','train/train-electric-bullet-a.glb',0),('train_city','train/train-electric-city-a.glb',0),('train_city_b','train/train-electric-city-b.glb',0),
 ('sailboat','water/boat-sail-a.glb',0),('speedboat','water/boat-speed-a.glb',0),('liner','water/ship-ocean-liner-small.glb',0),
 ('rowboat','water/boat-row-small.glb',0),('cargoship','water/ship-cargo-a.glb',0),('galleon','water/ship-small.glb',0),
 ('rocket','space/spaceCraft3.obj',180),('ufo','space/spaceCraft6.obj',180),
]
only=sys.argv[1:]
os.makedirs(OUT,exist_ok=True)
env={**os.environ,'NODE_PATH':subprocess.check_output(['npm','root','-g']).decode().strip()}
total=0
for mid,f,yaw in MODELS:
    if only and mid not in only: continue
    tmp=f'{ROOT}/out/b_{mid}'
    subprocess.run(['node','render.js',f,tmp,str(FRAMES),str(RENDER),str(ELEV),str(yaw)],cwd=ROOT,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
    for i in range(FRAMES):
        im=Image.open(f'{tmp}_{i:02d}.png').convert('RGBA').resize((FINAL,FINAL),Image.LANCZOS)
        p=f'{OUT}/{mid}_{i:02d}.webp'
        im.save(p,'WEBP',quality=90,method=6)
        total+=os.path.getsize(p)
        os.remove(f'{tmp}_{i:02d}.png')
    print(mid,'ok',flush=True)
print('total KB',total//1024)
