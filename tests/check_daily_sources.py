#!/usr/bin/env python3
"""Source and provenance regression checks (offline)."""
import json, pathlib, subprocess, hashlib
ROOT = pathlib.Path(__file__).resolve().parents[1]
rows = [line.split('\t', 2) for line in (ROOT/'app/assets/daily.tsv').read_text().splitlines() if line and not line.startswith('#')]
manifest = json.loads((ROOT/'docs/hadith-sources.json').read_text())
q = [r for r in rows if r[0] == 'Q']
h = [r for r in rows if r[0] == 'H']
assert len(q) == 29 and len(h) == len(manifest) == 60
assert len({r['id'] for r in manifest}) == len({r['url'] for r in manifest}) == 60
assert h == [['H', r['source'], r['text']] for r in manifest]
# Pin every Quran line to its pre-hadith revision, byte-for-byte.
baseline = subprocess.check_output(['git', 'show', '11bd97c:app/assets/daily.tsv'], cwd=ROOT).decode()
assert q == [l.split('\t',2) for l in baseline.splitlines() if l.startswith('Q\t')]
for r in manifest:
    assert r['url'].startswith('https://sunnah.com/') and r['cache_sha256']
    assert r['text'].strip() == r['text'] and '\n' not in r['text']
    assert r['height'] <= 252
print('PASS daily sources: 60 unique references; 29 Quran rows unchanged; asset/manifest identical')
