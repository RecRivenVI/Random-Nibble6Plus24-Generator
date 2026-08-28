package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.mojang.brigadier.Command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.SeedCommand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import io.github.recrivenvi.randomnibble6plus24generator.command.MosaicSeedCommandIntegration;

@Mixin(SeedCommand.class)
abstract class SeedCommandMixin {

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;executes(Lcom/mojang/brigadier/Command;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"),
            index = 0)
    private static Command<CommandSourceStack> randomnibble6plus24generator$wrapVanillaSeedCommand(
            Command<CommandSourceStack> vanillaCommand) {
        return MosaicSeedCommandIntegration.wrapVanillaCommand(vanillaCommand);
    }
}
