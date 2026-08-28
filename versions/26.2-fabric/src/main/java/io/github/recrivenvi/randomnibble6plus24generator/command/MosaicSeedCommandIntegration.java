package io.github.recrivenvi.randomnibble6plus24generator.command;

import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.brigadier.Command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;

public final class MosaicSeedCommandIntegration {

    public static final String CHUNK_WORLD_SEED_TRANSLATION_KEY =
            "commands.randomnibble6plus24generator.seed.chunk_world_seed";

    private MosaicSeedCommandIntegration() {
    }

    public static Command<CommandSourceStack> wrapVanillaCommand(Command<CommandSourceStack> vanillaCommand) {
        return wrapCommand(vanillaCommand, MosaicSeedCommandIntegration::appendMosaicFeedback);
    }

    static <S> Command<S> wrapCommand(Command<S> vanillaCommand, Consumer<S> additionalFeedback) {
        return context -> {
            int vanillaResult = vanillaCommand.run(context);
            additionalFeedback.accept(context.getSource());
            return vanillaResult;
        };
    }

    public static Optional<MutableComponent> createMosaicFeedback(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Optional<MosaicRuntimeContext> runtimeContext = MosaicWorldIdentity.runtimeContext(level);
        if (runtimeContext.isEmpty()) {
            return Optional.empty();
        }

        int chunkX = SectionPos.blockToSectionCoord(source.getPosition().x);
        int chunkZ = SectionPos.blockToSectionCoord(source.getPosition().z);
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        long localWorldSeed = runtimeContext.get().resolveLocalWorldSeed(level.dimension(), chunkPos);
        return Optional.of(createMosaicFeedback(
                level.dimension().identifier().toString(),
                chunkPos,
                localWorldSeed));
    }

    public static MutableComponent createMosaicFeedback(
            String dimension,
            ChunkPos chunkPos,
            long localWorldSeed) {
        Component copyableSeed = ComponentUtils.copyOnClickText(Long.toString(localWorldSeed));
        return Component.translatable(
                CHUNK_WORLD_SEED_TRANSLATION_KEY,
                dimension,
                chunkPos.x(),
                chunkPos.z(),
                copyableSeed);
    }

    private static void appendMosaicFeedback(CommandSourceStack source) {
        createMosaicFeedback(source).ifPresent(message -> source.sendSuccess(() -> message, false));
    }
}
