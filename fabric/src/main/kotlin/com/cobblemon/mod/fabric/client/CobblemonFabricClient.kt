/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.client

import com.cobblemon.mod.common.CobblemonClientImplementation
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonClient.pokedexUsageContext
import com.cobblemon.mod.common.client.CobblemonClient.reloadCodedAssets
import com.cobblemon.mod.common.client.keybind.CobblemonKeyBinds
import com.cobblemon.mod.common.client.pokedex.PokedexType
import com.cobblemon.mod.common.client.render.atlas.CobblemonAtlases
import com.cobblemon.mod.common.client.render.item.CobblemonModelPredicateRegistry
import com.cobblemon.mod.common.item.PokedexItem
import com.cobblemon.mod.common.particle.CobblemonParticles
import com.cobblemon.mod.common.particle.SnowstormParticleType
import com.cobblemon.mod.common.platform.events.ClientEntityEvent
import com.cobblemon.mod.common.platform.events.ClientPlayerEvent
import com.cobblemon.mod.common.platform.events.ClientTickEvent
import com.cobblemon.mod.common.platform.events.ItemTooltipEvent
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.isUsingPokedex
import com.cobblemon.mod.fabric.CobblemonFabric
import com.mojang.blaze3d.vertex.PoseStack
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.color.block.BlockTintSource
import com.cobblemon.mod.common.client.render.color.ItemColor
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType

class CobblemonFabricClient: ClientModInitializer, CobblemonClientImplementation {
    override fun onInitializeClient() {
        CobblemonAtlases.initialize()
        net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties.ID_MAPPER.put(cobblemonResource("predicate"),
            com.cobblemon.mod.common.client.render.item.CobblemonItemProperty.CODEC)
        net.minecraft.client.color.item.ItemTintSources.ID_MAPPER.put(cobblemonResource("item"),
            com.cobblemon.mod.common.client.render.color.CobblemonItemTintSource.CODEC)
        net.minecraft.client.renderer.item.ItemModels.ID_MAPPER.put(cobblemonResource("builtin"),
            com.cobblemon.mod.common.client.render.item.CobblemonBuiltinItemModel.Unbaked.CODEC)
        net.fabricmc.fabric.api.client.particle.v1.ParticleGroupRegistry.register(
            com.cobblemon.mod.common.client.render.SnowstormParticleGroup.TYPE,
            { engine -> com.cobblemon.mod.common.client.render.SnowstormParticleGroup(engine) }
        )
        net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register {
            com.cobblemon.mod.common.client.gui.GuiBedrockRenderer()
        }
        com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository.registerAdditionalInbuiltPosers =
            com.cobblemon.mod.common.client.render.models.blockbench.repository.ProductionInbuiltPosers::register
        registerParticleFactory(CobblemonParticles.SNOWSTORM_PARTICLE_TYPE, SnowstormParticleType::Factory)
        CobblemonClient.initialize(this)
        ModelLoadingPlugin.register {
            com.cobblemon.mod.common.client.CobblemonBakingOverrides.models.forEach { model ->
                it.addModel(model.key, net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel.blockStateModel(model.modelLocation))
            }

        }

        CobblemonFabric.networkManager.registerClientHandlers()

        val resourceLoader = net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(PackType.CLIENT_RESOURCES)
        val codedAssetsId = cobblemonResource("coded_assets")
        resourceLoader.registerReloadListener(codedAssetsId, object : net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener<Unit>() {
            override fun prepare(shared: PreparableReloadListener.SharedState) = Unit
            override fun apply(prepared: Unit, shared: PreparableReloadListener.SharedState) {
                reloadCodedAssets(shared.resourceManager())
                com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository.patchModels()
            }
        })
        resourceLoader.addListenerOrdering(net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys.Client.ATLAS, codedAssetsId)
        resourceLoader.addListenerOrdering(net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys.Client.MODELS, codedAssetsId)

        // Preserve vanilla HUD suppression during a first-person Pokedex scan without hiding the scan HUD itself.
        listOf(
            VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR, VanillaHudElements.SPECTATOR_MENU,
            VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
            VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
            VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP,
            VanillaHudElements.SPECTATOR_TOOLTIP, VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR,
            VanillaHudElements.SLEEP, VanillaHudElements.DEMO_TIMER, VanillaHudElements.SCOREBOARD,
            VanillaHudElements.OVERLAY_MESSAGE, VanillaHudElements.TITLE_AND_SUBTITLE, VanillaHudElements.CHAT,
            VanillaHudElements.PLAYER_LIST, VanillaHudElements.SUBTITLES
        ).forEach { id ->
            HudElementRegistry.replaceElement(id) { original ->
                net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement { graphics, delta ->
                    if (!pokedexUsageContext.scanningGuiOpen || !Minecraft.getInstance().options.cameraType.isFirstPerson) {
                        original.extractRenderState(graphics, delta)
                    }
                }
            }
        }
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, cobblemonResource("party_and_battle")) { graphics, delta ->
            if (!pokedexUsageContext.scanningGuiOpen || !Minecraft.getInstance().options.cameraType.isFirstPerson) {
                CobblemonClient.beforeChatRender(graphics, delta)
            }
        }

