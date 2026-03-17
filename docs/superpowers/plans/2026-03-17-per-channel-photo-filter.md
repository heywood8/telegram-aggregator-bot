# Per-Channel Photo Filter Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a per-channel boolean setting that hides photo-only messages (photos with no caption) when disabled; when enabled, shows them as `[Photo]` in the feed.

**Architecture:** Add `include_photos` column to the `subscriptions` table (default 0 = OFF). Store photo-only messages in the DB regardless of the setting. Filter them out at display time in `FeedViewModel.filteredMessages` using a 4-way `combine`. All three message-insertion paths in `FeedViewModel` and `SyncWorker` are updated to pass `mediaType` through.

**Tech Stack:** Kotlin, Jetpack Compose, Room (v4 migration), Hilt, WorkManager, JUnit 4

---

## File Map

**Created:**
- `android/app/src/test/kotlin/com/heywood8/telegramnews/ui/feed/PhotoFilterPredicateTest.kt`

**Modified:**
- `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/SubscriptionEntity.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Subscription.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/AppDatabase.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/dao/SubscriptionDao.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/repository/LocalRepository.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/LocalRepositoryImpl.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/TelegramRepositoryImpl.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/worker/SyncWorker.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelViewModel.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelSettingsSheet.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelListScreen.kt`
- `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedScreen.kt`

**All Gradle commands must be run from the `android/` directory.**

---

## Chunk 1: Data Layer

### Task 1: Add `includePhotos` to data models

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/SubscriptionEntity.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Subscription.kt`

- [ ] **Step 1: Add `includePhotos` field to `SubscriptionEntity`**

  Open `SubscriptionEntity.kt`. Current content:
  ```kotlin
  @Entity(tableName = "subscriptions", primaryKeys = ["userId", "channel"])
  data class SubscriptionEntity(
      val userId: Long,
      val channel: String,
      val mode: String = "all",
      val active: Boolean = true
  )
  ```

  Replace with:
  ```kotlin
  import androidx.room.ColumnInfo
  import androidx.room.Entity

  @Entity(tableName = "subscriptions", primaryKeys = ["userId", "channel"])
  data class SubscriptionEntity(
      val userId: Long,
      val channel: String,
      val mode: String = "all",
      val active: Boolean = true,
      @ColumnInfo(name = "include_photos", defaultValue = "0")
      val includePhotos: Boolean = false,
  )
  ```

- [ ] **Step 2: Add `includePhotos` field to `Subscription` domain model**

  Open `Subscription.kt`. Current content:
  ```kotlin
  data class Subscription(
      val channel: String,
      val mode: String,
      val keywords: List<String>,
      val active: Boolean
  )
  ```

  Replace with:
  ```kotlin
  data class Subscription(
      val channel: String,
      val mode: String,
      val keywords: List<String>,
      val active: Boolean,
      val includePhotos: Boolean = false,
  )
  ```

---

### Task 2: DB migration v3 → v4

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/AppDatabase.kt`

- [ ] **Step 1: Add `MIGRATION_3_4` and bump DB version**

  Open `AppDatabase.kt`. Add the migration below the existing `MIGRATION_2_3` block, and bump the version from 3 to 4:

  ```kotlin
  val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE subscriptions ADD COLUMN include_photos INTEGER NOT NULL DEFAULT 0")
      }
  }

  @Database(
      entities = [
          SubscriptionEntity::class,
          KeywordEntity::class,
          MessageEntity::class,
          LastSeenEntity::class,
          ReadMessageEntity::class,
      ],
      version = 4,   // bumped from 3
      exportSchema = false
  )
  abstract class AppDatabase : RoomDatabase() { ... }
  ```

- [ ] **Step 2: Wire the migration into `DatabaseModule`**

  Find the Hilt module that builds the `AppDatabase` (look in `android/app/src/main/kotlin/com/heywood8/telegramnews/di/`). It will contain a `Room.databaseBuilder(...)` call with `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

  First, add the import at the top of `DatabaseModule.kt` (alongside the existing migration imports):
  ```kotlin
  import com.heywood8.telegramnews.data.local.MIGRATION_3_4
  ```

  Then update the `addMigrations` call:
  ```kotlin
  .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
  ```

- [ ] **Step 3: Build to verify no compile errors**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```
  Expected: BUILD SUCCESSFUL

---

### Task 3: DAO, repository interface, and repository implementation

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/dao/SubscriptionDao.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/domain/repository/LocalRepository.kt`
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/LocalRepositoryImpl.kt`

