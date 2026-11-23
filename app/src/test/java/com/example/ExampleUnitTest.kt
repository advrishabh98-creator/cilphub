package com.example

import com.example.data.model.AnalysisStatus
import com.example.data.model.ClipCategory
import com.example.data.model.HighlightClip
import com.example.data.model.StreamAnalysis
import com.example.data.model.StreamSourceType
import com.example.data.model.formatDurationSeconds
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun formatDurationSeconds_formatsMinutesAndHoursCorrectly() {
        assertEquals("00:00", formatDurationSeconds(0))
        assertEquals("00:45", formatDurationSeconds(45))
        assertEquals("06:52", formatDurationSeconds(412))
        assertEquals("1:05:09", formatDurationSeconds(3909))
    }

    @Test
    fun models_fallbackSafelyOnUnknownEnumValues() {
        val stream = StreamAnalysis(sourceType = "UNKNOWN_SRC", status = "UNKNOWN_STATUS")
        assertEquals(StreamSourceType.VIDEO_FILE, stream.sourceTypeEnum())
        assertEquals(AnalysisStatus.READY, stream.statusEnum())

        val clip = HighlightClip(category = "LEGACY_TYPE", startSec = 10, endSec = 25)
        assertEquals(ClipCategory.HIGHLIGHT, clip.categoryEnum())
        assertEquals(15, clip.durationSeconds)
    }
}
