/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Reuses CPU frames and GPU textures; separate outputs prevent poses sharing their interpolation phase. */
object InterpolatedModelTextures {
    private class Output(val owner: WeakReference<PosableState>, val supplier: AnimatedModelTextureSupplier,
                         val slot: Int, val id: Identifier, val texture: DynamicTexture) {
        var lastFrame: AnimationTextureFrame? = null
        var lastUsed = System.nanoTime()
    }
    private val sourceImages = mutableMapOf<Identifier, NativeImage>()
    private val outputs = WeakHashMap<PosableState, MutableMap<AnimatedModelTextureSupplier, Output>>()
    private val allocated = mutableSetOf<Output>()
    private val freeSlots = ArrayDeque<Int>()
    private var nextSlot = 0
    private var lastSweep = 0L

    fun resolve(supplier: AnimatedModelTextureSupplier, state: PosableState, frame: AnimationTextureFrame): Identifier {
        if (frame.blend == 0F || frame.current == frame.next) return supplier.frames[frame.current]
        val client = Minecraft.getInstance()
        val now = System.nanoTime()
        if (now - lastSweep > 1_000_000_000L) {
            lastSweep = now
            for (output in allocated.toList()) {
                if (output.owner.get() == null || now - output.lastUsed > 30_000_000_000L) release(output)
            }
        }
        fun source(id: Identifier): NativeImage = sourceImages.getOrPut(id) {
            client.resourceManager.getResourceOrThrow(id).open().use(NativeImage::read)
        }
        val first = source(supplier.frames[frame.current])
        val second = source(supplier.frames[frame.next])
        require(first.width == second.width && first.height == second.height) { "Interpolated texture frames must have identical dimensions" }
        val perState = outputs.getOrPut(state) { mutableMapOf() }
        var output = perState[supplier]
        if (output != null && (output.texture.pixels.width != first.width || output.texture.pixels.height != first.height)) {
            release(output)
            output = null
        }
        if (output == null) {
            val slot = if (freeSlots.isEmpty()) nextSlot++ else freeSlots.removeFirst()
            val id = Identifier.fromNamespaceAndPath("cobblemon", "dynamic/animated_model_$slot")
            val texture = DynamicTexture("Cobblemon interpolated model $slot", first.width, first.height, false)
            client.textureManager.register(id, texture)
            output = Output(WeakReference(state), supplier, slot, id, texture)
            outputs.getOrPut(state) { mutableMapOf() }[supplier] = output
            allocated.add(output)
        }
        output.lastUsed = now
        if (output.lastFrame != frame) {
            val image = output.texture.pixels
            for (y in 0 until image.height) for (x in 0 until image.width) {
                image.setPixel(x, y, TextureInterpolation.pixel(first.getPixel(x, y), second.getPixel(x, y), frame.blend))
            }
            output.texture.upload()
            output.lastFrame = frame
        }
        return output.id
    }

    private fun release(output: Output) {
        Minecraft.getInstance().textureManager.release(output.id)
        output.owner.get()?.let { owner -> outputs[owner]?.remove(output.supplier) }
        allocated.remove(output)
        freeSlots.addLast(output.slot)
    }

    /** Called by model resource reload, before new supplier and poser instances are installed. */
    fun clear() {
        allocated.toList().forEach(::release)
        outputs.clear()
        sourceImages.values.forEach(NativeImage::close)
        sourceImages.clear()
    }
}
