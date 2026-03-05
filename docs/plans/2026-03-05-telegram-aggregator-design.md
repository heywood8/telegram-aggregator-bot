# Telegram News Aggregator Bot — Design

**Date:** 2026-03-05

## Overview

A single-instance Telegram bot that aggregates messages from multiple public Telegram channels and forwards matching messages to subscribed users. Each user configures per-channel filtering rules via a button-based UI. Access is restricted to an allowlist of Telegram user IDs.

## Constraints & Goals

- Resource-cheap: targets ~60-80MB RAM, runs on a $4-6/month VPS or Railway/Fly.io hobby tier
- No phone number required: uses a bot token with Pyrogram MTProto to read public channels
- Public channels only: no private channel support
- Single Python async process: no separate services or databases

## Technology Stack

- **Language:** Python 3.12+
- **Telegram library:** Pyrogram (bot token mode — MTProto access without phone number)
- **Database:** SQLite via `aiosqlite`
- **Runtime:** Single async process, deployed as a Docker container or systemd service

## Architecture

Two concurrent async tasks within one process:

1. **Bot handler** — Pyrogram event handlers respond to user interactions (button taps, text input)
2. **Poller** — `asyncio` background task, wakes every 60 seconds, fetches and forwards new messages

```
┌─────────────────────────────────────┐
│           Python async app          │
│                                     │
│  ┌──────────────┐  ┌─────────────┐  │
│  │  Bot handler │  │   Poller    │  │
│  │  (Pyrogram)  │  │  (60s loop) │  │
│  └──────┬───────┘  └──────┬──────┘  │
│         │                 │         │
│         └────────┬────────┘         │
│                  │                  │
│           ┌──────▼──────┐           │
│           │   db.py     │           │
│           │  (SQLite)   │           │
│           └─────────────┘           │
└─────────────────────────────────────┘
```

## Project Structure

```
telegram-news-bot/
├── bot.py              # Entry point: starts Pyrogram client + poller task
├── poller.py           # Background polling loop
├── db.py               # All SQLite queries (aiosqlite)
├── filters.py          # Pure filtering logic (no I/O)
├── config.py           # Loads env vars (BOT_TOKEN, ALLOWED_USERS)
├── handlers/
│   ├── __init__.py
│   ├── start.py        # /start command, access check
│   ├── channels.py     # Add/remove channel flows
│   └── settings.py     # Per-channel mode and keyword management
├── tests/
│   ├── test_filters.py
│   └── test_db.py
├── Dockerfile
├── .env.example
└── requirements.txt
```

## Database Schema

```sql
-- User's subscription to a channel
CREATE TABLE subscriptions (
    user_id   INTEGER NOT NULL,
    channel   TEXT    NOT NULL,  -- e.g. "durov"
    mode      TEXT    NOT NULL DEFAULT 'all',  -- 'all' | 'include' | 'exclude'
    active    INTEGER NOT NULL DEFAULT 1,      -- 0 when user blocked the bot
    PRIMARY KEY (user_id, channel)
);

-- Keywords per user per channel
CREATE TABLE keywords (
    user_id  INTEGER NOT NULL,
    channel  TEXT    NOT NULL,
    keyword  TEXT    NOT NULL,
    PRIMARY KEY (user_id, channel, keyword)
);

-- Polling cursor: last message ID seen per channel
CREATE TABLE last_seen (
    channel     TEXT    PRIMARY KEY,
    message_id  INTEGER NOT NULL DEFAULT 0
);
```

## Filtering Logic (`filters.py`)

Three modes per subscription:

| Mode | Keywords | Behavior |
|------|----------|----------|
| `all` | any | Forward every message |
| `include` | ≥1 required | Forward only if any keyword appears in message text |
| `exclude` | ≥1 required | Forward unless any keyword appears in message text |

- Keyword matching is case-insensitive substring match
- Mode can only be set to `include` or `exclude` if ≥1 keyword exists
- If all keywords are removed from an `include`/`exclude` subscription, mode resets to `all`

## Data Flow

### Adding a channel

1. User taps "Add Channel" → bot asks for channel username
2. User sends `@channelname` → bot calls `client.get_chat(username)`
3. If channel not found or is private → inline error, no save
4. On success → insert into `subscriptions` (mode=`all`), insert into `last_seen` if not present
5. Show per-channel settings menu

### Polling loop

1. Every 60s: query all distinct active `channel` values from `subscriptions`
2. For each channel:
   a. Fetch messages with `id > last_seen.message_id` via `client.get_messages(channel, ...)`
   b. For each message, find all active subscribers
   c. Apply `filters.py` logic per user
   d. Forward matching messages via `client.forward_messages(user_id, channel, message_id)`
   e. Update `last_seen.message_id`
3. On channel fetch failure: log error, skip (don't update `last_seen`), retry next cycle
4. After N consecutive failures: notify subscribed users that channel may be unavailable

### Bot UI flows

- `/start` on a user who was inactive → reactivate their subscriptions
- Per-channel menu: view current mode, add/remove keywords, change mode, remove channel
- Mode selector only shows `include`/`exclude` options when ≥1 keyword exists

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Channel not found / private at add time | Inline error message, subscription not saved |
| Channel fetch failure during polling | Log, skip channel this cycle, retry next |
| N consecutive channel failures | Notify subscribed users |
| Forward failure (user blocked bot) | Mark user's subscriptions `active=0` |
| User sends `/start` after blocking | Mark user's subscriptions `active=1` |
| Unauthorized user sends any message | Silent rejection — no response |

## Configuration (env vars)

| Variable | Description |
|----------|-------------|
| `BOT_TOKEN` | Telegram bot token from BotFather |
| `ALLOWED_USERS` | Comma-separated Telegram user IDs allowed to use the bot |
| `POLL_INTERVAL_SECONDS` | Polling interval (default: 60) |
| `DB_PATH` | Path to SQLite file (default: `data/bot.db`) |

## Testing Strategy

- **`tests/test_filters.py`** — unit tests for `filters.py` pure functions: all three modes, empty keyword lists, case-insensitivity, partial word matches
- **`tests/test_db.py`** — unit tests using in-memory SQLite: add/remove subscriptions, keyword CRUD, last_seen updates, active/inactive toggling
- No integration tests against real Telegram API (thin wrapper, not worth mocking MTProto)
