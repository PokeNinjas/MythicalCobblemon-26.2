package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.LocatorAccess
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class LocatorInitializationTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }
    @Test fun `spawn locators work before rendering and ignore another entity's context`() {
        val bone = mock(Bone::class.java)
        val model = PosableModel(bone)
        model.locatorAccess = LocatorAccess(bone)
        val state = mock(PosableState::class.java)
        val locators = mutableMapOf<String, MatrixWrapper>()
        `when`(state.locatorStates).thenReturn(locators)
        val entity = mock(GenericBedrockEntity::class.java)
        `when`(entity.scale).thenReturn(2F)
        `when`(entity.boundingBox).thenReturn(net.minecraft.world.phys.AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0))
        model.updateLocators(entity, state)
        val first = locators.getValue("root").matrix
        assertEquals(2F, kotlin.math.abs(first.m00()))
        assertTrue(first.isFinite)

        model.context = RenderContext().apply {
            put(RenderContext.SCALE, 99F)
            put(RenderContext.POSABLE_STATE, mock(PosableState::class.java))
        }
        model.updateLocators(entity, state)
        assertEquals(first, locators.getValue("root").matrix)
    }
}
