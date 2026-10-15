import json,re,sys,time,os,threading,urllib.request,urllib.parse
from concurrent.futures import ThreadPoolExecutor
CACHE='C:/zhwork/units_cache.jsonl'
src=json.load(open('C:/zhwork/ui.json',encoding='utf-8'))
tag=re.compile(r'(<[^>]*>)')
HAS=re.compile(r'[A-Za-z]{2}')
def units_of(s):
    """yield (core) text units that runtime would look up"""
    segs=[]
    if tag.search(s) and '<' in s:
        for seg in tag.split(s):
            if seg.startswith('<') and seg.endswith('>'): continue
            segs.append(seg.replace('&lt;','<').replace('&gt;','>').replace('&nbsp;',' ').replace('&amp;','&'))
    else: segs=[s]
    for seg in segs:
        for ln in seg.split('\n'):
            c=ln.strip()
            if c and HAS.search(c): yield c
units=set()
for s in src:
    if re.match(r'^\s{6,}',s): continue
    for u in units_of(s): units.add(u)
units=sorted(units)
cache={}
if os.path.exists(CACHE):
    for l in open(CACHE,encoding='utf-8'):
        try: d=json.loads(l); cache[d['k']]=d['v']
        except: pass
todo=[u for u in units if u not in cache]
print('units',len(units),'todo',len(todo),flush=True)
lock=threading.Lock(); out=open(CACHE,'a',encoding='utf-8')
def gt(q):
    u="https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl=zh-CN&q="+urllib.parse.quote(q)
    for a in range(8):
        try:
            raw=urllib.request.urlopen(u,timeout=30).read().decode('utf-8')
            d=json.loads(raw)
            if isinstance(d,list) and d:
                x=d[0]
                if isinstance(x,list): x=x[0]
                return x
        except Exception:
            pass
        time.sleep(2*(a+1))
    return None
def put(k,v):
    with lock:
        cache[k]=v; out.write(json.dumps({'k':k,'v':v},ensure_ascii=False)+'\n')
def batch(items):
    r=gt('\n'.join(items))
    if r is not None:
        parts=r.split('\n')
        if len(parts)==len(items):
            for k,v in zip(items,parts): put(k,v.strip())
            return
    for k in items:
        r=gt(k)
        if r is not None: put(k,r.strip())
B=15
batches=[]
cur=[];curlen=0
for u in todo:
    cur.append(u);curlen+=len(u)
    if len(cur)>=B or curlen>1500: batches.append(cur);cur=[];curlen=0
if cur: batches.append(cur)
t0=time.time()
with ThreadPoolExecutor(int(sys.argv[1]) if len(sys.argv)>1 else 16) as ex:
    for i,_ in enumerate(ex.map(batch,batches)):
        if i%50==0: print(i,len(batches),round(time.time()-t0),flush=True); out.flush()
out.flush(); print('finished',round(time.time()-t0),len(cache))
