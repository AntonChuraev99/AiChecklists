$env:Path = "C:\Program Files\nodejs;C:\Users\Admin\AppData\Roaming\npm;" + $env:Path

# Get access token from firebase
$token = firebase login:ci --no-localhost 2>$null
if (-not $token) {
    # Try to get token from gcloud
    $token = gcloud auth print-access-token 2>$null
}

if (-not $token) {
    Write-Host "Could not get access token. Please run 'gcloud auth application-default login' first."
    exit 1
}

$projectId = "aichecklists-40230"
$url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/remote_config/current"

$body = @{
    fields = @{
        initial_ai_credits = @{ integerValue = "100" }
        ai_action_cost = @{ integerValue = "30" }
        feature_ai_analysis_enabled = @{ booleanValue = $true }
        ai_analysis_max_input_length = @{ integerValue = "10000" }
    }
} | ConvertTo-Json -Depth 10

Write-Host "Creating remote_config/current document..."
Invoke-RestMethod -Uri $url -Method PATCH -Headers @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
} -Body $body

Write-Host "Done!"
