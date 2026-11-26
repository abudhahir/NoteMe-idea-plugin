package com.cleveloper.notemeideaplugin

import com.intellij.openapi.diagnostic.Logger

class MarkdownCommandRegistry(
    providers: List<MarkdownCommandProvider> = emptyList()
) {
    private val log = Logger.getInstance(MarkdownCommandRegistry::class.java)
    private val providers = listOf(BuiltInMarkdownCommandProvider) + providers

    companion object {
        fun fromExtensions(): MarkdownCommandRegistry =
            MarkdownCommandRegistry(MarkdownCommandProvider.EP_NAME.extensionList)
    }

    fun availableCommands(context: MarkdownCommandContext): List<MarkdownCommandDescriptor> {
        if (!context.isMarkdown || !context.isWritable) return emptyList()

        val accepted = LinkedHashMap<String, MarkdownCommandDescriptor>()
        providers.forEach { provider ->
            val commands = try {
                provider.commands(context)
            } catch (error: Throwable) {
                log.warn("Markdown command provider '${provider.id}' failed", error)
                return@forEach
            }
            commands.forEach { command ->
                if (command.id.isBlank() || command.name.isBlank()) {
                    log.warn("Ignoring invalid Markdown command from provider '${provider.id}'")
                } else if (accepted.containsKey(command.id)) {
                    log.warn("Ignoring duplicate Markdown command '${command.id}' from provider '${provider.id}'")
                } else if (runCatching { command.isAvailable(context) }.getOrElse {
                        log.warn("Availability check failed for '${command.id}'", it)
                        false
                    }
                ) {
                    accepted[command.id] = command
                }
            }
        }
        return accepted.values.toList()
    }

    fun findByQuery(
        context: MarkdownCommandContext,
        query: String
    ): List<MarkdownCommandDescriptor> {
        val normalizedQuery = query.lowercase()
        return availableCommands(context).filter { command ->
            command.normalizedNames.any { it.contains(normalizedQuery) }
        }
    }
}
