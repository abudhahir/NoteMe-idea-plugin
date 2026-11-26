package com.cleveloper.notemeideaplugin

class BuiltInMarkdownCommandProviderExtension : MarkdownCommandProvider {
    override val id: String = BuiltInMarkdownCommandProvider.id

    override fun commands(context: MarkdownCommandContext): List<MarkdownCommandDescriptor> =
        BuiltInMarkdownCommandProvider.commands(context)
}