- [ ] **Step 1: Add `setIncludePhotos` to `SubscriptionDao`**

  Append to the DAO interface body (before the closing `}`):
  ```kotlin
  @Query("UPDATE subscriptions SET include_photos = :value WHERE userId = :userId AND channel = :channel")
  suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean)
  ```

- [ ] **Step 2: Add `setIncludePhotos` to `LocalRepository` interface**

  Append to the interface body:
  ```kotlin
  suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean)
  ```

- [ ] **Step 3: Implement in `LocalRepositoryImpl` and map the field**

  In `LocalRepositoryImpl`, update the `observeSubscriptions` mapper to include `includePhotos`:
  ```kotlin
  override fun observeSubscriptions(userId: Long): Flow<List<Subscription>> =
      subscriptionDao.observeByUser(userId).map { entities ->
          entities.map { entity ->
              Subscription(
                  channel = entity.channel,
                  mode = entity.mode,
                  keywords = emptyList(),
                  active = entity.active,
                  includePhotos = entity.includePhotos,
              )
          }
      }
  ```

  Add the new method at the end of the class (before the closing `}`):
  ```kotlin
  override suspend fun setIncludePhotos(userId: Long, channel: String, value: Boolean) {
      subscriptionDao.setIncludePhotos(userId, channel, value)
  }
  ```

- [ ] **Step 4: Build to verify**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```
  Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/entity/SubscriptionEntity.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/model/Subscription.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/AppDatabase.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/di/DatabaseModule.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/dao/SubscriptionDao.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/domain/repository/LocalRepository.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/local/LocalRepositoryImpl.kt
  git commit -m "feat: add includePhotos field to subscriptions — data layer"
  ```

---

## Chunk 2: Message Storage

### Task 4: Write photo-filter predicate tests

**Files:**
- Create: `android/app/src/test/kotlin/com/heywood8/telegramnews/ui/feed/PhotoFilterPredicateTest.kt`

- [ ] **Step 1: Write the failing test file**

  Create the file with this content:
  ```kotlin
  package com.heywood8.telegramnews.ui.feed

  import org.junit.Assert.*
  import org.junit.Test

  class PhotoFilterPredicateTest {

      // Replicates the filter predicate used in FeedViewModel.filteredMessages:
      //   includePhotos || mediaType != "photo" || text.isNotBlank()
      private fun shouldShow(
          includePhotos: Boolean,
          mediaType: String?,
          text: String,
      ): Boolean = includePhotos || mediaType != "photo" || text.isNotBlank()

      @Test
      fun `photo-only message hidden when includePhotos is false`() {
          assertFalse(shouldShow(includePhotos = false, mediaType = "photo", text = ""))
      }

      @Test
      fun `photo-only message shown when includePhotos is true`() {
          assertTrue(shouldShow(includePhotos = true, mediaType = "photo", text = ""))
      }

      @Test
      fun `photo with caption always shown regardless of setting`() {
          assertTrue(shouldShow(includePhotos = false, mediaType = "photo", text = "Some caption"))
      }

      @Test
      fun `text message always shown`() {
          assertTrue(shouldShow(includePhotos = false, mediaType = null, text = "hello world"))
      }

      @Test
      fun `video with no caption always hidden (not affected by photo setting)`() {
          assertFalse(shouldShow(includePhotos = true, mediaType = "video", text = ""))
      }

      @Test
      fun `missing subscription defaults to exclude photos (false)`() {
          val includePhotos: Boolean = null ?: false
          assertFalse(shouldShow(includePhotos = includePhotos, mediaType = "photo", text = ""))
      }
  }
  ```

- [ ] **Step 2: Run test to confirm it fails (class/method not found is acceptable at this stage)**

  ```
  cd android
  ./gradlew :app:test --tests "com.heywood8.telegramnews.ui.feed.PhotoFilterPredicateTest"
  ```

  Since the test file defines its own `shouldShow` helper, it should actually PASS already (the predicate is self-contained). If it passes, that confirms the logic is correct — proceed.

---

