$env:Path = "C:\Program Files\nodejs;C:\Users\Admin\AppData\Roaming\npm;" + $env:Path
Set-Location "C:\Users\Admin\StudioProjects\Checklists\firebase-functions"
.\venv\Scripts\Activate.ps1
python setup_remote_config.py
