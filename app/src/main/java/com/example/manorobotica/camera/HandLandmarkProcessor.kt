package com.example.manorobotica.camera

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.*

object HandLandmarkProcessor {

    private fun Float.coerceAngle(): Float = coerceIn(0f, 180f)

    fun computeAngles(result: HandLandmarkerResult): FloatArray? {
        if (result.landmarks().isEmpty()) return null
        val lms = result.landmarks()[0]
        if (lms.size < 21) return null

        val x = { i: Int -> lms[i].x() }
        val y = { i: Int -> lms[i].y() }

        val thumbDir = angleAt(x(2), y(2), x(3), y(3), x(4), y(4))
        val thumbClose = thumbCloseBonus(x(4), y(4), x(5), y(5), x(2), y(2))

        return floatArrayOf(
            ((180f - thumbDir) + thumbClose).coerceAngle(),
            fingerCurl(x(5), y(5), x(6), y(6), x(7), y(7)),
            fingerCurl(x(9), y(9), x(10), y(10), x(11), y(11)),
            fingerCurl(x(13), y(13), x(14), y(14), x(15), y(15)),
            fingerCurl(x(17), y(17), x(18), y(18), x(19), y(19))
        )
    }

    private fun angleAt(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
        val v1x = ax - bx
        val v1y = ay - by
        val v2x = cx - bx
        val v2y = cy - by

        val dot = v1x * v2x + v1y * v2y
        val m1 = sqrt(v1x * v1x + v1y * v1y)
        val m2 = sqrt(v2x * v2x + v2y * v2y)

        if (m1 == 0f || m2 == 0f) return 0f
        val cos = (dot / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos.toDouble())).toFloat()
    }

    private fun fingerCurl(mcpX: Float, mcpY: Float, pipX: Float, pipY: Float, dipX: Float, dipY: Float): Float {
        return (180f - angleAt(mcpX, mcpY, pipX, pipY, dipX, dipY)).coerceAngle()
    }

    private fun thumbCloseBonus(tipX: Float, tipY: Float, idxMcpX: Float, idxMcpY: Float, thumbMcpX: Float, thumbMcpY: Float): Float {
        val dist = hypot(tipX - idxMcpX, tipY - idxMcpY)
        val palm = hypot(thumbMcpX - idxMcpX, thumbMcpY - idxMcpY)
        return if (palm > 0 && dist < palm * 0.5f) 30f else 0f
    }
}