### Task 5: Update `TelegramRepositoryImpl` to store photo-only messages

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/TelegramRepositoryImpl.kt`

Background: `extractText` returns just a `String`. We need a companion helper to derive `mediaType`. There are two blank-check patterns to update — one in `fetchMessagesSince` (uses `.ifBlank { return@mapNotNull null }`) and one in `observeNewMessages` (uses `if (text.isNotBlank())`).

- [ ] **Step 1: Add `extractMediaType` private helper**

  In `TelegramRepositoryImpl`, add this private function near the existing `extractText`:
  ```kotlin
  private fun extractMediaType(content: TdApi.MessageContent): String? = when (content) {
      is TdApi.MessagePhoto -> "photo"
      is TdApi.MessageVideo -> "video"
      is TdApi.MessageDocument -> "document"
      is TdApi.MessageAnimation -> "animation"
      else -> null
  }
  ```

- [ ] **Step 2: Fix `fetchMessagesSince` to keep photo-only messages**

  Find this block in `fetchMessagesSince`:
  ```kotlin
  result.messages?.mapNotNull { msg ->
      val text = extractText(msg.content, chat.title).ifBlank { return@mapNotNull null }
      Message(
          id = msg.id,
          channel = channel,
          channelTitle = chat.title,
          text = text,
          timestamp = msg.date.toLong()
      )
  } ?: emptyList()
  ```

  Replace with:
  ```kotlin
  result.messages?.mapNotNull { msg ->
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
  } ?: emptyList()
  ```

- [ ] **Step 3: Fix `observeNewMessages` to keep photo-only messages**

  Find this block in `observeNewMessages`:
  ```kotlin
  val text = extractText(msg.content, title)
  if (text.isNotBlank()) {
      send(
          Message(
              id = msg.id,
              channel = username,
              channelTitle = title,
              text = text,
              timestamp = msg.date.toLong()
          )
      )
  }
  ```

  Replace with:
  ```kotlin
  val text = extractText(msg.content, title)
  val mediaType = extractMediaType(msg.content)
  if (text.isNotBlank() || mediaType == "photo") {
      send(
          Message(
              id = msg.id,
              channel = username,
              channelTitle = title,
              text = text,
              timestamp = msg.date.toLong(),
              mediaType = mediaType,
          )
      )
  }
  ```

- [ ] **Step 4: Build**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```
  Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/data/telegram/TelegramRepositoryImpl.kt
  git add android/app/src/test/kotlin/com/heywood8/telegramnews/ui/feed/PhotoFilterPredicateTest.kt
  git commit -m "feat: store photo-only messages; add extractMediaType helper"
  ```

---

## Chunk 3: FeedViewModel and SyncWorker

### Task 6: Update `FeedViewModel` — bypass filter + update combine

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt`

Background: `FeedViewModel` has three message-insertion paths that all apply `filterUseCase.shouldForward`. Each needs a bypass for photo-only messages (text blank + mediaType "photo") so they always reach the DB. Additionally, `filteredMessages` needs `subscriptions` as a 4th input and a photo-filter step. The `MessageEntity(...)` constructors don't currently pass `mediaType`.

- [ ] **Step 1: Update `filteredMessages` combine to add subscription map and photo filter**

  Find the current `filteredMessages` declaration:
  ```kotlin
  val filteredMessages: StateFlow<List<Message>> = combine(
      messageDao.observeAll().map { entities ->
          entities.map { e ->
              Message(e.id, e.channel, e.channelTitle.ifBlank { e.channel }, e.text, e.timestamp)
          }
      },
      readMessageDao.observeReadIds().map { it.toHashSet() },
      _selectedChannel,
  ) { msgs, readSet, channel ->
      val withRead = msgs.map { it.copy(isRead = it.id in readSet) }
      if (channel == null) withRead else withRead.filter { it.channel == channel }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  ```

  Replace with:
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

  Note: `subscriptions` is declared just before `filteredMessages` in the same class — it is available as a Flow input.

- [ ] **Step 2: Update the real-time insertion path (init block) to bypass `shouldForward` for photos and pass `mediaType`**

  Find the real-time listener in the `init` block:
  ```kotlin
  telegramRepo.observeNewMessages(channels)
      .filter { msg ->
          val sub = subs.find { it.channel == msg.channel }
          sub != null && filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
      }
  ```

  Replace the `.filter { ... }` block with:
  ```kotlin
  telegramRepo.observeNewMessages(channels)
      .filter { msg ->
          val sub = subs.find { it.channel == msg.channel } ?: return@filter false
          (msg.mediaType == "photo" && msg.text.isBlank()) ||
              filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
      }
  ```

  Then find the `MessageEntity(...)` constructor call in the `.collect { msg -> }` block:
  ```kotlin
  MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
  ```
  Replace with:
  ```kotlin
  MessageEntity(id = msg.id, channel = msg.channel, channelTitle = msg.channelTitle, text = msg.text, timestamp = msg.timestamp, mediaType = msg.mediaType)
  ```

