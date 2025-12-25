package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import me.rerere.rikkahub.RouteActivity

/**
 * Handles shortcut and widget intents.
 * Routes assistant shortcuts to the main app with the assistant ID.
 */
class ShortcutHandlerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle assistant shortcut
        val data = intent?.data
        if (data?.scheme == "lastchat" && data.host == "assistant") {
            val assistantId = data.pathSegments.firstOrNull()
            if (!assistantId.isNullOrBlank()) {
                val routeIntent = Intent(this, RouteActivity::class.java).apply {
                    putExtra("assistantId", assistantId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(routeIntent)
            }
        }
        
        // Always finish this activity
        finish()
    }
}
