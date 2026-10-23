#!/usr/bin/env python3
"""Sony-only QA for file import, replacing a same-named image, invalid-image fallback and safe cleanup."""
import hashlib,json,subprocess,time
from pathlib import Path
SERIAL='10.3.30.60:5555'
ROOT=Path('/srv/projects/free-my-tv-ar-src')
OUT=Path('/srv/projects/free-my-tv-ar/tv-recon/sony-v2-20261023')
REMOTE='/sdcard/Android/data/io.freemytv/files/wallpapers/sony-v2-qa-photo.jpg'
def adb(*a,ok=True):
    p=subprocess.run(['adb','-s',SERIAL,*a],capture_output=True,timeout=20)
    if ok and p.returncode:raise RuntimeError(p.stderr.decode(errors='replace'))
    return p

def text(*a):return adb(*a).stdout.decode().strip()
def restart():
    adb('shell','am','force-stop','io.freemytv');adb('shell','am','start','-n','io.freemytv/.HomeActivity');time.sleep(8)
def logs():
    pid=text('shell','pidof','io.freemytv:home')
    return text('logcat','-d','--pid='+pid,'-s','FreeMyTV:I')
assert text('shell','getprop','ro.product.manufacturer').lower()=='sony'
assert adb('shell','test','-e',REMOTE,ok=False).returncode!=0,'Refusing to overwrite a pre-existing file'
results=[];last_sha=None
invalid=Path('/home/sulaiman/.hermes/cache/scratch/sony-v2-invalid-image.jpg')
invalid.write_bytes(b'Deliberately invalid image: negative QA fixture, not wallpaper content.\n')
try:
    for label,source,expected in [('import',ROOT/'app/assets/saudi/s10.jpg','custom'),('replacement',ROOT/'app/assets/saudi/s39.jpg','custom'),('invalid-fallback',invalid,'bundled')]:
        last_sha=hashlib.sha256(source.read_bytes()).hexdigest()
        adb('push',str(source),REMOTE);assert text('shell','sha256sum',REMOTE).split()[0]==last_sha
        restart();s=logs();assert 'wallpaper source: '+expected in s,(label,s)
        # An invalid personal image must never be deleted by the app.
        assert text('shell','sha256sum',REMOTE).split()[0]==last_sha
        image=adb('exec-out','screencap','-p').stdout;(OUT/('custom-'+label+'.png')).write_bytes(image)
        results.append({'case':label,'source_sha256':last_sha,'selected':expected,'passed':True,'log':[l for l in s.splitlines() if 'wallpaper' in l or 'recommendation image:' in l]})
        print(label,'PASS',flush=True)
finally:
    # Remove only the generated fixture, and only if its bytes are still the bytes this test wrote.
    actual=adb('shell','sha256sum',REMOTE,ok=False)
    if actual.returncode==0 and actual.stdout.decode().split()[0]==last_sha:
        adb('shell','rm','--',REMOTE)
    assert adb('shell','test','-e',REMOTE,ok=False).returncode!=0,'Fixture changed externally; preserved for inspection'
    restart()
assert 'wallpaper source: bundled' in logs()
results.append({'case':'restore-bundled-catalogue','selected':'bundled','passed':True})
(OUT/'custom-wallpaper-qa.json').write_text(json.dumps(results,indent=2)+'\n')
print('SONY_CUSTOM_WALLPAPER_QA PASS',flush=True)
