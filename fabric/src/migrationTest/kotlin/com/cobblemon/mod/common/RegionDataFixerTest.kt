package com.cobblemon.mod.common

import com.cobblemon.mod.common.datafixer.CobblemonSchemas
import net.minecraft.SharedConstants
import net.minecraft.nbt.TagParser
import net.minecraft.util.datafix.DataFixTypes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RegionDataFixerTest {
    @Test fun `legacy Pokemon retain identity and held items while IVs gain their new structure`() {
        SharedConstants.tryDetectVersion()
        val input = TagParser.parseCompoundFully("""{Entities:[{id:"cobblemon:pokemon",UUID:[I;1,2,3,4],Pokemon:{Species:"cobblemon:wooloo",IVs:{hp:31},HeldItem:{id:"minecraft:diamond",Count:1b,tag:{custom:"keep"}}}}]}""")
        val result = DataFixTypes.ENTITY_CHUNK.update(CobblemonSchemas.DATA_FIXER, input, 0, CobblemonSchemas.DATA_VERSION)
        val entity = result.getListOrEmpty("Entities").getCompoundOrEmpty(0)
        assertArrayEquals(intArrayOf(1,2,3,4), entity.getIntArray("UUID").orElseThrow())
        val pokemon = entity.getCompoundOrEmpty("Pokemon")
        assertEquals("cobblemon:wooloo", pokemon.getStringOr("Species", ""))
        assertEquals(31, pokemon.getCompoundOrEmpty("IVs").getCompoundOrEmpty("Base").getIntOr("cobblemon:hp", 0))
        assertEquals(1, pokemon.getCompoundOrEmpty("HeldItem").getIntOr("count", 0))
        assertEquals("keep", pokemon.getCompoundOrEmpty("HeldItem").getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data").getStringOr("custom", ""))
    }
    @Test fun `componentized item data is not componentized twice`() {
        SharedConstants.tryDetectVersion()
        val item = TagParser.parseCompoundFully("""{id:"minecraft:diamond",count:3,components:{"minecraft:custom_data":{keep:1}}}""")
        val input = TagParser.parseCompoundFully("""{block_entities:[{id:"cobblemon:chest",Items:[]}]}""")
        input.getListOrEmpty("block_entities").getCompoundOrEmpty(0).getListOrEmpty("Items").add(item.copy())
        val result = DataFixTypes.CHUNK.update(CobblemonSchemas.DATA_FIXER, input, 0, CobblemonSchemas.DATA_VERSION)
        assertEquals(item, result.getListOrEmpty("block_entities").getCompoundOrEmpty(0).getListOrEmpty("Items").getCompoundOrEmpty(0))
    }
    @Test fun `unmarked vanilla terrain preserves block entities and unknown data`() {
        SharedConstants.tryDetectVersion()
        val input = TagParser.parseCompoundFully("""{xPos:0,zPos:0,block_entities:[{id:"minecraft:chest",x:1,y:64,z:1,Items:[{id:"minecraft:diamond",count:3}]}],custom:{keep:1}}""")
        assertEquals(input, DataFixTypes.CHUNK.update(CobblemonSchemas.DATA_FIXER, input.copy(), 0, CobblemonSchemas.DATA_VERSION))
    }
    @Test fun `unmarked vanilla entity region is unchanged`() {
        SharedConstants.tryDetectVersion()
        val input = TagParser.parseCompoundFully("""{Position:[I;0,0],Entities:[{id:"minecraft:pig",Health:10.0f,custom:{keep:1}}]}""")
        assertEquals(input, DataFixTypes.ENTITY_CHUNK.update(CobblemonSchemas.DATA_FIXER, input.copy(), 0, CobblemonSchemas.DATA_VERSION))
    }
    @Test fun `legacy block entity items are componentized without losing custom tags`() {
        SharedConstants.tryDetectVersion()
        val input = TagParser.parseCompoundFully("""{block_entities:[{id:"cobblemon:chest",x:1,y:64,z:1,Items:[{id:"minecraft:diamond",Count:3b,tag:{custom:"keep"}}]}]}""")
        val result = DataFixTypes.CHUNK.update(CobblemonSchemas.DATA_FIXER, input, 0, CobblemonSchemas.DATA_VERSION)
        val item = result.getListOrEmpty("block_entities").getCompoundOrEmpty(0).getListOrEmpty("Items").getCompoundOrEmpty(0)
        assertEquals(3, item.getIntOr("count", 0))
        assertEquals("keep", item.getCompoundOrEmpty("components").getCompoundOrEmpty("minecraft:custom_data").getStringOr("custom", ""))
    }
}
