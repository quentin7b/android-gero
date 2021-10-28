package com.github.quentin7b.android.gero

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.util.*
import kotlin.collections.HashMap

class Gero private constructor(
    private val currentLocale: Locale,
    private var hasTextLoaded: Boolean,
    private var sendKeyIfNotFound: Boolean
) {

    /**
     * List of files containing locales
     * Key is the language tag (fr_FR, fr) value is the files that matches the key
     */
    private val fileLocales = HashMap<String, List<String>>()

    /**
     * List of translations for the current locale (need to be loaded)
     * @see loadCurrentLanguageAsync
     */
    private val localeTexts = mutableListOf<GetTextFile>()

    /**
     * Initialize the library.
     * It will use the `currentLocale` of the Gero instance to look for the po file to use
     * If the locale is for example Locale.FRENCH, we'll be looking for files with
     * - Language: fr
     * - Language: fr_FR (ISO 3166)
     *
     * > Note that for a key contained in both fr and fr_FR, the one in fr_FR will be used
     */
    private fun loadCurrentLanguageAsync(context: Context): Deferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        CoroutineScope(Job()).launch(Dispatchers.IO) {
            if (fileLocales.isEmpty()) {
                fileLocales.putAll(registerTranslationFilesAsync(context).await())
            }

            var reader: BufferedReader? = null
            var inputStreamReader: InputStreamReader? = null
            try {
                // 0 - Clear
                hasTextLoaded = false
                localeTexts.onEach {
                    it.first.clear()
                    it.second.clear()
                }.clear()
                // 1 - Locate the file
                val languageTag = currentLocale.language
                val countryTag = currentLocale.country
                val completeTag = "${languageTag}_${countryTag}"

                Log.d(
                    "Gero",
                    "loadCurrentLanguageAsync- Looking for files ($languageTag, $completeTag)"
                )
                val fileList = mutableListOf<String>()
                if (!fileLocales.containsKey(completeTag) && !fileLocales.containsKey(languageTag)) {
                    throw FileNotFoundException("Cant find a po file for locale $currentLocale")
                }

                fileLocales[languageTag]?.let {
                    fileList.addAll(it)
                }
                // Add the complete tags after the language tag so that if there is the same key
                // The complete tag value will override the basic one
                fileLocales[completeTag]?.let {
                    fileList.addAll(it)
                }

                Log.d("Gero", "loadCurrentLanguageAsync - Found files : $fileList")
                fileList.forEach { filePath ->
                    Log.d("Gero", "loadCurrentLanguageAsync - Parsing $filePath")
                    val newFile = Pair(SingleValues(), PluralsValues())

                    inputStreamReader =
                        InputStreamReader(context.assets.open(filePath), "UTF-8")
                    reader = BufferedReader(inputStreamReader)

                    // 2 - Read the file
                    val iterator = reader!!.lineSequence().iterator()
                    var currentKey = ""
                    var currentKeyIsPlural = false

                    while (iterator.hasNext()) {
                        // Handle it
                        val line = iterator.next()
                        if (line == "msgid \"\"" || line == "msgstr \"\"") {
                            // Useless header line (or unused line)
                            continue
                        }

                        if (line.startsWith("\"Plural-Forms")) {
                            newFile.second.pluralsFormula = parsePluralsHeader(line)
                        } else if (line.startsWith("msgid_plural")) {
                            currentKey = parseKeyLine(line)
                            currentKeyIsPlural = true
                            // Plural must be tested before msgid because it contains more text
                        } else if (line.startsWith("msgid")) {
                            currentKey = parseKeyLine(line)
                            currentKeyIsPlural = false
                        } else if (line.startsWith("msgstr")) {
                            if (currentKeyIsPlural) {
                                val textQuantityValue = parsePluralLine(line)
                                newFile.second.addValue(
                                    currentKey,
                                    textQuantityValue.first,
                                    textQuantityValue.second
                                )
                            } else {
                                newFile.first.setValue(currentKey, parseValueLine(line))
                            }
                        }
                    }

                    localeTexts.add(newFile)
                }
                // 3 - Complete
                hasTextLoaded = true
                deferred.complete(Unit)
            } catch (err: Exception) {
                deferred.completeExceptionally(err)
            } finally {
                inputStreamReader?.close()
                reader?.close()
            }
        }
        return deferred
    }

    private fun parseKeyLine(line: String): String {
        // Remove : msgid " and the last "
        return line.substring(line.indexOf('"') + 1, line.length - 1)
    }

    private fun parseValueLine(line: String): String {
        // Remove : msgstr " and the last "
        return line.substring(line.indexOf('"') + 1, line.length - 1)
    }

    private fun parsePluralLine(line: String): Pair<Int, String> {
        // Remove : msgstr " and the last "
        val quantity = line.substring(line.indexOf('[') + 1, line.indexOf(']'))
        val value = parseValueLine(line)
        return Pair(quantity.toInt(), value)
    }

    private fun parsePluralsHeader(line: String): PluralsFormula {
        val formula = PluralsFormula()
        val lineWithoutNewLine = line.replace("\n", "")
        val firstRealIndex =
            when {
                lineWithoutNewLine.contains("(") -> lineWithoutNewLine.indexOf("(") + 1
                else -> lineWithoutNewLine.indexOf("plural=") + 7 // 7 as "plural="'s length is 7
            }
        // In this case  "\"Plural-Forms: nplurals=1; plural=0;\n" we don't have any ()
        val lastRealIndex =
            when {
                lineWithoutNewLine.contains(")") -> lineWithoutNewLine.indexOf(")")
                lineWithoutNewLine.endsWith(";") -> lineWithoutNewLine.length - 1
                else -> lineWithoutNewLine.length
            }
        val textFormulas = lineWithoutNewLine
            .substring(firstRealIndex, lastRealIndex)
            .split(':')
        // .map { it }
        if (textFormulas.size > 1) {
            // More than one formula, like "plural=(n==0 ? 0 : n==1 ? 1 : n==2 ? 2 : n%3==0 ? 3 : 4)"
            textFormulas
                .map {
                    val indexOfQMark = it.indexOf('?')
                    if (indexOfQMark != -1) {
                        val f = it.substring(0, indexOfQMark)
                        val result = it.substring(indexOfQMark + 1, it.length)
                            .replace(" ", "")
                            .toInt()
                        Pair(f, result)
                    } else {
                        Pair("", it.replace(" ", "").toInt())
                    }
                }.onEach {
                    formula.addFormula(it.first, it.second)
                }
        } else {
            // Only one formula, like "plural=(n != 1)" so the index if match is 1 (else it is 0)
            formula.addFormula(textFormulas.first(), 1)
        }
        return formula
    }

    private fun singleForKey(key: String, vararg args: Any?): String? {
        return localeTexts
            .map { it.first }
            .firstOrNull { it.hasTextForKey(key) }
            ?.getText(key, *args)
    }

    private fun pluralForKey(
        key: String,
        quantity: Int,
        vararg args: Any?
    ): String? {
        return localeTexts
            .map { it.second }
            .firstOrNull { it.hasTextForKey(key) }
            ?.getText(key, quantity, *args)
    }

    /**
     * Companion for quick access
     */
    companion object {
        private var CURRENT_GERO: Gero? = null
        private var FALLBACK_GERO: Gero? = null

        /**
         * First method call, initialize Gero with a specific locale !
         * For example:
         * - `setLocaleAsync(baseContext, Locale.FRENCH)`
         * - `setLocaleAsync(baseContext, Locale.getDefault())`
         * - `setLocaleAsync(baseContext, Locale.US, fallbackLocale = Locale.FRENCH)`
         * - `setLocaleAsync(baseContext, Locale.US, fallbackLocale = Locale.FRENCH, sendKeyIfNotFound = true)`
         *
         * @param context an android Context
         * @param locale the locale to use to fetch the translations
         * @param fallbackLocale an optional fallback used if the string can't be found in the locale, by default, it looks in en_US or en
         * @param sendKeyIfNotFound an optional boolean, if set to true, if Gero can't find the key, it returns the key, if false, it crashes. By default, it is false
         */
        @Synchronized
        fun setLocaleAsync(
            context: Context,
            locale: Locale,
            fallbackLocale: Locale = Locale.US,
            sendKeyIfNotFound: Boolean = false,
        ): Deferred<Unit> {
            val loadingDeferred = CompletableDeferred<Unit>()
            val shouldReload = CURRENT_GERO == null ||
                    (locale.language != CURRENT_GERO?.currentLocale?.language
                            || locale.country != CURRENT_GERO?.currentLocale?.country)
            if (!shouldReload) {
                loadingDeferred.complete(Unit)
            } else {
                CURRENT_GERO =
                    Gero(locale, hasTextLoaded = false, sendKeyIfNotFound = sendKeyIfNotFound)
                FALLBACK_GERO = Gero(
                    fallbackLocale,
                    hasTextLoaded = false,
                    sendKeyIfNotFound = sendKeyIfNotFound
                )
                CoroutineScope(Job()).launch(Dispatchers.IO) {
                    var baseLocaleIsInitialized = true
                    try {
                        CURRENT_GERO!!.loadCurrentLanguageAsync(context).await()
                    } catch (err: java.lang.Exception) {
                        baseLocaleIsInitialized = false
                    }

                    try {
                        FALLBACK_GERO!!.loadCurrentLanguageAsync(context).await()
                        loadingDeferred.complete(Unit)
                    } catch (err: java.lang.Exception) {
                        if (!baseLocaleIsInitialized) {
                            // Neither baseLocale or fallbackLocale could be initialized
                            // Looks like a crash is needed
                            loadingDeferred.completeExceptionally(
                                RuntimeException(
                                    "Cant initialize Gero",
                                    err
                                )
                            )
                        } else {
                            loadingDeferred.complete(Unit)
                        }
                    }
                }
            }
            return loadingDeferred
        }

        /**
         * Fetch an string value from a key and format it with optional args.
         *
         * @param key the key for the value to look for
         * @param args any arguments used to format the string (%d, %f, ...)
         * @return the formatted string value
         * @see SingleValues.getText
         */
        @Synchronized
        fun getText(key: String, vararg args: Any?): String {
            var locale: Locale? = null
            if (CURRENT_GERO != null && CURRENT_GERO!!.hasTextLoaded) {
                val translation = CURRENT_GERO!!.singleForKey(key, *args)
                locale = CURRENT_GERO!!.currentLocale
                if (translation != null) {
                    return translation
                }
            }

            // Check for fallback
            if (FALLBACK_GERO != null && FALLBACK_GERO!!.hasTextLoaded) {
                val translation = FALLBACK_GERO!!.singleForKey(key, *args)
                locale = CURRENT_GERO!!.currentLocale
                if (translation != null) {
                    return translation
                }
            }

            Log.w("Gero", "The key [$key] is not found for locale [$locale]")
            return if (CURRENT_GERO?.sendKeyIfNotFound == true) {
                key
            } else {
                ""
            }
        }

        /**
         * Fetch an string value from a key and format it with optional args.
         *
         * @param key the key for the value to look for
         * @param quantity the quantity used to fetch the right plural
         * @param args any arguments used to format the string (%d, %f, ...)
         * @return the formatted string value
         * @see PluralsValues.getText
         */
        @Synchronized
        fun getQuantityText(key: String, quantity: Int, vararg args: Any?): String {
            var locale: Locale? = null
            if (CURRENT_GERO != null && CURRENT_GERO!!.hasTextLoaded) {
                val translation = CURRENT_GERO!!.pluralForKey(key, quantity, *args)
                locale = CURRENT_GERO!!.currentLocale
                if (translation != null) {
                    return translation
                }
            }

            // Check for fallback
            if (FALLBACK_GERO != null && FALLBACK_GERO!!.hasTextLoaded) {
                val translation = FALLBACK_GERO!!.pluralForKey(key, quantity, *args)
                locale = CURRENT_GERO!!.currentLocale
                if (translation != null) {
                    return translation
                }
            }

            Log.w("Gero", "The key [$key] is not found for locale [$locale]")
            return if (CURRENT_GERO?.sendKeyIfNotFound == true) {
                key
            } else {
                ""
            }
        }

        /**
         * Returns the current locale of the Gero instance
         * @return the current Locale
         * @see Locale
         */
        @Synchronized
        fun getCurrentLocale(): Locale {
            return when {
                CURRENT_GERO?.hasTextLoaded == true -> {
                    CURRENT_GERO!!.currentLocale
                }
                FALLBACK_GERO?.hasTextLoaded != true -> {
                    FALLBACK_GERO!!.currentLocale
                }
                else -> {
                    throw NullPointerException("Gero is null or has no loaded text ($CURRENT_GERO, ${CURRENT_GERO?.hasTextLoaded}, $FALLBACK_GERO, ${FALLBACK_GERO?.hasTextLoaded}")
                }
            }
        }
    }
}

/**
 * Type alias used to simplify comprehension
 * Represents a po file.
 */
internal typealias GetTextFile = Pair<SingleValues, PluralsValues>