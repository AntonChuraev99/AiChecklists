# Google Service Account Setup

This guide explains how to create a Service Account for RevenueCat integration with Google Play.

---

## Step 1: Google Cloud Console

1. Open [Google Cloud Console](https://console.cloud.google.com)
2. Select the project linked to your app
3. Navigate to: **IAM & Admin** → **Service Accounts**
4. Click **+ Create Service Account**

---

## Step 2: Configure Service Account

| Field | Value |
|-------|-------|
| Name | `revenuecat-integration` |
| ID | (auto-generated) |
| Description | `RevenueCat purchase validation` |

Click **Create and Continue**

---

## Step 3: Grant Permissions

Skip this step (click **Continue**) — permissions will be granted in Google Play Console.

---

## Step 4: Create JSON Key

1. Find your Service Account in the list
2. Click **⋮** → **Manage keys**
3. Click **Add Key** → **Create new key**
4. Select **JSON** → **Create**
5. The file downloads automatically 📥

**Important**: Keep this file secure, never commit to git!

---

## Step 5: Grant Access in Google Play Console

1. Open [Google Play Console](https://play.google.com/console)
2. Go to **Settings** → **API access**
3. Find your Service Account and click **Grant access**
4. Grant permissions:
   - ✅ View financial data
   - ✅ Manage orders and subscriptions

---

## Step 6: Upload to RevenueCat

1. Open [RevenueCat Dashboard](https://app.revenuecat.com)
2. Go to your project → **Settings** → **App Settings**
3. Under Google Play, click **Service Account credentials**
4. Upload the JSON file

---

## Security Notes

- Service Account is like a "robot user" for automation
- JSON contains a private key — **never commit to git!**
- RevenueCat uses it to verify purchases via Google Play API
- Rotate keys periodically for security
