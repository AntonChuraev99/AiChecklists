# Firebase Setup

## Current Configuration

The project is configured with Firebase project: `aichecklists-40230`

### Android
- `google-services.json` is in `composeApp/`
- Firebase Analytics, Crashlytics, and Remote Config are integrated

### iOS (TODO)
Firebase iOS requires CocoaPods setup:

1. Add to `iosApp/Podfile`:
```ruby
pod 'Firebase/Analytics'
pod 'Firebase/Crashlytics'
pod 'Firebase/RemoteConfig'
```

2. Run `pod install`

3. Add `GoogleService-Info.plist` to iOS project

4. Initialize Firebase in `AppDelegate.swift`:
```swift
import Firebase
FirebaseApp.configure()
```

## Remote Config Keys

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `feature_ai_analysis_enabled` | Boolean | true | Enable AI analysis feature |
| `feature_paywall_enabled` | Boolean | false | Enable paywall |
| `max_checklist_items` | Long | 100 | Max items per checklist |
| `ai_analysis_max_input_length` | Long | 10000 | Max input length for AI |
| `min_app_version` | String | "1.0.0" | Minimum supported app version |
| `maintenance_mode` | Boolean | false | Enable maintenance mode |

## Managing Remote Config via Script

Use the Python script to manage Remote Config:

```bash
# Show current config
python scripts/firebase_remote_config.py get

# Set up default parameters
python scripts/firebase_remote_config.py setup
```

Requires: `pip install google-auth google-auth-oauthlib requests`

## Setting Up Remote Config in Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select project `aichecklists-40230`
3. Navigate to Remote Config
4. Add parameters with keys from the table above
5. Publish changes

## API Keys

### Gemini API Key
Stored in `local.properties` (not committed):
```
GEMINI_API_KEY=your_key_here
```

Get a key at: https://makersuite.google.com/app/apikey
