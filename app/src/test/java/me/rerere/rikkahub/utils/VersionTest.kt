package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun `compare ignores tag suffixes`() {
        assertTrue(Version("1.2.7-plus") > Version("1.2.6"))
        assertTrue(Version("v1.2.7-plus") > Version("1.2.6"))
        assertEquals(0, Version("1.2.7+build.1").compareTo(Version("1.2.7")))
        assertEquals(0, Version("1.2.7-plus+build.1").compareTo(Version("1.2.7")))
    }

    @Test
    fun `compare works for numeric versions`() {
        assertTrue(Version("1.2.10") > Version("1.2.9"))
        assertEquals(0, Version("1.2").compareTo(Version("1.2.0")))
    }
}

