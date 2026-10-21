/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.repository

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.client.render.ModelLayer
import com.cobblemon.mod.common.client.render.ModelVariationSet
import com.cobblemon.mod.common.client.render.SpriteType
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver
import com.cobblemon.mod.common.client.render.models.blockbench.*
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.client.render.models.blockbench.blockentity.BlockEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.fossil.FossilModel
import com.cobblemon.mod.common.client.render.models.blockbench.pokemon.PokemonPosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.pose.ModelPartTransformation
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Pose
import com.cobblemon.mod.common.util.adapters.ExpressionAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.ModelPartTransformationAdapter
import com.cobblemon.mod.common.util.adapters.Vec3dAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.fromJson
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.function.BiFunction
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.resources.Identifier as ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.phys.Vec3

/**
 * A repository for [PosableModel]s. Can be parameterized with [PosableModel] itself or a subclass.
 * This will handle the loading of all factors of [PosableModel]s, including variations, posers, models, and indirectly
 * the animations by providing directories for the [BedrockAnimationRepository] to read from. This class will also
 * hang onto poser instances for reuse.
 *
 * @author Hiroku
 * @since February 28th, 2023
 */
object VaryingModelRepository {
    val posers = mutableMapOf<ResourceLocation, (Bone) -> PosableModel>()
    val variations = mutableMapOf<ResourceLocation, VaryingRenderableResolver>()
    val texturedModels = mutableMapOf<ResourceLocation, Bone>()
    private val modelCullingRadii = mutableMapOf<ResourceLocation, Double>()
    private val speciesCullingRadii = mutableMapOf<ResourceLocation, Double>()

    // Include all model variations so culling never needs to evaluate animated MoLang conditions.
    fun cullingRadius(name: ResourceLocation): Double = speciesCullingRadii[name] ?: 0.0

    private val types = listOf(
        "pokemon",
        "fossils",
        "npcs",
        "poke_balls",
        "generic",
        "block_entities",
    )

    val poserDirectories: List<Pair<String, Class<out PosableModel>>> = listOf(
        "bedrock/posers" to PosableModel::class.java,
        "bedrock/pokemon/posers" to PokemonPosableModel::class.java,
        "bedrock/fossils/posers" to FossilModel::class.java,
        "bedrock/block_entities/posers" to BlockEntityModel::class.java,
        "bedrock/npcs/posers" to PosableModel::class.java,
        "bedrock/poke_balls/posers" to PosableModel::class.java,
        "bedrock/generic/posers" to PosableModel::class.java,
    )

    val variationDirectories: List<String> = listOf(
        "bedrock/species",
        "bedrock/pokemon/resolvers"
    ) + types.map { "bedrock/$it/variations" }

    val modelDirectories: List<String> = listOf(
        "bedrock/models"
    ) + types.map { "bedrock/$it/models" }

    val animationDirectories: List<String> = listOf(
        "bedrock/animations"
    ) + types.map { "bedrock/$it/animations" }

    val fallback: ResourceLocation = cobblemonResource("substitute")

