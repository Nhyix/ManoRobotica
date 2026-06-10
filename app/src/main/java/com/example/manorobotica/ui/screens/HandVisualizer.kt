package com.example.manorobotica.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.manorobotica.ui.theme.CardBackgroundGlass
import com.example.manorobotica.ui.theme.CardBorderGlass
import com.example.manorobotica.ui.theme.NeonCyan
import com.example.manorobotica.ui.theme.NeonPurple

@Composable
fun HandVisualizer(
    thumbAngle: Float,
    indexAngle: Float,
    middleAngle: Float,
    ringAngle: Float,
    pinkyAngle: Float,
    modifier: Modifier = Modifier
) {
    // Animaciones suaves para las transiciones de ángulo
    val animatedThumb = animateFloatAsState(targetValue = thumbAngle, animationSpec = tween(150), label = "thumb")
    val animatedIndex = animateFloatAsState(targetValue = indexAngle, animationSpec = tween(150), label = "index")
    val animatedMiddle = animateFloatAsState(targetValue = middleAngle, animationSpec = tween(150), label = "middle")
    val animatedRing = animateFloatAsState(targetValue = ringAngle, animationSpec = tween(150), label = "ring")
    val animatedPinky = animateFloatAsState(targetValue = pinkyAngle, animationSpec = tween(150), label = "pinky")

    Box(
        modifier = modifier
            .aspectRatio(1.1f)
            .background(CardBackgroundGlass, shape = RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Coordenadas base de la muñeca / centro de la palma
            val wristX = width * 0.5f
            val wristY = height * 0.95f
            val palmCenterX = width * 0.5f
            val palmCenterY = height * 0.65f

            // 1. Dibujar contorno de la palma (estilo holográfico/neon)
            val palmPath = Path().apply {
                moveTo(width * 0.3f, height * 0.75f) // Lateral meñique bajo
                lineTo(width * 0.25f, height * 0.55f) // Lateral meñique alto
                lineTo(width * 0.38f, height * 0.48f) // Nudo índice/medio
                lineTo(width * 0.62f, height * 0.48f) // Nudo medio/anular
                lineTo(width * 0.75f, height * 0.55f) // Lateral pulgar alto
                lineTo(width * 0.78f, height * 0.7f) // Lateral pulgar bajo
                lineTo(width * 0.65f, height * 0.85f) // Muñeca derecha
                lineTo(width * 0.35f, height * 0.85f) // Muñeca izquierda
                close()
            }

            drawPath(
                path = palmPath,
                brush = Brush.radialGradient(
                    colors = listOf(NeonPurple.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(palmCenterX, palmCenterY),
                    radius = width * 0.4f
                )
            )

            drawPath(
                path = palmPath,
                color = CardBorderGlass.copy(alpha = 0.6f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Puntos de partida de los nudillos para cada dedo (X, Y)
            val knuckles = listOf(
                Offset(width * 0.72f, height * 0.63f), // Pulgar (Thumb)
                Offset(width * 0.35f, height * 0.48f), // Índice (Index)
                Offset(width * 0.47f, height * 0.46f), // Medio (Middle)
                Offset(width * 0.58f, height * 0.47f), // Anular (Ring)
                Offset(width * 0.68f, height * 0.50f)  // Meñique (Pinky)
            )

            // Ángulos de flexión representados: 0° = Totalmente extendido (hacia arriba), 180° = Totalmente encogido (hacia la palma)
            val fingerAngles = listOf(
                animatedThumb.value,
                animatedIndex.value,
                animatedMiddle.value,
                animatedRing.value,
                animatedPinky.value
            )

            // Longitudes relativas de cada segmento del dedo (tres falanges por dedo)
            val fingerSegmentLengths = listOf(
                listOf(0.08f, 0.08f),         // Pulgar (2 segmentos principales)
                listOf(0.10f, 0.09f, 0.07f),   // Índice (3 segmentos)
                listOf(0.11f, 0.10f, 0.08f),   // Medio (3 segmentos)
                listOf(0.10f, 0.09f, 0.07f),   // Anular (3 segmentos)
                listOf(0.08f, 0.07f, 0.06f)    // Meñique (3 segmentos)
            )

            // Direcciones iniciales base (vector unitario) para cada dedo al estar extendido (0 grados)
            val baseDirections = listOf(
                Offset(0.7f, -0.4f),  // Pulgar (apunta diagonal-derecha)
                Offset(-0.1f, -1.0f), // Índice (casi vertical arriba)
                Offset(0.0f, -1.0f),  // Medio (vertical arriba)
                Offset(0.1f, -1.0f),  // Anular (casi vertical arriba)
                Offset(0.3f, -0.9f)   // Meñique (diagonal-izquierda)
            )

            // 2. Dibujar dedos con nodos dinámicos
            for (i in knuckles.indices) {
                val knuckle = knuckles[i]
                val baseDir = baseDirections[i]
                val anglePercent = fingerAngles[i] / 180f // 0.0 (abierto) a 1.0 (cerrado)
                val segments = fingerSegmentLengths[i]

                var currentPoint = knuckle
                // El ángulo de rotación se acumula en cada articulación para simular flexión natural curvada
                var currentDx = baseDir.x
                var currentDy = baseDir.y

                // Dibujar nudillo de inicio
                drawCircle(
                    color = NeonCyan,
                    radius = 6.dp.toPx(),
                    center = knuckle
                )

                for (j in segments.indices) {
                    val segmentLength = segments[j] * height
                    
                    // Calcular flexión: a más ángulo, más se desvía de la vertical hacia el centro de la palma
                    // El pulgar rota diferente a los otros dedos
                    val rotationAngle = if (i == 0) {
                        // Pulgar se flexiona hacia la izquierda de la palma
                        anglePercent * -1.4f
                    } else {
                        // Los otros dedos se curvan hacia el centro de la palma (abajo)
                        anglePercent * 1.5f * (if (i < 3) 1f else -0.5f)
                    }

                    // Rotación de vector base
                    val cosA = kotlin.math.cos(rotationAngle)
                    val sinA = kotlin.math.sin(rotationAngle)
                    
                    // Modificar la dirección efectiva según la flexión
                    val nextDx = (baseDir.x * cosA - baseDir.y * sinA).toFloat()
                    val nextDy = (baseDir.x * sinA + baseDir.y * cosA).toFloat()

                    // El factor de encogimiento visual de la falange al flexionarse hacia adentro de la pantalla
                    // Al cerrarse el dedo (180°), la proyección 2D se acorta drásticamente simulando 3D
                    val perspectiveFactor = 1.0f - (anglePercent * 0.4f)
                    
                    val nextPoint = Offset(
                        currentPoint.x + nextDx * segmentLength * perspectiveFactor,
                        currentPoint.y + nextDy * segmentLength * perspectiveFactor
                    )

                    // Dibujar hueso / falange
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(NeonCyan, NeonPurple)
                        ),
                        start = currentPoint,
                        end = nextPoint,
                        strokeWidth = (6 - j).dp.toPx(), // Se hace más delgado en las puntas
                        cap = StrokeCap.Round
                    )

                    // Dibujar articulación (nodo)
                    drawCircle(
                        color = if (j == segments.size - 1) NeonPurple else NeonCyan.copy(alpha = 0.8f),
                        radius = (5 - j).dp.toPx(),
                        center = nextPoint
                    )

                    currentPoint = nextPoint
                }
            }

            // 3. Conexiones centrales de la muñeca
            drawLine(
                color = CardBorderGlass.copy(alpha = 0.3f),
                start = Offset(width * 0.35f, height * 0.85f),
                end = Offset(width * 0.5f, height * 0.95f),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = CardBorderGlass.copy(alpha = 0.3f),
                start = Offset(width * 0.65f, height * 0.85f),
                end = Offset(width * 0.5f, height * 0.95f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
