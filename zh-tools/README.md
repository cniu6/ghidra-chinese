# 汉化词典生成工具

词典 `Ghidra/Framework/Docking/src/main/resources/zh_CN_dict.tsv` 由下面几步生成（路径写死为作者本机，使用前请改）：

1. `extract.py <源码根目录>`  提取所有 .java 里的字符串字面量 → cands.json
2. `filter.py`                 过滤出"像界面文本"的 → ui.json
3. `mt2.py`                    机器翻译（分句批量）→ units_cache.jsonl
4. `build_dict.py <输出>`      合并术语表覆盖 + 术语纠正 → zh_CN_dict.tsv

运行时由 `docking.ZhTranslator` 加载并翻译界面（`-Dghidra.zh=false` 关闭）。
