package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.command.MosaicLocateCommandIntegration;
import io.github.recrivenvi.randomnibble6plus24generator.command.MosaicSeedCommandIntegration;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.VillagerTrades;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Actual native consumers on a ready physical region, paired with Vanilla's no-structures world.
 */
public final class WorldSearchEvidence {
    private static boolean observing;
    private static int searchCalls;

    private WorldSearchEvidence() {}

    public static boolean enabled() {
        return Boolean.getBoolean(ModConstants.MOD_ID + ".phase3c2.production.worldSearchProof");
    }

    public static void searchRequested() {
        if (observing) searchCalls++;
    }

    public static JsonObject verify(ServerLevel level, ChunkPos owner) {
        boolean mosaic = MosaicWorldIdentity.isMosaicWorld(level);
        if (!mosaic && level.getServer().getWorldGenSettings().options().generateStructures())
            throw new IllegalStateException(
                    "No-result oracle requires native generate-structures=false");
        var before = MosaicPhysicalMaterializer.metrics();
        int loaded = level.getChunkSource().getLoadedChunksCount();
        int entities = entityCount(level);
        BlockPos position =
                new BlockPos(
                        owner.getMinBlockX() + 8, level.getMaxY() - 12, owner.getMinBlockZ() + 8);
        JsonObject result = new JsonObject();
        observing = true;
        searchCalls = 0;
        try {
            var recording = new RecordingSource();
            var source =
                    level.getServer()
                            .createCommandSourceStack()
                            .withSource(recording)
                            .withLevel(level)
                            .withPosition(Vec3.atCenterOf(position))
                            .withPermission(PermissionSet.ALL_PERMISSIONS);
            level.getServer().getCommands().performPrefixedCommand(source, "seed");
            require(
                    recording.messages.size() == (mosaic ? 2 : 1),
                    "Incorrect /seed feedback count");
            require(
                    recording.messages.stream()
                            .anyMatch(
                                    message ->
                                            message.toString().contains("commands.seed.success")),
                    "Native world seed feedback missing");
            if (mosaic)
                require(
                        recording.messages.stream()
                                .anyMatch(
                                        message ->
                                                message.toString()
                                                        .contains(
                                                                MosaicSeedCommandIntegration
                                                                        .CHUNK_WORLD_SEED_TRANSLATION_KEY)),
                        "Local seed feedback missing");
            result.addProperty("seedMessages", recording.messages.size());
            if (mosaic) {
                for (String command :
                        List.of(
                                "locate structure minecraft:stronghold",
                                "locate biome minecraft:plains",
                                "locate poi minecraft:home")) {
                    recording.messages.clear();
                    int calls = searchCalls;
                    level.getServer().getCommands().performPrefixedCommand(source, command);
                    require(
                            recording.messages.size() == 1
                                    && recording
                                            .messages
                                            .getFirst()
                                            .toString()
                                            .contains(
                                                    MosaicLocateCommandIntegration
                                                            .UNSUPPORTED_TRANSLATION_KEY),
                            "Locate did not reject before search: " + command + recording.messages);
                    require(searchCalls == calls, "Disabled command activated structure search");
                }
            } else {
                recording.messages.clear();
                level.getServer()
                        .getCommands()
                        .performPrefixedCommand(source, "locate structure minecraft:stronghold");
                require(
                        recording.messages.size() == 1
                                && recording
                                        .messages
                                        .getFirst()
                                        .toString()
                                        .contains("commands.locate.structure.not_found"),
                        "Native locate no-result changed: " + recording.messages);
                recording.messages.clear();
                String biome =
                        level.getBiome(position).unwrapKey().orElseThrow().identifier().toString();
                level.getServer()
                        .getCommands()
                        .performPrefixedCommand(source, "locate biome " + biome);
                require(
                        recording.messages.size() == 1
                                && recording
                                        .messages
                                        .getFirst()
                                        .toString()
                                        .contains("commands.locate.biome.success"),
                        "Native biome locate changed: " + recording.messages);
            }
            result.addProperty(
                    "locatePolicy",
                    mosaic
                            ? "ALL_THREE_DISABLED_BEFORE_SEARCH"
                            : "NATIVE_STRUCTURE_MISS_AND_BIOME_SUCCESS");

            var player =
                    new ServerPlayer(
                            level.getServer(),
                            level,
                            new GameProfile(
                                    UUID.nameUUIDFromBytes(
                                            "mosaic-search-component"
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)),
                                    "SearchProbe"),
                            ClientInformation.createDefault());
            player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
            player.setXRot(-90);
            var eye = new ItemStack(Items.ENDER_EYE, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, eye);
            int calls = searchCalls;
            Items.ENDER_EYE.use(level, player, InteractionHand.MAIN_HAND);
            require(
                    searchCalls == calls + 1
                            && eye.getCount() == 2
                            && entityCount(level) == entities,
                    "Eye no-result spawned an entity or consumed the item");
            player.stopUsingItem();
            result.addProperty("eyeUnchangedNoProjectile", true);

            var merchant = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            var context =
                    new LootContext.Builder(
                                    new LootParams(
                                            level,
                                            new ContextMap.Builder()
                                                    .withParameter(
                                                            LootContextParams.ORIGIN,
                                                            Vec3.atCenterOf(position))
                                                    .withParameter(
                                                            LootContextParams.THIS_ENTITY, merchant)
                                                    .create(
                                                            net.minecraft.world.level.storage.loot
                                                                    .parameters.LootContextParamSets
                                                                    .CHEST),
                                            Map.of(),
                                            0))
                            .withOptionalRandomSeed(123456789)
                            .create(Optional.empty());
            var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            for (var tag :
                    List.of(StructureTags.ON_TREASURE_MAPS, StructureTags.ON_OCEAN_EXPLORER_MAPS)) {
                var function =
                        ExplorationMapFunction.makeExplorationMap()
                                .setDestination(tag)
                                .setSearchRadius(100)
                                .setSkipKnownStructures(false)
                                .build();
                var blank = new ItemStack(Items.MAP);
                calls = searchCalls;
                var output = function.apply(blank, context);
                require(
                        searchCalls == calls + 1
                                && ItemStack.isSameItemSameComponents(blank, output)
                                && !output.has(net.minecraft.core.component.DataComponents.MAP_ID),
                        "Native map no-result acquired a destination");
            }
            result.addProperty("lootMapMisses", 2);
            var trades = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
            for (var key :
                    List.of(
                            VillagerTrades.CARTOGRAPHER_3_EMERALD_AND_COMPASS_OCEAN_EXPLORER_MAP,
                            VillagerTrades.CARTOGRAPHER_3_EMERALD_AND_COMPASS_TRIAL_CHAMBER_MAP,
                            VillagerTrades
                                    .CARTOGRAPHER_5_EMERALD_AND_COMPASS_WOODLAND_MANSION_MAP)) {
                calls = searchCalls;
                require(
                        trades.getOrThrow(key).value().getOffer(context) == null
                                && searchCalls == calls + 1,
                        "Native cartographer no-result produced an offer or skipped the search consumer: "
                                + key);
            }
            result.addProperty("cartographerOffersAbsent", 3);
            merchant.discard();

            var dolphin = new Dolphin(EntityType.DOLPHIN, level);
            dolphin.setPos(Vec3.atCenterOf(position));
            dolphin.setAirSupply(4800);
            dolphin.setGotFish(true);
            try {
                var constructor =
                        Class.forName(Dolphin.class.getName() + "$DolphinSwimToTreasureGoal")
                                .getDeclaredConstructor(Dolphin.class);
                constructor.setAccessible(true);
                var goal = (Goal) constructor.newInstance(dolphin);
                require(goal.canUse(), "Dolphin fixture never entered native treasure goal");
                calls = searchCalls;
                goal.start();
                require(
                        searchCalls == calls + 1 && !goal.canContinueToUse(),
                        "Dolphin no-result kept an active destination");
                goal.stop();
                require(
                        !goal.canUse() && !dolphin.gotFish(),
                        "Dolphin retried a failed search without another feeding");
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Unable to call the native dolphin goal", failure);
            }
            dolphin.discard();
            result.addProperty("dolphinClearedWithoutRetry", true);
            require(
                    MosaicPhysicalMaterializer.metrics().artifactCaptureCount()
                            == before.artifactCaptureCount(),
                    "Search consumers generated an Artifact");
            require(entityCount(level) == entities, "Search consumers registered an entity");
            if (mosaic)
                require(
                        level.getChunkSource().getLoadedChunksCount() == loaded,
                        "Disabled locate loaded a physical Chunk");
            result.addProperty("searchCalls", searchCalls);
            result.addProperty("mosaic", mosaic);
            result.addProperty("artifactGenerations", 0);
            result.addProperty("status", "PASS");
            return result;
        } finally {
            observing = false;
            searchCalls = 0;
        }
    }

    private static int entityCount(ServerLevel level) {
        int count = 0;
        for (var entity : level.getAllEntities()) count++;
        return count;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static final class RecordingSource implements CommandSource {
        private final List<Component> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component component) {
            messages.add(component);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
