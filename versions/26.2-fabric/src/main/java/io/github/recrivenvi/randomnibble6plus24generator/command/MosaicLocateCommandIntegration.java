package io.github.recrivenvi.randomnibble6plus24generator.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;

/** Keeps Vanilla locate success/permission handling and scopes only its Mosaic miss diagnostic. */
public final class MosaicLocateCommandIntegration {

    public static final String GENERATED_AREA_NOT_FOUND_TRANSLATION_KEY =
            "commands.randomnibble6plus24generator.locate.generated_area_not_found";

    private MosaicLocateCommandIntegration() {
    }

    public static Command<CommandSourceStack> wrapStructureCommand(
            Command<CommandSourceStack> vanillaCommand) {
        return context -> {
            try {
                return vanillaCommand.run(context);
            } catch (CommandSyntaxException exception) {
                CommandSourceStack source = context.getSource();
                if (MosaicWorldIdentity.isMosaicWorld(source.getLevel())) {
                    throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                            Component.translatable(GENERATED_AREA_NOT_FOUND_TRANSLATION_KEY)).create();
                }
                throw exception;
            }
        };
    }
}
