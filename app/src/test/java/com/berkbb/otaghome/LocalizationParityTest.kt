package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationParityTest {

    // Gradle runs tests from the module dir; IDE runners may use the project root
    private val resDir = listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.isDirectory }
        ?: File("src/main/res")

    private fun parseStringKeys(file: File): Map<String, String> {
        assertTrue("File ${file.absolutePath} must exist", file.exists())
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val nodes = doc.getElementsByTagName("string")

        val map = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            val name = element.getAttribute("name")
            val content = element.textContent
            map[name] = content
        }
        return map
    }

    private fun extractPlaceholders(text: String): List<String> {
        val pattern = Pattern.compile("%[0-9]+\\$[a-zA-Z%]")
        val matcher = pattern.matcher(text)
        val placeholders = mutableListOf<String>()
        while (matcher.find()) {
            placeholders.add(matcher.group())
        }
        return placeholders.sorted()
    }

    @Test
    fun testAllLanguagesHaveIdenticalKeys() {
        val defaultFile = File(resDir, "values/strings.xml")
        val trFile = File(resDir, "values-tr/strings.xml")
        val roFile = File(resDir, "values-ro/strings.xml")

        val defaultStrings = parseStringKeys(defaultFile)
        val trStrings = parseStringKeys(trFile)
        val roStrings = parseStringKeys(roFile)

        assertTrue("Default strings must not be empty", defaultStrings.isNotEmpty())

        // 1. Turkish key parity check
        val missingInTr = defaultStrings.keys - trStrings.keys
        val extraInTr = trStrings.keys - defaultStrings.keys
        assertEquals("Missing keys in Turkish translation: $missingInTr", emptySet<String>(), missingInTr)
        assertEquals("Extra keys in Turkish translation: $extraInTr", emptySet<String>(), extraInTr)

        // 2. Romanian key parity check
        val missingInRo = defaultStrings.keys - roStrings.keys
        val extraInRo = roStrings.keys - defaultStrings.keys
        assertEquals("Missing keys in Romanian translation: $missingInRo", emptySet<String>(), missingInRo)
        assertEquals("Extra keys in Romanian translation: $extraInRo", emptySet<String>(), extraInRo)
    }

    @Test
    fun testNonEmptyTranslations() {
        val defaultFile = File(resDir, "values/strings.xml")
        val trFile = File(resDir, "values-tr/strings.xml")
        val roFile = File(resDir, "values-ro/strings.xml")

        listOf(defaultFile, trFile, roFile).forEach { file ->
            val strings = parseStringKeys(file)
            strings.forEach { (key, value) ->
                assertTrue("Translation for key '$key' in ${file.name} must not be empty or blank", value.isNotBlank())
            }
        }
    }

    @Test
    fun testPlaceholderParityAcrossTranslations() {
        val defaultStrings = parseStringKeys(File(resDir, "values/strings.xml"))
        val trStrings = parseStringKeys(File(resDir, "values-tr/strings.xml"))
        val roStrings = parseStringKeys(File(resDir, "values-ro/strings.xml"))

        defaultStrings.forEach { (key, defaultVal) ->
            val defaultPlaceholders = extractPlaceholders(defaultVal)

            val trVal = trStrings[key] ?: ""
            val trPlaceholders = extractPlaceholders(trVal)
            assertEquals("Placeholder mismatch in TR for key '$key'", defaultPlaceholders, trPlaceholders)

            val roVal = roStrings[key] ?: ""
            val roPlaceholders = extractPlaceholders(roVal)
            assertEquals("Placeholder mismatch in RO for key '$key'", defaultPlaceholders, roPlaceholders)
        }
    }
}
