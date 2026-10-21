package com.cobblemon.mod.common.client.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

/** Optional Iris API integration. No Iris classes are linked when Iris is absent. */
object IrisMaterialBridge {
    private val log = LoggerFactory.getLogger("Cobblemon shader materials")
    private val apiClass by lazy {
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            try { Class.forName("net.irisshaders.iris.api.v0.IrisApi") }
            catch (e: ReflectiveOperationException) { log.warn("Iris API unavailable", e); null }
        } else null
    }
    fun register(pipeline: RenderPipeline, program: String): RenderPipeline {
        val api = apiClass ?: return pipeline
        try {
            val keyClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram")
            val key = keyClass.enumConstants.first { (it as Enum<*>).name == program }
            api.getMethod("assignPipeline", RenderPipeline::class.java, keyClass)
                .invoke(api.getMethod("getInstance").invoke(null), pipeline, key)
            val shadowClass = Class.forName("net.irisshaders.iris.api.v0.IrisShadowProgram")
            val shadowName = if (program.startsWith("PARTICLES")) "SHADOW" else "SHADOW_ENTITIES"
            val shadow = shadowClass.enumConstants.first { (it as Enum<*>).name == shadowName }
            api.getMethod("assignPipelineShadow", RenderPipeline::class.java, shadowClass)
                .invoke(api.getMethod("getInstance").invoke(null), pipeline, shadow)
            log.info("Registered shader material {} as {}", pipeline.location, program)
        } catch (e: ReflectiveOperationException) {
            log.warn("Could not register shader material {}", pipeline.location, e)
        }
        return pipeline
    }
}
