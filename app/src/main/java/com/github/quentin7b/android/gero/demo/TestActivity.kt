package com.github.quentin7b.android.gero.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.github.quentin7b.android.gero.Gero
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlobalScope.launch {
            Gero.setLocaleAsync(baseContext, Locale.US).await()
            startActivity(Intent(baseContext, TestActivity::class.java))
            finish()
        }
    }
}

class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Look for a text with the key "BACK"
        show(Gero.getText("BACK"))
        // --> "Previous"

        // Look for a text with the key "YOU TAPPED TIMES" and format it with 5
        show(Gero.getText("YOU TAPPED TIMES", 5))
        // --> Counter: 5

        // Look for a plural text with the key "QUANTITY" and use the plural matching 10 in quantity
        show(Gero.getQuantityText("QUANTITY", 10))
        // --> items

        // Look for a plural text with the key "YOU HAVE QUANTITY"
        // Use the plural matching 0 in quantity
        show(Gero.getQuantityText("YOU HAVE QUANTITY", 0))
        // --> You have no bag

        // Look for a plural text with the key "QUANTITY"
        // Use the plural matching 2 in quantity
        // Format the result with 2 arguments 2 and 120
        show(Gero.getQuantityText("YOU HAVE QUANTITY", 2, 2, 120))
        // --> You have a pair of bag with 120 items inside

        // Look for a plural text with the key "QUANTITY"
        // Use the plural matching 3 in quantity
        // Format the result with 2 arguments 3 and 95
        show(Gero.getQuantityText("YOU HAVE QUANTITY", 3, 3, 95))
        // --> You have 3 of bag with 95 items inside

    }

    fun show(line: String) {
        Log.d("Gero", "Test - $line")
    }
}