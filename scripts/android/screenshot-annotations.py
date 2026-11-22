#!/usr/bin/env python3
"""Small previews of explicitly selected, synthetic-AVD screenshots for visual review.
Full-resolution files remain in the Actions artifact. No logcat or user files are read.
"""
import base64
import io
import os
import sys
from PIL import Image

names = ["01_permissions_onboarding", "02_home", "03_feed", "04_chat", "05_quarto",
         "06_computer", "07_settings", "08_goals", "09_memory", "10_library", "11_permissions", "05b_quarto_stage_dialog"]
selection = names[:6] if sys.argv[1] == '1' else names[6:]
for name in selection:
    path = f'evidence-android/test-screenshots/{name}.png'
    if not os.path.isfile(path):
        print(f'::warning title=Screenshot missing::{name}')
        continue
    with Image.open(path) as image:
        image.thumbnail((260, 520))
        output = io.BytesIO()
        image.convert('RGB').save(output, format='JPEG', quality=35, optimize=True)
    print(f'::notice title=Screenshot {name}::{base64.b64encode(output.getvalue()).decode()}')
