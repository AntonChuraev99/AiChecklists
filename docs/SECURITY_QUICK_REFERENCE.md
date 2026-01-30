# Security Quick Reference Card

## DO & DON'T Cheat Sheet

### DO ✓

```bash
# ✓ Copy from template (first-time setup)
cp composeApp/google-services.json.example composeApp/google-services.json

# ✓ Get actual file from Firebase Console
# https://console.firebase.google.com/project/aichecklists-40230/settings/general

# ✓ Verify file is gitignored
git status composeApp/google-services.json
# (should show no output = ignored)

# ✓ Store API keys in environment variables
export GEMINI_API_KEY="your_key_here"

# ✓ Scan before pushing
git secrets --scan

# ✓ Use Cloud Secrets for production
gcloud secrets create gemini-api-key --data-file=-
```

### DON'T ✗

```bash
# ✗ Never commit google-services.json
git add composeApp/google-services.json  # BLOCKED by .gitignore

# ✗ Never hardcode API keys in code
const API_KEY = "AIzaSy..."  # ✗ BAD

# ✗ Never commit .env with real values
echo "GEMINI_API_KEY=AIzaSy..." > .env
git add .env  # ✗ BAD

# ✗ Never share via email/Slack
# Instead: "Download from Firebase Console"

# ✗ Never use service accounts in mobile app
// ✗ BAD: Loading service-account.json in app
```

## File Locations Quick Map

| What | Where | Action |
|------|-------|--------|
| Firebase Android config | `composeApp/google-services.json` | Get from Console, don't commit |
| Template for above | `composeApp/google-services.json.example` | Reference only |
| Local build props | `local.properties` | Create from `.example`, don't commit |
| Build props template | `local.properties.example` | Keep in repo |
| Cloud Functions env | `firebase-functions/.env` | Use env var, don't commit |
| Functions env template | `firebase-functions/.env.example` | Keep in repo |

## Setup Checklist (First Day)

- [ ] `git clone` repository
- [ ] `cp composeApp/google-services.json.example composeApp/google-services.json`
- [ ] Download actual `google-services.json` from [Firebase Console](https://console.firebase.google.com/project/aichecklists-40230/settings/general)
- [ ] Replace file content
- [ ] `git status google-services.json` → should be ignored
- [ ] `cp local.properties.example local.properties`
- [ ] Add `GEMINI_API_KEY` from [AI Studio](https://aistudio.google.com/app/apikey)
- [ ] `git status local.properties` → should be ignored
- [ ] `git secrets --scan` → should pass
- [ ] Run `./gradlew build` ✓

## Before Every Push

```bash
# 1. Scan for secrets
git secrets --scan

# 2. Check if anything will leak
git diff --cached | grep -i "AIzaSy\|firebase\|secret"

# 3. View what you're pushing
git log -p @{u}..HEAD | head -50

# 4. If all clear, push
git push
```

## If You See a Leaked Key

1. **Don't panic** - Firebase keys can be regenerated instantly
2. **Stop using it** - Regenerate in [Firebase Console](https://console.firebase.google.com/project/aichecklists-40230/settings/general)
3. **Tell the team** - Slack: "Firebase key exposed in commit [SHA], regenerating now"
4. **Clean history** - Post cleanup step in team channel
5. **Rotate** - Follow [SECURITY.md](../SECURITY.md#security-incident-what-to-do-if-a-key-is-leaked)

## Console Links (Bookmark These)

- [Firebase Console](https://console.firebase.google.com/project/aichecklists-40230/settings/general) - Get google-services.json
- [AI Studio](https://aistudio.google.com/app/apikey) - Generate/regenerate Gemini API key
- [Google Cloud Console](https://console.cloud.google.com/functions?project=aichecklists-40230) - Deploy Cloud Functions

## Key Restrictions Verification

```bash
# Check Firebase API key restrictions
# Console → Settings → API Keys → Select key
# ✓ Should restrict to: Android + package name + SHA-1

# Check Gemini API key restrictions
# AI Studio → API Keys → Settings
# ✓ Should restrict to: Generative Language API only

# Check Cloud Functions secret
gcloud secrets list
# Output: gemini-api-key (secret, not code)
```

## Emergency Response

**Key leaked?**
```bash
# 1. Regenerate
# Firebase Console → Settings → Your apps → Regenerate

# 2. Update local
cp /path/from/console/google-services.json composeApp/

# 3. Notify team
# Team channel: "Firebase key regenerated, update your local copy"

# 4. If committed to git
gcloud auth login
cd /repo
git filter-repo --path composeApp/google-services.json --invert-paths
git push origin --force-with-lease master
```

---

**Need help?** See [SECURITY.md](../SECURITY.md)
**Quick start?** Follow "Setup Checklist" above
**Lost your key?** Download fresh from [Firebase Console](https://console.firebase.google.com/project/aichecklists-40230/settings/general)
