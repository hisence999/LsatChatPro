import zipfile
import json
import os

backup_path = r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840.zip"

try:
    with zipfile.ZipFile(backup_path, 'r') as z:
        print("Files in backup:")
        for name in z.namelist():
            print(f" - {name}")
            
        if "settings.json" in z.namelist():
            print("\nSettings.json content:")
            with z.open("settings.json") as f:
                settings = json.load(f)
                print(json.dumps(settings, indent=2))
        else:
            print("\nsettings.json not found in backup.")
            
except Exception as e:
    print(f"Error: {e}")
