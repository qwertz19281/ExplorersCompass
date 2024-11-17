package com.chaosthedude.explorerscompass.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.text.WordUtils;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureSet.WeightedEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

public class StructureUtils {
	
	public static ListMultimap<Identifier, Identifier> getGroupIDsToStructureIDs(ServerWorld world) {
		ListMultimap<Identifier, Identifier> groupIDsToStructureIDs = ArrayListMultimap.create();
		if (getStructureRegistry(world).isPresent()) {
			for (Structure structure : getStructureRegistry(world).get()) {
				groupIDsToStructureIDs.put(getGroupForStructure(world, structure), getIDForStructure(world, structure));
			}
		}
		return groupIDsToStructureIDs;
	}
	
	public static Map<Identifier, Identifier> getStructureIDsToGroupIDs(ServerWorld world) {
		Map<Identifier, Identifier> structureIDsToGroupIDs = new HashMap<Identifier, Identifier>();
		if (getStructureRegistry(world).isPresent()) {
			for (Structure structure : getStructureRegistry(world).get()) {
				structureIDsToGroupIDs.put(getIDForStructure(world, structure), getGroupForStructure(world, structure));
			}
		}
		return structureIDsToGroupIDs;
	}
	
	public static Identifier getGroupForStructure(ServerWorld world, Structure structure) {
		if (getStructureRegistry(world).isPresent()) {
			Registry<StructureSet> registry = getStructureSetRegistry(world).get();
			for (StructureSet set : registry) {
				for (WeightedEntry entry : set.structures()) {
					if (entry.structure().value().equals(structure)) {
						return registry.getId(set);
					}
				}
			}
		}
		return Identifier.of(ExplorersCompass.MODID, "none");
	}

	public static Identifier getIDForStructure(ServerWorld world, Structure structure) {
		if (getStructureRegistry(world).isPresent()) {
			return getStructureRegistry(world).get().getId(structure);
		}
		return null;
	}

	public static Structure getStructureForID(ServerWorld world, Identifier id) {
		if (getStructureRegistry(world).isPresent()) {
			return getStructureRegistry(world).get().get(id);
		}
		return null;
	}
	
	public static RegistryEntry<Structure> getEntryForStructure(ServerWorld world, Structure structure) {
		if (getStructureRegistry(world).isPresent()) {
			final Identifier structureID = getIDForStructure(world, structure);
			if (structureID != null) {
				return getStructureRegistry(world).get().getEntry(structureID).get();
			}
		}
		return null;
	}

	public static List<Identifier> getAllowedStructureIDs(ServerWorld world) {
		final List<Identifier> structureIDs = new ArrayList<Identifier>();
		if (getStructureRegistry(world).isPresent()) {
			for (Structure structure : getStructureRegistry(world).get()) {
				if (structure != null && getIDForStructure(world, structure) != null && !getIDForStructure(world, structure).getNamespace().isEmpty() && !getIDForStructure(world, structure).getPath().isEmpty() && !structureIsBlacklisted(world, structure) && !structureIsHidden(world, structure)) {
					structureIDs.add(getIDForStructure(world, structure));
				}
			}
		}

		return structureIDs;
	}
	
