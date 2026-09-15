#!/usr/bin/env python3
"""Install a local update only if it preserves the existing app's identity."""
import argparse
import os
from pathlib import Path
import plistlib
import re
import shutil
import signal
import subprocess
import tempfile
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('candidate', type=Path)
parser.add_argument('installed', type=Path)
parser.add_argument('--check-only', action='store_true')
args = parser.parse_args()
candidate, installed = args.candidate.resolve(), args.installed.resolve()

def info(app):
    with (app / 'Contents/Info.plist').open('rb') as file:
        return plistlib.load(file)

try:
    old, new = info(installed), info(candidate)
    if old['CFBundleIdentifier'] != new['CFBundleIdentifier'] or candidate == installed:
        raise ValueError('Use separate app bundles with the same bundle identifier.')
    subprocess.run(['codesign', '--verify', '--deep', '--strict', str(candidate)], check=True)
    identity = subprocess.check_output(['codesign', '-d', '-r-', str(installed)], stderr=subprocess.STDOUT, text=True)
    requirement = re.search(r'(?m)^(?:# )?designated => (.+)$', identity)
    if not requirement:
        raise ValueError('Cannot establish the installed signing identity.')
    subprocess.run(['codesign', '--verify', '--strict', '-R', '=' + requirement[1], str(candidate)], check=True)
except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
    parser.exit(1, f'Update refused; installed app was not changed. Build/sign with the original signing identity.\n{error}\n')

if args.check_only:
    print('Signature matches the installed app; existing permission identity is preserved.')
    raise SystemExit(0)

# Stage before stopping the app. Keep a rollback copy rather than deleting it.
with tempfile.TemporaryDirectory(prefix='.foldlight-update-', dir=installed.parent) as temporary:
    staged = Path(temporary) / installed.name
    shutil.copytree(candidate, staged, symlinks=True)
    subprocess.run(['codesign', '--verify', '--deep', '--strict', '-R', '=' + requirement[1], str(staged)], check=True)
    executable = installed / 'Contents/MacOS' / old['CFBundleExecutable']
    found = subprocess.run(['pgrep', '-f', '^' + re.escape(str(executable)) + '$'], capture_output=True, text=True)
    processes = [int(pid) for pid in found.stdout.split()]
    for pid in processes:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    for _ in range(100):
        if all(subprocess.run(['kill', '-0', str(pid)], capture_output=True).returncode != 0 for pid in processes):
            break
        time.sleep(.1)
    else:
        parser.exit(1, 'App did not exit; update was not installed.\n')
    backup_root = Path.home() / 'Library/Application Support/Foldlight/Update Backups'
    backup_root.mkdir(parents=True, exist_ok=True)
    backup = backup_root / f'{time.time_ns()}-{installed.name}'
    installed.rename(backup)
    try:
        staged.rename(installed)
    except OSError:
        backup.rename(installed)
        raise
    subprocess.run(['open', str(installed)], check=True)
    print(f'Installed {new["CFBundleShortVersionString"]}; previous app retained at {backup}')
