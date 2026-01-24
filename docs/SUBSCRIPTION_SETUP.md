# Subscription Setup Guide

This guide explains how to set up the subscription product for Gisti app.

## Product Configuration

| Parameter | Value |
|-----------|-------|
| Product ID | `premium_monthly` |
| Type | Auto-renewable subscription |
| Price | $1.99 USD |
| Duration | 1 month |
| Free Trial | 3 days |

---

## 1. Google Play Console Setup

### Step 1: Create Subscription

1. Go to [Google Play Console](https://play.google.com/console)
2. Select your app: **Gisti**
3. Navigate to: **Monetize** → **Products** → **Subscriptions**
4. Click **Create subscription**
5. Fill in:
   - **Product ID**: `premium_monthly`
   - **Name**: `Premium Monthly`

### Step 2: Add Base Plan

1. In the subscription, click **Add base plan**
2. Configure:
   - **Base plan ID**: `monthly`
   - **Renewal type**: Auto-renewing
   - **Billing period**: 1 month
   - **Price**: $1.99 USD
3. Add prices for other countries (click "Set prices")
4. **Save** the base plan

### Step 3: Add Free Trial Offer

1. In the base plan, click **Add offer**
2. Select **Free trial**
3. Configure:
   - **Offer ID**: `free-trial-3d`
   - **Eligibility**: New customer acquisition only
   - **Free trial period**: 3 days
4. **Save** the offer
5. **Activate** the base plan

### Step 4: Activate Subscription

1. Review all settings
2. Click **Activate** on the subscription

---

## 2. App Store Connect Setup (iOS)

### Step 1: Create Subscription Group

1. Go to [App Store Connect](https://appstoreconnect.apple.com)
2. Select your app: **Gisti**
3. Navigate to: **Features** → **Subscriptions**
4. Click **Create** under "Subscription Groups"
5. Enter group name: `Premium`
6. Click **Create**

### Step 2: Create Subscription

1. In the Premium group, click **Create** under "Subscriptions"
2. Configure:
   - **Reference Name**: `Premium Monthly`
   - **Product ID**: `premium_monthly`
3. Click **Create**

### Step 3: Configure Subscription Details

1. **Subscription Duration**: 1 Month
2. **Subscription Prices**:
   - Click **Add Subscription Price**
   - Base Country: United States
   - Price: $1.99 (Tier 2)
   - Save and apply to all territories

### Step 4: Add Introductory Offer (Free Trial)

1. In the subscription, scroll to **Introductory Offers**
2. Click **Set Up Introductory Offer**
3. Configure:
   - **Type**: Free
   - **Duration**: 3 Days
   - **Territories**: All territories
4. Click **Confirm**

### Step 5: Add Localization

1. Add App Store Localization (at least English):
   - **Display Name**: `Premium Monthly`
   - **Description**: `Unlimited checklists, daily AI credits refill, priority support`

### Step 6: Submit for Review

1. Ensure subscription status shows ready
2. Submit with next app update

---

## 3. RevenueCat Dashboard Setup

### Step 1: Add Products

1. Go to [RevenueCat Dashboard](https://app.revenuecat.com)
2. Select your project: **Gisti**
3. Navigate to: **Products**
4. Click **+ New**
5. Configure:
   - **Identifier**: `premium_monthly`
   - **App Store Product ID**: `premium_monthly`
   - **Play Store Product ID**: `premium_monthly:monthly`
6. Click **Add**

### Step 2: Configure Entitlement

1. Navigate to: **Entitlements**
2. You should already have `premium` entitlement
3. Click on `premium`
4. Under "Products", click **Attach**
5. Select `premium_monthly`
6. Click **Add**

### Step 3: Configure Offering

1. Navigate to: **Offerings**
2. Click on `default` offering (or create if not exists)
3. Under "Packages", click **+ New**
4. Configure:
   - **Identifier**: `$rc_monthly` (or `monthly`)
   - **Product**: Select `premium_monthly`
5. Click **Add**

### Step 4: Set as Current Offering

1. Ensure `default` offering is set as "Current"
2. This makes it available to the app

---

## 4. Testing

### Sandbox Testing (Google Play)

1. Add test account in Google Play Console:
   - **Setup** → **License testing**
   - Add your test email
2. Install app from internal testing track
3. Purchase with test account (won't charge)

### Sandbox Testing (iOS)

1. Create Sandbox tester in App Store Connect:
   - **Users and Access** → **Sandbox** → **Testers**
   - Add new tester with fake email
2. Sign out of App Store on device
3. Make purchase in app (will prompt for Sandbox credentials)

### RevenueCat Sandbox

1. In RevenueCat Dashboard → **Customers**
2. Search for your test user
3. Verify entitlements are granted after purchase

---

## 5. Verification Checklist

### Google Play
- [x] Subscription created with product ID `premium_monthly`
- [x] Base plan has correct price ($1.99)
- [x] Free trial offer configured (3 days)
- [x] Pending transactions enabled in RevenueCatInitializer

### App Store (TODO)
- [ ] Subscription created with product ID `premium_monthly`
- [ ] Subscription price set ($1.99 / Tier 2)
- [ ] Introductory offer configured (3 days free)

### RevenueCat
- [x] Product added with correct store IDs
- [x] `premium` entitlement attached to product
- [x] `default` offering contains the package
- [x] PurchasesDelegate configured for real-time updates

### Testing
- [x] Android sandbox testing successful
- [ ] iOS sandbox testing (pending iOS setup)

---

## Product IDs Summary

| Platform | Product ID | Base Plan / Offer |
|----------|------------|-------------------|
| Google Play | `premium_monthly` | `monthly` / `free-trial-3d` |
| App Store | `premium_monthly` | - / Introductory Offer |
| RevenueCat | `premium_monthly` | - |

---

## Troubleshooting

### "Product not found" error
- Ensure product is activated in store console
- Wait 15-30 minutes for store sync
- Check RevenueCat product ID matches exactly

### Trial not showing
- Ensure user is eligible (new customer)
- Check offer is properly configured
- Verify RevenueCat is fetching introductory price

### Purchase fails
- Check app signing (release build for production)
- Verify billing permissions in Android manifest
- Ensure test account is properly set up
