#!/usr/bin/env python3
"""Compare settings.json format."""

import json
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SOURCE = "The conversion test/extracted/settings.json"
EXAMPLE = "The conversion test/example_extracted/settings.json"

print("="*60)
print("COMPARING SETTINGS.JSON")
print("="*60)

with open(SOURCE, 'r', encoding='utf-8') as f:
    source_settings = json.load(f)

with open(EXAMPLE, 'r', encoding='utf-8') as f:
    example_settings = json.load(f)

print("\nSOURCE KEYS:")
for key in sorted(source_settings.keys()):
    val_type = type(source_settings[key]).__name__
    print(f"  {key}: {val_type}")

print("\nEXAMPLE KEYS:")
for key in sorted(example_settings.keys()):
    val_type = type(example_settings[key]).__name__
    print(f"  {key}: {val_type}")

# Find key differences
source_keys = set(source_settings.keys())
example_keys = set(example_settings.keys())

only_in_source = source_keys - example_keys
only_in_example = example_keys - source_keys

if only_in_source:
    print(f"\n[!] KEYS ONLY IN SOURCE: {only_in_source}")
if only_in_example:
    print(f"\n[!] KEYS ONLY IN EXAMPLE: {only_in_example}")

# Deep compare assistants
print("\n" + "="*60)
print("COMPARING ASSISTANTS STRUCTURE")
print("="*60)

if 'assistants' in source_settings and 'assistants' in example_settings:
    src_asst = source_settings['assistants'][0] if source_settings['assistants'] else {}
    ex_asst = example_settings['assistants'][0] if example_settings['assistants'] else {}
    
    print("\nSOURCE ASSISTANT KEYS:")
    for key in sorted(src_asst.keys()):
        print(f"  {key}")
    
    print("\nEXAMPLE ASSISTANT KEYS:")
    for key in sorted(ex_asst.keys()):
        print(f"  {key}")
    
    src_keys = set(src_asst.keys())
    ex_keys = set(ex_asst.keys())
    
    only_src = src_keys - ex_keys
    only_ex = ex_keys - src_keys
    
    if only_src:
        print(f"\n[!] ASSISTANT KEYS ONLY IN SOURCE: {only_src}")
    if only_ex:
        print(f"\n[!] ASSISTANT KEYS ONLY IN EXAMPLE: {only_ex}")
