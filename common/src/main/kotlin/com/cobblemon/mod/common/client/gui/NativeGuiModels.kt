/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.item.HeldItemRenderState
import com.cobblemon.mod.common.client.render.models.blockbench.*
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.pokemon.PokemonMaterials
import com.cobblemon.mod.common.entity.PoseType
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.system.MemoryStack
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.world.item.ItemStack
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

class GuiBedrockLayer(val type: RenderType, val color: Int, val light: Int, val u: Float = 0F, val v: Float = 0F)
enum class GuiModelLighting { POKEMON, BATTLE_BALL }
class GuiBedrockRenderState(
    val body: BedrockModelSnapshot, val transform: Matrix4f, val layers: List<GuiBedrockLayer>,
    val items: List<HeldItemRenderState>, val light: Int, val lighting: GuiModelLighting, val depthExtent: Float,
    private val rectangle: ScreenRectangle, private val guiPose: Matrix3x2f, private val scissor: ScreenRectangle?
) : PictureInPictureRenderState {
    override fun x0() = rectangle.left()
    override fun x1() = rectangle.right()
    override fun y0() = rectangle.top()
    override fun y1() = rectangle.bottom()
    override fun scale() = 1F
    override fun pose(): Matrix3x2fc = guiPose
    override fun scissorArea() = scissor
    override fun bounds(): ScreenRectangle? {
        val transformed = rectangle.transformMaxBounds(guiPose)
        return if (scissor == null) transformed else transformed.intersection(scissor)
    }
}

/** Minecraft/Fabric own target pooling, depth, projection, alpha compositing, and disposal. */
class GuiBedrockRenderer : PictureInPictureRenderer<GuiBedrockRenderState>() {
    private val heldItems = HeldItemRenderer()
    private val lights = mutableMapOf<GuiModelLighting, GpuBuffer>()
    private val depthProjection = Projection()
    private val depthProjectionBuffer = ProjectionMatrixBuffer("Cobblemon GUI model depth")

    private fun lightingBuffer(kind: GuiModelLighting): GpuBuffer = lights.getOrPut(kind) {
        // Preserve Cobblemon's original shader light vectors; vanilla inventory
        // lighting darkens the front of PC, summary and party previews.
        val (first, second) = when (kind) {
            GuiModelLighting.POKEMON -> Vector3f(-1F, 1F, 1F) to Vector3f(1.3F, -1F, 1F)
            GuiModelLighting.BATTLE_BALL -> Vector3f(2.2F, 4F, -4F) to Vector3f(1.1F, -4F, 7F)
        }
        MemoryStack.stackPush().use { stack ->
            val data = Std140Builder.onStack(stack, Lighting.UBO_SIZE).putVec3(first).putVec3(second).get()
            RenderSystem.getDevice().createBuffer({ "Cobblemon GUI lights $kind" }, GpuBuffer.USAGE_UNIFORM, data)
        }
    }

