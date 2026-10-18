"""Every lang file carries every key of the generated en_us.json (release rule, docs/RELEASE.md).
usage: lang_check.py   exit 1 when a key is missing or a file is not valid JSON"""
import glob, json, os, sys
root = os.path.join(os.path.dirname(__file__), '..')
en = json.load(open(os.path.join(root, 'src/generated/resources/assets/tentaworks/lang/en_us.json'), encoding='utf-8'))
ok = True
for f in sorted(glob.glob(os.path.join(root, 'src/main/resources/assets/tentaworks/lang/*.json'))):
    name = os.path.basename(f)
    try:
        d = json.load(open(f, encoding='utf-8'))
    except ValueError as e:
        print(f'FAIL {name}: {e}'); ok = False; continue
    missing = sorted(k for k in en if k not in d)
    extra = sorted(k for k in d if k not in en)
    if missing or extra:
        ok = False
        print(f'FAIL {name}: {len(missing)} missing, {len(extra)} unknown' + (': ' + ', '.join(missing[:5]) if missing else ''))
    else:
        print(f'PASS {name}: {len(d)} keys')
print('PASS' if ok else 'FAIL')
sys.exit(0 if ok else 1)
