package net.imprex.orebfuscator.config;

import java.util.Map;

import org.bukkit.Material;

import net.imprex.orebfuscator.NmsInstance;

public class OrebfuscatorBlockMask implements BlockMask {

	private final short[] blockMask = new short[NmsInstance.get().getMaterialSize()];

	public OrebfuscatorBlockMask(OrebfuscatorWorldConfig worldConfig, OrebfuscatorProximityConfig proximityConfig) {
		if (worldConfig != null && worldConfig.enabled()) {
			for (Material material : worldConfig.getHiddenBlocks()) {
				this.setBlockMask(material, BLOCK_MASK_OBFUSCATE);
			}
			for (Material material : worldConfig.getDarknessBlocks()) {
				this.setBlockMask(material, BLOCK_MASK_DARKNESS);
			}
		}
		if (proximityConfig != null && proximityConfig.enabled()) {
			for (Map.Entry<Material, Short> entry : proximityConfig.getHiddenBlocks()) {
				this.setBlockMask(entry.getKey(), entry.getValue());
			}
		}
	}

	public void setBlockMask(Material material, int mask) {
		for (int blockId : NmsInstance.get().getMaterialIds(material)) {
			int blockMask = this.blockMask[blockId] | mask;

			if (NmsInstance.get().isTileEntity(blockId)) {
				blockMask |= BLOCK_MASK_TILEENTITY;
			}

			this.blockMask[blockId] = (short) blockMask;
		}
	}

	@Override
	public int mask(int blockId) {
		return this.blockMask[blockId];
	}

	@Override
	public int mask(int blockId, int y) {
		short blockMask = this.blockMask[blockId];
		if (OrebfuscatorProximityConfig.matchHideCondition(blockMask, y)) {
			blockMask |= BLOCK_MASK_PROXIMITY;
		}
		return blockMask;
	}
}