	public static boolean structureIsBlacklisted(ServerWorld world, Structure structure) {
		final List<String> structureBlacklist = ExplorersCompassConfig.structureBlacklist;
		for (String structureKey : structureBlacklist) {
			if (getIDForStructure(world, structure).toString().matches(convertToRegex(structureKey))) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean structureIsHidden(ServerWorld world, Structure structure) {
		if (getStructureRegistry(world).isPresent()) {
			final Registry<Structure> structureRegistry = getStructureRegistry(world).get();
			final Identifier structureID = getIDForStructure(world, structure);
			if (structureID != null && structureRegistry.getEntry(structureID).isPresent()) {
				final RegistryEntry<Structure> structureEntry = structureRegistry.getEntry(structureID).get();
				return structureEntry.streamTags().anyMatch(tag -> tag.id().getPath().equals("c:hidden_from_locator_selection"));
			}
		}
		return false;
	}
	
	public static List<Identifier> getGeneratingDimensionIDs(ServerWorld serverWorld, Structure structure) {
		final List<Identifier> dimensions = new ArrayList<Identifier>();
		for (ServerWorld world : serverWorld.getServer().getWorlds()) {
			ChunkGenerator chunkGenerator = world.getChunkManager().getChunkGenerator();
			Set<RegistryEntry<Biome>> biomeSet = chunkGenerator.getBiomeSource().getBiomes();
			if (!structure.getValidBiomes().stream().noneMatch(biomeSet::contains)) {
				dimensions.add(world.getRegistryKey().getValue());
			}
		}
		// Fix empty dimensions for stronghold
		if (structure == StructureType.STRONGHOLD && dimensions.isEmpty()) {
			dimensions.add(Identifier.of("minecraft:overworld"));
		}
		return dimensions;
	}

	public static ListMultimap<Identifier, Identifier> getGeneratingDimensionIDsForAllowedStructures(ServerWorld serverWorld) {
		ListMultimap<Identifier, Identifier> dimensionsForAllowedStructures = ArrayListMultimap.create();
		for (Identifier id : getAllowedStructureIDs(serverWorld)) {
			Structure structure = getStructureForID(serverWorld, id);
			dimensionsForAllowedStructures.putAll(id, getGeneratingDimensionIDs(serverWorld, structure));
		}
		return dimensionsForAllowedStructures;
	}

	public static int getHorizontalDistanceToLocation(PlayerEntity player, int x, int z) {
		return getHorizontalDistanceToLocation(player.getBlockPos(), x, z);
	}

	public static int getHorizontalDistanceToLocation(BlockPos startPos, int x, int z) {
		return (int) MathHelper.sqrt((float) startPos.getSquaredDistance(new BlockPos(x, startPos.getY(), z)));
	}

	@Environment(EnvType.CLIENT)
	public static String getStructureName(Identifier id) {
		if (id == null) {
			return "";
		}
		String name = id.toString();
		if (ExplorersCompassConfig.translateStructureNames) {
			name = I18n.translate(Util.createTranslationKey("structure", id));
		}
		if (name.equals(Util.createTranslationKey("structure", id)) || !ExplorersCompassConfig.translateStructureNames) {
			name = id.toString();
			if (name.contains(":")) {
				name = name.substring(name.indexOf(":") + 1);
			}
			name = WordUtils.capitalize(name.replace('_', ' '));
		}
		return name;
	}

	@Environment(EnvType.CLIENT)
	public static String getStructureSource(Identifier id) {
		if (id == null) {
			return "";
		}
		String registryEntry = id.toString();
		String modid = registryEntry.substring(0, registryEntry.indexOf(":"));
		if (modid.equals("minecraft")) {
			return "Minecraft";
		}
		Optional<? extends ModContainer> sourceContainer = FabricLoader.getInstance().getModContainer(modid);
		if (sourceContainer.isPresent()) {
			return sourceContainer.get().getMetadata().getName();
		}
		return modid;
	}

	@Environment(EnvType.CLIENT)
	public static String structureDimensionsToString(List<Identifier> dimensions) {
		String str = "";
		if (dimensions != null && dimensions.size() > 0) {
			str = getDimensionName(dimensions.get(0));
			for (int i = 1; i < dimensions.size(); i++) {
				str += ", " + getDimensionName(dimensions.get(i));
			}
		}
		return str;
	}

	@Environment(EnvType.CLIENT)
	private static String getDimensionName(Identifier dimensionKey) {
		String name = I18n.translate(Util.createTranslationKey("dimension", dimensionKey));
		if (name.equals(Util.createTranslationKey("dimension", dimensionKey))) {
			name = dimensionKey.toString();
			if (name.contains(":")) {
				name = name.substring(name.indexOf(":") + 1);
			}
			name = WordUtils.capitalize(name.replace('_', ' '));
		}
		return name;
	}
	
	private static Optional<Registry<Structure>> getStructureRegistry(ServerWorld world) {
		return world.getRegistryManager().getOptional(RegistryKeys.STRUCTURE);
	}
	
	private static Optional<Registry<StructureSet>> getStructureSetRegistry(ServerWorld world) {
		return world.getRegistryManager().getOptional(RegistryKeys.STRUCTURE_SET);
	}

	private static String convertToRegex(String glob) {
		String regex = "^";
		for (char i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			if (c == '*') {
				regex += ".*";
			} else if (c == '?') {
				regex += ".";
			} else if (c == '.') {
				regex += "\\.";
			} else {
				regex += c;
			}
		}
		regex += "$";
		return regex;
	}

}