- [ ] **Step 3: Update the on-load fetch path (init block) to bypass and pass `mediaType`**

  Find the on-load fetch in `init` (second `viewModelScope.launch`):
  ```kotlin
  val filtered = messages.filter {
      filterUseCase.shouldForward(it.text, sub.mode, sub.keywords)
  }
  if (filtered.isNotEmpty()) {
      messageDao.insertAll(filtered.map { msg ->
          MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
      })
  ```

  Replace with:
  ```kotlin
  val filtered = messages.filter { msg ->
      (msg.mediaType == "photo" && msg.text.isBlank()) ||
          filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
  }
  if (filtered.isNotEmpty()) {
      messageDao.insertAll(filtered.map { msg ->
          MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp, msg.mediaType)
      })
  ```

- [ ] **Step 4: Update the `refresh()` path to bypass and pass `mediaType`**

  In `refresh()`, find:
  ```kotlin
  val filtered = messages.filter {
      filterUseCase.shouldForward(it.text, sub.mode, sub.keywords)
  }
  if (filtered.isNotEmpty()) {
      messageDao.insertAll(filtered.map { msg ->
          MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp)
      })
  ```

  Replace with:
  ```kotlin
  val filtered = messages.filter { msg ->
      (msg.mediaType == "photo" && msg.text.isBlank()) ||
          filterUseCase.shouldForward(msg.text, sub.mode, sub.keywords)
  }
  if (filtered.isNotEmpty()) {
      messageDao.insertAll(filtered.map { msg ->
          MessageEntity(msg.id, msg.channel, msg.channelTitle, msg.text, msg.timestamp, msg.mediaType)
      })
  ```

- [ ] **Step 5: Build and run unit tests**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```
  ```
  cd android
  ./gradlew :app:test
  ```
  Expected: BUILD SUCCESSFUL, all tests PASS

---

### Task 7: Update `SyncWorker` to pass `mediaType`

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/worker/SyncWorker.kt`

`SyncWorker` already stores all messages without filtering; it just needs `mediaType` added to the `MessageEntity` constructor so photo-only messages are tagged correctly.

- [ ] **Step 1: Add `mediaType` to the `MessageEntity` constructor in `SyncWorker`**

  Find:
  ```kotlin
  val entities = messages.map { msg ->
      MessageEntity(
          id = msg.id,
          channel = msg.channel,
          channelTitle = msg.channelTitle,
          text = msg.text,
          timestamp = msg.timestamp,
      )
  }
  ```

  Replace with:
  ```kotlin
  val entities = messages.map { msg ->
      MessageEntity(
          id = msg.id,
          channel = msg.channel,
          channelTitle = msg.channelTitle,
          text = msg.text,
          timestamp = msg.timestamp,
          mediaType = msg.mediaType,
      )
  }
  ```

- [ ] **Step 2: Build**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```

- [ ] **Step 3: Commit**

  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedViewModel.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/worker/SyncWorker.kt
  git commit -m "feat: wire photo filter into FeedViewModel and SyncWorker"
  ```

---

## Chunk 4: UI

### Task 8: Add `setIncludePhotos` to `ChannelViewModel`

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelViewModel.kt`

- [ ] **Step 1: Add `setIncludePhotos` function**

  Add after the `setMode` function (around line 90):
  ```kotlin
  fun setIncludePhotos(channel: String, value: Boolean) {
      viewModelScope.launch {
          localRepo.setIncludePhotos(FeedViewModel.USER_ID, channel, value)
          _selectedSub.value = subscriptions.value.find { it.channel == channel }
      }
  }
  ```

  Note: The `_selectedSub.value` refresh after the DB write mirrors the pattern already used in `setMode`. This ensures the settings sheet reflects the new value immediately.

---

### Task 9: Add toggle to `ChannelSettingsSheet`

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelSettingsSheet.kt`

- [ ] **Step 1: Add `Switch` import and new composable parameter**

  Add to the imports:
  ```kotlin
  import androidx.compose.material3.Switch
  ```

  Update the function signature from:
  ```kotlin
  fun ChannelSettingsSheet(
      sub: Subscription,
      onSetMode: (String) -> Unit,
      onAddKeyword: (String) -> Unit,
      onRemoveKeyword: (String) -> Unit,
  )
  ```
  To:
  ```kotlin
  fun ChannelSettingsSheet(
      sub: Subscription,
      onSetMode: (String) -> Unit,
      onAddKeyword: (String) -> Unit,
      onRemoveKeyword: (String) -> Unit,
      onSetIncludePhotos: (Boolean) -> Unit,
  )
  ```

- [ ] **Step 2: Add the toggle row**

  After the closing brace of the mode selector `Row` (around line 59, just before the `// Keywords` comment), add:
  ```kotlin
  // Photo toggle
  Text("Photos", style = MaterialTheme.typography.labelMedium)
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
      Text("Include photo-only messages", style = MaterialTheme.typography.bodyMedium)
      Switch(
          checked = sub.includePhotos,
          onCheckedChange = onSetIncludePhotos,
      )
  }
  ```

