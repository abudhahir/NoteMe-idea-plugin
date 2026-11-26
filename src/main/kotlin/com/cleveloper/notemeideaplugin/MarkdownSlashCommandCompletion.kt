package com.cleveloper.notemeideaplugin

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.Language
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class MarkdownSlashCommandCompletionContributor : CompletionContributor() {
    init {
        val markdown = Language.findLanguageByID("Markdown")
        if (markdown != null) {
            extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(markdown),
                Provider()
            )
        }
    }

    override fun beforeCompletion(context: CompletionInitializationContext) {
        val query = MarkdownSlashCommandContextAnalyzer.analyze(
            context.editor,
            context.file
        ) ?: return
        context.setReplacementOffset(query.endOffset)
    }

    private class Provider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val query = MarkdownSlashCommandContextAnalyzer.analyze(
                parameters.editor,
                parameters.position.containingFile
            ) ?: return
            val commandContext = MarkdownCommandContext(
                text = parameters.editor.document.text,
                queryStart = query.startOffset,
                caretOffset = query.endOffset,
                isMarkdown = true,
                isWritable = parameters.editor.document.isWritable
            )
            MarkdownCommandRegistry.fromExtensions()
                .findByQuery(commandContext, query.query)
                .forEach { command ->
                    var builder = LookupElementBuilder.create(
                        query.query.takeIf { it.isNotEmpty() }?.let { "/$it" } ?: command.name
                    )
                        .withPresentableText(command.name)
                        .withTailText("  ${command.description}", true)
                    builder = builder.withInsertHandler { insertionContext, _ ->
                        applyCommand(insertionContext, command.id)
                    }
                    result.addElement(builder)
                }
        }
    }

    companion object {
        fun applyCommand(context: InsertionContext, commandId: String) {
            val editor = context.editor
            val project = context.project ?: return
            val caretOffset = editor.caretModel.offset
            val slashStart = queryStartAfterInsertion(
                editor.document.text,
                context.startOffset,
                caretOffset
            )
            if (slashStart >= caretOffset) return
            val commandContext = MarkdownCommandContext(
                text = editor.document.text,
                queryStart = slashStart,
                caretOffset = caretOffset,
                isMarkdown = true,
                isWritable = editor.document.isWritable
            )
            val command = MarkdownCommandRegistry.fromExtensions()
                .availableCommands(commandContext)
                .firstOrNull { it.id == commandId } ?: return
            val result = runCatching { command.transform(commandContext) }.getOrElse { return }
            if (!editor.document.isWritable) return
            if (result.edits.any { it.start < 0 || it.end > editor.document.textLength }) return
            WriteCommandAction.runWriteCommandAction(
                project,
                "Markdown ${command.name}",
                null,
                Runnable {
                    result.edits.sortedByDescending { it.start }.forEach { edit ->
                        editor.document.replaceString(edit.start, edit.end, edit.replacement)
                    }
                editor.caretModel.moveToOffset(result.caretOffset.coerceIn(0, editor.document.textLength))
                if (result.selectionStart != result.selectionEnd) {
                    editor.selectionModel.setSelection(
                        result.selectionStart.coerceIn(0, editor.document.textLength),
                        result.selectionEnd.coerceIn(0, editor.document.textLength)
                    )
                } else {
                    editor.selectionModel.removeSelection()
                }
                }
            )
        }

        internal fun queryStartAfterInsertion(
            text: String,
            startOffset: Int,
            caretOffset: Int
        ): Int {
            val safeStart = startOffset.coerceIn(0, caretOffset)
            return if (safeStart > 0 && text.getOrNull(safeStart - 1) == '/') {
                safeStart - 1
            } else {
                safeStart
            }
        }
    }
}
