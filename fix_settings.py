#!/usr/bin/env python3
"""
Create fixed settings.json with corrected package paths.
"""

import json
import re
import os

SOURCE = "The conversion test/extracted/settings.json"
OUTPUT = "The conversion test/converted/settings_fixed.json"

OLD_PACKAGE = "lastchat.rikkafork.cocolal"
NEW_PACKAGE = "me.rerere.rikkahub"

print("="*60)
print("FIXING SETTINGS.JSON PATHS")
print("="*60)

with open(SOURCE, 'r', encoding='utf-8') as f:
    content = f.read()

# Count occurrences
old_count = content.count(OLD_PACKAGE)
print(f"\nFound {old_count} occurrences of old package name")

# Replace all occurrences
fixed_content = content.replace(OLD_PACKAGE, NEW_PACKAGE)

# Verify
new_count = fixed_content.count(NEW_PACKAGE)
print(f"Replaced with {new_count} occurrences of new package name")

# Save
with open(OUTPUT, 'w', encoding='utf-8') as f:
    f.write(fixed_content)

print(f"\nSaved to: {OUTPUT}")

# Verify the fix
settings = json.loads(fixed_content)
print("\nVerification:")
print(f"  User Avatar: {settings['displaySetting']['userAvatar']['url'][:80]}...")

for asst in settings['assistants']:
    avatar_url = asst['avatar'].get('url', 'none') if isinstance(asst['avatar'], dict) else 'none'
    print(f"  {asst['name']} Avatar: {avatar_url[:80] if len(avatar_url) > 80 else avatar_url}...")
