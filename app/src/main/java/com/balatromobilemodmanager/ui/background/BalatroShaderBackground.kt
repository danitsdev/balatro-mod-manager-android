package com.balatromobilemodmanager.ui.background

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

@Composable
fun BalatroShaderBackground(enabled: Boolean, darkMode: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            StaticShaderFallback(darkMode, Modifier.fillMaxSize())
        } else {
            AnimatedShaderBackground(darkMode, Modifier.fillMaxSize())
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AnimatedShaderBackground(darkMode: Boolean, modifier: Modifier) {
    val shader = remember { RuntimeShader(BalatroAgsl) }
    val brush = remember(shader) { ShaderBrush(shader) }
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var startedAt = 0L
        var lastFrame = 0L
        while (isActive) {
            withFrameNanos { frameTime ->
                if (startedAt == 0L) startedAt = frameTime
                if (frameTime - lastFrame >= FrameIntervalNanos) {
                    lastFrame = frameTime
                    elapsedSeconds = (frameTime - startedAt) / 1_000_000_000f
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", elapsedSeconds)
        if (darkMode) {
            shader.setFloatUniform("colour_1", 20f / 255f, 15f / 255f, 90f / 255f, 1f)
            shader.setFloatUniform("colour_2", 40f / 255f, 5f / 255f, 70f / 255f, 1f)
            shader.setFloatUniform("colour_3", 3f / 255f, 3f / 255f, 15f / 255f, 1f)
        } else {
            shader.setFloatUniform("colour_1", 0.85f, 0.2f, 0.2f, 1f)
            shader.setFloatUniform("colour_2", 0f, 156f / 255f, 1f, 1f)
            shader.setFloatUniform("colour_3", 0f, 0f, 0f, 1f)
        }
        drawRect(brush)
    }
}

@Composable
private fun StaticShaderFallback(darkMode: Boolean, modifier: Modifier) {
    val bgColor = if (darkMode) Color(0xFF0B1220) else Color(0xFFA53535)
    val dotColor = if (darkMode) Color(0xFF17253D) else Color(0xFFFF9999)
    Canvas(modifier = modifier.background(bgColor)) {
        val sizePx = 18.dp.toPx()
        val halfSizePx = sizePx / 2f
        val radiusPx = 0.75.dp.toPx()

        val cols = (size.width / sizePx).toInt() + 2
        val rows = (size.height / sizePx).toInt() + 2

        for (col in -1..cols) {
            for (row in -1..rows) {
                val cx1 = col * sizePx
                val cy1 = row * sizePx
                drawCircle(
                    color = dotColor,
                    radius = radiusPx,
                    center = androidx.compose.ui.geometry.Offset(cx1, cy1)
                )

                val cx2 = col * sizePx + halfSizePx
                val cy2 = row * sizePx + halfSizePx
                drawCircle(
                    color = dotColor,
                    radius = radiusPx,
                    center = androidx.compose.ui.geometry.Offset(cx2, cy2)
                )
            }
        }
    }
}

private const val FrameIntervalNanos = 33_333_333L

// GPL-compatible port of the desktop BMM shader, translated from GLSL to AGSL.
private const val BalatroAgsl = """
uniform float iTime;
uniform float2 iResolution;
uniform float4 colour_1;
uniform float4 colour_2;
uniform float4 colour_3;

half4 main(float2 fragCoord) {
    float pixel_size = length(iResolution) / 700.0;
    float2 uv = (floor(fragCoord / pixel_size) * pixel_size - 0.5 * iResolution) / length(iResolution);
    float uv_len = length(uv);
    float speed = iTime * 0.05 + 302.2;
    float angle = atan(uv.y, uv.x) + speed - 10.0 * (0.7 * uv_len + 0.3);
    float2 mid = (iResolution / length(iResolution)) / 2.0;
    uv = float2(uv_len * cos(angle) + mid.x, uv_len * sin(angle) + mid.y) - mid;
    uv *= 30.0;
    speed = iTime;
    float2 uv2 = float2(uv.x + uv.y);

    for (int i = 0; i < 5; i++) {
        uv2 += uv + cos(length(uv));
        uv += 0.5 * float2(
            cos(5.1123314 + 0.353 * uv2.y + speed * 0.131121),
            sin(uv2.x - 0.113 * speed)
        );
        uv -= cos(uv.x + uv.y) - sin(uv.x * 0.711 - uv.y);
    }

    float contrast_mod = 1.925;
    float paint = clamp(length(uv) * 0.035 * contrast_mod, 0.0, 2.0);
    float c1p = max(0.0, 1.0 - contrast_mod * abs(1.0 - paint));
    float c2p = max(0.0, 1.0 - contrast_mod * abs(paint));
    float c3p = 1.0 - min(1.0, c1p + c2p);
    float4 result = 0.2 * colour_1 + 0.8 * (
        colour_1 * c1p + colour_2 * c2p + float4(c3p * colour_3.rgb, c3p * colour_1.a)
    );
    result += 0.3 * max(c1p * 5.0 - 4.0, 0.0);
    result += 0.4 * max(c2p * 5.0 - 4.0, 0.0);
    return half4(result);
}
"""
