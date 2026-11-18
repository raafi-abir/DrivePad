package com.drivepad.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object Dimens {
    // 8.dp baseline grid spacing
    val spacing2 = 2.dp
    val spacing4 = 4.dp
    val spacing6 = 6.dp
    val spacing8 = 8.dp
    val spacing12 = 12.dp
    val spacing16 = 16.dp
    val spacing20 = 20.dp
    val spacing24 = 24.dp
    val spacing32 = 32.dp
    val spacing40 = 40.dp
    val spacing48 = 48.dp

    // Accessibility standard touch target
    val minTouchTarget = 48.dp

    // Corner Radii
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
    val radiusCard = 20.dp
    val radiusPill = 999.dp

    // Rounded Corner Shapes
    val shapeSmall = RoundedCornerShape(radiusSmall)
    val shapeMedium = RoundedCornerShape(radiusMedium)
    val shapeLarge = RoundedCornerShape(radiusLarge)
    val shapeCard = RoundedCornerShape(radiusCard)
    val shapePill = RoundedCornerShape(radiusPill)

    // Elevation & Borders
    val elevationSubtle = 2.dp
    val elevationMedium = 6.dp
    val elevationHigh = 12.dp
    val borderWidthThin = 1.dp
    val borderWidthMedium = 2.dp
    val borderWidthThick = 3.dp

    // Layout Constraints
    val maxContentWidthTablet = 720.dp
}
