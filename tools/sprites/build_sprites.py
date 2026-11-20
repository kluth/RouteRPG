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
WALK_FRAMES=6; WALK_HEADINGS=24; WALK_SIZE=128
PETS=[('dog','animal-dog'),('cat','animal-cat'),('lion','animal-lion'),('tiger','animal-tiger'),('bunny','animal-bunny'),('elephant','animal-elephant'),
 ('giraffe','animal-giraffe'),('pig','animal-pig'),('cow','animal-cow'),('monkey','animal-monkey'),('fish','animal-fish'),('parrot','animal-parrot'),
 ('chick','animal-chick'),('caterpillar','animal-caterpillar'),('hog','animal-hog')]
PEOPLE=[(f'male_{c}',f'character-male-{c}') for c in 'abcdefg']+[(f'female_{c}',f'character-female-{c}') for c in 'abcdef']+[(n,f'character-{n}') for n in ['worker','robot','soldier','knight','alien','zombie','raider']]
WALKERS=[(i,'pets/'+f+'.glb') for i,f in PETS]+[(i,'mini/'+f+'.glb') for i,f in PEOPLE]
only=sys.argv[1:]
os.makedirs(OUT,exist_ok=True)
env={**os.environ,'NODE_PATH':subprocess.check_output(['npm','root','-g']).decode().strip()}
total=0
for mid,f,yaw in MODELS:
    if only and mid not in only: continue
    tmp=f'{ROOT}/out/b_{mid}'
    subprocess.run(['node','render.js',f,tmp,str(FRAMES),str(RENDER),str(ELEV),str(yaw)],cwd=ROOT,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
    for i in range(FRAMES):
        im=Image.open(f'{tmp}_{i:03d}.png').convert('RGBA').resize((FINAL,FINAL),Image.LANCZOS)
        p=f'{OUT}/{mid}_{i:02d}.webp'
        im.save(p,'WEBP',quality=90,method=6)
        total+=os.path.getsize(p)
        os.remove(f'{tmp}_{i:03d}.png')
    print(mid,'ok',flush=True)
# Laufende Figuren: Gehzyklus (6 Bilder) in 13 Richtungen von Norden bis Sueden ueber Osten; die andere Haelfte wird gespiegelt
for mid,f in WALKERS:
    if only and mid not in only: continue
    tmp=f'{ROOT}/out/w_{mid}'
    subprocess.run(['node','render.js',f,tmp,str(WALK_HEADINGS),'256',str(ELEV),'0','walk',str(WALK_FRAMES)],cwd=ROOT,env={**env,'HALF':'1'},stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
    for h in range(WALK_HEADINGS//2+1):
        for w in range(WALK_FRAMES):
            src=f'{tmp}_{h*WALK_FRAMES+w:03d}.png'
            im=Image.open(src).convert('RGBA').resize((WALK_SIZE,WALK_SIZE),Image.LANCZOS)
            p=f'{OUT}/{mid}_h{h:02d}_w{w}.webp'
            im.save(p,'WEBP',quality=82,method=6)
            total+=os.path.getsize(p); os.remove(src)
    print(mid,'ok',flush=True)
print('total KB',total//1024)
