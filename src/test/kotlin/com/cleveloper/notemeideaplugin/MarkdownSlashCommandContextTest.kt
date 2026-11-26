package com.cleveloper.notemeideaplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class MarkdownSlashCommandContextTest : BasePlatformTestCase() {
    fun `test slash command is available at line start`() {
        myFixture.configureByText("note.md", "/<caret>")

        val query = MarkdownSlashCommandContextAnalyzer.analyze(
            myFixture.editor,
            myFixture.file
        )

        assertNotNull(query)
        assertEquals("", query!!.query)
    }

    fun `test slash command is available after whitespace and prose`() {
        myFixture.configureByText("note.md", "text /bo<caret>")

        val query = MarkdownSlashCommandContextAnalyzer.analyze(
            myFixture.editor,
            myFixture.file
        )

        assertNotNull(query)
        assertEquals("bo", query!!.query)
    }

    fun `test slash command is suppressed in markdown content contexts`() {
        listOf(
            "https://example.com/<caret>",
            "path/to/<caret>",
            "[text](destination/<caret>)",
            "`inline/<caret>`",
            "```text\ncode/<caret>\n```",
            "\\/<caret>"
        ).forEach { content ->
            myFixture.configureByText("note.md", content)
            assertNull(
                "Expected suppressed context: $content",
                MarkdownSlashCommandContextAnalyzer.analyze(myFixture.editor, myFixture.file)
            )
        }
    }

    fun `test slash command is suppressed in non markdown files`() {
        myFixture.configureByText("note.txt", "/<caret>")

        assertNull(
            MarkdownSlashCommandContextAnalyzer.analyze(myFixture.editor, myFixture.file)
        )
    }

    fun `test slash command is suppressed in read-only documents`() {
        myFixture.configureByText("note.md", "/<caret>")
        myFixture.editor.document.setReadOnly(true)

        assertNull(
            MarkdownSlashCommandContextAnalyzer.analyze(myFixture.editor, myFixture.file)
        )
    }
}
