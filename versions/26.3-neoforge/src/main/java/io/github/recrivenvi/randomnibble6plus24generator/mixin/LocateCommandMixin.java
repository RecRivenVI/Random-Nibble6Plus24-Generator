package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.mojang.brigadier.Command;
import io.github.recrivenvi.randomnibble6plus24generator.command.MosaicLocateCommandIntegration;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.LocateCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Rejects Mosaic structure, biome and POI locate commands before their search begins. */
@Mixin(LocateCommand.class)
abstract class LocateCommandMixin {

    @ModifyArg(
            method = "register",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;executes(Lcom/mojang/brigadier/Command;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"),
            index = 0,
            require = 3)
    private static Command<CommandSourceStack> randomnibble6plus24generator$wrapLocateCommand(
            Command<CommandSourceStack> vanillaCommand) {
        return MosaicLocateCommandIntegration.wrapCommand(vanillaCommand);
    }
}
