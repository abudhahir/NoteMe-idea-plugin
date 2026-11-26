package com.cleveloper.notemeideaplugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownCommandTest {
    @Test
    fun `paired command replaces query and places caret inside delimiters`() {
        val context = MarkdownCommandContext("hello /bold", 6, 11)
        val result = MarkdownTransformations.paired(context, "**")

        assertEquals("hello ****", result.applyTo(context.text))
        assertEquals(8, result.caretOffset)
    }

    @Test
    fun `line command removes query and normalizes an existing prefix`() {
        val context = MarkdownCommandContext("# /h", 2, 4)
        val result = MarkdownTransformations.linePrefix(context, "## ")

        assertEquals("## ", result.applyTo(context.text))
        assertEquals(3, result.caretOffset)
    }

    @Test
    fun `line command inserts prefix at line start when query follows prose`() {
        val context = MarkdownCommandContext("prose /h", 6, 8)
        val result = MarkdownTransformations.linePrefix(context, "# ")

        assertEquals("# prose ", result.applyTo(context.text))
        assertEquals(8, result.caretOffset)
    }

    @Test
    fun `link command selects url field`() {
        val context = MarkdownCommandContext("/link", 0, 5)
        val result = MarkdownTransformations.link(context)

        assertEquals("[Link text](url)", result.applyTo(context.text))
        assertEquals(12, result.selectionStart)
        assertEquals(15, result.selectionEnd)
    }

    @Test
    fun `code block is language neutral and positions caret inside`() {
        val context = MarkdownCommandContext("/code", 0, 5)
        val result = MarkdownTransformations.codeBlock(context)

        assertEquals("```\n\n```", result.applyTo(context.text))
        assertEquals(4, result.caretOffset)
    }

    @Test
    fun `built-in provider exposes the complete stable command set`() {
        val ids = BuiltInMarkdownCommandProvider.commands(
            MarkdownCommandContext("/command", 0, 8)
        ).map { it.id }

        assertEquals(
            listOf(
                "heading-1", "heading-2", "heading-3", "bullet-list", "numbered-list",
                "task-item", "block-quote", "bold", "italic", "inline-code",
                "code-block", "link", "horizontal-rule"
            ),
            ids
        )
    }

    @Test
    fun `line commands normalize task and quote prefixes`() {
        val task = MarkdownTransformations.linePrefix(
            MarkdownCommandContext("- [ ] /task", 6, 11),
            "- [ ] "
        )
        val quote = MarkdownTransformations.linePrefix(
            MarkdownCommandContext("> /quote", 2, 8),
            "> "
        )

        assertEquals("- [ ] ", task.applyTo("- [ ] /task"))
        assertEquals("> ", quote.applyTo("> /quote"))
    }

    @Test
    fun `line command replaces a bare command token at line start without overlapping edits`() {
        val result = MarkdownTransformations.linePrefix(
            MarkdownCommandContext("/quote", 0, 6),
            "> "
        )

        assertEquals("> ", result.applyTo("/quote"))
    }

    @Test
    fun `registry keeps built-ins first and isolates duplicate and failing providers`() {
        val duplicate = object : MarkdownCommandProvider {
            override val id = "duplicate"
            override fun commands(context: MarkdownCommandContext) =
                listOf(MarkdownCommandDescriptor("bold", "Other Bold", "duplicate") { error("unreachable") })
        }
        val failing = object : MarkdownCommandProvider {
            override val id = "failing"
            override fun commands(context: MarkdownCommandContext): List<MarkdownCommandDescriptor> =
                error("provider failure")
        }
        val healthy = object : MarkdownCommandProvider {
            override val id = "healthy"
            override fun commands(context: MarkdownCommandContext) =
                listOf(
                    MarkdownCommandDescriptor(
                        "custom",
                        "Custom",
                        "custom",
                        transform = { MarkdownTransformationResult(emptyList(), it.caretOffset) }
                    )
                )
        }

        val commands = MarkdownCommandRegistry(listOf(duplicate, failing, healthy))
            .availableCommands(MarkdownCommandContext("", 0, 0))

        assertEquals(listOf("heading-1", "heading-2", "heading-3", "bullet-list",
            "numbered-list", "task-item", "block-quote", "bold", "italic", "inline-code",
            "code-block", "link", "horizontal-rule", "custom"), commands.map { it.id })
        assertTrue(commands[7].name == "Bold")
    }
}
