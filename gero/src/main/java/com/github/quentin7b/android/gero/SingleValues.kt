package com.github.quentin7b.android.gero

import android.content.res.Resources
import android.util.Log

internal class SingleValues {
    /**
     * Contains all the translations that are not plurals.
     * HashMap where key is the msgid and the value the msgstr.
     *
     * For example:
     * msgid "this is a test"
     * msgstr "this is the value of the test"
     *
     * singles["this is a test"] = "this is the value of the test"
     */
    private val singles = HashMap<String, String>()

    /**
     * Clear the single values
     */
    internal fun clear() {
        singles.clear()
    }

    /**
     * Add a value to the list of translations
     * > Note, it will replace an existing key if already set
     *
     * @param key the key used by the text
     * @param value the value to set for this key
     */
    internal fun setValue(key: String, value: String) {
        if (singles.containsKey(key)) {
            Log.d(
                "Gero - SingleValues",
                "Be aware we are going to override the value for $key with $value"
            )
        }
        singles[key] = value
    }

    /**
     * Fetch an string value from a key and format it with optional args.
     *
     * @param key the key for the value to look for
     * @param args any arguments used to format the string (%d, %f, ...)
     * @return the formatted string value
     *
     * @throws Resources.NotFoundException if the string value is not found
     */
    internal fun getText(key: String, vararg args: Any?): String {
        if (singles.containsKey(key)) {
            return String.format(singles[key]!!, *args)
        } else {
            throw Resources.NotFoundException("Cant find string with key $key in current files")
        }
    }

    /**
     * Check if this Single has a value for a specific Key
     * @param key the key to check for a value
     * @return true if there is a matching key, false otherwise
     */
    internal fun hasTextForKey(key: String): Boolean = singles.containsKey(key)

}