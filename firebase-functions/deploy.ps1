$env:Path = "C:\Program Files\nodejs;C:\Users\Admin\AppData\Roaming\npm;" + $env:Path
Set-Location "C:\Users\Admin\StudioProjects\Checklists"
firebase deploy --only functions
