# Sanin TV QR Authentication System

## Overview

Sanin TV uses a QR code-based authentication system for AniList login on Android TV. The system consists of:

1. **Android TV App** - Generates QR codes and polls for authentication status
2. **Cloudflare Worker** - Manages sessions and handles OAuth flow
3. **AniList OAuth** - External authentication provider

## Authentication Flow

### TV Login Flow

1. User selects "QR Code" in the app
2. App calls `POST /api/session/create` to create a new session
3. Worker returns session ID and QR URL
4. App generates QR code from the QR URL
5. User scans QR with phone
6. Phone opens `GET /qr/{sessionId}`
7. User taps "Continue with AniList"
8. Worker redirects to AniList OAuth
9. User authorizes on AniList
10. AniList redirects to `GET /callback`
11. Worker exchanges code for token
12. Worker marks session as authenticated
13. App polls `GET /api/session/{sessionId}` every 2 seconds
14. When status is "authenticated", app retrieves user profile
15. App stores token and user data

### Session Lifecycle

- Sessions expire after 5 minutes
- OAuth state is single-use (deleted after callback)
- Access tokens are stored in KV with session
- Sessions are automatically cleaned up via KV TTL

## Worker Endpoints

### POST /api/session/create

Creates a new authentication session.

**Response:**
```json
{
  "sessionId": "uuid",
  "expiresIn": 300,
  "qrUrl": "https://sanin-auth.shemaus58.workers.dev/qr/{sessionId}"
}
```

### GET /qr/{sessionId}

Displays the QR login page for phone users.

**Response:** HTML page with "Continue with AniList" button

### GET /api/session/{sessionId}

Returns the current session status.

**Response:**
```json
{
  "status": "pending" | "authenticated" | "expired"
}
```

### GET /callback

OAuth callback endpoint. Validates state, exchanges code for token.

**Query Parameters:**
- `code` - Authorization code from AniList
- `state` - OAuth state (UUID)

**Response:** HTML page (success or error)

## Android TV App

### Key Classes

- `LoginFragment.kt` - Main login UI
- `QrLoginApi.kt` - API service for Worker communication
- `Anilist.kt` - AniList API client

### Polling Implementation

- Polls every 2 seconds
- Single polling coroutine (no duplicates)
- Cancels on: authenticated, expired, dialog dismiss, network error
- Countdown timer shows remaining time

### Network Resilience

- 10-second timeout on requests
- 3 retries with exponential backoff
- Friendly error messages
- Graceful recovery

## Security

### OAuth State

- Generated with `crypto.randomUUID()`
- Stored in KV with session
- Deleted after successful callback
- Single-use only

### Access Tokens

- Never exposed through API responses
- Never logged
- Stored in KV with session
- Auto-deleted via TTL

### Input Validation

- UUID format validated on all endpoints
- Session existence checked
- Expiration checked
- Status checked (prevent duplicate callbacks)

## Troubleshooting

### QR Code Not Working

1. Check if session expired (5 minutes)
2. Verify Worker is online: `GET /health`
3. Check network connectivity

### Login Fails

1. Check if token is valid
2. Verify AniList API is accessible
3. Check Worker logs for errors

### App Not Staying Logged In

1. Verify token is stored in SharedPreferences
2. Check if token is expired
3. Verify user data is being fetched

## Environment Variables

### Cloudflare Worker

- `ANILIST_CLIENT_ID` - AniList OAuth client ID
- `ANILIST_REDIRECT_URI` - OAuth redirect URI
- `ANILIST_CLIENT_SECRET` - AniList OAuth client secret (encrypted)

## Deployment

### Cloudflare Worker

```bash
cd sanin-auth-worker/sanin-auth
wrangler deploy
```

### Android TV App

Build and install via Android Studio or command line.
