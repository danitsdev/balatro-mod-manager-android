package com.balatromodmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.balatromodmanager.openUrl
import com.balatromodmanager.ui.theme.BmmColor

private data class LicenseCredit(
    val name: String,
    val license: String,
    val description: String,
    val url: String,
)

private val licenseCredits = listOf(
    LicenseCredit(
        name = "Balatro Mod Manager",
        license = "GPL-3.0",
        description = "Original desktop manager and the main reference for behavior and visual direction.",
        url = "https://github.com/skyline69/balatro-mod-manager",
    ),
    LicenseCredit(
        name = "Thunderstore Balatro API",
        license = "Online service",
        description = "Provides the community catalog, package metadata and mod downloads.",
        url = "https://thunderstore.io/c/balatro/",
    ),
    LicenseCredit(
        name = "m6x11plus",
        license = "Free with attribution",
        description = "Pixel typeface by Daniel Linssen used throughout the interface.",
        url = "https://managore.itch.io/m6x11",
    ),
    LicenseCredit(
        name = "Coil",
        license = "Apache-2.0",
        description = "Image loading and caching for mod artwork.",
        url = "https://github.com/coil-kt/coil",
    ),
    LicenseCredit(
        name = "Multiplatform Markdown Renderer",
        license = "Apache-2.0",
        description = "Native rendering for mod descriptions.",
        url = "https://github.com/mikepenz/multiplatform-markdown-renderer",
    ),
    LicenseCredit(
        name = "AndroidX & Jetpack Compose",
        license = "Apache-2.0",
        description = "Android application, storage and interface libraries.",
        url = "https://github.com/androidx/androidx",
    ),
    LicenseCredit(
        name = "Kotlin & kotlinx",
        license = "Apache-2.0",
        description = "Programming language, coroutines and serialization libraries.",
        url = "https://github.com/JetBrains/kotlin",
    ),
)

@Composable
internal fun LicensesScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = BmmColor.Cream,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("Licenses & credits", style = MaterialTheme.typography.headlineSmall, color = BmmColor.Gold)
            }
        }

        item {
            PixelPanel(borderColor = BmmColor.Gold) {
                Text("Balatro Mod Manager", style = MaterialTheme.typography.titleLarge, color = BmmColor.Gold)
                Text(
                    "This native Android project is open source under GPL-3.0.",
                    color = BmmColor.Cream,
                )
                CreditLink(
                    label = "Source code & license",
                    onClick = { context.openUrl("https://github.com/danitsdev/balatro-mod-manager-android") },
                )
            }
        }

        items(licenseCredits) { credit ->
            PixelPanel(
                modifier = Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    context.openUrl(credit.url)
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(credit.name, style = MaterialTheme.typography.titleMedium, color = BmmColor.Gold)
                        Text(credit.license, style = MaterialTheme.typography.labelMedium, color = BmmColor.Accent)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open ${credit.name}",
                        tint = BmmColor.MutedCream,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(credit.description, color = BmmColor.Cream, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CreditLink(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = BmmColor.Accent, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = BmmColor.Accent, modifier = Modifier.size(18.dp))
    }
}
