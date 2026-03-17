# Per-Channel Photo Filter — Design Spec

**Date:** 2026-03-17
**Issue:** heywood8/taggro#19

## Summary

Add a per-channel boolean setting that controls whether photo-only messages (photos with no caption) are shown in the feed. Photo messages that have a caption always appear regardless of the setting.

## Behavior

| Message type | Setting OFF (default) | Setting ON |
|---|---|---|
| Photo, no caption | Dropped | Shown as `[Photo]` |
| Photo + caption | Shown (caption text) | Shown (caption text) |
| Text only | Shown | Shown |
| Other media, no caption | Dropped | Dropped |

Default is OFF, preserving current behavior for existing subscriptions.

## Section 1: Data Model & Database

### `SubscriptionEntity`

Add one column:

```kotlin
@ColumnInfo(name = "include_photos", defaultValue = "0")
val includePhotos: Boolean = false
```

### `Subscription` (domain model)

```kotlin
val includePhotos: Boolean = false
```

### DB migration v3 → v4

```sql
ALTER TABLE subscriptions ADD COLUMN include_photos INTEGER NOT NULL DEFAULT 0
```

`AppDatabase` version bumped to 4. New migration object named `MIGRATION_3_4` (following existing convention of `MIGRATION_1_2`, `MIGRATION_2_3`), added to the `addMigrations(...)` call in `DatabaseModule`.

### `SubscriptionDao`

```kotlin
@Query("UPDATE subscriptions SET include_photos = :value WHERE userId = :userId AND channel = :channel")
suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean)
```

### `LocalRepository` (interface) and `LocalRepositoryImpl`

Add method to both:
```kotlin
suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean)
```

Map `includePhotos` in both entity→domain and domain→entity conversions in `LocalRepositoryImpl`.

## Section 2: Message Storage

### `extractMediaType` helper in `TelegramRepositoryImpl`

`extractText` currently returns only a `String`. Add a companion helper to derive the media type:

```kotlin
private fun extractMediaType(content: TdApi.MessageContent): String? = when (content) {
    is TdApi.MessagePhoto -> "photo"
    is TdApi.MessageVideo -> "video"
    is TdApi.MessageDocument -> "document"
    is TdApi.MessageAnimation -> "animation"
    else -> null
}
```

Call `extractMediaType(message.content)` alongside `extractText(message.content, channelTitle)` in both `fetchMessagesSince` and `observeNewMessages`.

### Dropping logic in `TelegramRepositoryImpl`

The two methods use different blank-check patterns.

**`fetchMessagesSince`** (uses `.ifBlank { return@mapNotNull null }` inline):

```kotlin
// Before:
val text = extractText(msg.content, chat.title).ifBlank { return@mapNotNull null }

// After:
val rawText = extractText(msg.content, chat.title)
val mediaType = extractMediaType(msg.content)
if (rawText.isBlank() && mediaType != "photo") return@mapNotNull null
Message(
    id = msg.id,
    channel = channel,
    channelTitle = chat.title,
    text = rawText,
    timestamp = msg.date.toLong(),
    mediaType = mediaType,
)
```

**`observeNewMessages`** (uses `if (text.isNotBlank())`):

```kotlin
// Before:
val text = extractText(msg.content, title)
if (text.isNotBlank()) { send(Message(...)) }

// After:
val text = extractText(msg.content, title)
val mediaType = extractMediaType(msg.content)
if (text.isNotBlank() || mediaType == "photo") {
    send(Message(..., mediaType = mediaType))
}
```

Photo-only messages are stored with `text = ""` and `mediaType = "photo"`. All other blank-text messages (stickers, videos with no caption, etc.) continue to be dropped.

No schema changes — `mediaType` column already exists in `MessageEntity`.

### Keyword filter bypass — all insertion paths

`FilterUseCase.shouldForward` with mode `"include"` returns `false` when text is blank (no keyword can match), which would silently drop photo-only messages before they reach the DB. Photo-only messages must bypass keyword filtering entirely and always be stored; display-time filtering in `filteredMessages` decides whether to show them.

**`TelegramRepositoryImpl` — `observeNewMessages`** (currently has no explicit `shouldForward` call; filtering is done in FeedViewModel — see below).

**`FeedViewModel` — three insertion paths all need the same bypass:**

*Real-time listener (init block):*
```kotlin
.filter { msg ->
    (msg.mediaType == "photo" && msg.text.isBlank()) ||
        filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
}
```

*On-load fetch (init block) and `refresh()`:*
```kotlin
val filtered = messages.filter { msg ->
    (msg.mediaType == "photo" && msg.text.isBlank()) ||
        filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
}
```

All three `MessageEntity(...)` constructor calls in FeedViewModel must also pass `mediaType`:
```kotlin
MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp, msg.mediaType)
```

**`SyncWorker`** does not apply `FilterUseCase` at all (it stores all messages from `fetchMessagesSince`). It only needs `mediaType` added to its `MessageEntity` constructor:
```kotlin
MessageEntity(
    id = msg.id,
    channel = msg.channel,
    channelTitle = msg.channelTitle,
    text = msg.text,
    timestamp = msg.timestamp,
    mediaType = msg.mediaType,
)
```

## Section 3: FeedViewModel Filtering

