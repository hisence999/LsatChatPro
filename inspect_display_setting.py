import zipfile
import json

zip_path = r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840.zip"

try:
    with zipfile.ZipFile(zip_path, 'r') as z:
        if 'settings.json' in z.namelist():
            with z.open('settings.json') as f:
                data = json.load(f)
                display_setting = data.get('displaySetting', {})
                print(json.dumps(display_setting, indent=2))
        else:
            print("settings.json not found in zip")
except Exception as e:
    print(f"Error: {e}")
