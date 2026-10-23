#!/usr/bin/env python3
"""Measure frame stats for one scenario at a time on a TV (adb serial from argv)."""
import re, subprocess, sys, time

D = sys.argv[1]
def sh(*a): return subprocess.check_output(['adb', '-s', D, 'shell', *a], text=True)
def stats():
    t = sh('dumpsys', 'gfxinfo', 'io.freemytv:home')
    g = lambda p: (re.search(p, t).group(1) if re.search(p, t) else '?')
    return ('frames %s janky %s med %s p90 %s p95 %s p99 %s' % (
        g(r'Total frames rendered: (\d+)'), g(r'Janky frames: (\d+)'),
        g(r'50th percentile: (\d+)'), g(r'90th percentile: (\d+)'),
        g(r'95th percentile: (\d+)'), g(r'99th percentile: (\d+)')))

def scenario(name, actions, wait=2.5):
    sh('input', 'keyevent', 'KEYCODE_HOME'); time.sleep(1.5)
    sh('dumpsys', 'gfxinfo', 'io.freemytv:home', 'reset')
    for a in actions:
        sh('input', 'keyevent', a); time.sleep(0.4)
    time.sleep(wait)
    print(name, stats(), flush=True)

scenario('open1 ', ['KEYCODE_DPAD_DOWN'], 3)
scenario('close1', ['KEYCODE_DPAD_DOWN', 'KEYCODE_BACK'], 2.5)
scenario('open2 ', ['KEYCODE_DPAD_DOWN'], 3)
scenario('close2', ['KEYCODE_DPAD_DOWN', 'KEYCODE_BACK'], 2.5)
scenario('tabsw ', ['KEYCODE_DPAD_UP', 'KEYCODE_DPAD_LEFT'], 3)
scenario('tabback', ['KEYCODE_DPAD_RIGHT'], 3)
