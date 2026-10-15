import os,re,sys,json,collections
root=sys.argv[1]
BS=chr(92)
lit=re.compile(r'"((?:[^"\\\n]|\\.)*)"')
cnt=collections.Counter()
skipdirs=('/src/test/','/src/screen/','/src/test.slow/','/ghidra_scripts/','/Test/','/src/javadoc/','/GhidraBuild/','/certification','/src/pcodeTest','/Skeleton/','/src/main/help/','/src/test.processors/')
for dp,dn,fn in os.walk(root):
    if '.git' in dp.split(os.sep): continue
    p=dp.replace(BS,'/')+'/'
    if any(s in p for s in skipdirs): continue
    for f in fn:
        if not f.endswith('.java'): continue
        try: txt=open(os.path.join(dp,f),encoding='utf-8',errors='replace').read()
        except: continue
        for line in txt.split('\n'):
            s=line.strip()
            if s.startswith(('//','*','/*','import ','package ','@')): continue
            for m in lit.finditer(line):
                cnt[m.group(1)]+=1
def ok(s):
    if len(s)<3 or len(s)>500: return False
    if not re.search(r'[A-Za-z]{3}',s): return False
    if re.fullmatch(r'[\w./:$#%-]+',s):
        return bool(re.fullmatch(r'[A-Z][a-z]{2,}',s))
    if re.match(r'^(java|javax|ghidra|docking|generic|resources|help|http|https)[./]',s): return False
    if re.search(r'\.(java|png|gif|jpg|html|xml|properties|class)$',s): return False
    return True
c2={k:v for k,v in cnt.items() if ok(k)}
print(len(cnt),len(c2))
json.dump(sorted(c2),open('C:/zhwork/cands.json','w'),ensure_ascii=False,indent=0)
