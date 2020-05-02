package net.imprex.orebfuscator.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import net.imprex.orebfuscator.util.OFCLogger;

public class ConfigParser {

	public static List<ConfigurationSection> serializeSectionList(ConfigurationSection parentSection, String path) {
		List<ConfigurationSection> sections = new ArrayList<>();

		List<?> sectionList = parentSection.getList(path);
		if (sectionList != null) {
			for (int i = 0; i < sectionList.size(); i++) {
				Object section = sectionList.get(i);
				if (section instanceof Map) {
					sections.add(ConfigParser.convertMapsToSections((Map<?, ?>) section,
							parentSection.createSection(path + "-" + i)));
				}
			}
		}

		return sections;
	}

	private static ConfigurationSection convertMapsToSections(Map<?, ?> input, ConfigurationSection section) {
		for (Map.Entry<?, ?> entry : input.entrySet()) {
			String key = entry.getKey().toString();
			Object value = entry.getValue();

			if (value instanceof Map) {
				convertMapsToSections((Map<?, ?>) value, section.createSection(key));
			} else {
				section.set(key, value);
			}
		}
		return section;
	}

	public static void serializeWorldList(ConfigurationSection section, List<World> worlds, String path) {
		worlds.clear();

		List<String> worldNameList = section.getStringList(path);
		if (worldNameList == null || worldNameList.isEmpty()) {
			return;
		}

		for (String worldName : worldNameList) {
			World world = Bukkit.getWorld(worldName);

			if (world == null) {
				OFCLogger.warn(String.format("config section '%s.%s' contains unknown world '%s'",
						section.getCurrentPath(), path, worldName));
				continue;
			}

			worlds.add(world);
		}
	}

	public static void serializeRandomMaterialList(ConfigurationSection section, Map<Material, Integer> randomBlocks,
			String path) {
		randomBlocks.clear();

		ConfigurationSection materialSection = section.getConfigurationSection(path);
		if (materialSection == null) {
			return;
		}

		Set<String> materialNames = materialSection.getKeys(false);
		if (materialNames.isEmpty()) {
			return;
		}

		for (String materialName : materialNames) {
			Material material = Material.matchMaterial(materialName);

			if (material == null) {
				OFCLogger.warn(String.format("config section '%s.%s' contains unknown block '%s'",
						section.getCurrentPath(), path, materialName));
				continue;
			}

			if (materialSection.isInt(materialName)) {
				randomBlocks.put(material, materialSection.getInt(materialName, 1));
			} else {
				randomBlocks.put(material, 1);
			}
		}
	}
}
