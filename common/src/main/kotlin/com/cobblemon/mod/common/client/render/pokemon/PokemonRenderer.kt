/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokemon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.item.HeldItemRenderState
import com.cobblemon.mod.common.client.keybind.boundKey
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding
import com.cobblemon.mod.common.client.render.models.blockbench.BedrockModelSnapshot
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.ScrollingVertexConsumer
import com.cobblemon.mod.common.client.render.models.blockbench.SnapshotBedrockModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.settings.ServerSettings
import com.cobblemon.mod.common.entity.PlatformType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.isLookingAt
import com.cobblemon.mod.common.util.lang
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth
import org.joml.Quaternionf
import java.util.WeakHashMap
import kotlin.math.min
import kotlin.math.pow

/** Frame-owned values only. Gameplay, locators and MoLang are evaluated during extraction. */
class PokemonRenderState : EntityRenderState() {
    var hideLocalMountBody = false
    var heldItems: List<HeldItemRenderState> = emptyList()
    var platformModel: PokemonExtraModel? = null
    var sendOutBall: PokemonExtraModel? = null
    var recallBeam: PokemonRecallBeam? = null
    var alphaEyes = AlphaEyeGeometry.EMPTY
    var body: BedrockModelSnapshot? = null
    var texture: Identifier? = null
    var layers: List<PokemonLayerSubmission> = emptyList()
    var scale = 1F
    var bodyRotation = Quaternionf()
    var rotationPivot = 0F
    var platformOffset = 0F
    var shadow = 0.5F
    var overlay = OverlayTexture.NO_OVERLAY
    var bodyColor = -1
    var bodyVisible = true
    var ghostVisible = false
    var label: FormattedCharSequence? = null
    var labelWidth = 0
    var challenge: FormattedCharSequence? = null
    var challengeWidth = 0
    var labelHeight = 0.0
    var labelSize = 1F
    var labelOffset = 0.0
    var labelDepth = 0.0
    var labelBackground = 0
}

data class PokemonLayerSubmission(val type: RenderType, val color: Int, val emissive: Boolean, val uOffset: Float = 0F, val vOffset: Float = 0F)

/** The production registration uses the original PokemonEntity and its original client delegate. */
class PokemonRenderer(context: EntityRendererProvider.Context) : EntityRenderer<PokemonEntity, PokemonRenderState>(context) {
    private val extraRendering = PokemonExtraRendering()
    private val heldItemRenderer = HeldItemRenderer()
    private val models = WeakHashMap<PosableModel, SnapshotBedrockModel>()
    private val translucentCull = mutableMapOf<Identifier, RenderType>()
    private val animationContext = RenderContext()

    companion object {
        fun ease(x: Double): Double = 1 - (1 - x).pow(3)

        fun hideLocalMountBody(firstPerson: Boolean, localPassenger: Boolean) = firstPerson && localPassenger

        /** Shared by camera/passenger pre-animation and the actual world body extraction. */
        fun prepareBodyTransform(entity: PokemonEntity, delegate: PokemonClientDelegate, partial: Float, state: PokemonRenderState): Float {
            val baseScale = entity.effects.mockEffect?.scale ?: (entity.exposedForm.baseScale * entity.pokemon.effectiveScale)
            state.scale = baseScale * delegate.activeSendoutScale * entity.scale
            state.platformOffset = if (entity.platform != PlatformType.NONE) .25F * delegate.activeSendoutScale else 0F
            state.rotationPivot = 0F
            state.bodyRotation = Axis.YP.rotationDegrees(180F - Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot))
            if (entity.passengers.isNotEmpty() && entity is OrientationControllable && entity.orientationController.active) {
                delegate.applyRenderRotation(partial)
                state.rotationPivot = entity.bbHeight / 2F
                state.bodyRotation = Quaternionf(entity.orientationController.getRenderOrientation(partial))
            }
            return baseScale
        }
        fun applyBodyTransform(state: PokemonRenderState, poses: PoseStack) {
            poses.translate(0F, state.platformOffset + state.rotationPivot, 0F)
            poses.mulPose(state.bodyRotation)
            poses.translate(0F, -state.rotationPivot, 0F)
            poses.scale(-state.scale, -state.scale, state.scale)
        }

