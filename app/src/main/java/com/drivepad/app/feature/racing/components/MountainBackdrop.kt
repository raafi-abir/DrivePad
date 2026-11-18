package com.drivepad.app.feature.racing.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Pure obsidian-black racing cockpit backdrop with subtle carbon radial depth
 * and the signature crimson diagonal corner light slashes (top-left and bottom-right).
 */
@Composable
fun MountainBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val baseBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF101216),
                        Color(0xFF07080A),
                        Color(0xFF040507)
                    ),
                    center = Offset(w * 0.45f, h * 0.45f),
                    radius = maxOf(w, h).coerceAtLeast(1f) * 0.78f
                )
                val topLeftSlash = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE01414).copy(alpha = 0.85f),
                        Color(0xFFB00E0E).copy(alpha = 0.40f),
                        Color.Transparent
                    ),
                    start = Offset(w * 0.03f, -h * 0.04f),
                    end = Offset(-w * 0.04f, h * 0.30f)
                )
                val topLeftGlow = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC81010).copy(alpha = 0.24f),
                        Color.Transparent
                    ),
                    center = Offset(0f, 0f),
                    radius = (w * 0.20f).coerceAtLeast(1f)
                )
                val bottomRightSlash = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFB00E0E).copy(alpha = 0.42f),
                        Color(0xFFE01414).copy(alpha = 0.88f)
                    ),
                    start = Offset(w * 0.90f, h * 1.04f),
                    end = Offset(w * 1.03f, h * 0.68f)
                )
                val bottomRightGlow = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC81010).copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(w, h),
                    radius = (w * 0.18f).coerceAtLeast(1f)
                )
                onDrawBehind {
                    if (w <= 0f || h <= 0f) return@onDrawBehind
                    drawRect(brush = baseBrush)
                    drawLine(
                        brush = topLeftSlash,
                        start = Offset(w * 0.038f, -h * 0.05f),
                        end = Offset(-w * 0.025f, h * 0.34f),
                        strokeWidth = w * 0.045f
                    )
                    drawCircle(brush = topLeftGlow, radius = w * 0.20f, center = Offset(0f, 0f))
                    drawLine(
                        brush = bottomRightSlash,
                        start = Offset(w * 0.90f, h * 1.06f),
                        end = Offset(w * 1.03f, h * 0.66f),
                        strokeWidth = w * 0.045f
                    )
                    drawCircle(brush = bottomRightGlow, radius = w * 0.18f, center = Offset(w, h))
                }
            }
    )
}
