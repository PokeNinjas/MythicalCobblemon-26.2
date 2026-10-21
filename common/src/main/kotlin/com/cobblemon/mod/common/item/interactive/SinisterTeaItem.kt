/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.food

import com.cobblemon.mod.common.CobblemonItemComponents
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.level.Level

class SinisterTeaItem(properties: Properties) : Item(
        properties.stacksTo(16)
                .food(FoodProperties.Builder()
                        .alwaysEdible()
                        .nutrition(0)
                        .saturationModifier(0.0f)
                        .build(), net.minecraft.world.item.component.Consumables.DEFAULT_DRINK)
                .usingConvertsTo(Items.BOWL)
) {
    override fun finishUsingItem(stack: ItemStack, world: Level, user: LivingEntity): ItemStack {
        if (!world.isClientSide) {
            val effectsComponent = stack.get(CobblemonItemComponents.MOB_EFFECTS)

            if (effectsComponent != null) {
                for (raw in effectsComponent.mobEffects) {
                    val inst = MobEffectInstance(raw)
                    val holder = inst.effect
                    val effect = holder.value()

                    if (effect.isInstantaneous && (inst.duration <= 1)) {
                        effect.applyInstantaneousEffect(world as net.minecraft.server.level.ServerLevel, user as? Player, null, user, inst.amplifier, 1.0)
                    } else {
                        user.addEffect(inst)
                    }
                }
            }
        }

        return super.finishUsingItem(stack, world, user)
    }

    override fun getUseAnimation(stack: ItemStack) = ItemUseAnimation.DRINK


}