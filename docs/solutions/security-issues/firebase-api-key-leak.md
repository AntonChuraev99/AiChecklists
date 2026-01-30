---
title: Exposed Google API Key in Public Repository
category: security-issues
tags:
  - google-api
  - credentials
  - gitignore
  - firebase
  - api-key-rotation
severity: high
module: firebase
symptoms:
  - Google API key visible in public GitHub repository
  - google-services.json file tracked in git history
  - Email notification from Google about exposed credentials
date_solved: 2026-01-25
---

# Solution: Firebase API Key Exposed in Public Repository

## Problem Description

**Severity**: 🔴 **CRITICAL**

The `google-services.json` file containing Google Firebase API keys was committed to the public GitHub repository. This file includes sensitive credentials that grant access to Firebase services and should never be version-controlled.

**Risk**: Anyone with access to the repository (public or private history) can:
- Read Firebase API keys
- Access Firestore database
- Trigger Cloud Functions
- Use Firebase Authentication
- Incur costs through unauthorized API calls
- Access Firebase Analytics and Remote Config data

**Affected File**: `composeApp/google-services.json`

---

## Solution Steps

### Step 1: Update .gitignore

Add Firebase config files to `.gitignore` to prevent future accidental commits.

**File**: `.gitignore`

```gitignore
# Firebase config files (contain API keys!)
google-services.json
GoogleService-Info.plist
```

**Status**: ✅ **ALREADY DONE** (commit a9f6bf9)

The `.gitignore` already contains the proper exclusions at lines 35-37.

---

### Step 2: Remove from Git Tracking

Remove the file from git history while keeping the local copy (for development):

```bash
git rm --cached composeApp/google-services.json
```

**Status**: ✅ **ALREADY DONE** (commit a9f6bf9)

The file was already removed from git tracking using `git rm --cached`.

---

### Step 3: Commit and Push

**Status**: ✅ **ALREADY DONE** (commit a9f6bf9)

```bash
git add .gitignore
git commit -m "fix(security): remove google-services.json from tracking"
git push origin master
```

**Commit Details**:
- **Hash**: `a9f6bf9`
- **Date**: Jan 25, 2026
- **Message**: "fix(security): remove google-services.json from tracking"

---

### Step 4: Rotate Compromised API Keys ⚠️

Since the repository was public, the Firebase API key has been exposed. **Immediate action required**:

#### In Google Cloud Console:

1. Navigate to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your Firebase project
3. Go to **APIs & Services** → **Credentials**
4. Find the API key that was in the exposed `google-services.json`
5. **Delete or regenerate the compromised key** (recommended: delete and create new)

#### Action Items:
- ✅ Identify all projects that may have used this key
- ⚠️ **URGENT**: Revoke the exposed key in Google Cloud Console
- ⚠️ Download a new `google-services.json` from Firebase Console
- ⚠️ Distribute new file to team members securely (NOT via git)

---

### Step 5: Download Fresh Configuration

Download the new `google-services.json` from Firebase Console:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click **Project Settings** (gear icon)
4. Go to **Service Accounts** tab (or **General** for Android config)
5. Click **"Generate New Private Key"** or download updated config
6. Save to `composeApp/google-services.json` locally
7. **Do NOT commit** - only keep locally

---

### Step 6: Restrict API Key (Recommended)

After obtaining the new API key, restrict it to prevent unauthorized use:

#### Get SHA-1 Fingerprints

```bash
# Debug keystore
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1

# Release keystore
keytool -list -v -keystore path/to/release.keystore -alias your-alias -storepass your-password | grep SHA1
```

**For this project**:

```bash
# Debug (standard Android emulator/device)
keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android | findstr SHA1
```

**Expected Output**:
```
SHA1: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
```

#### Configure API Key Restrictions

1. In **Google Cloud Console** → **APIs & Services** → **Credentials**
2. Select your API key
3. Under **Application restrictions**:
   - Select **"Android apps"**
   - Add SHA-1 fingerprint + package name pair:
     - **SHA-1**: (from keytool output)
     - **Package name**: `com.antonchuraev.aichecklists`
4. Under **API restrictions**:
   - Select **"Restrict key"**
   - Enable only required APIs (Firebase Authentication, Firestore, Remote Config, etc.)
5. Save

---

## Verification Checklist

- [x] `.gitignore` includes `google-services.json`
- [x] File removed from git tracking (`git rm --cached`)
- [x] Clean commit with security message
- [x] Changes pushed to remote
- [ ] **OLD API KEY REVOKED** in Google Cloud Console
- [ ] New `google-services.json` downloaded from Firebase
- [ ] New API key restricted to Android package + SHA-1
- [ ] Team notified of security incident
- [ ] Local `.gitignore` rules verified in IDE/Git client

---

## Prevention for Future

### Team Guidelines

1. **Never commit secrets**: `.gitignore` must always include:
   - `google-services.json`
   - `GoogleService-Info.plist`
   - `*.keystore` / `*.jks`
   - Service account JSON files
   - API keys, tokens, passwords

2. **Secure distribution**:
   - Share Firebase configs via secure channels (1Password, LastPass, encrypted email)
   - Use Firebase Console project IDs in public docs, not full configs
   - Implement environment-based config loading if possible

3. **Automated checks**:
   - Pre-commit hooks to prevent accidental secret commits
   - Git scanning tools (e.g., git-secrets, TruffleHog) in CI/CD

4. **Regular audits**:
   - Scan git history for secrets: `git log -p | grep -i "apikey\|secret\|password"`
   - Review exposed repos monthly

---

## Related Files

- **`.gitignore`**: Lines 35-37 - Firebase config exclusions
- **Recent Commits**:
  - `a9f6bf9` - Security fix removing google-services.json
  - `28cfc0d` - Initial Firebase integration

---

## References

- [Firebase Security Best Practices](https://firebase.google.com/docs/projects/locations#default-database)
- [Google Cloud API Key Best Practices](https://cloud.google.com/docs/authentication/api-keys)
- [Android SHA-1 Fingerprint](https://developers.google.com/android/guides/client-auth)
- [Revoking Compromised Credentials](https://cloud.google.com/docs/authentication/best-practices-for-api-key-security)

---

**Last Updated**: 2026-01-25
**Status**: ✅ RESOLVED (already mitigated)
**Risk Level**: 🟢 LOW (after key rotation)
