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

`AppDatabase` version bumped to 4. Migration added to the migration list.

### `SubscriptionDao`

```kotlin
@Query("UPDATE subscriptions SET include_photos = :value WHERE userId = :userId AND channel = :channel")
suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean)
```

### `LocalRepositoryImpl`

Map `includePhotos` in both entity→domain and domain→entity conversions.

Add method:
```kotlin
suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean)
```

## Section 2: Message Storage

### Current behavior

Both `fetchMessagesSince` and `observeNewMessages` drop any message where extracted text is blank:

```kotlin
if (text.isNullOrBlank()) continue
```

This silently drops photo-only messages.

### Change

```kotlin
if (text.isNullOrBlank() && mediaType != "photo") continue
```

Photo-only messages are stored with `text = ""` and `mediaType = "photo"`. All other blank-text messages (stickers, videos with no caption, etc.) continue to be dropped.

No schema changes — `mediaType` column already exists in `MessageEntity`.

## Section 3: FeedViewModel Filtering

`filteredMessages` already combines messages, read status, and channel selection. Add subscription map as a fourth input.

```kotlin
// after existing channel filter
.filter { message ->
    val includePhotos = subscriptionsByChannel[message.channel]?.includePhotos ?: true
    includePhotos || message.mediaType != "photo" || message.text.isNotBlank()
}
```

A message is dropped only when **all three** conditions hold: the subscription excludes photos, the message mediaType is `"photo"`, and the text is blank.

`subscriptionsByChannel: Map<String, Subscription>` is derived from the existing `observeSubscriptions()` flow, keyed by channel name.

## Section 4: UI

### `ChannelSettingsSheet`

Add a toggle row below the existing filter-mode chips:

```
[ Switch ] Include photo-only messages
```

- Implemented as a `Row` with `Text` and `Switch`
- Initial state: `subscription.includePhotos`
- On toggle: `viewModel.setIncludePhotos(channel, checked)`

### `ChannelViewModel`

```kotlin
fun setIncludePhotos(channel: String, value: Boolean) {
    viewModelScope.launch {
        localRepo.setIncludePhotos(USER_ID, channel, value)
    }
}
```

### Feed message list

Photo-only messages (`mediaType == "photo"` and `text.isBlank()`) render their body as:

```
[Photo]
```

displayed in italics where the message text would normally appear.

## Files Changed

| File | Change |
|---|---|
| `data/local/entity/SubscriptionEntity.kt` | Add `includePhotos` column |
| `domain/model/Subscription.kt` | Add `includePhotos` field |
| `data/local/dao/SubscriptionDao.kt` | Add `setIncludePhotos` query |
| `data/local/LocalRepositoryImpl.kt` | Map field, add method |
| `data/local/AppDatabase.kt` | Bump version to 4, add migration |
| `data/telegram/TelegramRepositoryImpl.kt` | Keep photo-only messages |
| `ui/feed/FeedViewModel.kt` | Filter photo messages by setting |
| `ui/channels/ChannelSettingsSheet.kt` | Add toggle row |
| `ui/channels/ChannelViewModel.kt` | Add `setIncludePhotos` |
| `ui/feed/FeedScreen.kt` | Render `[Photo]` placeholder |
