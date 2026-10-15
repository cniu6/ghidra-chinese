import json,re,sys,os,glob
tag=re.compile(r'(<[^>]*>)')
HAS=re.compile(r'[A-Za-z]{2}')
src=json.load(open('C:/zhwork/ui.json',encoding='utf-8'))
mt={}
for l in open('C:/zhwork/units_cache.jsonl',encoding='utf-8'):
    try: d=json.loads(l); mt[d['k']]=d['v']
    except: pass
# glossary overrides (human reviewed): key stripped
gl={}
for f in sorted(glob.glob('C:/zhwork/gl/*.tsv')):
    for l in open(f,encoding='utf-8'):
        l=l.rstrip('\n')
        if '\t' not in l: continue
        k,v=l.split('\t',1)
        k=k.replace('\\n','\n'); v=v.replace('\\n','\n')
        if not k.strip() or not v.strip(): continue
        if re.fullmatch(r'[A-Za-z0-9_]+',k.strip()) and '_' in k: continue
        gl[k.strip()]=v.strip()
# term normalisation of MT output
FIX=[ # (english regex, wrong zh, right zh)
 (r'\bfunctions?\b','功能','函数'),
 (r'\bdata ?types?\b','数据类别','数据类型'),
 (r'\bdata ?types?\b','资料类型','数据类型'),
 (r'\bdisassembl','拆卸','反汇编'),
 (r'\bdisassembl','反汇编程序','反汇编器'),
 (r'\bdecompil','反编译程序','反编译器'),
 (r'\bsymbols?\b','象征','符号'),
 (r'\bnamespaces?\b','名称空间','命名空间'),
 (r'\bbookmarks?\b','书签','书签'),
 (r'\bprogram trees?\b','节目树','程序树'),
 (r'\bprogram\b','节目','程序'),
 (r'\bprograms\b','节目','程序'),
 (r'\bcomments?\b','评论','注释'),
 (r'\bthunk','雷击','Thunk'),
 (r'\bequates?\b','等价物','等值'),
 (r'\bequates?\b','等式','等值'),
 (r'\bscripts?\b','脚本','脚本'),
 (r'\bcodebrowser\b','代码浏览器','CodeBrowser'),
 (r'\bcode ?browser\b','代码浏览器','CodeBrowser'),
 (r'\bmnemonics?\b','助记词','助记符'),
 (r'\boperands?\b','操作数','操作数'),
 (r'\bpcode\b','p代码','Pcode'),
 (r'\bp-code\b','p代码','Pcode'),
 (r'\bbyte ?viewer\b','字节查看器','字节查看器'),
 (r'\bcheck ?out\b','签出','检出'),
 (r'\bcheck ?in\b','签入','检入'),
 (r'\bsleigh\b','睡眠','Sleigh'),
 (r'\bcursor\b','光标','光标'),
 (r'\bapply\b','应用','应用'),
 (r'actions?','行动','操作'),
 (r'actions?','动作','操作'),
 (r'unions?','工会','联合体'),
 (r'unions?','联盟','联合体'),
 (r'listing','上市','列表'),
 (r'listing','清单','列表'),
 (r'plugins?','插件','插件'),
 (r'highlight','突出显示','高亮'),
 (r'navigat','航行','导航'),
 (r'graph','图表','图'),
 (r'script','剧本','脚本'),
 (r'structures?','建筑','结构'),
 (r'memory','记忆','内存'),
 (r'registers?','寄存','寄存器'),
 (r'registers?','登记册','寄存器'),
 (r'undo','撤销','撤销'),
 (r'stack','堆栈','栈'),
 (r'import','进口','导入'),
 (r'export','出口','导出'),
 (r'project','工程','项目'),
 (r'field','领域','字段'),
 (r'fields','田','字段'),
 (r'bookmark','标签页','书签'),
]
def fix(en,zh):
    low=en.lower()
    for pat,wrong,right in FIX:
        if wrong!=right and wrong in zh and re.search(pat,low): zh=zh.replace(wrong,right)
    return zh
def units_of(s):
    segs=[]
    if tag.search(s) and '<' in s:
        for seg in tag.split(s):
            if seg.startswith('<') and seg.endswith('>'): continue
            segs.append(seg.replace('&lt;','<').replace('&gt;','>').replace('&nbsp;',' ').replace('&amp;','&'))
    else: segs=[s]
    return segs
out={}
def lookup(core):
    if core in gl: return gl[core]
    v=mt.get(core)
    if v is None: return None
    return fix(core,v)
def esc(x): return x.replace('\\','\\\\').replace('\n','\\n').replace('\t','\\t').replace('\r','')
for s in src:
    if re.match(r'^\s{6,}',s): continue
    for seg in units_of(s):
        lines=seg.split('\n')
        # whole-seg override from glossary
        if seg.strip() in gl and '\n' not in seg.strip():
            pass
        for ln in lines:
            c=ln.strip()
            if not c or not HAS.search(c): continue
            z=lookup(c)
            if z and z!=c: out[c]=z
        # fragment entries keep surrounding whitespace (single line only)
        if '\n' not in seg and seg!=seg.strip() and seg.strip() in out:
            m=re.match(r'^(\s*)(.*?)(\s*)$',seg,re.S)
            out[seg]=m.group(1)+out[seg.strip()]+m.group(3)
# glossary entries not covered by source scan (full-text overrides)
for k,v in gl.items():
    if v!=k and HAS.search(k): out[k]=v
rows=[]
for k,v in out.items():
    if '\t' in k or len(k)<2: continue
    if not re.search(r'[\u4e00-\u9fff]',v): continue   # untranslated / no CJK
    if re.match(r'^\s{2,}',k): continue
    if re.match(r'^\s*"\w+"\s*:',k): continue
    if ' ' not in k.strip() and len(k.strip())<4 and k.strip() not in gl: continue
    if k.startswith('&') and len(k)<8: continue
    rows.append((k,v))
rows.sort()
dst=sys.argv[1]
with open(dst,'w',encoding='utf-8',newline='\n') as f:
    f.write('# Ghidra 12.1.4 界面汉化词典 (英文<TAB>中文)\n')
    for k,v in rows: f.write(esc(k)+'\t'+esc(v)+'\n')
print(len(rows),os.path.getsize(dst))
