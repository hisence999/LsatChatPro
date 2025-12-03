import zipfile
import json

backup_path = r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840.zip"

try:
    with zipfile.ZipFile(backup_path, 'r') as z:
        if "settings.json" in z.namelist():
            with z.open("settings.json") as f:
                content = f.read().decode('utf-8')
                print(content)
        else:
            print("settings.json not found.")
            
except Exception as e:
    print(f"Error: {e}")
