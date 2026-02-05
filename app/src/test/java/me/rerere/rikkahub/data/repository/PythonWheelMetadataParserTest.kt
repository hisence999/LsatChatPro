package me.rerere.rikkahub.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PythonWheelMetadataParserTest {
    @Test
    fun parseRequiresDistValue_parsesNameAndVersionSpec() {
        val req = PythonWheelMetadataParser.parseRequiresDistValue("requests (>=2.31.0)")!!
        assertEquals("requests", req.name)
        assertEquals(">=2.31.0", req.versionSpec)
        assertNull(req.marker)
    }

    @Test
    fun parseRequiresDistValue_parsesMarker() {
        val req = PythonWheelMetadataParser.parseRequiresDistValue("importlib-metadata; python_version < \"3.8\"")!!
        assertEquals("importlib-metadata", req.name)
        assertEquals("python_version < \"3.8\"", req.marker)
        assertNull(req.versionSpec)
    }

    @Test
    fun parseRequiresDistValue_parsesExtras_andExactVersion() {
        val req = PythonWheelMetadataParser.parseRequiresDistValue("pydantic[email] (==2.10.0); extra == \"email\"")!!
        assertEquals("pydantic", req.name)
        assertEquals("email", req.extras)
        assertEquals("==2.10.0", req.versionSpec)
        assertEquals("2.10.0", req.exactVersionOrNull())
        assertEquals(true, req.isOptional)
        assertEquals("email", req.optionalExtraNameOrNull())
    }

    @Test
    fun parseRequiresDistValue_supportsDirectSpecifier() {
        val req = PythonWheelMetadataParser.parseRequiresDistValue("pyyaml>=6.0")!!
        assertEquals("pyyaml", req.name)
        assertEquals(">=6.0", req.versionSpec)
        assertNull(req.marker)
    }

    @Test
    fun normalizePackageName_normalizesCommonSeparators() {
        assertEquals("my-package", PythonWheelMetadataParser.normalizePackageName("My_Package"))
        assertEquals("my-package", PythonWheelMetadataParser.normalizePackageName("my.package"))
        assertEquals("my-package", PythonWheelMetadataParser.normalizePackageName("my package"))
    }
}