    val gson: Gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(Vec3::class.java, Vec3dAdapter)
            .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
            .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
            .registerTypeAdapter(PosableModel::class.java, JsonModelAdapter(::PosableModel))
            .registerTypeAdapter(PokemonPosableModel::class.java, JsonModelAdapter(::PokemonPosableModel))
            .registerTypeAdapter(FossilModel::class.java, JsonModelAdapter(::FossilModel))
            .registerTypeAdapter(BlockEntityModel::class.java, JsonModelAdapter(::BlockEntityModel))
            .registerTypeAdapter(Pose::class.java, PoseAdapter { JsonModelAdapter.model!! })
            .registerTypeAdapter(ModelPartTransformation::class.java, ModelPartTransformationAdapter)
            .addDeserializationExclusionStrategy(MixinCompatibilityExclusionStrategy)
            .create()
    }

    //Some mods will inject extra properties into the model part, which we use GSON for (through the Bone interface)
    //If through a 3rd party mixin fields get injected that cant be deserialized by default (e.g. optional), we crash
    //this strategy aims to skip the known 3rd party libraries that do this to avoid crashing
    object MixinCompatibilityExclusionStrategy : ExclusionStrategy {
        private var known3rdPartyMixins = listOf("embeddium")

        private var knownUnusedClasses = listOf(Optional::class.java)

        override fun shouldSkipField(field: FieldAttributes?): Boolean {
            if (known3rdPartyMixins.any { field?.name?.contains(it) == true }) {
                Cobblemon.LOGGER.debug("Skipping non-vanilla field encountered during model deserialization ${field?.name}")
                return true
            }
            return false
        }

        override fun shouldSkipClass(p0: Class<*>?): Boolean {
            if (p0 in knownUnusedClasses) {
                Cobblemon.LOGGER.debug("Skipping non-vanilla class encountered during model deserialization: ${p0?.name}")
                return true
            }
            return false
        }
    }

    fun loadJsonPoser(fileName: String, json: String, poserClass: Class<out PosableModel>): (Bone) -> PosableModel {
        // Faster to deserialize during asset load rather than rerunning this every time a poser is constructed.
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        return {
            var boneName = jsonObject.getAsJsonPrimitive("rootBone")
            JsonModelAdapter.modelPart = if (boneName != null && it.children[boneName.asString] != null) it.children[boneName.asString] else it.children[fileName] ?: it.children.entries.filter { LocatorAccess.PREFIX !in it.key }.first().value
            gson.fromJson(jsonObject, poserClass).also {
                it.poses.forEach { (poseName, pose) -> pose.poseName = poseName }
            }
        }
    }

    fun registerPosers(resourceManager: ResourceManager) {
        posers.clear()
        registerInBuiltPosers()
        registerJsonPosers(resourceManager)
        Cobblemon.LOGGER.info("Loaded ${posers.size} posers.")
    }

    /** Production supplies the entire upstream catalog; the isolated renderer harness supplies its subset. */
    var registerAdditionalInbuiltPosers: () -> Unit = {}
    fun registerInBuiltPosers() {
        registerAdditionalInbuiltPosers()
        inbuilt("butterfree", { com.cobblemon.mod.common.client.render.models.blockbench.pokemon.gen1.ButterfreeModel(it) })
    }

    fun registerJsonPosers(resourceManager: ResourceManager) {
        for ((directory, poserClass) in poserDirectories) {
            resourceManager
                .listResources(directory) { path -> path.path.endsWith(".json") }
                .forEach { (identifier, resource) ->
                    resource.open().use { stream ->
                        val json = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                        val resolvedIdentifier = ResourceLocation.fromNamespaceAndPath(identifier.namespace, File(identifier.path).nameWithoutExtension)
                        posers[resolvedIdentifier] = loadJsonPoser(resolvedIdentifier.path, json, poserClass)
                    }
                }
        }
    }

    fun inbuilt(name: String, model: (ModelPart) -> PosableModel) {
        posers[cobblemonResource(name)] = { bone -> model.invoke(bone as ModelPart) }
    }

    fun registerVariations(resourceManager: ResourceManager) {
        var variationCount = 0
        val nameToModelVariationSets = mutableMapOf<ResourceLocation, MutableList<ModelVariationSet>>()
        for (directory in variationDirectories) {
            resourceManager
                .listResources(directory) { path -> path.path.endsWith(".json") }
                .forEach { (_, resource) ->
                    resource.open().use { stream ->
                        val json = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                        val modelVariationSet = VaryingRenderableResolver.GSON.fromJson<ModelVariationSet>(json)
                        nameToModelVariationSets.getOrPut(modelVariationSet.name) { mutableListOf() }.add(modelVariationSet)
                        variationCount += modelVariationSet.variations.size
                    }
                }
        }

        for ((species, speciesVariationSets) in nameToModelVariationSets) {
            val variations = speciesVariationSets.sortedBy { it.order }.flatMap { it.variations }.toMutableList()
            this.variations[species] = VaryingRenderableResolver(species, variations)
        }

        variations.values.forEach { it.initialize(this) }
        speciesCullingRadii.clear()
        variations.forEach { (species, resolver) ->
            speciesCullingRadii[species] = resolver.getAllModels().maxOfOrNull { modelCullingRadii[it] ?: 0.0 } ?: 0.0
        }

        Cobblemon.LOGGER.info("Loaded $variationCount variations.")
    }

    fun registerModels(resourceManager: ResourceManager) {
        texturedModels.clear()
        modelCullingRadii.clear()
        speciesCullingRadii.clear()
        var models = 0
        for (directory in modelDirectories) {
            MODEL_FACTORIES.forEach { (key, func) ->
                resourceManager.listResources(directory) { path -> path.path.endsWith(key) }
                    .mapNotNull { func.apply(it.key, it.value) }
                    .forEach {
                        texturedModels[it.first] = it.second
                        models++
                    }
            }

        }

        Cobblemon.LOGGER.info("Loaded $models models.")
    }

    fun reload(resourceManager: ResourceManager) {
        com.cobblemon.mod.common.client.render.InterpolatedModelTextures.clear()
        com.cobblemon.mod.common.client.render.layer.PokemonOnShoulderRenderer.clear()
        com.cobblemon.mod.common.client.render.pokemon.PokemonMaterials.clearTextures()
        com.cobblemon.mod.common.client.render.item.CobblemonBuiltinItemRendererRegistry.clearCaches()
        Cobblemon.LOGGER.info("Loading varying Bedrock assets...")
        this.variations.clear()
        this.posers.clear()
        registerModels(resourceManager)
        registerPosers(resourceManager)
        registerVariations(resourceManager)
    }

    fun getPoser(name: ResourceLocation, state: PosableState): PosableModel {
        try {
            val poser = this.variations[name]?.getPoser(state)
            if (poser != null) {
                return poser
            }
        } catch(e: IllegalStateException) {
            e.printStackTrace()
        }
        error("No real poser for $name with aspects ${state.currentAspects}")
    }

    fun getTexture(name: ResourceLocation, state: PosableState): ResourceLocation {
        try {
            val texture = this.variations[name]?.getTexture(state)
            if (texture != null) {
                return texture
            }
        } catch(_: IllegalStateException) { }
        error("No real texture for $name with aspects ${state.currentAspects}")
    }

    fun getTextureNoSubstitute(name: ResourceLocation, state: PosableState): ResourceLocation? {
        try {
            val texture = this.variations[name]?.getTexture(state)
            if (texture != null && net.minecraft.client.Minecraft.getInstance().resourceManager.getResource(texture).isPresent) {
                return texture
            }
        } catch(_: IllegalStateException) {}
        return null
    }

    fun getLayers(name: ResourceLocation, state: PosableState): Iterable<ModelLayer> {
        try {
            val layers = this.variations[name]?.getLayers(state)
            if (layers != null) {
                return layers
            }
        } catch(_: IllegalStateException) { }
        error("No layers definition for $name with aspects ${state.currentAspects}")
    }

    fun getSprite(name: ResourceLocation, state: PosableState, type: SpriteType): ResourceLocation? {
        try {
            return this.variations[name]?.getSprite(state, type)
        } catch (_: IllegalStateException) {}
        return null
    }

    fun registerFactory(id: String, factory: BiFunction<ResourceLocation, Resource, Pair<ResourceLocation, Bone>?>) {
        MODEL_FACTORIES[id] = factory
    }

    /*
        Needs to be java function to work with non kotlin sidemods.
        - Waterpicker
     */
    private var MODEL_FACTORIES = mutableMapOf<String, BiFunction<ResourceLocation, Resource, Pair<ResourceLocation, Bone>?>>().also {
        it[".geo.json"] = BiFunction<ResourceLocation, Resource, Pair<ResourceLocation, Bone>?> { identifier: ResourceLocation, resource: Resource ->
            resource.open().use { stream ->
                val json = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                val resolvedIdentifier = ResourceLocation.fromNamespaceAndPath(identifier.namespace, File(identifier.path).nameWithoutExtension)

                val texturedModel = TexturedModel.from(json)
                if (texturedModel == null) {
                    LOGGER.warn("Failed to load model file with identifier $identifier You can ignore this (and the above message) if this is not a cobblemon model")
                    return@BiFunction null
                }
                texturedModel.geometry?.firstOrNull()?.description?.let { description ->
                    BedrockVisibleBounds.radius(description.visibleBoundsWidth, description.visibleBoundsHeight,
                        description.visibleBoundsOffset)?.let { modelCullingRadii[resolvedIdentifier] = it }
                }
                resolvedIdentifier to texturedModel.create().bakeRoot()
            }
        }
    }
}
