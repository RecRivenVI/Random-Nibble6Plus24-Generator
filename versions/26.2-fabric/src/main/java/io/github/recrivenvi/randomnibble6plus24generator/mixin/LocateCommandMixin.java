package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.mojang.brigadier.Command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.LocateCommand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import io.github.recrivenvi.randomnibble6plus24generator.command.MosaicLocateCommandIntegration;

/** Wraps only LocateCommand's structure executor; biome and POI commands remain untouched. */
@Mixin(LocateCommand.class)
abstract class LocateCommandMixin {

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;executes(Lcom/mojang/brigadier/Command;)Lcom/mojang/brigadier/builder/ArgumentBuilder;",
                    ordinal = 0),
            index = 0)
    private static Command<CommandSourceStack> randomnibble6plus24generator$wrapStructureLocateCommand(
            Command<CommandSourceStack> vanillaCommand) {
        return MosaicLocateCommandIntegration.wrapStructureCommand(vanillaCommand);
    }
}
