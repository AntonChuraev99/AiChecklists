# Firebase Cloud Functions for AI Checklists

This directory contains Firebase Cloud Functions that handle AI operations for the app.

## Functions

### 1. `analyze_and_fill_checklist`
Auto-fill an existing checklist based on user-provided data.

**Request:**
```json
{
    "user_id": "string",
    "is_premium": false,
    "checklist": {
        "id": 1,
        "name": "My Checklist",
        "items": [{"text": "Item 1", "checked": false}]
    },
    "input_type": "text",
    "input_data": "user provided content..."
}
```

**Response:**
```json
{
    "success": true,
    "filled_items": [
        {"index": 0, "text": "Item 1", "checked": true, "note": "Found in data"}
    ],
    "summary": "Analysis summary",
    "confidence": 0.85,
    "usage": {"count": 5, "limit": 10}
}
```

### 2. `generate_checklist`
Generate a new checklist from user prompt and optional data.

**Request:**
```json
{
    "user_id": "string",
    "is_premium": false,
    "prompt": "Create a checklist for apartment viewing",
    "input_type": "text",
    "input_data": "optional context..."
}
```

**Response:**
```json
{
    "success": true,
    "checklist_name": "Apartment Viewing Checklist",
    "items": [
        {"text": "Check water pressure", "checked": false},
        {"text": "Inspect windows", "checked": false}
    ],
    "summary": "Checklist for viewing apartments",
    "confidence": 0.9,
    "usage": {"count": 6, "limit": 10}
}
```

### 3. `get_usage_stats`
Get user's AI usage statistics.

**Request:**
```json
{
    "user_id": "string",
    "is_premium": false
}
```

**Response:**
```json
{
    "success": true,
    "usage": {
        "today": 5,
        "limit": 10,
        "remaining": 5,
        "requests": [...]
    }
}
```

## Input Types

- `text` - Raw text content
- `url` - URL to analyze
- `image_base64` - Base64-encoded image
- `none` - No additional data (for prompt-only generation)

## Deployment

### Prerequisites

1. Install Google Cloud SDK: https://cloud.google.com/sdk/docs/install
2. Authenticate: `gcloud auth login`
3. Set project: `gcloud config set project aichecklists-40230`
4. Set Gemini API key: `export GEMINI_API_KEY=your_key`

### Deploy

```bash
chmod +x deploy.sh
./deploy.sh
```

Or manually:

```bash
# Create secret for API key
echo -n "$GEMINI_API_KEY" | gcloud secrets create gemini-api-key --data-file=-

# Deploy functions
gcloud functions deploy analyze_and_fill_checklist \
    --gen2 \
    --runtime=python312 \
    --region=us-central1 \
    --source=. \
    --entry-point=analyze_and_fill_checklist \
    --trigger-http \
    --allow-unauthenticated \
    --set-secrets="GEMINI_API_KEY=gemini-api-key:latest" \
    --memory=512MB
```

## Usage Tracking

Usage is tracked in Firestore:
- Collection: `usage`
- Document ID: `{user_id}_{date}` (e.g., `user123_2024-01-15`)
- Fields: `count`, `requests[]`, `last_request`

## Limits

| User Type | Daily Limit |
|-----------|-------------|
| Free      | 10 requests |
| Premium   | 100 requests |

Limits are configurable via Remote Config:
- `ai_daily_limit_free`
- `ai_daily_limit_premium`

## Local Testing

```bash
pip install -r requirements.txt
functions-framework --target=analyze_and_fill_checklist --port=8080
```

Then test with curl:
```bash
curl -X POST http://localhost:8080 \
    -H "Content-Type: application/json" \
    -d '{"user_id": "test", "prompt": "Create a shopping checklist"}'
```
