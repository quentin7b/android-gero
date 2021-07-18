package com.github.quentin7b.android.gero

import android.content.res.Resources

/**
 * Manage the plurals
 */
internal class PluralsValues {

    /**
     * Contains all the translations that are plurals.
     * HashMap where key is the msgid and the values are another HashMap with key the
     * index and value the msgstr.
     *
     * For example:
     * msgid_plural "QUANTITY"
     * msgstr[0] "Quantity"
     * msgstr[1] "Quantities"
     *
     * plurals["QUANTITY"][0] = "Quantity"
     * plurals["QUANTITY"][1] = "Quantities"
     */
    private val plurals = HashMap<String, HashMap<Int, String>>()

    /**
     * Contains the formula to evaluate which plural to use.
     */
    internal var pluralsFormula: PluralsFormula = PluralsFormula()

    /**
     * Clear the plurals values
     */
    internal fun clear() {
        plurals.onEach { it.value.clear() }.clear()
    }

    /**
     * Add a plural value to the list of translations
     *
     * @param key the key used by the text
     * @param quantityIndex the index of the plural
     * @param value the value to set for this key
     */
    internal fun addValue(key: String, quantityIndex: Int, value: String) {
        if (!plurals.containsKey(key)) {
            plurals[key] = HashMap()
        }
        plurals[key]!![quantityIndex] = value
    }

    /**
     * Fetch an string value from a key and format it with optional args.
     *
     * @param key the key for the value to look for
     * @param quantity the quantity used to fetch the right plural
     * @param args any arguments used to format the string (%d, %f, ...)
     * @return the formatted string value
     *
     * @throws Resources.NotFoundException if the string value is not found
     */
    internal fun getText(key: String, quantity: Int, vararg args: Any?): String {
        if (plurals.containsKey(key)) {
            val textValues = plurals[key]!!
            val quantityKey = pluralsFormula.evaluate(quantity)
            if (textValues.containsKey(quantityKey)) {
                return String.format(textValues[quantityKey]!!, *args)
            } else {
                throw Resources.NotFoundException("Cant find plural quantity with key $key (quantity : $quantityKey)")
            }
        } else {
            throw Resources.NotFoundException("Cant find plural with key $key in current file")
        }
    }

    /**
     * Check if this Single has a value for a specific Key
     * @param key the key to check for a value
     * @return true if there is a matching key, false otherwise
     */
    internal fun hasTextForKey(key: String): Boolean = plurals.containsKey(key)

}