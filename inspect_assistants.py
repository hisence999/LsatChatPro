import zipfile
import json

zip_path = r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840.zip"

try:
    with zipfile.ZipFile(zip_path, 'r') as z:
        if 'settings.json' in z.namelist():
            with z.open('settings.json') as f:
                data = json.load(f)
                assistants = data.get('assistants', [])
                print(f"Assistants count: {len(assistants)}")
                print(json.dumps(assistants, indent=2))
                
                print(f"Selected Assistant ID: {data.get('assistantId')}")
        else:
            print("settings.json not found in zip")
except Exception as e:
    print(f"Error: {e}")
