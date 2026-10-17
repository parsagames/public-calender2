#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
prepare_android.py - helper used by .github/workflows/build-apk.yml

  python3 prepare_android.py web       (run BEFORE "npx cap add android")
      creates the www/ folder that Capacitor packs into the APK:
      index.html + pdf.js (used by the in-app Quran viewer)

  python3 prepare_android.py android   (run AFTER "npx cap add android")
      app icon (adaptive + normal), splash image, location permissions,
      version number

  python3 prepare_android.py sdk       (run before the Gradle build)
      prints what the build machine has and installs Android platform 34
      / build-tools 34.0.0 only if they are missing
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.abspath(__file__))
os.chdir(ROOT)

RES = os.path.join('android', 'app', 'src', 'main', 'res')
MANIFEST = os.path.join('android', 'app', 'src', 'main', 'AndroidManifest.xml')

PDFJS_VERSION = '3.11.174'
PDFJS_FILES = ['pdf.min.js', 'pdf.worker.min.js']
PDFJS_SOURCES = [
    'https://cdn.jsdelivr.net/npm/pdfjs-dist@%s/legacy/build/' % PDFJS_VERSION,
    'https://unpkg.com/pdfjs-dist@%s/legacy/build/' % PDFJS_VERSION,
    'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/%s/' % PDFJS_VERSION,
]


# ---------------------------------------------------------------- web
def download(url, dest, tries=3):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            if len(data) < 10000:
                raise ValueError('file too small (%d bytes)' % len(data))
            with open(dest, 'wb') as f:
                f.write(data)
            return True
        except Exception as e:  # noqa
            print('   attempt %d failed: %s' % (i + 1, e))
            time.sleep(2)
    return False


def prepare_web():
    if not os.path.isfile('index.html'):
        sys.exit('ERROR: index.html was not found next to prepare_android.py')
    shutil.rmtree('www', ignore_errors=True)
    os.makedirs(os.path.join('www', 'pdfjs'))
    shutil.copy('index.html', os.path.join('www', 'index.html'))
    if os.path.isfile('supabase-config.js'):
        shutil.copy('supabase-config.js', os.path.join('www', 'supabase-config.js'))
    else:
        print('WARNING: supabase-config.js is missing - online sharing will not work')

    ok = False
    for base in PDFJS_SOURCES:
        print('pdf.js from', base)
        if all(download(base + name, os.path.join('www', 'pdfjs', name)) for name in PDFJS_FILES):
            ok = True
            break
    if not ok:
        print('WARNING: pdf.js could not be downloaded - the Quran viewer will not work inside the APK')
        shutil.rmtree(os.path.join('www', 'pdfjs'), ignore_errors=True)
    print('www/ is ready:', sorted(os.listdir('www')))


# ---------------------------------------------------------------- sdk
def ensure_sdk():
    home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '/usr/local/lib/android/sdk'
    print('Android SDK folder:', home)
    for sub in ('platforms', 'build-tools'):
        p = os.path.join(home, sub)
        print('  %s: %s' % (sub, sorted(os.listdir(p)) if os.path.isdir(p) else 'missing'))

    wanted = ['platforms;android-34', 'build-tools;34.0.0']
    missing = [w for w in wanted if not os.path.isdir(os.path.join(home, *w.split(';')))]
    if not missing:
        print('All needed SDK packages are already installed.')
        return

    sdkm = shutil.which('sdkmanager')
    if not sdkm:
        found = sorted(glob.glob(os.path.join(home, 'cmdline-tools', '*', 'bin', 'sdkmanager')))
        sdkm = found[0] if found else None
    if not sdkm:
        print('sdkmanager not found - Gradle will try to download:', missing)
        return

    print('Installing:', missing)
    r = subprocess.run([sdkm, '--sdk_root=' + home, '--install'] + missing,
                       input='y\n' * 30, universal_newlines=True)
    print('sdkmanager exit code:', r.returncode)


