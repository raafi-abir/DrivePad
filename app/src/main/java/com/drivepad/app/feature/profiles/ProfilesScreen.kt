package com.drivepad.app.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepad.app.core.designsystem.CockpitBorder
import com.drivepad.app.core.designsystem.CockpitSurfaceElevated
import com.drivepad.app.core.designsystem.DeepGraphiteBackground
import com.drivepad.app.core.designsystem.Dimens
import com.drivepad.app.core.designsystem.NeonGreenPrimary
import com.drivepad.app.core.designsystem.TextMuted
import com.drivepad.app.core.designsystem.TextPrimary
import com.drivepad.app.core.designsystem.TextSecondary
import com.drivepad.app.domain.model.ControllerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles = ControllerProfile.DefaultProfiles

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepGraphiteBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Vehicle Profiles",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("profiles_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepGraphiteBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Dimens.spacing16),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacing16)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Dimens.shapeMedium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CockpitBorder))
                ) {
                    Row(
                        modifier = Modifier.padding(Dimens.spacing16),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NeonGreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacing12))
                        Text(
                            text = "Preset profiles store calibrated steering ratios, deadzones, and transmission defaults tailored to specific simulators.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            items(profiles) { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Dimens.shapeMedium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CockpitBorder))
                ) {
                    Column(modifier = Modifier.padding(Dimens.spacing16)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = NeonGreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(Dimens.spacing8))
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (profile.isDefault) {
                                Surface(
                                    shape = Dimens.shapeSmall,
                                    color = NeonGreenPrimary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "PRIMARY SIM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonGreenPrimary,
                                        modifier = Modifier.padding(horizontal = Dimens.spacing6, vertical = Dimens.spacing2)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(Dimens.spacing8))
                        Text(
                            text = profile.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(Dimens.spacing12))
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacing16)) {
                            Text(
                                text = "Sensitivity: ${profile.config.steeringSensitivity}x",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                            Text(
                                text = "Gearbox: ${profile.config.defaultTransmissionMode.displayName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
