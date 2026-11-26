package com.cleveloper.notemeideaplugin

object BuiltInMarkdownCommandProvider : MarkdownCommandProvider {
    override val id: String = "noteme.built-in"

    override fun commands(context: MarkdownCommandContext): List<MarkdownCommandDescriptor> = listOf(
        line("heading-1", "Heading 1", "Insert a level-one heading", listOf("h1")) { "# " },
        line("heading-2", "Heading 2", "Insert a level-two heading", listOf("h2")) { "## " },
        line("heading-3", "Heading 3", "Insert a level-three heading", listOf("h3")) { "### " },
        line("bullet-list", "Bullet List", "Insert a bullet list item", listOf("bullet", "ul")) { "- " },
        line("numbered-list", "Numbered List", "Insert a numbered list item", listOf("ordered", "ol")) { "1. " },
        line("task-item", "Task Item", "Insert a task list item", listOf("task", "todo")) { "- [ ] " },
        line("block-quote", "Block Quote", "Insert a block quote", listOf("quote")) { "> " },
        inline("bold", "Bold", "Insert bold Markdown", listOf("strong"), "**"),
        inline("italic", "Italic", "Insert italic Markdown", listOf("em"), "*"),
        inline("inline-code", "Inline Code", "Insert inline code", listOf("code"), "`"),
        MarkdownCommandDescriptor(
            id = "code-block",
            name = "Code Block",
            description = "Insert an empty fenced code block",
            aliases = listOf("fence", "pre"),
            transform = MarkdownTransformations::codeBlock
        ),
        MarkdownCommandDescriptor(
            id = "link",
            name = "Link",
            description = "Insert a Markdown link",
            aliases = listOf("url"),
            transform = MarkdownTransformations::link
        ),
        MarkdownCommandDescriptor(
            id = "horizontal-rule",
            name = "Horizontal Rule",
            description = "Insert a horizontal rule",
            aliases = listOf("hr", "rule"),
            transform = MarkdownTransformations::horizontalRule
        )
    )

    private fun line(
        id: String,
        name: String,
        description: String,
        aliases: List<String>,
        prefix: () -> String
    ) = MarkdownCommandDescriptor(
        id = id,
        name = name,
        description = description,
        aliases = aliases,
        transform = { MarkdownTransformations.linePrefix(it, prefix()) }
    )

    private fun inline(
        id: String,
        name: String,
        description: String,
        aliases: List<String>,
        delimiter: String
    ) = MarkdownCommandDescriptor(
        id = id,
        name = name,
        description = description,
        aliases = aliases,
        transform = { MarkdownTransformations.paired(it, delimiter) }
    )
}
