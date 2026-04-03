package org.label.translate.labeltranslate

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange

class EditLabelAction : AnAction("Edit Label") {

    companion object {
        // Matches __('key'), __("key"), trans('key'), trans("key"), @lang('key'), @lang("key")
        private val TRANSLATION_REGEX = Regex("""(?:__\s*\(\s*|trans\s*\(\s*|@lang\s*\(\s*)['"]([^'"]+)['"]""")
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        val isBladeFile = virtualFile?.name?.endsWith(".blade.php") == true

        if (!isBladeFile || editor == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val key = findTranslationKeyAtCursor(editor)
        e.presentation.isEnabledAndVisible = key != null
        if (key != null) {
            e.presentation.text = "Edit Label '$key'"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        val key = findTranslationKeyAtCursor(editor) ?: return

        // The part before the first dot is the filename/group (e.g. "auth" in "auth.failed")
        val group = key.substringBefore(".")

        val translationSet = findTranslationSet(project, group)
        if (translationSet == null) {
            Messages.showWarningDialog(
                project,
                "No translation file found for group '$group'.",
                "Label Translate"
            )
            return
        }

        val dialog = EditLabelDialogWrapper(project, translationSet, key)
        if (dialog.showAndGet()) {
            dialog.saveChanges()
        }
    }

    private fun findTranslationKeyAtCursor(editor: Editor): String? {
        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(caretOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStart, lineEnd))

        val cursorColumn = caretOffset - lineStart

        for (match in TRANSLATION_REGEX.findAll(lineText)) {
            if (cursorColumn >= match.range.first && cursorColumn <= match.range.last + 1) {
                return match.groupValues[1]
            }
        }

        return null
    }

    private fun findTranslationSet(project: Project, group: String): TranslationSet? {
        val projectPath = project.basePath

        for (resourcePath in TranslationSet.getResourcePaths()) {
            val sets = TranslationSet.loadFromPath(projectPath, resourcePath)
            val match = sets.firstOrNull { it.displayName.toLowerCase() == group.toLowerCase() }
            if (match != null) return match
        }

        return null
    }
}
