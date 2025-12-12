#!/usr/bin/env python3
"""Compare ZIP file structures."""
import zipfile
import os

EXAMPLE = "The conversion test/Example.zip"
CONVERTED = "The conversion test/LastChat_converted_backup.zip"

print("="*60)
print("ZIP FILE STRUCTURE COMPARISON")
print("="*60)

print("\n1. EXAMPLE ZIP:")
with zipfile.ZipFile(EXAMPLE, 'r') as z:
    for info in z.infolist():
        print(f"  {info.filename}: {info.file_size} bytes, compress={info.compress_type}")

print("\n2. CONVERTED ZIP:")
with zipfile.ZipFile(CONVERTED, 'r') as z:
    # Just show first 20 entries since there are many
    entries = z.infolist()
    print(f"  Total entries: {len(entries)}")
    for info in entries[:20]:
        print(f"  {info.filename}: {info.file_size} bytes, compress={info.compress_type}")
    if len(entries) > 20:
        print(f"  ... and {len(entries) - 20} more entries")

print("\n3. KEY FILES COMPARISON:")
with zipfile.ZipFile(EXAMPLE, 'r') as z:
    ex_files = {i.filename: i.file_size for i in z.infolist()}
with zipfile.ZipFile(CONVERTED, 'r') as z:
    cv_files = {i.filename: i.file_size for i in z.infolist()}

for key in ["rikka_hub.db", "settings.json", "rikka_hub-wal", "rikka_hub-shm"]:
    ex_size = ex_files.get(key, "MISSING")
    cv_size = cv_files.get(key, "MISSING")
    print(f"  {key}: example={ex_size}, converted={cv_size}")

print("\n4. EXAMPLE DB SIZE IN ZIP vs EXTRACTED:")
# Check if the DB in the zip matches what we have extracted
import tempfile
with zipfile.ZipFile(EXAMPLE, 'r') as z:
    db_info = z.getinfo("rikka_hub.db")
    print(f"  In ZIP: {db_info.file_size} bytes")

extracted_size = os.path.getsize("The conversion test/example_extracted/rikka_hub.db")
print(f"  Extracted: {extracted_size} bytes")

print("\n5. CONVERTED DB SIZE:")
with zipfile.ZipFile(CONVERTED, 'r') as z:
    db_info = z.getinfo("rikka_hub.db")
    print(f"  In ZIP: {db_info.file_size} bytes")

converted_size = os.path.getsize("The conversion test/converted/rikka_hub.db")
print(f"  On disk: {converted_size} bytes")
