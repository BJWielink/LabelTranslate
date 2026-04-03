package org.label.translate.labeltranslate

import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.swing.SwingUtilities

object OpenAiService {

    fun hasApiKey(): Boolean = ApiKeyConfig().apiKey.isNotEmpty()

    private fun apiKey(): String = ApiKeyConfig().apiKey

    /**
     * Translates [wordToTranslate] (in [sourceLang]) into all [languages].
     * [key] is used as the field name in the OpenAI JSON prompt/response.
     * Results arrive on the Swing EDT via [onSuccess] as a map of UPPERCASE lang → translation.
     */
    fun translate(
        key: String,
        wordToTranslate: String,
        sourceLang: String,
        languages: List<String>,
        onSuccess: (Map<String, String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val client = OkHttpClient()
        val sourceLangUpper = sourceLang.toUpperCase()
        val langsUpper = languages.map { it.toUpperCase() }

        val startsWithCapital = wordToTranslate.firstOrNull()?.isUpperCase() == true
        val capitalizationInstruction = if (startsWithCapital)
            "The original word starts with a capital letter, so all translations must also start with a capital letter. "
        else
            "The original word starts with a lowercase letter, so all translations must also start with a lowercase letter. "

        val targetLanguages = langsUpper.filter { it != sourceLangUpper }
        val languagesJson = langsUpper.joinToString(", ") { "\\\"$it\\\": {\\\"$key\\\": \\\"translation\\\"}" }
        val jsonContent = """
            Translate the \"$sourceLangUpper\" word/sentence \"$wordToTranslate\" into the following languages: ${targetLanguages.joinToString(", ")}.
            Also return \"$sourceLangUpper\" in the response with the spelling-corrected version of the original word.
            $capitalizationInstruction
            Use this as a key \"$key\". Please return all languages in the following structured JSON format: {$languagesJson}
            and only return that, not other text.
        """.trimIndent().replace("\n", " ")

        val json = """
            {
                "model": "gpt-4o-mini",
                "messages": [
                    {"role": "system", "content": "You are a helpful assistant."},
                    {"role": "user", "content": "$jsonContent"}
                ],
                "max_completion_tokens": ${ApiKeyConfig().maxTokens}
            }
        """.trimIndent()

        val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${apiKey()}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                SwingUtilities.invokeLater { onError("Error during translation: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: ""

                        if (!response.isSuccessful) {
                            SwingUtilities.invokeLater {
                                onError("Unexpected response code: ${response.code}\n$responseBody")
                            }
                            return
                        }

                        val content = JsonParser.parseString(responseBody)
                            .asJsonObject["choices"]
                            .asJsonArray[0].asJsonObject["message"].asJsonObject["content"].asString

                        val cleaned = content.trim().removePrefix("```json").removeSuffix("```").trim()
                        val parsedJson = JsonParser.parseString(cleaned).asJsonObject

                        val results = mutableMapOf<String, String>()
                        for (lang in langsUpper) {
                            if (parsedJson.has(lang) && parsedJson[lang].asJsonObject.has(key)) {
                                var translation = parsedJson[lang].asJsonObject[key].asString
                                if (translation.isNotEmpty()) {
                                    translation = if (startsWithCapital)
                                        translation[0].toUpperCase() + translation.substring(1)
                                    else
                                        translation[0].toLowerCase() + translation.substring(1)
                                }
                                results[lang] = translation
                            }
                        }

                        SwingUtilities.invokeLater { onSuccess(results) }
                    } catch (e: Exception) {
                        SwingUtilities.invokeLater { onError("Error processing response: ${e.message}") }
                    }
                }
            }
        })
    }
}
