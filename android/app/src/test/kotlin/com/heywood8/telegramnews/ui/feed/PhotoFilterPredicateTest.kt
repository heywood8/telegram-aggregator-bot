package com.heywood8.telegramnews.ui.feed

import com.heywood8.telegramnews.domain.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class PhotoFilterPredicateTest {

    // Replicates the filter predicate used in FeedViewModel.filteredMessages:
    //   includePhotos || mediaType != MediaType.PHOTO || text.isNotBlank()
    // Photo-only messages (no caption) are hidden when includePhotos is false.
    // Photo messages with captions are always shown (photo suppressed in UI when includePhotos is false).
    private fun shouldShow(
        includePhotos: Boolean,
        mediaType: String?,
        text: String,
    ): Boolean = includePhotos || mediaType != MediaType.PHOTO || text.isNotBlank()

    @Test
    fun `photo-only message hidden when includePhotos is false`() {
        assertFalse(shouldShow(includePhotos = false, mediaType = MediaType.PHOTO, text = ""))
    }

    @Test
    fun `photo-only message shown when includePhotos is true`() {
        assertTrue(shouldShow(includePhotos = true, mediaType = MediaType.PHOTO, text = ""))
    }

    @Test
    fun `photo with caption always shown regardless of setting`() {
        assertTrue(shouldShow(includePhotos = false, mediaType = MediaType.PHOTO, text = "Some caption"))
    }

    @Test
    fun `photo with caption shown when includePhotos is true`() {
        assertTrue(shouldShow(includePhotos = true, mediaType = MediaType.PHOTO, text = "Some caption"))
    }

    @Test
    fun `text message always shown`() {
        assertTrue(shouldShow(includePhotos = false, mediaType = null, text = "hello world"))
    }

    @Test
    fun `non-photo non-text message with caption passes predicate`() {
        assertTrue(shouldShow(includePhotos = false, mediaType = "video", text = "Watch this"))
    }

    @Test
    fun `missing subscription defaults to exclude photos (false)`() {
        val includePhotos: Boolean = null ?: false
        assertFalse(shouldShow(includePhotos = includePhotos, mediaType = MediaType.PHOTO, text = ""))
    }
}
