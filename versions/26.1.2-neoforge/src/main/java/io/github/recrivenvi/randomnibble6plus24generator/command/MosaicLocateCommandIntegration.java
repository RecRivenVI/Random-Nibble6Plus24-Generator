package io.github.recrivenvi.randomnibble6plus24generator.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Keeps Vanilla parsing and permissions; Mosaic never starts a locate search. */
public final class MosaicLocateCommandIntegration {

    public static final String UNSUPPORTED_TRANSLATION_KEY =
            "commands." + ModConstants.MOD_ID + ".locate.unsupported";
    private static final SimpleCommandExceptionType UNSUPPORTED =
            new SimpleCommandExceptionType(Component.translatable(UNSUPPORTED_TRANSLATION_KEY));

    private MosaicLocateCommandIntegration() {}

    public static Command<CommandSourceStack> wrapCommand(
            Command<CommandSourceStack> vanillaCommand) {
        return context -> {
            if (MosaicWorldIdentity.isMosaicWorld(context.getSource().getLevel()))
                throw UNSUPPORTED.create();
            return vanillaCommand.run(context);
        };
    }
}