        private val LEVEL_STYLE = Style.EMPTY.withColor(ChatFormatting.WHITE).withBold(false)
            .withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false)
    }

    override fun createRenderState() = PokemonRenderState()
    override fun getShadowRadius(state: PokemonRenderState) = state.shadow
    override fun shouldShowName(entity: PokemonEntity, distanceToCameraSq: Double) = false

    override fun getBoundingBoxForCulling(entity: PokemonEntity): net.minecraft.world.phys.AABB {
        val base = entity.boundingBox
        val delegate = entity.delegate as PokemonClientDelegate
        val modelScale = entity.effects.mockEffect?.scale ?: (entity.exposedForm.baseScale * entity.pokemon.effectiveScale)
        val scale = kotlin.math.abs(modelScale * entity.scale * delegate.activeSendoutScale).toDouble()
        val radius = VaryingModelRepository.cullingRadius(entity.exposedSpecies.resourceIdentifier) * scale
        if (!radius.isFinite() || radius <= 0.0) return base
        // The radius is rotation invariant, including rolling mounts; preserve the native hitbox too.
        val pivotAllowance = if (entity.passengers.isNotEmpty()) entity.bbHeight.toDouble() else 0.0
        val extent = radius + pivotAllowance + if (entity.platform != PlatformType.NONE) 0.25 else 0.0
        return base.minmax(net.minecraft.world.phys.AABB(entity.position(), entity.position()).inflate(extent))
    }

    override fun extractRenderState(entity: PokemonEntity, state: PokemonRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        val delegate = entity.delegate as PokemonClientDelegate
        val mountedPartial = if (entity.passengers.isNotEmpty()) MountedPokemonAnimationRenderController.getPartialTick(entity) else null
        val animationPartial = mountedPartial ?: partialTicks
        val freezeFrame = entity.entityData.get(PokemonEntity.FREEZE_FRAME)
        delegate.updateAge(if (freezeFrame >= 0F) 0 else entity.tickCount)
        delegate.updatePartialTicks(if (freezeFrame >= 0F) freezeFrame * 20F else animationPartial)
        if (mountedPartial != null) delegate.updateLocatorPosition(entity.getPosition(mountedPartial))

        val species = entity.exposedSpecies.resourceIdentifier
        delegate.currentAspects = entity.exposedAspects
        val poser = VaryingModelRepository.getPoser(species, delegate)
        val texture = VaryingModelRepository.getTexture(species, delegate)
        val baseScale = prepareBodyTransform(entity, delegate, animationPartial, state)
        state.shadow = min(entity.boundingBox.xsize, entity.boundingBox.zsize).toFloat() / 1.5F * delegate.activeSendoutScale
        state.overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(entity.hurtTime > 0 || entity.deathTime > 0))
        state.bodyVisible = !entity.isInvisible
        val player = Minecraft.getInstance().player
        state.hideLocalMountBody = hideLocalMountBody(Minecraft.getInstance().options.cameraType.isFirstPerson,
            player != null && entity.hasPassenger(player))
        if (state.hideLocalMountBody) state.shadow = 0F
        state.ghostVisible = !state.bodyVisible && player != null && !entity.isInvisibleTo(player)

        val bodyYaw = Mth.rotLerp(animationPartial, entity.yBodyRotO, entity.yBodyRot)
        val headYaw = Mth.wrapDegrees(Mth.rotLerp(animationPartial, entity.yHeadRotO, entity.yHeadRot) - bodyYaw)
        val headPitch = Mth.lerp(animationPartial, entity.xRotO, entity.xRot)
        val age = if (freezeFrame >= 0F) freezeFrame * 20F else entity.tickCount + animationPartial
        animationContext.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
        animationContext.put(RenderContext.ENTITY, entity)
        animationContext.put(RenderContext.POSABLE_STATE, delegate)
        animationContext.put(RenderContext.SPECIES, species)
        animationContext.put(RenderContext.ASPECTS, delegate.currentAspects)
        animationContext.put(RenderContext.SCALE, baseScale)
        animationContext.put(RenderContext.TEXTURE, texture)
        poser.context = animationContext
        try {
            // Original pose selection, transitions, quirks, expressions and keyframes.
            poser.applyAnimations(entity, delegate, entity.walkAnimation.position(animationPartial),
                entity.walkAnimation.speed(animationPartial), age, headYaw, headPitch)
            var drawingModel = models[poser]
            if (drawingModel == null || drawingModel.sourceRoot !== poser.rootPart) {
                drawingModel = SnapshotBedrockModel(poser.rootPart as ModelPart)
                models[poser] = drawingModel
            }
            state.body = drawingModel.capture()
            state.texture = texture
            val recallTint = if (entity.beamMode == 3 && delegate.secondsSinceBeamEffectStarted > PokemonClientDelegate.BEAM_EXTEND_TIME) {
                1F - min(0.6F, (delegate.secondsSinceBeamEffectStarted - PokemonClientDelegate.BEAM_EXTEND_TIME) / PokemonClientDelegate.BEAM_SHRINK_TIME)
            } else 1F
            val opacity = if (state.ghostVisible) 0.15F else 1F
            state.bodyColor = tint(1F, recallTint, recallTint, opacity)
            state.layers = VaryingModelRepository.getLayers(species, delegate).mapNotNull { layer ->
                val layerTexture = layer.texture?.invoke(delegate) ?: return@mapNotNull null
                val type = when {
                    layer.emissive -> PokemonMaterials.emissive(layerTexture, layer.translucent)
                    layer.translucent_cull -> translucentCull.getOrPut(layerTexture) {
                        RenderType.create("cobblemon_translucent_cull", RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                            .withTexture("Sampler0", layerTexture).useLightmap().useOverlay().sortOnUpload().createRenderSetup())
                    }
                    layer.translucent -> RenderTypes.entityTranslucent(layerTexture)
                    else -> RenderTypes.entityCutoutCull(layerTexture)
                }
                val c = layer.tint
                PokemonLayerSubmission(type, tint(c.x, c.y * recallTint, c.z * recallTint, c.w * opacity), layer.emissive,
                    ((layer.scrolling?.speedU ?: 0F) * delegate.animationSeconds) % 1F,
                    ((layer.scrolling?.speedV ?: 0F) * delegate.animationSeconds) % 1F)
            }
            // Locator consumers use exactly the same model transform as the submitted body.
            val locatorPose = PoseStack()
            applyBodyTransform(state, locatorPose)
            if (state.scale > 0F) poser.locatorAccess.update(locatorPose, entity, state.scale, delegate.locatorStates, isRoot = true)
            state.alphaEyes = extractAlphaEyes(entity, delegate, animationPartial)
            state.heldItems = if (state.bodyVisible && state.scale > 0F) heldItemRenderer.extract(entity.shownItem, delegate, entity) else emptyList()
            state.platformModel = extraRendering.extractPlatform(entity, delegate)
        } finally {
            poser.setDefault()
            animationContext.pop()
        }
        val (ball, beam) = extraRendering.extractTransition(entity, delegate, animationPartial)
        state.sendOutBall = ball
        state.recallBeam = beam
        extractLabels(entity, delegate, state)
    }

    override fun submit(state: PokemonRenderState, poses: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        val body = state.body
        val texture = state.texture
        if (!state.hideLocalMountBody && body != null && texture != null && (state.bodyVisible || state.ghostVisible || state.appearsGlowing())) {
            poses.pushPose()
            applyBodyTransform(state, poses)
            val type = when {
                state.ghostVisible -> RenderTypes.entityTranslucentCullItemTarget(texture)
                state.bodyVisible -> RenderTypes.entityCutoutCull(texture)
                else -> RenderTypes.outline(texture)
            }
            collector.submitModel(body.model, body, poses, type, state.lightCoords, state.overlay, state.bodyColor, null, state.outlineColor, null)
            if (state.bodyVisible || state.ghostVisible) for (layer in state.layers) {
                val light = if (layer.emissive) 15728880 else state.lightCoords
                val overlay = state.overlay
                if (layer.uOffset == 0F && layer.vOffset == 0F) {
                    collector.order(1).submitModel(body.model, body, poses, layer.type, light, overlay, layer.color, null, state.outlineColor, null)
                } else {
                    collector.order(1).submitCustomGeometry(poses, layer.type) { pose, vertices ->
                        val drawPose = PoseStack()
                        drawPose.last().set(pose)
                        body.model.setupAnim(body)
                        body.model.renderToBuffer(drawPose, ScrollingVertexConsumer(vertices, layer.uOffset, layer.vOffset), light, overlay, layer.color)
                    }
                }
            }
            poses.popPose()
        }
        if (!state.hideLocalMountBody) extraRendering.submitModel(state.platformModel, poses, collector, state.lightCoords)
        extraRendering.submitModel(state.sendOutBall, poses, collector, state.lightCoords)
        extraRendering.submitBeam(state.recallBeam, poses, collector)
        if (!state.hideLocalMountBody) {
            heldItemRenderer.submit(state.heldItems, poses, collector, state.lightCoords, state.outlineColor)
            submitAlphaEyes(state.alphaEyes, poses, collector)
        }
        super.submit(state, poses, collector, camera)
        submitLabels(state, poses, collector, camera)
    }


    private fun extractLabels(entity: PokemonEntity, delegate: PokemonClientDelegate, state: PokemonRenderState) {
        state.label = null
        state.challenge = null
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        // Do not draw a label through the camera of the Pokemon's own passenger.
        if (player.vehicle === entity) return
        if (client.gui.hud.isHidden || entity.isInvisible || entity.entityData.get(PokemonEntity.HIDE_LABEL) || delegate.phaseTarget != null ||
            CobblemonClient.pokedexUsageContext.scanningGuiOpen ||
            (Cobblemon.config.displayEntityLabelsWhenCrouchingOnly && !player.isCrouching) || !player.isLookingAt(entity)) return
        val distance = state.distanceToCameraSq
        if (distance > 4096.0) return
        val label = when {
            !ServerSettings.displayEntityNameLabel -> Component.empty()
            Cobblemon.config.displayNameForUnknownPokemon || CobblemonClient.clientPokedexData.getKnowledgeForSpecies(entity.pokemon.species.resourceIdentifier) != PokedexEntryProgress.UNREGISTERED -> entity.getTitledName()
            else -> Component.literal("???")
        }
        if (ServerSettings.displayEntityLevelLabel && entity.labelLevel() > 0) {
            if (ServerSettings.displayEntityNameLabel) label.append(" ")
            label.append(lang("label.lv", entity.labelLevel()).setStyle(LEVEL_STYLE))
        }
        state.label = label.visualOrderText
        state.labelWidth = font.width(label)
        if (CobblemonClient.clientPlayerData.showChallengeLabel && entity.canBattle(player)) {
            val challenge = lang("challenge_label", PartySendBinding.boundKey().displayName)
            state.challenge = challenge.visualOrderText
            state.challengeWidth = font.width(challenge)
        }
        val distanceScale = ((distance + 16.0) / 112.0).coerceIn(0.65, 1.5)
        val fraction = (distanceScale - 0.65) / 0.85
        state.labelSize = (0.5 + fraction * 0.5).toFloat()
        state.labelOffset = fraction / 2
        state.labelDepth = -(distanceScale + fraction)
        val modelTop = delegate.locatorStates["top"]?.matrix?.m31()?.toDouble()?.takeIf(Double::isFinite)
        val modelHead = delegate.locatorStates["head"]?.matrix?.m31()?.toDouble()?.takeIf(Double::isFinite)
        state.labelHeight = maxOf(entity.boundingBox.ysize, modelTop ?: 0.0, modelHead ?: 0.0) + 0.5
        state.labelBackground = (client.options.getBackgroundOpacity(0.25F) * 255F).toInt() shl 24
    }

    private fun submitLabels(state: PokemonRenderState, poses: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        val label = state.label ?: return
        poses.pushPose()
        poses.translate(0.0, state.labelHeight, 0.0)
        poses.mulPose(camera.orientation)
        poses.translate(0.0, state.labelOffset, state.labelDepth)
        poses.scale(0.025F * state.labelSize, -0.025F * state.labelSize, state.labelSize)
        fun line(text: FormattedCharSequence, width: Int, y: Float) {
            collector.submitText(poses, -width / 2F, y, text, false, Font.DisplayMode.SEE_THROUGH, 15728880, 0x20FFFFFF, state.labelBackground, 0)
            collector.submitText(poses, -width / 2F, y, text, false, Font.DisplayMode.NORMAL, 15728880, -1, 0, 0)
        }
        line(label, state.labelWidth, 0F)
        state.challenge?.let { line(it, state.challengeWidth, 10F) }
        poses.popPose()
    }

    private fun tint(red: Float, green: Float, blue: Float, alpha: Float): Int {
        fun channel(value: Float) = (value.coerceIn(0F, 1F) * 255F).toInt()
        return ARGB.color(channel(alpha), channel(red), channel(green), channel(blue))
    }
}
