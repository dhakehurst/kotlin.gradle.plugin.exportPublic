package net.akehurst.kotlin.gradle.plugin.exportPublic

import kotlin.test.Test
import kotlin.test.assertFalse

class test_Regex {

    @Test
    fun testRegexNestedCharacterClass() {
        val regex = Regex("[^1-9&&[^/]]")
        assertFalse(regex.matches("/"))
    }

}