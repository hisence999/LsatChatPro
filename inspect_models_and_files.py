import zipfile
import json

backup_path = r"c:/Users/julia/Documents/LastChat-LastChat/backup example/LastChat_backup_20251203_022840.zip"

try:
    with zipfile.ZipFile(backup_path, 'r') as z:
        if "settings.json" in z.namelist():
            with z.open("settings.json") as f:
                settings = json.load(f)
                print("Models in settings:")
                for model in settings.get("models", []):
                    print(json.dumps(model, indent=2))
        
        print("\nFiles in 'files/' folder in backup:")
        found_files = False
        for name in z.namelist():
            if name.startswith("files/"):
                print(f" - {name}")
                found_files = True
        if not found_files:
            print("No files found in 'files/' folder.")
            
except Exception as e:
    print(f"Error: {e}")
