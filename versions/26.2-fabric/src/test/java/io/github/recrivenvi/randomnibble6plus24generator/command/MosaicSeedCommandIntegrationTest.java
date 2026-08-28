package io.github.recrivenvi.randomnibble6plus24generator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.commands.SeedCommand;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;

class MosaicSeedCommandIntegrationTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void feedbackMirrorsVanillaCopyableSeedComponent() {
        long localWorldSeed = -5161763991829980711L;
        ChunkPos chunkPos = new ChunkPos(125, -37);

        Component expected = Component.translatable(
                MosaicSeedCommandIntegration.CHUNK_WORLD_SEED_TRANSLATION_KEY,
                "minecraft:overworld",
                125,
                -37,
                ComponentUtils.copyOnClickText(Long.toString(localWorldSeed)));

        assertEquals(
                expected,
                MosaicSeedCommandIntegration.createMosaicFeedback(
                        "minecraft:overworld",
                        chunkPos,
                        localWorldSeed));
    }

    @Test
    void originFeedbackCanDisplayTheSameWorldAndChunkWorldSeed() {
        long masterSeed = 123456789L;
        Component feedback = MosaicSeedCommandIntegration.createMosaicFeedback(
                "minecraft:overworld",
                ChunkPos.ZERO,
                masterSeed);

        assertEquals(
                Component.translatable(
                        MosaicSeedCommandIntegration.CHUNK_WORLD_SEED_TRANSLATION_KEY,
                        "minecraft:overworld",
                        0,
                        0,
                        ComponentUtils.copyOnClickText(Long.toString(masterSeed))),
                feedback);
    }

    @Test
    void mosaicFeedbackUsesTheRuntimeResolverForNonOriginChunk() {
        long masterSeed = 123456789L;
        ChunkPos chunkPos = new ChunkPos(125, -37);
        MosaicWorldProfile profile = MosaicWorldProfile.version1();
        MosaicRuntimeContext runtime = new MosaicRuntimeContext(
                MosaicTestWorlds.mosaicSettings(masterSeed, profile),
                profile);
        long localWorldSeed = runtime.resolveLocalWorldSeed(Level.OVERWORLD, chunkPos);

        assertEquals(-5161763991829980711L, localWorldSeed);
        assertEquals(
                MosaicSeedCommandIntegration.createMosaicFeedback(
                        "minecraft:overworld",
                        chunkPos,
                        localWorldSeed),
                MosaicSeedCommandIntegration.createMosaicFeedback(
                        "minecraft:overworld",
                        chunkPos,
                        runtime.seedResolver().resolveLocalWorldSeed(masterSeed, Level.OVERWORLD, chunkPos)));
    }

    @Test
    void originFeedbackUsesWorldOptionsSeedThroughTheRuntimeBridge() {
        long masterSeed = -987654321L;
        MosaicWorldProfile profile = MosaicWorldProfile.version1();
        MosaicRuntimeContext runtime = new MosaicRuntimeContext(
                MosaicTestWorlds.mosaicSettings(masterSeed, profile),
                profile);

        assertEquals(masterSeed, runtime.masterSeed());
        assertEquals(masterSeed, runtime.resolveLocalWorldSeed(Level.OVERWORLD, ChunkPos.ZERO));
    }

    @Test
    void wrapperExecutesVanillaFirstAndPreservesItsResult() throws Exception {
        List<String> events = new ArrayList<>();
        Command<String> vanilla = context -> {
            events.add("vanilla:" + context.getSource());
            return 0x13572468;
        };
        Command<String> wrapped = MosaicSeedCommandIntegration.wrapCommand(
                vanilla,
                source -> events.add("mosaic:" + source));
        CommandContext<String> context = context("source");

        assertEquals(0x13572468, wrapped.run(context));
        assertEquals(List.of("vanilla:source", "mosaic:source"), events);
    }

    @Test
    void integratedSeedCommandKeepsVanillaAllLevelPermission() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SeedCommand.register(dispatcher, false);

        assertTrue(dispatcher.getRoot().getChild("seed").canUse(source(
                LevelBasedPermissionSet.forLevel(PermissionLevel.ALL))));
    }

    @Test
    void dedicatedSeedCommandKeepsVanillaGamemasterPermission() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SeedCommand.register(dispatcher, true);

        assertFalse(dispatcher.getRoot().getChild("seed").canUse(source(
                LevelBasedPermissionSet.forLevel(PermissionLevel.ALL))));
        assertTrue(dispatcher.getRoot().getChild("seed").canUse(source(LevelBasedPermissionSet.GAMEMASTER)));
    }

    private static CommandContext<String> context(String source) {
        CommandDispatcher<String> dispatcher = new CommandDispatcher<>();
        return new CommandContextBuilder<>(dispatcher, source, dispatcher.getRoot(), 0).build("seed");
    }

    private static CommandSourceStack source(LevelBasedPermissionSet permissions) {
        return new CommandSourceStack(
                CommandSource.NULL,
                Vec3.ZERO,
                Vec2.ZERO,
                null,
                permissions,
                "test",
                Component.literal("test"),
                null,
                null);
    }
}
