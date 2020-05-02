package net.imprex.orebfuscator.obfuscation;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.bukkit.World;

import com.lishid.orebfuscator.obfuscation.CalculationsUtil;

import net.imprex.orebfuscator.NmsInstance;
import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.cache.ChunkCache;
import net.imprex.orebfuscator.cache.ChunkCacheEntry;
import net.imprex.orebfuscator.chunk.Chunk;
import net.imprex.orebfuscator.chunk.ChunkSection;
import net.imprex.orebfuscator.chunk.ChunkStruct;
import net.imprex.orebfuscator.config.BlockMask;
import net.imprex.orebfuscator.config.OrebfuscatorConfig;
import net.imprex.orebfuscator.config.ProximityConfig;
import net.imprex.orebfuscator.config.WorldConfig;
import net.imprex.orebfuscator.util.BlockCoords;
import net.imprex.orebfuscator.util.ChunkPosition;
import net.imprex.orebfuscator.util.MaterialUtil;
import net.imprex.orebfuscator.util.MathUtil;

public class Obfuscator {

	private final OrebfuscatorConfig config;
	private final ChunkCache chunkCache;

	public Obfuscator(Orebfuscator orebfuscator) {
		this.config = orebfuscator.getOrebfuscatorConfig();
		this.chunkCache = orebfuscator.getChunkCache();
	}

	private LinkedList<Long> avgTimes = new LinkedList<>();
	private double calls = 0;
	private DecimalFormat formatter = new DecimalFormat("###,###,###,###.00");

	public ChunkCacheEntry obfuscateOrUseCache(World world, ChunkStruct chunkStruct) {
		long time = System.nanoTime();
		try {
			return obfuscateOrUseCache0(world, chunkStruct);
		} finally {
			long diff = System.nanoTime() - time;

			avgTimes.add(diff);
			if (avgTimes.size() > 1000) {
				avgTimes.removeFirst();
			}

			if (calls++ % 100 == 0) {
				System.out.println("avg: "
						+ formatter.format(
								((double) avgTimes.stream().reduce(0L, Long::sum) / (double) avgTimes.size()) / 1000D)
						+ "μs");
			}
		}
	}

	public ChunkCacheEntry obfuscateOrUseCache0(World world, ChunkStruct chunkStruct) {
		if (chunkStruct.primaryBitMask == 0) {
			return null;
		}

		final ChunkPosition position = new ChunkPosition(world.getName(), chunkStruct.chunkX, chunkStruct.chunkZ);
		final long hash = CalculationsUtil.Hash(chunkStruct.data, chunkStruct.data.length, this.config.hash());

		if (this.config.cache().enabled()) {
			return this.chunkCache.get(position, hash, key -> this.obfuscate(hash, world, chunkStruct));
		} else {
			return this.obfuscate(hash, world, chunkStruct);
		}
	}

	private ChunkCacheEntry obfuscate(long hash, World world, ChunkStruct chunkStruct) {
		BlockMask blockMask = this.config.blockMask(world);
		WorldConfig worldConfig = this.config.world(world);
		ProximityConfig proximityConfig = this.config.proximity(world);
		int initialRadius = this.config.general().initialRadius();

		Set<BlockCoords> proximityBlocks = new HashSet<>();
		Set<BlockCoords> removedTileEntities = new HashSet<>();

		int baseX = chunkStruct.chunkX << 4;
		int baseZ = chunkStruct.chunkZ << 4;

		try (Chunk chunk = Chunk.fromChunkStruct(chunkStruct)) {
			for (int sectionIndex = 0; sectionIndex < chunk.getSectionCount(); sectionIndex++) {
				ChunkSection chunkSection = chunk.nextChunkSection();
				// TODO faster buffer + pre calc palette

				final int baseY = sectionIndex * 16;
				for (int index = 0; index < 4096; index++) {
					int blockData = chunkSection.getBlock(index);

					int y = baseY + (index >> 8 & 15);

					int obfuscateBits = blockMask.mask(blockData, y);
					if ((obfuscateBits & 0xF) == 0) {
						continue;
					}

					int x = baseX + (index & 15);
					int z = baseZ + (index >> 4 & 15);

					boolean obfuscateFlag = (obfuscateBits & BlockMask.BLOCK_MASK_OBFUSCATE) != 0;
					boolean darknessFlag = (obfuscateBits & BlockMask.BLOCK_MASK_DARKNESS) != 0;
					boolean tileEntityFlag = (obfuscateBits & BlockMask.BLOCK_MASK_TILEENTITY) != 0;
					boolean proximityFlag = (obfuscateBits & BlockMask.BLOCK_MASK_PROXIMITY) != 0;

					boolean obfuscate = proximityFlag;

					// Check if the block should be obfuscated
					if (obfuscateFlag) {
						if (initialRadius == 0) {
							obfuscate = true;
						} else if (!areAjacentBlocksTransparent(world, x, y, z, false, initialRadius)) {
							obfuscate = true;
						}
					}

					// Check if the block should be obfuscated because of proximity check
					if (!obfuscate && proximityFlag) {
						proximityBlocks.add(new BlockCoords(x, y, z));
						obfuscate = true;
					}

					// Check if the block is obfuscated
					if (obfuscate) {
						if (proximityFlag) {
							blockData = proximityConfig.randomBlockId();
						} else {
							blockData = worldConfig.randomBlockId();
						}
						chunkSection.setBlock(index, blockData);
					}

					// Check if the block should be obfuscated because of the darkness
					if (!obfuscate && darknessFlag && worldConfig.darknessBlocksEnabled()) {
						if (!MathUtil.areAjacentBlocksBright(world, x, y, z, 1)) {
							// Hide block, setting it to air
							chunkSection.setBlock(index, NmsInstance.get().getCaveAirBlockId());
							obfuscate = true;
						}
					}

					// remove obfuscated tile entities
					if (obfuscate && tileEntityFlag) {
						removedTileEntities.add(new BlockCoords(x, y, z));
					}
				}

				chunk.writeChunkSection(chunkSection);
			}

			byte[] data = chunk.finalizeOutput();

			return new ChunkCacheEntry(hash, data, proximityBlocks, removedTileEntities);
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error(e);
		}
	}

	private static boolean areAjacentBlocksTransparent(World world, int x, int y, int z, boolean check, int depth) {
		if (y >= world.getMaxHeight() || y < 0) {
			return true;
		}

		if (check) {
			int blockId = NmsInstance.get().loadChunkAndGetBlockId(world, x, y, z);
			if (blockId >= 0 && MaterialUtil.isTransparent(blockId)) {
				return true;
			}
		}

		if (depth == 0) {
			return false;
		}

		if (areAjacentBlocksTransparent(world, x, y + 1, z, true, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(world, x, y - 1, z, true, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(world, x + 1, y, z, true, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(world, x - 1, y, z, true, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(world, x, y, z + 1, true, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksTransparent(world, x, y, z - 1, true, depth - 1)) {
			return true;
		}

		return false;
	}

	public static boolean areAjacentBlocksBright(World world, int x, int y, int z, int depth) {
		if (NmsInstance.get().getBlockLightLevel(world, x, y, z) > 0) {
			return true;
		}

		if (depth == 0) {
			return false;
		}

		if (areAjacentBlocksBright(world, x, y + 1, z, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(world, x, y - 1, z, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(world, x + 1, y, z, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(world, x - 1, y, z, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(world, x, y, z + 1, depth - 1)) {
			return true;
		}
		if (areAjacentBlocksBright(world, x, y, z - 1, depth - 1)) {
			return true;
		}

		return false;
	}
}
