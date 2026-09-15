#!/usr/bin/env python3
"""Check Git-selected files; report locations without echoing sensitive values."""
from pathlib import Path
import hashlib
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
selected = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).decode().split('\0')
rules = {
    'private key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'GitHub token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b'),
    'cloud access key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'personal home path': re.compile(r'/(?:Users|home)/[A-Za-z0-9_.-]+/'),
    'private network address': re.compile(r'\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b'),
    'Apple signing team': re.compile(r'DEVELOPMENT_TEAM\s*=\s*[A-Z0-9]{10}\s*;'),
    'Samsung serial': re.compile(r'\bR5[A-Z0-9]{9}\b'),
    'Apple device ID': re.compile(r'\b00008[0-9A-F]{3}-[0-9A-F]{16}\b'),
}
errors=[]
# Reviewed raster export of the public website's vector logo, without metadata.
reviewed_binary_hashes={'android-global/gradle/wrapper/gradle-wrapper.jar':'2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046','web/apple-touch-icon.png':'7b11e363e71cfbbbb8dd2549c37bd6869fc40efe6c6c9fc9e3b23b6dac4ce504'}
for name in sorted(set(filter(None,selected))):
    if name.startswith('analytics/'):
        errors.append(f'{name}: website administration is excluded from the public repository');continue
    path=ROOT/name
    if not path.is_file():continue
    if path.suffix.lower() in {'.apk','.aab','.ipa','.dmg','.pem','.key','.jks','.keystore','.p12','.mobileprovision','.sqlite','.db'} or path.name=='local.properties' or ((path.name.startswith('.env') or path.name.endswith('.env')) and path.name!='.env.example'):
        errors.append(f'{name}: prohibited local/artifact file');continue
    if path.stat().st_size>2_000_000:errors.append(f'{name}: oversized source file')
    try:text=path.read_text()
    except UnicodeDecodeError:
        if name in reviewed_binary_hashes:
            if hashlib.sha256(path.read_bytes()).hexdigest()!=reviewed_binary_hashes[name]:errors.append(f'{name}: binary changed since review')
        elif name not in {'android/gradle/wrapper/gradle-wrapper.jar','DuoLikeAnimation/Assets.xcassets/AppIcon.appiconset/AppIcon.png'}:errors.append(f'{name}: unreviewed binary')
        continue
    for number,line in enumerate(text.splitlines(),1):
        for label,pattern in rules.items():
            if pattern.search(line):errors.append(f'{name}:{number}: {label}')
        for email in re.findall(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}',line):
            if not email.endswith(('@example.com','@example.org','@example.net')):errors.append(f'{name}:{number}: non-example email')
if errors:
    print('\n'.join(errors));sys.exit(1)
print(f'Public-content scan passed ({len(set(filter(None,selected)))} files). Manual asset and history review remains required.')
