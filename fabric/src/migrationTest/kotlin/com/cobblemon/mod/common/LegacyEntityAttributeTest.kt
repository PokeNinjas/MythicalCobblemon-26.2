package com.cobblemon.mod.common

import com.cobblemon.mod.common.util.LegacyEntityAttributeMigration
import net.minecraft.SharedConstants
import net.minecraft.nbt.TagParser
import net.minecraft.util.datafix.DataFixers
import net.minecraft.util.datafix.DataFixTypes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LegacyEntityAttributeTest {
    @Test fun `native 1_21_1 entity conversion preserves mod attributes after prefix repair`() {
        SharedConstants.tryDetectVersion()
        val original = TagParser.parseCompoundFully("""{DataVersion:3955,Position:[I;0,0],Entities:[{
            id:"cobblemon:pokemon",UUID:[I;1,2,3,4],Pokemon:{Species:"cobblemon:wooloo"},
            attributes:[{id:"minecraft:generic.max_health",base:19.0d},
                {id:"minecraft:generic.follow_range",base:32.0d,modifiers:[{
                    id:"minecraft:random_spawn_bonus",amount:0.25d,operation:"add_multiplied_base"}]}]
        }]}""")
        val vanilla = DataFixTypes.ENTITY_CHUNK.updateToCurrentVersion(DataFixers.getDataFixer(), original, 3955)
        val entity = vanilla.getListOrEmpty("Entities").getCompoundOrEmpty(0)
        val attributes = entity.getListOrEmpty("attributes")
        assertEquals("minecraft:generic.max_health", attributes.getCompoundOrEmpty(0).getStringOr("id", ""))
        val expected = vanilla.copy()
        val expectedAttributes = expected.getListOrEmpty("Entities").getCompoundOrEmpty(0).getListOrEmpty("attributes")
        expectedAttributes.getCompoundOrEmpty(0).putString("id", "minecraft:max_health")
        expectedAttributes.getCompoundOrEmpty(1).putString("id", "minecraft:follow_range")
        assertEquals(expected, LegacyEntityAttributeMigration.migrateRegion(vanilla))
        assertEquals(expected, LegacyEntityAttributeMigration.migrateRegion(vanilla))
    }

    @Test fun `nested mod passengers are repaired without rewriting other namespaces or custom fields`() {
        val region = TagParser.parseCompoundFully("""{Entities:[{id:"minecraft:boat",attributes:[{id:"minecraft:generic.armor",base:2.0d}],
            Passengers:[{id:"cobblemon:npc",attributes:[{id:"generic.armor",base:7.0d},{id:"other:generic.custom",base:3.0d}],
                PersistentData:{id:"minecraft:generic.max_health"}}]}]}""")
        val expected = region.copy()
        expected.getListOrEmpty("Entities").getCompoundOrEmpty(0).getListOrEmpty("Passengers")
            .getCompoundOrEmpty(0).getListOrEmpty("attributes").getCompoundOrEmpty(0).putString("id", "minecraft:armor")
        assertEquals(expected, LegacyEntityAttributeMigration.migrateRegion(region))
    }
}
