package com.cleveloper.notemeideaplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class MarkdownSlashCommandCompletionTest : BasePlatformTestCase() {
    fun `test command range handles lookup replacing or preserving the slash`() {
        check(
            MarkdownSlashCommandCompletionContributor.queryStartAfterInsertion(
                text = "Block Quote",
                startOffset = 0,
                caretOffset = 11
            ) == 0
        )
        check(
            MarkdownSlashCommandCompletionContributor.queryStartAfterInsertion(
                text = "/Block Quote",
                startOffset = 1,
                caretOffset = 12
            ) == 0
        )
    }

    fun `test dropdown contains built-in commands`() {
        myFixture.configureByText("note.md", "/<caret>")

        myFixture.completeBasic()

        val lookup = myFixture.lookupElements
        assertNotNull(lookup)
        val names = lookup!!.map { it.lookupString }.toSet()
        check("Bold" in names)
        check("Heading 1" in names)
        check("Code Block" in names)
    }

    fun `test selecting block quote after a bare slash applies the command`() {
        myFixture.configureByText("note.md", "/<caret>")

        myFixture.completeBasic()
        val blockQuote = myFixture.lookupElements!!.first { it.lookupString == "Block Quote" }
        myFixture.lookup!!.currentItem = blockQuote
        myFixture.finishLookup('\n')

        check(myFixture.editor.document.text == "> ") {
            "text=${myFixture.editor.document.text}"
        }
        check(myFixture.editor.caretModel.offset == 2) {
            "caret=${myFixture.editor.caretModel.offset}"
        }
    }

    fun `test partial query filters command names and aliases`() {
        myFixture.configureByText("note.md", "/bo<caret>")

        myFixture.completeBasic()

        val lookup = myFixture.lookupElements
        assertNotNull(lookup)
        check(lookup!!.map { it.lookupString }.contains("/bo")) {
            "lookup=${lookup.map { it.lookupString }}"
        }
    }

    fun `test no match leaves completion unavailable`() {
        myFixture.configureByText("note.md", "/does-not-exist<caret>")

        myFixture.completeBasic()

        val lookup = myFixture.lookupElements
        check(lookup == null || lookup.none {
            it.lookupString in setOf("Bold", "Italic", "Heading 1", "Code Block")
        })
    }

    fun `test suppressed contexts do not show slash commands`() {
        myFixture.configureByText("note.md", "https://example.com/<caret>")

        myFixture.completeBasic()

        val lookup = myFixture.lookupElements
        check(lookup == null || lookup.none { it.lookupString == "Bold" })
    }

    fun `test accepting a command applies one editor transformation`() {
        myFixture.configureByText("note.md", "/bo<caret>")

        myFixture.completeBasic()
        myFixture.finishLookup('\n')

        assertNotNull(myFixture.file)
        check(myFixture.editor.document.text == "****") {
            "text=${myFixture.editor.document.text}"
        }
        check(myFixture.editor.caretModel.offset == 2)
    }

    fun `test accepted command is undoable as one editor operation`() {
        myFixture.configureByText("note.md", "/bo<caret>")

        myFixture.completeBasic()
        myFixture.finishLookup('\n')
        check(myFixture.editor.document.text == "****")

        UndoManager.getInstance(project).undo(
            FileEditorManager.getInstance(project).selectedEditor
        )

        check(myFixture.editor.document.text == "/bo")
        check(myFixture.editor.caretModel.offset == 3)
    }
}
