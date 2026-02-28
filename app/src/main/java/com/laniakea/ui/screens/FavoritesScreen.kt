package com.laniakea.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FavoritesScreen(padding: PaddingValues) {
    // Dummy data for the favorites list
    val favoriteEntries = listOf(
        "I had a great day at the park today.",
        "The sunset was absolutely beautiful.",
        "Feeling very productive with my new project.",
        "Had a wonderful dinner with friends."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        Text(
            text = "Your Favorites",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (favoriteEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No favorites yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(favoriteEntries) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(entry) },
                            supportingContent = { Text("Saved on Jan 24, 2025") },
                            leadingContent = {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red)
                            }
                        )
                    }
                }
            }
        }
    }
}