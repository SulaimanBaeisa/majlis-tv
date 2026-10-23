#!/usr/bin/env python3
"""Sony-only, opt-in manual QA: one PRAYER_TEST banner over native Settings, never edits the real prayer alarm."""
import json,re,subprocess,time
from pathlib import Path
SERIAL='10.3.30.60:5555'
OUT=Path('/srv/projects/free-my-tv-ar/tv-recon/sony-v2-20261023')
def adb(*args):
    p=subprocess.run(['adb','-s',SERIAL,*args],capture_output=True,text=True,timeout=15)
    if p.returncode:raise RuntimeError(p.stderr)
    return p.stdout
def windows():return adb('shell','dumpsys','window')
def focus(text):return next((x.strip() for x in text.splitlines() if 'mCurrentFocus=' in x),'')
def visible(text):return any(re.search(r'Window #\d+ Window\{.*Free My TV prayer',l) for l in text.splitlines())
assert adb('shell','getprop','ro.product.manufacturer').strip().lower()=='sony'
adb('shell','am','start','-a','android.settings.SETTINGS');time.sleep(2)
before=focus(windows());assert before and 'io.freemytv' not in before,before
started=time.monotonic()
print(adb('shell','am','broadcast','-a','io.freemytv.PRAYER_TEST','-n','io.freemytv/.PrayerAlarm'),flush=True)
samples=[]
for target in [2,55,59,60,60.5,61,62,63]:
    time.sleep(max(0,started+target-time.monotonic()))
    w=windows();sample={'seconds':round(time.monotonic()-started,3),'present':visible(w),'focus':focus(w)};samples.append(sample)
    print(sample,flush=True)
    if target==2:
        assert sample['present'],'No popup appeared'
        png=subprocess.check_output(['adb','-s',SERIAL,'exec-out','screencap','-p']);(OUT/'adhan-over-native-settings.png').write_bytes(png)
        adb('shell','input','keyevent','KEYCODE_DPAD_DOWN')
        assert focus(windows())==before,'Banner took native Settings focus'
assert samples[1]['present'],'Banner did not last approximately one minute'
assert not samples[-1]['present'],'Banner failed to disappear'
assert all(s['focus']==before for s in samples),'Native app focus changed'
report={'tv':SERIAL,'native_focus':before,'samples':samples,'last_observed_present':max(s['seconds'] for s in samples if s['present']),'first_observed_absent':min(s['seconds'] for s in samples if not s['present']),'passed':True}
(OUT/'overlay-timing.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
adb('shell','input','keyevent','KEYCODE_HOME')
print('SONY_OVERLAY_QA PASS',flush=True)
