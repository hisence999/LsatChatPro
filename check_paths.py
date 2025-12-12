#!/usr/bin/env python3
"""Check file paths in source settings."""
import json
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

with open("The conversion test/extracted/settings.json", 'r', encoding='utf-8') as f:
    s = json.load(f)

print("User Avatar:", s['displaySetting']['userAvatar'])

for i, asst in enumerate(s['assistants']):
    print(f"\nAssistant {i} ({asst['name']}):")
    print(f"  ID: {asst['id']}")
    print(f"  Avatar: {asst['avatar']}")
    print(f"  Background: {asst.get('background')}")