        // Register the HUD render callback for Pokédex
        HudElementRegistry.addLast(cobblemonResource("pokedex_scan")) { graphics, tickDelta ->
            val client = Minecraft.getInstance()
            val player = client.player
            if (player != null) {
                if (player.isUsingPokedex() || pokedexUsageContext.transitionIntervals > 0) {
                    if (!player.isUsingItem) pokedexUsageContext.resetState(false)
                    pokedexUsageContext.renderUpdate(graphics, tickDelta)
                } else {
                    pokedexUsageContext.resetState()
                }
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            val player = client.player
            if (player != null) {
                val itemStack = player.mainHandItem
                val offhandStack = player.offhandItem
                if (((itemStack.item is PokedexItem && player.usedItemHand == InteractionHand.MAIN_HAND) ||
                    (offhandStack.item is PokedexItem && player.usedItemHand == InteractionHand.OFF_HAND)) &&
                    player.isUsingItem &&
                    pokedexUsageContext.scanningGuiOpen
                ) {
                    val keyAttack = client.options.keyAttack
                    pokedexUsageContext.attackKeyHeld(keyAttack.isDown)
                }
            }
        })

        CobblemonKeyBinds.register(KeyMappingHelper::registerKeyMapping)

        ClientEntityEvents.ENTITY_LOAD.register { entity, level -> PlatformEvents.CLIENT_ENTITY_LOAD.post(ClientEntityEvent.Load(entity, level))}
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, level -> PlatformEvents.CLIENT_ENTITY_UNLOAD.post(ClientEntityEvent.Unload(entity, level))}
        ClientTickEvents.START_CLIENT_TICK.register { client -> PlatformEvents.CLIENT_TICK_PRE.post(ClientTickEvent.Pre(client)) }
        ClientTickEvents.END_CLIENT_TICK.register { client -> PlatformEvents.CLIENT_TICK_POST.post(ClientTickEvent.Post(client)) }
        ClientPlayConnectionEvents.JOIN.register { _, _, client -> client.player?.let { PlatformEvents.CLIENT_PLAYER_LOGIN.post(ClientPlayerEvent.Login(it)) } }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.player?.let { PlatformEvents.CLIENT_PLAYER_LOGOUT.post(ClientPlayerEvent.Logout(it)) } }
        ItemTooltipCallback.EVENT.register { stack, context, type, lines -> PlatformEvents.CLIENT_ITEM_TOOLTIP.post(ItemTooltipEvent(stack, context, type, lines)) }

        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            PlatformEvents.RENDER.post(RenderEvent(RenderEvent.Stage.SUBMIT, context.levelRenderer(),
                context.poseStack(), context.submitNodeCollector(), context.levelState().cameraRenderState, context.levelState().gameTime))
        }

        CobblemonModelPredicateRegistry.registerPredicates()
    }

    override fun registerLayer(modelLayer: ModelLayerLocation, supplier: Supplier<LayerDefinition>) {
        ModelLayerRegistry.registerModelLayer(modelLayer) { supplier.get() }
    }

    override fun <T : ParticleOptions> registerParticleFactory(type: ParticleType<T>, factory: (SpriteSet) -> ParticleProvider<T>) {
        ParticleProviderRegistry.getInstance().register(type, ParticleProviderRegistry.PendingParticleProvider { factory(it) })
    }

    override fun registerItemColors(provider: ItemColor, vararg items: Item) {
        com.cobblemon.mod.common.client.render.color.CobblemonItemColors.register(provider, *items)
    }

    override fun registerBlockColors(provider: BlockTintSource, vararg blocks: Block) {
        BlockColorRegistry.register(listOf(provider), *blocks)
    }

    override fun <T : BlockEntity, S : net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> registerBlockEntityRenderer(type: BlockEntityType<out T>, factory: BlockEntityRendererProvider<T, S>) {
        BlockEntityRenderers.register(type, factory)
    }

    override fun <T : Entity> registerEntityRenderer(type: EntityType<out T>, factory: EntityRendererProvider<T>) {
        EntityRendererRegistry.register(type, factory)
    }
}