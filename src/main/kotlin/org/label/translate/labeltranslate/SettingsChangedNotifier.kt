package org.label.translate.labeltranslate

import com.intellij.util.messages.Topic

interface SettingsChangedNotifier {
    fun onFolderPathsChanged()

    companion object {
        val TOPIC = Topic.create(
            "LabelTranslate.SettingsChanged",
            SettingsChangedNotifier::class.java
        )
    }
}
