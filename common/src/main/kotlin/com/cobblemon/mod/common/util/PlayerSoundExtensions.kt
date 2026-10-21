package com.cobblemon.mod.common.util

import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

fun ServerPlayer.playSoundToPlayer(sound: SoundEvent, source: SoundSource, volume: Float, pitch: Float) {
    connection.send(ClientboundSoundPacket(Holder.direct(sound), source, x, y, z, volume, pitch, random.nextLong()))
}