    override fun close() {
        lights.values.forEach { it.close() }
        lights.clear()
        depthProjectionBuffer.close()
        super.close()
    }
    override fun getRenderStateClass() = GuiBedrockRenderState::class.java
    override fun getTextureLabel() = "cobblemon_model"
    override fun getTranslateY(height: Int, guiScale: Int) = height / 2F
    override fun prepare(state: GuiBedrockRenderState, guiState: GuiRenderState,
                         dispatcher: FeatureRenderDispatcher, guiScale: Int) {
        // PIP allocates before applying the GUI pose. Match the displayed pixel
        // density so enlarged PC/summary models do not magnify a tiny framebuffer.
        try {
            super.prepare(state, guiState, dispatcher, previewRasterScale(state.pose(), guiScale))
        } finally {
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D)
        }
    }
    override fun renderToTexture(state: GuiBedrockRenderState, poses: PoseStack, collector: SubmitNodeCollector) {
        // Native PIP scales Z with raster density but fixes its clip planes at
        // +/-1000. Original model profiles can translate far in Z (Charizard:
        // -14 before its 20x scale), so enlarged previews need a wider depth range.
        // Adjust projection only: screen framing, geometry and lighting stay intact.
        val rasterScale = kotlin.math.abs(poses.last().pose().m22())
        val depth = previewDepthExtent(state.depthExtent, rasterScale)
        depthProjection.setupOrtho(-depth, depth, (state.x1() - state.x0()) * rasterScale,
            (state.y1() - state.y0()) * rasterScale, true)
        RenderSystem.setProjectionMatrix(depthProjectionBuffer.getBuffer(depthProjection), ProjectionType.ORTHOGRAPHIC)
        RenderSystem.setShaderLights(lightingBuffer(state.lighting).slice())
        poses.translate(-(state.x0() + state.x1()) / 2F, -(state.y0() + state.y1()) / 2F, 0F)
        poses.pushPose()
        poses.mulPose(state.transform)
        for ((index, layer) in state.layers.withIndex()) {
            if (layer.u == 0F && layer.v == 0F) {
                collector.order(index).submitModel(state.body.model, state.body, poses, layer.type, layer.light,
                    OverlayTexture.NO_OVERLAY, layer.color, null, 0, null)
            } else {
                val snapshot = state.body
                collector.order(index).submitCustomGeometry(poses, layer.type) { pose, vertices ->
                    val drawing = PoseStack()
                    drawing.last().set(pose)
                    snapshot.model.setupAnim(snapshot)
                    snapshot.model.renderToBuffer(drawing, ScrollingVertexConsumer(vertices, layer.u, layer.v),
                        layer.light, OverlayTexture.NO_OVERLAY, layer.color)
                }
            }
        }
        poses.popPose()
        heldItems.submit(state.items, poses, collector, state.light)
    }
}

fun previewRasterScale(pose: Matrix3x2fc, guiScale: Int): Int {
    val a = pose.m00().toDouble(); val b = pose.m01().toDouble()
    val c = pose.m10().toDouble(); val d = pose.m11().toDouble()
    val xx = a * a + b * b; val yy = c * c + d * d
    val xy = a * c + b * d
    val largestScale = kotlin.math.sqrt((xx + yy + hypot(xx - yy, 2.0 * xy)) / 2.0)
    return ceil(guiScale * maxOf(1.0, largestScale)).toInt()
}

fun previewDepthExtent(modelExtent: Float, rasterScale: Float): Float =
    maxOf(1000F, modelExtent * rasterScale + 16F)

object NativeGuiModels {
    private val viewportBounds = WeakHashMap<PosableState, Pair<ModelPart, ScreenRectangle>>()
    private val models = WeakHashMap<PosableModel, SnapshotBedrockModel>()
    private val translucent = mutableMapOf<Identifier, RenderType>()
    private val heldItems = HeldItemRenderer()