# ---------------------------------------------------------------- android
def build_icon_master():
    """icon.png -> square image with transparent corners (or None)"""
    from PIL import Image, ImageDraw, ImageFilter

    if not os.path.isfile('icon.png'):
        print('WARNING: icon.png not found - the default icon is kept')
        return None
    src = Image.open('icon.png').convert('RGBA')
    w, h = src.size
    # the picture is a rounded square on a white background -> make the white outside transparent
    for pt in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        r, g, b, a = src.getpixel(pt)
        if a > 0 and min(r, g, b) > 230:
            ImageDraw.floodfill(src, pt, (0, 0, 0, 0), thresh=70)
    alpha = src.getchannel('A').filter(ImageFilter.MinFilter(3))  # remove the 1px white fringe
    src.putalpha(alpha)
    bbox = alpha.getbbox()
    if bbox:
        src = src.crop(bbox)
    side = max(src.size)
    master = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    master.paste(src, ((side - src.size[0]) // 2, (side - src.size[1]) // 2))
    return master


def make_icons(master):
    from PIL import Image

    # colour of the gold frame -> background of the adaptive icon
    s = master.size[0]
    pts = [(int(s * 0.012) + k, s // 2) for k in range(0, 6)]
    px = [master.getpixel(p) for p in pts if master.getpixel(p)[3] > 200] or [(205, 162, 77, 255)]
    gold = tuple(sum(c[i] for c in px) // len(px) for i in range(3))

    legacy = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
    for dens, size in legacy.items():
        out = os.path.join(RES, 'mipmap-' + dens)
        os.makedirs(out, exist_ok=True)
        im = master.resize((size, size), Image.LANCZOS)
        im.save(os.path.join(out, 'ic_launcher.png'))
        im.save(os.path.join(out, 'ic_launcher_round.png'))
        # adaptive icon foreground: 108dp canvas, picture kept inside the safe zone
        fsize = round(size * 108 / 48)
        inner = int(fsize * 0.60)
        canvas = Image.new('RGBA', (fsize, fsize), (0, 0, 0, 0))
        small = master.resize((inner, inner), Image.LANCZOS)
        canvas.paste(small, ((fsize - inner) // 2, (fsize - inner) // 2), small)
        canvas.save(os.path.join(out, 'app_icon_fg.png'))

    anydpi = os.path.join(RES, 'mipmap-anydpi-v26')
    os.makedirs(anydpi, exist_ok=True)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@color/app_icon_bg"/>\n'
           '    <foreground android:drawable="@mipmap/app_icon_fg"/>\n'
           '</adaptive-icon>\n')
    for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        with open(os.path.join(anydpi, name), 'w', encoding='utf-8') as f:
            f.write(xml)

    values = os.path.join(RES, 'values')
    os.makedirs(values, exist_ok=True)
    with open(os.path.join(values, 'app_icon_colors.xml'), 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '    <color name="app_icon_bg">#%02X%02X%02X</color>\n</resources>\n' % gold)
    print('icons written, adaptive background colour = #%02X%02X%02X' % gold)


def make_splash(master):
    from PIL import Image

    bg = (12, 33, 30)  # same dark green as the app (#0c211e)
    files = glob.glob(os.path.join(RES, 'drawable*', 'splash.png'))
    for f in files:
        try:
            w, h = Image.open(f).size
            canvas = Image.new('RGB', (w, h), bg)
            side = int(min(w, h) * 0.36)
            icon = master.resize((side, side), Image.LANCZOS)
            canvas.paste(icon, ((w - side) // 2, (h - side) // 2), icon)
            canvas.save(f)
        except Exception as e:  # noqa
            print('splash skipped for %s: %s' % (f, e))
    print('splash images replaced:', len(files))


def patch_manifest():
    if not os.path.isfile(MANIFEST):
        print('WARNING: AndroidManifest.xml not found')
        return
    with open(MANIFEST, encoding='utf-8') as f:
        text = f.read()
    add = []
    for perm in ('android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION'):
        if perm not in text:
            add.append('    <uses-permission android:name="%s" />' % perm)
    for perm in ('android.permission.POST_NOTIFICATIONS', 'android.permission.FOREGROUND_SERVICE',
                 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK', 'android.permission.WAKE_LOCK',
                 'android.permission.RECEIVE_BOOT_COMPLETED'):
        if perm not in text:
            add.append('    <uses-permission android:name="%s" />' % perm)
    if 'AdhanService' not in text and '</application>' in text:
        comp = ('        <service android:name=".AdhanService" android:exported="false" '
                'android:foregroundServiceType="mediaPlayback" />\n'
                '        <receiver android:name=".AdhanReceiver" android:exported="true">\n'
                '            <intent-filter>\n'
                '                <action android:name="android.intent.action.BOOT_COMPLETED" />\n'
                '                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />\n'
                '            </intent-filter>\n'
                '        </receiver>\n')
        text = text.replace('</application>', comp + '    </application>')
    need = [os.path.join('android','app','src','main','res',d,n) for d,n in
            (('xml','calendar_widget_info.xml'),('layout','widget_calendar.xml'),
             ('drawable','widget_bg.xml'),('values','widget_strings.xml'))]
    widget_ok = all(os.path.isfile(x) for x in need) and os.path.isfile(
        os.path.join('android','app','src','main','java','com','parsagames','amiralmomenincalendar','CalendarWidget.java'))
    if not widget_ok:
        print('WARNING: widget files missing in native/ -> widget skipped. missing:',
              [x for x in need if not os.path.isfile(x)])
    if widget_ok and 'CalendarWidget' not in text and '</application>' in text:
        wid = ('        <receiver android:name=".CalendarWidget" android:exported="true">\n'
               '            <intent-filter>\n'
               '                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />\n'
               '                <action android:name="android.intent.action.DATE_CHANGED" />\n'
               '                <action android:name="android.intent.action.TIME_SET" />\n'
               '                <action android:name="android.intent.action.TIMEZONE_CHANGED" />\n'
               '            </intent-filter>\n'
               '            <meta-data android:name="android.appwidget.provider" '
               'android:resource="@xml/calendar_widget_info" />\n'
               '        </receiver>\n')
        text = text.replace('</application>', wid + '    </application>')
    if 'android.hardware.location.gps' not in text:
        add.append('    <uses-feature android:name="android.hardware.location.gps" android:required="false" />')
    if add and '</manifest>' in text:
        text = text.replace('</manifest>', '\n'.join(add) + '\n</manifest>')
        with open(MANIFEST, 'w', encoding='utf-8') as f:
            f.write(text)
    print('manifest: added %d line(s)' % len(add))


def patch_version():
    run = os.environ.get('GITHUB_RUN_NUMBER')
    path = os.path.join('android', 'app', 'build.gradle')
    if not (run and run.isdigit() and os.path.isfile(path)):
        return
    with open(path, encoding='utf-8') as f:
        text = f.read()
    new = re.sub(r'versionCode\s+\d+', 'versionCode ' + run, text, count=1)
    new = re.sub(r'versionName\s+"[^"]*"', 'versionName "1.0.%s"' % run, new, count=1)
    if new != text:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(new)
    print('versionCode =', run)


def install_native():
    """native adhan alarm: Java files + MainActivity that registers the plugin"""
    src = os.path.join(ROOT, 'native')
    pkg = 'com.parsagames.amiralmomenincalendar'
    dest = os.path.join('android', 'app', 'src', 'main', 'java', *pkg.split('.'))
    if not os.path.isdir(src) or not os.path.isdir(os.path.join('android', 'app', 'src', 'main')):
        print('WARNING: native adhan files skipped')
        return
    os.makedirs(dest, exist_ok=True)
    for f in glob.glob(os.path.join(src, '*.java')):
        shutil.copy(f, dest)
    # res files are stored flat in native/ as  <folder>__<name>  (e.g. layout__widget_calendar.xml)
    for f in glob.glob(os.path.join(src, '*__*')):
        folder, name = os.path.basename(f).split('__', 1)
        rdir = os.path.join('android', 'app', 'src', 'main', 'res', folder)
        os.makedirs(rdir, exist_ok=True)
        shutil.copy(f, os.path.join(rdir, name))
        print('  res copied:', folder + '/' + name)
    with open(os.path.join(dest, 'MainActivity.java'), 'w', encoding='utf-8') as f:
        f.write('package %s;\n\nimport android.os.Bundle;\nimport com.getcapacitor.BridgeActivity;\n\n'
                'public class MainActivity extends BridgeActivity {\n'
                '    @Override\n    public void onCreate(Bundle savedInstanceState) {\n'
                '        registerPlugin(AdhanPlugin.class);\n'
                '        super.onCreate(savedInstanceState);\n    }\n}\n' % pkg)
    print('native adhan files installed in', dest)


def prepare_android():
    install_native()
    master = build_icon_master()
    if master is not None:
        make_icons(master)
        make_splash(master)
    patch_manifest()
    patch_version()


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else ''
    if cmd == 'web':
        prepare_web()
    elif cmd == 'android':
        prepare_android()
    elif cmd == 'sdk':
        ensure_sdk()
    else:
        sys.exit(__doc__)