`filteredMessages` already combines messages, read status, and channel selection. Add `subscriptions` as a fourth flow input. Full updated `combine` signature:

```kotlin
val filteredMessages: StateFlow<List<Message>> = combine(
    messageDao.observeAll().map { entities ->
        entities.map { e ->
            Message(e.id, e.channel, e.channelTitle.ifBlank { e.channel }, e.text, e.timestamp, e.mediaType)
        }
    },
    readMessageDao.observeReadIds().map { it.toHashSet() },
    _selectedChannel,
    subscriptions.map { subs -> subs.associateBy { it.channel } },
) { msgs, readSet, channel, subscriptionsByChannel ->
    val withRead = msgs.map { it.copy(isRead = it.id in readSet) }
    val channelFiltered = if (channel == null) withRead else withRead.filter { it.channel == channel }
    channelFiltered.filter { message ->
        val includePhotos = subscriptionsByChannel[message.channel]?.includePhotos ?: false
        includePhotos || message.mediaType != "photo" || message.text.isNotBlank()
    }
}.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

A message is dropped only when **all three** conditions hold: the subscription excludes photos, the message `mediaType` is `"photo"`, and the text is blank. Fallback when a channel is not found in the map is `false`, consistent with the default-OFF behavior.

`subscriptions` is already a `StateFlow` declared before `filteredMessages` in the VM — it can be used directly in `combine` as a Flow.

### `Message` domain model mapping

The existing mapping in `FeedViewModel` does not pass `mediaType`. This must be updated:

```kotlin
Message(e.id, e.channel, e.channelTitle.ifBlank { e.channel }, e.text, e.timestamp, e.mediaType)
```

`Message` domain model must also gain `val mediaType: String? = null`.

### Pre-existing note: `observeSubscriptions` does not populate `keywords`

`LocalRepositoryImpl.observeSubscriptions` returns subscriptions with `keywords = emptyList()`. This is a pre-existing bug unrelated to this feature. The photo filter only reads `includePhotos`, which IS correctly persisted and mapped, so this bug does not affect correctness here.

## Section 4: UI

### `ChannelSettingsSheet`

Add a toggle row below the existing filter-mode chips:

```
[ Switch ] Include photo-only messages
```

- Implemented as a `Row` with `Text` and `Switch`
- Initial state: `subscription.includePhotos`
- New composable parameter: `onSetIncludePhotos: (Boolean) -> Unit`
- The sheet is invoked from `ChannelListScreen`, which must pass `{ viewModel.setIncludePhotos(channel, it) }` for this parameter

### `ChannelViewModel`

```kotlin
fun setIncludePhotos(channel: String, value: Boolean) {
    viewModelScope.launch {
        localRepo.setIncludePhotos(USER_ID, channel, value)
    }
}
```

`_selectedSub` is a `MutableStateFlow` set manually — it is NOT derived from the subscriptions flow. Follow the same pattern used in `setMode`:

```kotlin
fun setIncludePhotos(channel: String, value: Boolean) {
    viewModelScope.launch {
        localRepo.setIncludePhotos(USER_ID, channel, value)
        _selectedSub.value = subscriptions.value.find { it.channel == channel }
    }
}
```

### Feed message list

Photo-only messages (`mediaType == "photo"` and `text.isBlank()`) render their body as:

```
[Photo]
```

displayed in italics where the message text would normally appear.

### `ArticleSheet` (full-text detail sheet)

`ArticleSheet` renders `message.text` directly. For photo-only messages (`text.isBlank()`), display the same `[Photo]` placeholder in italics so the sheet is not blank.

## Files Changed

| File | Change |
|---|---|
| `data/local/entity/SubscriptionEntity.kt` | Add `includePhotos` column |
| `domain/model/Subscription.kt` | Add `includePhotos` field |
| `domain/model/Message.kt` | Add `mediaType` field |
| `data/local/dao/SubscriptionDao.kt` | Add `setIncludePhotos` query |
| `domain/repository/LocalRepository.kt` | Add `setIncludePhotos` to interface |
| `data/local/LocalRepositoryImpl.kt` | Map field, implement method |
| `data/local/AppDatabase.kt` | Bump version to 4, add `MIGRATION_3_4` |
| `data/telegram/TelegramRepositoryImpl.kt` | Add `extractMediaType` helper; keep photo-only messages |
| `worker/SyncWorker.kt` | Pass `mediaType` in `MessageEntity` constructor |
| `ui/feed/FeedViewModel.kt` | Bypass `shouldForward` for photo-only in all 3 insertion paths; pass `mediaType` in `MessageEntity` constructors; add subscriptions as 4th `combine` input; apply photo filter |
| `ui/channels/ChannelSettingsSheet.kt` | Add `onSetIncludePhotos` param and toggle row |
| `ui/channels/ChannelListScreen.kt` | Pass `onSetIncludePhotos` lambda at call site |
| `ui/channels/ChannelViewModel.kt` | Add `setIncludePhotos` |
| `ui/feed/FeedScreen.kt` | Render `[Photo]` placeholder in feed cards and `ArticleSheet` |

## Out of Scope

- `mediaUrl` — already exists in `MessageEntity` but is not mapped to `Message` and is not rendered. No change needed.
- Displaying actual photo images in the feed.