    fun draw(context: GuiGraphicsExtractor, identifier: Identifier, state: PosableState, partialTicks: Float,
             poseType: PoseType, contextScale: Float, doQuirks: Boolean, color: Int, light: Int,
             headYaw: Float = 0F, headPitch: Float = 0F, limbSwing: Float = 0F, limbSwingAmount: Float = 0F,
             ageInTicks: Float = 0F, item: ItemStack = ItemStack.EMPTY,
             lighting: GuiModelLighting = GuiModelLighting.POKEMON,
             transform: (PosableModel, PoseStack) -> Unit) {
        val savedLocators = state.locatorStates.mapValues { (_, locator) -> locator to Matrix4f(locator.matrix) }
        val model = VaryingModelRepository.getPoser(identifier, state)
        state.currentModel = model
        state.setPoseToFirstSuitable(poseType)
        state.updatePartialTicks(partialTicks)
        val texture = VaryingModelRepository.getTexture(identifier, state)
        val animation = RenderContext()
        animation.put(RenderContext.TEXTURE, texture)
        animation.put(RenderContext.SPECIES, identifier)
        animation.put(RenderContext.ASPECTS, state.currentAspects)
        animation.put(RenderContext.POSABLE_STATE, state)
        animation.put(RenderContext.SCALE, contextScale)
        animation.put(RenderContext.DO_QUIRKS, doQuirks)
        animation.put(RenderContext.RENDER_STATE, if (poseType == PoseType.PORTRAIT) RenderContext.RenderState.PORTRAIT else RenderContext.RenderState.PROFILE)
        model.context = animation
        try {
            model.applyAnimations(null, state, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch)
            val drawing = models[model]?.takeIf { it.sourceRoot === model.rootPart }
                ?: SnapshotBedrockModel(model.rootPart as ModelPart).also { models[model] = it }
            val snapshot = drawing.capture()
            val modelPose = PoseStack()
            transform(model, modelPose)
            model.locatorAccess.update(modelPose, null, contextScale, state.locatorStates, isRoot = true)
            val items = heldItems.extract(item, state)
            val layers = mutableListOf(GuiBedrockLayer(RenderTypes.entityCutoutCull(texture), color, light))
            for (layer in VaryingModelRepository.getLayers(identifier, state)) {
                val layerTexture = layer.texture?.invoke(state) ?: continue
                val type = when {
                    layer.emissive -> PokemonMaterials.emissive(layerTexture, layer.translucent)
                    layer.translucent_cull -> translucent.getOrPut(layerTexture) {
                        RenderType.create("cobblemon_gui_translucent", RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                            .withTexture("Sampler0", layerTexture).useLightmap().useOverlay().sortOnUpload().createRenderSetup())
                    }
                    layer.translucent -> RenderTypes.entityTranslucent(layerTexture)
                    else -> RenderTypes.entityCutoutCull(layerTexture)
                }
                val tint = layer.tint
                val layerColor = ARGB.multiply(color, ARGB.color((tint.w * 255).toInt(), (tint.x * 255).toInt(), (tint.y * 255).toInt(), (tint.z * 255).toInt()))
                layers += GuiBedrockLayer(type, layerColor, if (layer.emissive) 15728880 else light,
                    ((layer.scrolling?.speedU ?: 0F) * state.animationSeconds) % 1F,
                    ((layer.scrolling?.speedV ?: 0F) * state.animationSeconds) % 1F)
            }
            // Tight bounds keep each offscreen target proportional to the displayed model.
            var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
            var depthExtent = 0F
            fun point(x: Float, y: Float, z: Float) {
                minX = minOf(minX, x); minY = minOf(minY, y)
                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
                depthExtent = maxOf(depthExtent, kotlin.math.abs(z))
            }
            drawing.setupAnim(snapshot)
            val vertex = Vector3f()
            drawing.root().visit(modelPose) { pose, _, _, cube ->
                for (corner in 0..7) {
                    val x = if (corner and 1 == 0) cube.minX else cube.maxX
                    val y = if (corner and 2 == 0) cube.minY else cube.maxY
                    val z = if (corner and 4 == 0) cube.minZ else cube.maxZ
                    pose.pose().transformPosition(x / 16F, y / 16F, z / 16F, vertex)
                    point(vertex.x, vertex.y, vertex.z)
                }
            }
            for (attachment in items) attachment.item.visitExtents { extent ->
                val vertex = attachment.transform.transformPosition(extent, Vector3f())
                point(vertex.x, vertex.y, vertex.z)
            }
            if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite() || !depthExtent.isFinite()) return
            // Grow in blocks and retain the envelope across animations, avoiding per-frame GPU resizes.
            val previous = viewportBounds[state]?.takeIf { it.first === drawing.sourceRoot }?.second
            val x0 = minOf(previous?.left() ?: Int.MAX_VALUE, floor((minX - 2F) / 32F).toInt() * 32)
            val y0 = minOf(previous?.top() ?: Int.MAX_VALUE, floor((minY - 2F) / 32F).toInt() * 32)
            val x1 = maxOf(previous?.right() ?: Int.MIN_VALUE, ceil((maxX + 2F) / 32F).toInt() * 32)
            val y1 = maxOf(previous?.bottom() ?: Int.MIN_VALUE, ceil((maxY + 2F) / 32F).toInt() * 32)
            val viewport = ScreenRectangle(x0, y0, x1 - x0, y1 - y0)
            viewportBounds[state] = drawing.sourceRoot to viewport
            context.guiRenderState.addPicturesInPictureState(GuiBedrockRenderState(snapshot, Matrix4f(modelPose.last().pose()), layers, items,
                light, lighting, depthExtent, viewport, Matrix3x2f(context.pose()), context.scissorStack.peek()))
        } finally {
            // A dialogue portrait can reference a world delegate; leave its world-space locators intact.
            state.locatorStates.clear()
            savedLocators.forEach { (name, saved) -> saved.first.matrix = saved.second; state.locatorStates[name] = saved.first }
            model.setDefault()
            animation.pop()
        }
    }
}
