/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.cobblemon.mod.common.client.render.pokemon

import net.minecraft.util.ARGB
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class AlphaEyePoint(val name: String, val localPosition: Vec3, val forward: Vec3)

class AlphaEyeVertices internal constructor(val positions: FloatArray, val colors: IntArray) {
    val size: Int get() = colors.size
}

data class AlphaEyeRenderState(val bloom: AlphaEyeVertices, val trail: AlphaEyeVertices)

/** The original concentric bloom and tapered 400ms trail, extracted without rendering calls. */
object AlphaEyeGeometry {
    const val TRAIL_DURATION_MS = 400L
    const val MAX_TRAIL_SAMPLES = 128
    val EMPTY = AlphaEyeRenderState(AlphaEyeVertices(FloatArray(0), IntArray(0)), AlphaEyeVertices(FloatArray(0), IntArray(0)))

    fun extract(
        eyes: List<AlphaEyePoint>, entityPosition: Vec3, cameraPosition: Vec3, cameraUp: Vec3,
        sizeScale: Float, now: Long, history: MutableMap<String, ArrayDeque<Pair<Vec3, Long>>>
    ): AlphaEyeRenderState {
        history.keys.retainAll(eyes.map { it.name }.toSet())
        if (eyes.isEmpty() || sizeScale <= 0F || !sizeScale.isFinite()) {
            history.clear()
            return EMPTY
        }
        val bloom = Vertices()
        val trail = Vertices()
        for (eye in eyes) {
            val worldPosition = entityPosition.add(eye.localPosition)
            val positions = history.getOrPut(eye.name) { ArrayDeque() }
            val last = positions.lastOrNull()
            // Avoid ribbons across teleports or a backwards wall-clock correction.
            if (last != null && (last.first.distanceToSqr(worldPosition) > 4096.0 || now < last.second)) positions.clear()
            positions.addLast(worldPosition to now)
            while (positions.isNotEmpty() && (now - positions.first().second > TRAIL_DURATION_MS || positions.size > MAX_TRAIL_SAMPLES)) positions.removeFirst()
            buildBloom(eye, worldPosition, cameraPosition, cameraUp, sizeScale, bloom)
            buildTrail(positions, entityPosition, cameraPosition, sizeScale, trail)
        }
        return AlphaEyeRenderState(bloom.finish(), trail.finish())
    }

    private fun buildBloom(eye: AlphaEyePoint, worldPosition: Vec3, cameraPosition: Vec3, up: Vec3, scale: Float, output: Vertices) {
        val toCamera = cameraPosition.subtract(worldPosition).normalize()
        val perpendicular = up.cross(toCamera).normalize()
        val offAngle = (eye.forward.dot(toCamera) + 0.5).coerceIn(0.0, 1.0)
        val radius = 0.35 * offAngle * offAngle * scale
        if (radius < 1E-8) return
        val center = eye.localPosition.add(toCamera.scale(0.1 * offAngle))
        val rings = 5
        val segments = 12
        for (ring in 0 until rings) {
            val t0 = ring.toFloat() / rings
            val t1 = (ring + 1).toFloat() / rings
            val alpha0 = (1F - t0) * (1F - t0)
            val alpha1 = (1F - t1) * (1F - t1)
            for (segment in 0 until segments) {
                val angle0 = 2 * PI * segment / segments
                val angle1 = 2 * PI * (segment + 1) / segments
                val dir0 = perpendicular.scale(cos(angle0)).add(up.scale(sin(angle0)))
                val dir1 = perpendicular.scale(cos(angle1)).add(up.scale(sin(angle1)))
                val p00 = center.add(dir0.scale(radius * t0))
                val p01 = center.add(dir1.scale(radius * t0))
                val p10 = center.add(dir0.scale(radius * t1))
                val p11 = center.add(dir1.scale(radius * t1))
                output.add(p00, alpha0); output.add(p10, alpha1); output.add(p11, alpha1)
                output.add(p00, alpha0); output.add(p11, alpha1); output.add(p01, alpha0)
            }
        }
    }

    private fun buildTrail(positions: ArrayDeque<Pair<Vec3, Long>>, entityPosition: Vec3, camera: Vec3, scale: Float, output: Vertices) {
        if (positions.size < 2) return
        for (i in 0 until positions.size - 1) {
            val current = positions[i].first
            val next = positions[i + 1].first
            val t0 = i.toFloat() / (positions.size - 1)
            val t1 = (i + 1).toFloat() / (positions.size - 1)
            val perpendicular = next.subtract(current).normalize().cross(camera.subtract(current).normalize()).normalize()
            val local0 = current.subtract(entityPosition)
            val local1 = next.subtract(entityPosition)
            val width0 = perpendicular.scale(0.1 * scale * t0)
            val width1 = perpendicular.scale(0.1 * scale * t1)
            output.add(local0.subtract(width0), 0.6F * t0)
            output.add(local0.add(width0), 0.6F * t0)
            output.add(local1.add(width1), 0.6F * t1)
            output.add(local1.subtract(width1), 0.6F * t1)
        }
    }

    private class Vertices {
        private var positions = FloatArray(512 * 3)
        private var colors = IntArray(512)
        private var size = 0
        fun add(position: Vec3, alpha: Float) {
            if (size == colors.size) {
                colors = colors.copyOf(colors.size * 2)
                positions = positions.copyOf(positions.size * 2)
            }
            positions[size * 3] = position.x.toFloat()
            positions[size * 3 + 1] = position.y.toFloat()
            positions[size * 3 + 2] = position.z.toFloat()
            colors[size] = ARGB.color((alpha.coerceIn(0F, 1F) * 255F).toInt(), (0.7F * 255F).toInt(), 0, 0)
            size++
        }
        fun finish() = AlphaEyeVertices(positions.copyOf(size * 3), colors.copyOf(size))
    }
}
