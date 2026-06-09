import os
import re

color_map = {
    '"#333333"': '"@color/text_primary"',
    '"#333"': '"@color/text_primary"',
    '"#888888"': '"@color/text_secondary"',
    '"#D32F2F"': '"@color/danger"',
    '"#F44336"': '"@color/danger"',
    '"#9AA0A6"': '"@color/text_hint"',
    '"#4CAF50"': '"@color/primary"',
    '"#E0E0E0"': '"@color/outline"',
    '"#F5F5F5"': '"@color/background"',
    '"#FFF0F0"': '"@color/error_bg"',
    '"#FFCDD2"': '"@color/error_stroke"',
    '"#994CAF50"': '"@color/primary_alpha"',
    '"#E8E8E8"': '"@color/progress_track"',
    '"#FFFFFF"': '"@color/surface"',  # Be careful, but looking at usage it's mostly for text/background that should be surface.
    '"#2196F3"': '"@color/accent"',
}

dirs_to_check = [
    'app/src/main/res/layout',
    'app/src/main/res/drawable'
]

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    for old, new in color_map.items():
        # Only replace if the color doesn't have extra alpha except when it matches exactly
        content = content.replace(old, new)
        
        # Also handle lowercase variants
        content = content.replace(old.lower(), new)

    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")

for d in dirs_to_check:
    for root, _, files in os.walk(d):
        for file in files:
            if file.endswith('.xml'):
                process_file(os.path.join(root, file))

print("Done.")
