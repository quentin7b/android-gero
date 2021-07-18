package com.github.quentin7b.android.gero

import android.util.Log
import org.mozilla.javascript.Context

/**
 * This class manage the plurals formula for the plurals.
 * It means "how do we know which plural to use base on the quantity"
 */
internal class PluralsFormula {

    /**
     * This contains the list of formulas to execute and the index to use if the formula
     * is validated
     */
    private val formulas: MutableList<Pair<String, Int>> = mutableListOf()

    /**
     * Add a formula and the index linked to this formula
     */
    fun addFormula(formula: String, index: Int) {
        formulas.add(Pair(formula, index))
    }

    /**
     * Clear the formulas
     */
    fun clear() {
        formulas.clear()
    }

    /**
     * From a specific quantity, returns the index of the plurals to use.
     * @param quantity the quantity we have as input
     * @return the index to use in the plurals
     */
    fun evaluate(quantity: Number): Int {
        val rhino = Context.enter()
        rhino.optimizationLevel = -1
        val scope = rhino.initStandardObjects()

        var result: Int? = null
        var lastChanceResult = 0
        try {
            for (form in formulas) {
                val formula = form.first
                val formulaResult = form.second
                if (formula.isNotBlank()) {
                    val replacedValue = formula.replace("n", quantity.toString())
                    Log.d("Gero", "PluralsFormula - Evaluating $replacedValue")
                    val evaluatedResult =
                        rhino.evaluateString(scope, replacedValue, "JavaScript", 1, null)
                            .toString()
                    Log.d("Gero", "PluralsFormula - Result: $evaluatedResult")
                    if (evaluatedResult.toBoolean()) {
                        result = formulaResult
                        break
                    }
                } else {
                    lastChanceResult = formulaResult
                }
            }

            if (result == null) {
                result = lastChanceResult
            }
            Log.d("Gero", "PluralsFormula - For $quantity, result is $result")
            return result
        } catch (err: Exception) {
            return 0
        } finally {
            Context.exit()
        }
    }
}