---

### Task 10: Wire toggle callback in `ChannelListScreen`

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelListScreen.kt`

- [ ] **Step 1: Pass `onSetIncludePhotos` at the `ChannelSettingsSheet` call site**

  Find the `ChannelSettingsSheet(...)` invocation (around line 156):
  ```kotlin
  ChannelSettingsSheet(
      sub = selectedSub!!,
      onSetMode = { mode -> viewModel.setMode(selectedSub!!.channel, mode) },
      onAddKeyword = { kw -> viewModel.addKeyword(selectedSub!!.channel, kw) },
      onRemoveKeyword = { kw -> viewModel.removeKeyword(selectedSub!!.channel, kw) },
  )
  ```

  Replace with:
  ```kotlin
  ChannelSettingsSheet(
      sub = selectedSub!!,
      onSetMode = { mode -> viewModel.setMode(selectedSub!!.channel, mode) },
      onAddKeyword = { kw -> viewModel.addKeyword(selectedSub!!.channel, kw) },
      onRemoveKeyword = { kw -> viewModel.removeKeyword(selectedSub!!.channel, kw) },
      onSetIncludePhotos = { value -> viewModel.setIncludePhotos(selectedSub!!.channel, value) },
  )
  ```

- [ ] **Step 2: Build**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```
  Expected: BUILD SUCCESSFUL

---

### Task 11: Render `[Photo]` placeholder in `FeedScreen`

**Files:**
- Modify: `android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedScreen.kt`

Two places render `message.text` without a blank guard: the feed card and `ArticleSheet`. Both need to show `[Photo]` in italics when `message.text` is blank and `message.mediaType == "photo"`.

- [ ] **Step 1: Add `SpanStyle`/`buildAnnotatedString` imports and update the feed card body text**

  Add to imports:
  ```kotlin
  import androidx.compose.ui.text.SpanStyle
  import androidx.compose.ui.text.buildAnnotatedString
  import androidx.compose.ui.text.font.FontStyle
  import androidx.compose.ui.text.withStyle
  ```

  Find the message body `Text` in the feed card (around line 272):
  ```kotlin
  Text(
      text = message.text,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 8.dp),
      maxLines = 6,
      overflow = TextOverflow.Ellipsis,
  )
  ```

  Replace with:
  ```kotlin
  val bodyText = if (message.mediaType == "photo" && message.text.isBlank()) {
      buildAnnotatedString {
          withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("[Photo]") }
      }
  } else {
      buildAnnotatedString { append(message.text) }
  }
  Text(
      text = bodyText,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 8.dp),
      maxLines = 6,
      overflow = TextOverflow.Ellipsis,
  )
  ```

- [ ] **Step 2: Update `ArticleSheet` body text the same way**

  Find the body `Text` in `ArticleSheet` (around line 308):
  ```kotlin
  Text(
      text = message.text,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 12.dp),
  )
  ```

  Replace with:
  ```kotlin
  val articleBodyText = if (message.mediaType == "photo" && message.text.isBlank()) {
      buildAnnotatedString {
          withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("[Photo]") }
      }
  } else {
      buildAnnotatedString { append(message.text) }
  }
  Text(
      text = articleBodyText,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(top = 12.dp),
  )
  ```

- [ ] **Step 3: Build final**

  ```
  cd android
  ./gradlew :app:assembleDebug
  ```
  Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all unit tests**

  ```
  cd android
  ./gradlew :app:test
  ```
  Expected: all tests PASS including `PhotoFilterPredicateTest`

- [ ] **Step 5: Install on device and manually verify**

  ```
  cd android
  ./gradlew :app:installDebug
  ```

  Manual checklist:
  1. Open the Channels screen, tap a channel → settings sheet appears
  2. "Include photo-only messages" toggle is visible and defaults to OFF
  3. Toggle ON → toggle persists when you dismiss and reopen the sheet
  4. With toggle OFF: photo-only messages (no caption) do not appear in feed
  5. With toggle ON: photo-only messages appear as `[Photo]` in italics
  6. Photo messages that have a caption always appear regardless of toggle
  7. Tap a `[Photo]` message → ArticleSheet opens showing `[Photo]` in italics

- [ ] **Step 6: Commit**

  ```
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelViewModel.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelSettingsSheet.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/channels/ChannelListScreen.kt
  git add android/app/src/main/kotlin/com/heywood8/telegramnews/ui/feed/FeedScreen.kt
  git commit -m "feat: per-channel include photo-only messages setting"
  ```
