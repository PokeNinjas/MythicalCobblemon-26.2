/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.cobblemon.mod.common.client.render.models.blockbench.*
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.pokemon.PokemonLayerSubmission
import com.cobblemon.mod.common.client.render.pokemon.PokemonMaterials
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.special.SpecialModelRenderer
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector3fc
import java.util.WeakHashMap
import java.util.function.Consumer

data class PokemonItemFrame(val body: BedrockModelSnapshot, val texture: Identifier,
    val transform: Matrix4f, val color: Int, val layers: List<PokemonLayerSubmission>, val gui: Boolean)

class PokemonItemRenderer : CobblemonBuiltinItemRenderer, SpecialModelRenderer<PokemonItemFrame> {
    private val models = WeakHashMap<PosableModel, SnapshotBedrockModel>()
    private val states = WeakHashMap<ItemStack, FloatingState>()
    override fun clear() { models.clear(); states.clear() }

    override fun update(output: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver,
                        mode: ItemDisplayContext, level: ClientLevel?, owner: ItemOwner?, seed: Int, transform: Matrix4fc) {
        val item = stack.item as? PokemonItem ?: return
        val (species, aspects) = item.getSpeciesAndAspects(stack) ?: return
        val state = states.getOrPut(stack) { FloatingState() }
        state.currentAspects = aspects
        val model = VaryingModelRepository.getPoser(species.resourceIdentifier, state)
        val context = RenderContext()
        context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.PROFILE)
        context.put(RenderContext.DO_QUIRKS, false)
        context.put(RenderContext.SPECIES, species.resourceIdentifier)
        context.put(RenderContext.ASPECTS, aspects)
        context.put(RenderContext.POSABLE_STATE, state)
        model.context = context
        state.currentModel = model
        val placement = positions.getValue(mode)
        val poses = PoseStack()
        poses.mulPose(transform)
        // Native LayerRenderState applies the item-space (-.5, -.5, -.5) origin
        // to both submitted geometry and extents. Keep it out of this captured transform.
        poses.scale(placement.scale.x, placement.scale.y, placement.scale.z)
        poses.translate(placement.translation.x, placement.translation.y, placement.translation.z)
        try {
            state.setPoseToFirstSuitable(PoseType.PORTRAIT)
            model.applyAnimations(null, state, 0F, 0F, 0F, 0F, 0F)
            applyProfileScale(poses, stack, model.profileScale)
            poses.mulPose(Quaternionf().fromEulerXYZDegrees(Vector3f(placement.rotation.x, placement.rotation.y, placement.rotation.z)))
            val drawing = models[model]?.takeIf { it.sourceRoot === model.rootPart }
                ?: SnapshotBedrockModel(model.rootPart as ModelPart).also { models[model] = it }
            val texture = VaryingModelRepository.getTexture(species.resourceIdentifier, state)
            context.put(RenderContext.TEXTURE, texture)
            val tint = item.tint(stack)
            val color = ARGB.colorFromFloat(tint.w, tint.x, tint.y, tint.z)
            val frame = PokemonItemFrame(drawing.capture(), texture, Matrix4f(poses.last().pose()), color,
                PokemonMaterials.extractLayers(species.resourceIdentifier, state, color), mode == ItemDisplayContext.GUI)
            val layer = output.newLayer()
            layer.setUsesBlockLight(mode != ItemDisplayContext.GUI)
            layer.setupSpecialModel(this, frame)
            layer.setFoilType(if (stack.hasFoil()) ItemStackRenderState.FoilType.STANDARD else ItemStackRenderState.FoilType.NONE)
            val extents = mutableListOf<Vector3fc>()
            model.rootPart.let { it as ModelPart }.visit(poses) { pose, _, _, cube ->
                for (x in listOf(cube.minX, cube.maxX)) for (y in listOf(cube.minY, cube.maxY)) for (z in listOf(cube.minZ, cube.maxZ)) {
                    extents += pose.pose().transformPosition(Vector3f(x / 16F, y / 16F, z / 16F))
                }
            }
            val capturedExtents = extents.toTypedArray()
            layer.setExtents { capturedExtents }
            output.appendModelIdentityElement(this)
            output.appendModelIdentityElement(drawing)
            output.appendModelIdentityElement(species.resourceIdentifier)
            output.appendModelIdentityElement(aspects.toSet())
            output.appendModelIdentityElement(texture)
            output.appendModelIdentityElement(color)
            output.appendModelIdentityElement(mode)
            // Per-stack preview adapters can change the captured transform.
            // Their geometry must not reuse an icon cached at another scale.
            output.appendModelIdentityElement(frame.transform)
            output.appendModelIdentityElement(stack.hasFoil())
            if (stack.hasFoil()) output.setAnimated()
        } finally {
            model.setDefault()
            context.pop()
        }
    }

    /** Stable extraction hook for owned per-stack portrait scaling adapters. */
    fun applyProfileScale(poses: PoseStack, stack: ItemStack, profileScale: Float) {
        poses.scale(profileScale, profileScale, .15F)
    }

    override fun submit(frame: PokemonItemFrame?, poses: PoseStack, collector: SubmitNodeCollector,
                        light: Int, overlay: Int, hasFoil: Boolean, outline: Int) {
        if (frame == null) return
        poses.pushPose()
        poses.mulPose(frame.transform)
        val baseLight = if (frame.gui) LightCoordsUtil.pack(13, 13) else light
        collector.submitModel(frame.body.model, frame.body, poses, RenderTypes.entityCutoutCull(frame.texture),
            baseLight, overlay, frame.color, null, outline, null)
        for ((index, layer) in frame.layers.withIndex()) {
            val layerLight = if (layer.emissive) 15728880 else baseLight
            if (layer.uOffset == 0F && layer.vOffset == 0F) {
                collector.order(index + 1).submitModel(frame.body.model, frame.body, poses, layer.type,
                    layerLight, overlay, layer.color, null, outline, null)
            } else {
                collector.order(index + 1).submitCustomGeometry(poses, layer.type) { pose, buffer ->
                    val drawing = PoseStack(); drawing.last().set(pose)
                    frame.body.model.setupAnim(frame.body)
                    frame.body.model.renderToBuffer(drawing, ScrollingVertexConsumer(buffer, layer.uOffset, layer.vOffset), layerLight, overlay, layer.color)
                }
            }
        }
        if (hasFoil) collector.order(frame.layers.size + 1).submitModel(frame.body.model, frame.body, poses,
            RenderTypes.entityGlint(), baseLight, overlay, -1, null, 0, null)
        poses.popPose()
    }

    // ItemModel.update captures argument and exact bounds with its actual display context.
    override fun extractArgument(stack: ItemStack): PokemonItemFrame? = null
    override fun getExtents(output: Consumer<Vector3fc>) {
        output.accept(Vector3f(-.5F, -.5F, -.5F))
        output.accept(Vector3f(.5F, .5F, .5F))
    }

    companion object {
        val positions: MutableMap<ItemDisplayContext, Transformations> = mutableMapOf()

        init {
            positions[ItemDisplayContext.GUI] = Transformations(
                Transformation(1.0, -0.2, -0.5),
                Transformation(0.5F, -0.5F, -3.5F),
                Transformation(0F, 35F, 0F)
            )
            positions[ItemDisplayContext.FIXED] = Transformations(
                Transformation(1.0, -0.5, -5.0),
                Transformation(0.5F, -0.5F, -0.1F),
                Transformation(0F, 35F - 180F, 0F)
            )
            positions[ItemDisplayContext.FIRST_PERSON_RIGHT_HAND] = Transformations(
                Transformation(2.75, 0.0, 1.0),
                Transformation(0.5F, -0.5F, -0.5F),
                Transformation(0F, 35F, 0F)
            )
            positions[ItemDisplayContext.FIRST_PERSON_LEFT_HAND] = Transformations(
                Transformation(-0.75, 0.0, 1.0),
                Transformation(0.5F, -0.5F, -0.5F),
                Transformation(0F, -35F, 0F)
            )
            positions[ItemDisplayContext.THIRD_PERSON_RIGHT_HAND] = Transformations(
                Transformation(1.0, -1.0, -1.25),
                Transformation(0.5F, -0.5F, -0.5F),
                Transformation(0F, 35F, 0F)
            )
            positions[ItemDisplayContext.THIRD_PERSON_LEFT_HAND] = Transformations(
                Transformation(1.0, -1.0, -1.25),
                Transformation(0.5F, -0.5F, -0.5F),
                Transformation(0F, -35F, 0F)
            )
            positions[ItemDisplayContext.GROUND] = Transformations(
                Transformation(1.0, -0.5, -1.0),
                Transformation(0.5F, -0.5F, -0.5F),
                Transformation(0F, 35F, 0F)
            )
            positions[ItemDisplayContext.HEAD] = Transformations(
                Transformation(1.0, -3.5, 3.0),
                Transformation(0.5F, -0.5F, -0.5F),
                Transformation(0F, 215F, 0F)
            )
            positions[ItemDisplayContext.NONE] = Transformations(
                Transformation(0.0, 0.0, 0.0),
                Transformation(0.5F, -0.5F, -0.5F),
                Transformation(0F, 0F, 0F)
            )
        }
    }

    data class Transformations(val translation: Transformation<Double>, val scale: Transformation<Float>, val rotation: Transformation<Float>)
    data class Transformation<T>(val x: T, val y: T, val z: T)
}
