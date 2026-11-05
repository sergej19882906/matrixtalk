package com.matrix.messenger.data.model

import com.matrix.messenger.build.BuildVersion
import com.matrix.messenger.platform.appVersionName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildVersionTest {

    @Test
    fun runtimeVersionMatchesGeneratedBuildMetadata() {
        assertEquals(BuildVersion.NAME, appVersionName())
        assertTrue(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(BuildVersion.NAME))
    }
}
