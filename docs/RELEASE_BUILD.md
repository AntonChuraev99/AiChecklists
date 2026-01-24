# Release Build Setup

**Current Version:** 1.1 (versionCode 2)

## 1. Create Keystore

Run this command in the project root:

```bash
keytool -genkey -v -keystore gisti-release.keystore -alias gisti -keyalg RSA -keysize 2048 -validity 10000
```

You will be prompted for:
- **Keystore password**: Choose a strong password (save it!)
- **Key password**: Can be the same as keystore password
- **Name, Organization, etc.**: Fill in your details

**Important**:
- Store the keystore file securely (NOT in git)
- Never lose the keystore or passwords — you cannot update the app without them!

---

## 2. Configure local.properties

Add these lines to `local.properties` in the project root:

```properties
# Release signing (DO NOT COMMIT THIS FILE)
KEYSTORE_FILE=../gisti-release.keystore
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=gisti
KEY_PASSWORD=your_key_password
```

**Note**: `local.properties` is already in `.gitignore`

---

## 3. Build Release APK

```bash
./gradlew composeApp:assembleRelease
```

Output: `composeApp/build/outputs/apk/release/composeApp-release.apk`

---

## 4. Build Release Bundle (for Play Store)

```bash
./gradlew composeApp:bundleRelease
```

Output: `composeApp/build/outputs/bundle/release/composeApp-release.aab`

---

## 5. Verify Signing

Check that the APK is signed:

```bash
# Windows
"%JAVA_HOME%\bin\jarsigner" -verify -verbose -certs composeApp/build/outputs/apk/release/composeApp-release.apk

# Linux/Mac
jarsigner -verify -verbose -certs composeApp/build/outputs/apk/release/composeApp-release.apk
```

---

## Checklist Before Release

- [ ] Keystore created and stored securely
- [ ] local.properties configured with keystore paths
- [ ] Version code incremented (`versionCode` in build.gradle.kts)
- [ ] Version name updated (`versionName` in build.gradle.kts)
- [ ] Release build compiles without errors
- [ ] APK/AAB is signed correctly
- [ ] Tested on real device

---

## Backup Recommendations

1. **Keystore file**: Store in a secure location (password manager, encrypted drive)
2. **Passwords**: Store in password manager
3. **Never commit** keystore or passwords to git
