/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.scheduling.ClientTaskTracker
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.*
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.pokemon.PokemonMaterials
import com.cobblemon.mod.common.duck.ShoulderPokemonData
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.*
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import java.util.UUID
import java.util.WeakHashMap

class ShoulderAnimationState : PosableState() {
    override fun getEntity() = null
    override val schedulingTracker = ClientTaskTracker
    override fun updatePartialTicks(partialTicks: Float) { currentPartialTicks = partialTicks }
    private var previousSeconds: Float? = null
    fun advanceEffects() {
        val now = animationSeconds
        previousSeconds?.takeIf { now > it }?.let { runEffects(null, it, now) }
        previousSeconds = now
    }
}

class PokemonOnShoulderRenderer(parent: RenderLayerParent<AvatarRenderState, PlayerModel>) :
    RenderLayer<AvatarRenderState, PlayerModel>(parent) {
    override fun submit(poses: PoseStack, collector: SubmitNodeCollector, light: Int, state: AvatarRenderState, yRot: Float, xRot: Float) {
        if (state.isInvisible || state.isSpectator) return
        for (frame in (state as ShoulderRenderState).shoulderPokemon) {
            poses.pushPose()
            poses.mulPose(frame.transform)
            collector.submitModel(frame.body.model, frame.body, poses, RenderTypes.entityCutoutCull(frame.texture),
                light, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null)
            for ((index, layer) in frame.layers.withIndex()) {
                val layerLight = if (layer.emissive) 15728880 else light
                if (layer.uOffset == 0F && layer.vOffset == 0F) {
                    collector.order(index + 1).submitModel(frame.body.model, frame.body, poses, layer.type,
                        layerLight, OverlayTexture.NO_OVERLAY, layer.color, null, state.outlineColor, null)
                } else {
                    collector.order(index + 1).submitCustomGeometry(poses, layer.type) { pose, buffer ->
                        val drawing = PoseStack(); drawing.last().set(pose)
                        frame.body.model.setupAnim(frame.body)
                        frame.body.model.renderToBuffer(drawing, ScrollingVertexConsumer(buffer, layer.uOffset, layer.vOffset),
                            layerLight, OverlayTexture.NO_OVERLAY, layer.color)
                    }
                }
            }
            poses.popPose()
            heldItems.submit(frame.items, poses, collector, light, state.outlineColor)
        }
    }

    class ShoulderData(val uuid: UUID, val species: Species, val form: FormData, val aspects: Set<String>,
                       val scaleModifier: Float, val shownItem: ItemStack) {
        fun matches(other: ShoulderData?): Boolean = other != null && uuid == other.uuid && species === other.species &&
            form === other.form && aspects == other.aspects && scaleModifier == other.scaleModifier &&
            ItemStack.isSameItemSameComponents(shownItem, other.shownItem)
    }
    private class ShoulderCache {
        val data = arrayOfNulls<ShoulderData>(2)
        val animation = arrayOfNulls<ShoulderAnimationState>(2)
        val tags = arrayOfNulls<CompoundTag>(2)
    }
    companion object {
        // Weak player keys release all animations after despawn, disconnect and dimension replacement.
        private val playerCache = WeakHashMap<Player, ShoulderCache>()
        private val models = WeakHashMap<PosableModel, SnapshotBedrockModel>()
        private val heldItems = HeldItemRenderer()
        @JvmStatic fun clear() { playerCache.clear(); models.clear() }

        @JvmStatic fun extract(player: Player, avatar: AvatarRenderState, partialTicks: Float): List<ShoulderPokemonFrame> {
            val data = shoulderDataOf(player)
            val cache = playerCache.getValue(player)
            return listOf(data.first, data.second).mapIndexedNotNull { index, shoulder ->
                if (shoulder == null || avatar.isInvisible || avatar.isSpectator) return@mapIndexedNotNull null
                var animation = cache.animation[index]
                if (animation == null) animation = ShoulderAnimationState().also { cache.animation[index] = it }
                animation.currentAspects = shoulder.aspects
                val identifier = shoulder.species.resourceIdentifier
                val poser = VaryingModelRepository.getPoser(identifier, animation)
                if (animation.currentModel !== poser) configureState(animation, poser, index == 0, shoulder)
                animation.updateAge(player.tickCount)
                animation.updatePartialTicks(partialTicks)
                val texture = VaryingModelRepository.getTexture(identifier, animation)
                val scale = shoulder.form.baseScale * shoulder.scaleModifier
                if (!scale.isFinite() || scale <= 0F) return@mapIndexedNotNull null
                val transform = PoseStack()
                if (avatar.isCrouching) {
                    transform.mulPose(Axis.XP.rotation(.4F))
                    transform.translate(0F, 0F, -.15F)
                }
                val width = shoulder.form.hitbox.width * shoulder.scaleModifier / 2 - .7
                transform.translate(if (index == 0) -width else width, -1.5 * scale + if (avatar.isCrouching) .2 else 0.0, 0.0)
                transform.scale(scale, scale, scale)
                transform.translate(0F, 1.5F, 0F)
                val context = RenderContext()
                context.put(RenderContext.SPECIES, identifier)
                context.put(RenderContext.ASPECTS, shoulder.aspects)
                context.put(RenderContext.POSABLE_STATE, animation)
                context.put(RenderContext.TEXTURE, texture)
                context.put(RenderContext.SCALE, scale)
                context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
                poser.context = context
                try {
                    poser.applyAnimations(null, animation, avatar.walkAnimationPos, avatar.walkAnimationSpeed,
                        avatar.ageInTicks, avatar.yRot, avatar.xRot)
                    val model = models[poser]?.takeIf { it.sourceRoot === poser.rootPart }
                        ?: SnapshotBedrockModel(poser.rootPart as ModelPart).also { models[poser] = it }
                    poser.locatorAccess.update(transform, null, scale, animation.locatorStates, isRoot = true)
                    animation.advanceEffects()
                    ShoulderPokemonFrame(model.capture(), texture, Matrix4f(transform.last().pose()),
                        PokemonMaterials.extractLayers(identifier, animation), heldItems.extract(shoulder.shownItem, animation))
                } finally {
                    poser.setDefault()
                    context.pop()
                }
            }
        }

    fun configureState(
        state: ShoulderAnimationState,
        model: PosableModel,
        leftShoulder: Boolean,
        shoulderData: ShoulderData
    ): ShoulderAnimationState {
        state.currentModel = model
        state.setPoseToFirstSuitable(if (leftShoulder) PoseType.SHOULDER_LEFT else PoseType.SHOULDER_RIGHT)

        state.runtime.environment.query.addFunction("is_holding_item") {
            return@addFunction DoubleValue(shoulderData.shownItem.let {
                !it.isEmpty && !it.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS) && !it.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS)
            })
        }
        state.runtime.environment.query.addFunction("is_wearing_hat") {
            return@addFunction DoubleValue(
                shoulderData.shownItem.`is`(
                    CobblemonItemTags.WEARABLE_HAT_ITEMS
                )
            )
        }
        state.runtime.environment.query.addFunction("is_wearing_face") {
            return@addFunction DoubleValue(
                shoulderData.shownItem.`is`(
                    CobblemonItemTags.WEARABLE_FACE_ITEMS
                )
            )
        }

        return state
    }


        @JvmStatic fun shoulderDataOf(player: Player): Pair<ShoulderData?, ShoulderData?> {
            val cache = playerCache.getOrPut(player) { ShoulderCache() }
            val synced = player as ShoulderPokemonData
            for (index in 0..1) {
                val tag = synced.`cobblemon$getShoulderPokemon`(index == 0)
                if (cache.tags[index] != tag) {
                    val data = shoulderDataFrom(tag)
                    if (data == null || !data.matches(cache.data[index])) cache.animation[index] = null
                    cache.tags[index] = tag.copy()
                    cache.data[index] = data
                }
            }
            return cache.data[0] to cache.data[1]
        }

        @JvmStatic fun shoulderDataFrom(tag: CompoundTag): ShoulderData? {
            if (!tag.isPokemonEntity()) return null
            val uuid = runCatching { CompoundTagUtilities.getPokemonID(tag) }.getOrNull() ?: return null
            val ops = Minecraft.getInstance().level?.registryAccess()?.createSerializationContext(NbtOps.INSTANCE) ?: NbtOps.INSTANCE
            if (!tag.contains(DataKeys.SHOULDER_SPECIES)) {
                val pokemon = Pokemon.CLIENT_CODEC.parse(ops, tag.getCompoundOrEmpty(DataKeys.POKEMON)).result().orElse(null) ?: return null
                return ShoulderData(uuid, pokemon.species, pokemon.form, pokemon.aspects, pokemon.effectiveScale, pokemon.heldItem)
            }
            val identifier = Identifier.tryParse(tag.getStringOr(DataKeys.SHOULDER_SPECIES, "")) ?: return null
            val species = PokemonSpecies.getByIdentifier(identifier) ?: return null
            val form = species.forms.firstOrNull { it.name == tag.getStringOr(DataKeys.SHOULDER_FORM, "") } ?: species.standardForm
            val aspects = tag.getListOrEmpty(DataKeys.SHOULDER_ASPECTS).mapNotNull { it.asString().orElse(null) }.toSet()
            val item = ItemStack.OPTIONAL_CODEC.parse(ops, tag.getCompoundOrEmpty(DataKeys.SHOULDER_ITEM)).result().orElse(ItemStack.EMPTY)
            return ShoulderData(uuid, species, form, aspects, tag.getFloatOr(DataKeys.SHOULDER_SCALE_MODIFIER, 1F), item)
        }
    }
}
