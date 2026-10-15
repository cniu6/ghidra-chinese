import json,re,sys
c=json.load(open('C:/zhwork/cands.json',encoding='utf-8'))
def unesc(s):
    out=[];i=0
    while i<len(s):
        ch=s[i]
        if ch=='\\' and i+1<len(s):
            n=s[i+1];i+=2
            out.append({'n':'\n','t':'\t','r':'\r','"':'"',"'":"'",'\\':'\\'}.get(n,'\\'+n))
        else: out.append(ch);i+=1
    return ''.join(out)
code_pat=re.compile(r'::|->|=>|==|&&|\|\||\(\)|;\s*$|\bnull\b|\bSELECT\b|\bINSERT\b|\$\{|\\[dwsb]|\[\^|\.\*|\bhttp://|\bhttps://|\.\./|^\s*[#/*]|[A-Z]{2,}_[A-Z_]{2,}|\b0x[0-9a-fA-F]+\b.*\b0x|\bgetClass|\bthis\.|\bprivate |\bpublic |\bstatic ')
def ui_like(s):
    t=s.strip()
    if len(t)<3 or len(t)>400: return False
    if code_pat.search(t): return False
    letters=sum(ch.isalpha() for ch in t)
    if letters/ len(t) < 0.55: return False
    if ' ' not in t and not re.fullmatch(r'[A-Z][a-z]{2,}(\.\.\.|:)?',t): return False
    if re.fullmatch(r'[a-z]+(_[a-z0-9]+)+',t): return False
    if re.search(r'[a-z][A-Z][a-z]',t) and ' ' not in t: return False   # camelCase ident
    if re.search(r'\w\.\w+\(',t): return False
    if re.search(r'[{}<>]',t) and not t.lower().startswith('<html') and not re.search(r'</?(b|i|br|p|ul|li|font|code|tt|u|center|html|body|h\d|table|tr|td|hr)\b',t): return False
    if sum(not (ch.isalnum() or ch in ' .,:;\'"!?()-_/%&<>=#+*[]{}@\n\t') for ch in t)>2: return False
    return True
res=[]
seen=set()
for s in c:
    u=unesc(s)
    if u in seen: continue
    seen.add(u)
    if ui_like(u): res.append(u)
print(len(c),len(res))
json.dump(res,open('C:/zhwork/ui.json','w',encoding='utf-8'),ensure_ascii=False,indent=0)
import random;random.seed(3)
open('C:/zhwork/sample.txt','w',encoding='utf-8').write('\n'.join(random.sample(res,60)))
