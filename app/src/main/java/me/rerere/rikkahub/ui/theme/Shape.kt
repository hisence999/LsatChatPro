package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive Shape System
 * 
 * Consistent shape tokens for visual rhythm and hierarchy.
 * Shapes guide users' attention and create visual groupings.
 */
object AppShapes {
    // Large containers - cards, sheets, dialogs
    val CardLarge = RoundedCornerShape(28.dp)
    val CardMedium = RoundedCornerShape(24.dp)
    val CardSmall = RoundedCornerShape(16.dp)
    
    // Buttons and interactive elements
    val ButtonPill = RoundedCornerShape(50)           // Fully rounded pills
    val ButtonRounded = RoundedCornerShape(20.dp)     // Softer buttons
    val ButtonSquared = RoundedCornerShape(12.dp)     // Compact buttons
    
    // Input fields
    val InputField = RoundedCornerShape(16.dp)
    val SearchField = RoundedCornerShape(50)          // Search bars are pills
    
    // Chips and tags
    val Chip = RoundedCornerShape(12.dp)
    val Tag = RoundedCornerShape(50)                  // Tags are pill-shaped
    
    // Dialogs and sheets
    val Dialog = RoundedCornerShape(28.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    
    // Small elements
    val Avatar = RoundedCornerShape(50)               // Circular avatars
    val IconButton = RoundedCornerShape(50)           // Circular icon buttons
    val Indicator = RoundedCornerShape(8.dp)
    
    // List items
    val ListItem = RoundedCornerShape(16.dp)
    val ListItemFirst = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val ListItemLast = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    
    // Message bubbles
    val MessageOutgoing = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 6.dp
    )
    val MessageIncoming = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 6.dp,
        bottomEnd = 20.dp
    )
}

// Material 3 default shapes override
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
