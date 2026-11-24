#!/usr/bin/env python3
"""Expose required synthetic AVD images by immutable Git blob ID, without a branch commit.
GitHub's artifact storage host is unavailable to the review sandbox. Only the
allowlisted screenshots are sent; no logcat, user files, keys, or APKs.
"""
import base64
import json
import os
import subprocess

names = ['01_permissions_onboarding', '02_home', '03_feed', '04_chat', '05_quarto',
         '06_computer', '07_settings', '08_goals', '09_memory', '10_library',
         '11_permissions', '05b_quarto_stage_dialog']
# Fail with the missing names listed (not a bare FileNotFoundError): the evidence set is required, not optional.
missing = [n for n in names if not os.path.isfile(f'evidence-android/test-screenshots/{n}.png')]
if missing:
    print('::error title=Review image blobs::missing screenshots: ' + ', '.join(missing))
    raise SystemExit(1)
result = []
for name in names:
    path = f'evidence-android/test-screenshots/{name}.png'
    with open(path, 'rb') as image:
        data = base64.b64encode(image.read()).decode('ascii')
    payload = json.dumps({'content': data, 'encoding': 'base64'})
    output = subprocess.run(['gh', 'api', '-X', 'POST', f"repos/{os.environ['GITHUB_REPOSITORY']}/git/blobs", '--input', '-'], input=payload, text=True, capture_output=True, check=True)
    sha = json.loads(output.stdout)['sha']
    result.append(f'{name}={sha}')
print('::notice title=Review image blobs::' + '%0A'.join(result))
