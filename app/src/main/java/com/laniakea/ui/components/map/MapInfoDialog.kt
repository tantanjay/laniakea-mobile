package com.laniakea.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.engine.LayoutMode

@Composable
fun MapInfoDialog(
    layoutMode: LayoutMode,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Understanding Your Map", 
                fontWeight = FontWeight.Bold, 
                color = Color.White,
                fontSize = 20.sp
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (layoutMode) {
                    LayoutMode.CLUSTERS -> {
                        Text(
                            "This view is the core map of your mind. It automatically groups your entries based on their underlying meaning.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        InfoRow(
                            emoji = "🧲",
                            title = "Similar Thoughts",
                            desc = "Thoughts about the same topics pull each other closer like magnets."
                        )
                        
                        InfoRow(
                            emoji = "🌌",
                            title = "Unrelated Thoughts",
                            desc = "Thoughts with different themes naturally drift apart."
                        )
                        
                        GoalBox(
                            icon = "💡",
                            title = "The Magic",
                            desc = "This isn't a random layout! It's a literal, mathematical map of how your entries connect to each other."
                        )
                    }
                    LayoutMode.GALAXY -> {
                        Text(
                            "A beautiful, stylized galaxy built entirely from your own data.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        InfoRow(
                            emoji = "🌀",
                            title = "Spiral Arms",
                            desc = "Different topics form the sweeping arms of the galaxy."
                        )
                        
                        InfoRow(
                            emoji = "⏳",
                            title = "Time",
                            desc = "Older entries tend to appear closer to the core, while newer entries occupy the outer regions."
                        )
                        
                        InfoRow(
                            emoji = "⭐",
                            title = "Importance",
                            desc = "Highly emotional or significant thoughts act as heavy stars anchoring the structure."
                        )
                        
                        GoalBox(
                            icon = "🎨",
                            title = "Data as Art",
                            desc = "While it looks like a piece of art, every position is strictly driven by your actual entries."
                        )
                    }
                    LayoutMode.TIME_WARP -> {
                        Text(
                            "A flowing, twisted timeline showing how your thoughts evolve over time.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        InfoRow(
                            emoji = "➡️",
                            title = "The Timeline",
                            desc = "Your entries are arranged chronologically from past to present."
                        )
                        
                        InfoRow(
                            emoji = "🛤️",
                            title = "Parallel Lanes",
                            desc = "Similar topics travel together in continuous, parallel streams."
                        )
                        
                        GoalBox(
                            icon = "🌊",
                            title = "The Flow",
                            desc = "This view makes it easier to see how themes persist, disappear, and reappear throughout your journal. Use this mode to follow the life cycle of ideas across time."
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("🏷️", fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
                    Column {
                        Text(
                            "Why do some nodes have labels?",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            "You might notice some 'supernova' nodes automatically display their labels. These aren't always your deepest thoughts—often, they are routine entries that mathematically act as massive central hubs because they connect to so many other thoughts across time!",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it!", color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1E1E2E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun InfoRow(emoji: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun GoalBox(icon: String, title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF64FFDA).copy(alpha = 0.15f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, color = Color(0xFF64FFDA), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            desc,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }
}
