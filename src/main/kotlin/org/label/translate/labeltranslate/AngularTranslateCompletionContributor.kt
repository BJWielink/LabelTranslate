package org.label.translate.labeltranslate

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

private val PLUGIN_ICON by lazy {
    IconLoader.getIcon("/icons/pluginIcon.svg", AngularTranslateCompletionContributor::class.java)
}

class AngularTranslateCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), TranslateCompletionProvider())
    }
}

private class TranslateCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        // Use the editor's virtual file so this works even inside injected language fragments
        val virtualFile = parameters.editor.virtualFile ?: return
        val ext = virtualFile.extension?.toLowerCase() ?: return
        if (ext != "html" && ext != "htm") return

        val document = parameters.editor.document
        val project = parameters.position.project

        // Build the pipe regex from the configured pipe name
        val pipeName = Regex.escape(TranslatePipeConfig().pipeName)
        val translatePipe = Regex("""\|\s*$pipeName\b""", RegexOption.IGNORE_CASE)

        // parameters.offset may be an offset inside an injected fragment (e.g. {{ }} or [attr]="").
        // Convert it to the host document offset so line/text lookups are correct.
        val hostOffset = InjectedLanguageManager.getInstance(project)
            .injectedToHost(parameters.position, parameters.offset)

        val lineNum = document.getLineNumber(hostOffset)
        val lineStart = document.getLineStartOffset(lineNum)
        val lineEnd = document.getLineEndOffset(lineNum)

        val textBefore = document.getText(TextRange(lineStart, hostOffset))
        val textAfter = document.getText(TextRange(hostOffset, lineEnd))
            .replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, "")

        // Only provide completions when the configured pipe follows the cursor on this line
        if (!translatePipe.containsMatchIn(textAfter)) return

        // Find the opening quote immediately before the typed prefix
        val quotePos = textBefore.lastIndexOfAny(charArrayOf('\'', '"'))
        if (quotePos < 0) return

        // Ensure cursor is still inside the string (no closing quote between open and cursor)
        val quoteChar = textBefore[quotePos]
        val insideText = textBefore.substring(quotePos + 1)
        if (insideText.contains(quoteChar)) return

        val typedPrefix = insideText
        val allKeys = loadAllTranslationKeys(project)

        val customResult = result.withPrefixMatcher(SubstringPrefixMatcher(typedPrefix))
        for (key in allKeys) {
            customResult.addElement(
                LookupElementBuilder.create(key)
                    .withTypeText("Translation key")
                    .withIcon(PLUGIN_ICON)
                    .bold()
            )
        }
    }

    private fun loadAllTranslationKeys(project: Project): List<String> {
        val keys = mutableListOf<String>()
        for (resourcePath in TranslationSet.getResourcePaths()) {
            val sets = TranslationSet.loadFromPath(project.basePath, resourcePath)
            for (set in sets) {
                val group = set.displayName.toLowerCase()
                for (key in set.getKeys()) {
                    keys.add("$group.$key")
                }
            }
        }
        return keys.sorted()
    }
}

private class SubstringPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
    override fun prefixMatches(name: String): Boolean {
        if (prefix.isEmpty()) return true
        val lastDot = prefix.lastIndexOf('.')
        if (lastDot < 0) return name.contains(prefix, ignoreCase = true)

        // e.g. prefix = "labels.descr" → groupPart = "labels", keyFragment = "descr"
        val groupPart = prefix.substring(0, lastDot)
        val keyFragment = prefix.substring(lastDot + 1)

        // Group must match exactly
        if (!name.startsWith("$groupPart.", ignoreCase = true)) return false

        // The key part (everything after "labels.") must contain the fragment
        val keyPart = name.substring(groupPart.length + 1)
        return keyFragment.isEmpty() || keyPart.contains(keyFragment, ignoreCase = true)
    }

    override fun cloneWithPrefix(prefix: String) = SubstringPrefixMatcher(prefix)
}
