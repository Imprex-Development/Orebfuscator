package net.imprex.orebfuscator.nms.v1_13_R2;

import java.util.Iterator;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.imprex.orebfuscator.config.CacheConfig;
import net.imprex.orebfuscator.config.Config;
import net.imprex.orebfuscator.nms.AbstractBlockState;
import net.imprex.orebfuscator.nms.AbstractNmsManager;
import net.imprex.orebfuscator.nms.AbstractRegionFileCache;
import net.imprex.orebfuscator.util.BlockCoords;
import net.minecraft.server.v1_13_R2.Block;
import net.minecraft.server.v1_13_R2.BlockPosition;
import net.minecraft.server.v1_13_R2.ChunkSection;
import net.minecraft.server.v1_13_R2.EntityPlayer;
import net.minecraft.server.v1_13_R2.IBlockData;
import net.minecraft.server.v1_13_R2.MathHelper;
import net.minecraft.server.v1_13_R2.Packet;
import net.minecraft.server.v1_13_R2.PacketPlayOutBlockChange;
import net.minecraft.server.v1_13_R2.TileEntity;
import net.minecraft.server.v1_13_R2.WorldServer;

public class NmsManager extends AbstractNmsManager {

	private static WorldServer world(World world) {
		return ((CraftWorld) world).getHandle();
	}

	private static EntityPlayer player(Player player) {
		return ((CraftPlayer) player).getHandle();
	}

	private static boolean isChunkLoaded(WorldServer world, int chunkX, int chunkZ) {
		return world.getChunkProvider().isLoaded(chunkX, chunkZ);
	}

	private static IBlockData getBlockData(World world, int x, int y, int z, boolean loadChunk) {
		WorldServer worldServer = world(world);
		if (isChunkLoaded(worldServer, x >> 4, z >> 4) || loadChunk) {
			// will load chunk if not loaded already
			return worldServer.getType(new BlockPosition(x, y, z));
		}
		return null;
	}

	private final int blockIdCaveAir;
	private final Set<Integer> blockIdAir;

	public NmsManager(Config config) {
		super(config);

		for (Iterator<IBlockData> iterator = Block.REGISTRY_ID.iterator(); iterator.hasNext();) {
			IBlockData blockData = iterator.next();
			Material material = CraftBlockData.fromData(blockData).getMaterial();
			int id = Block.getCombinedId(blockData);
			this.registerMaterialId(material, id);
		}

		this.blockIdCaveAir = this.getMaterialIds(Material.CAVE_AIR).iterator().next();
		this.blockIdAir = this.mergeMaterialIds(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);
	}

	@Override
	protected AbstractRegionFileCache<?> createRegionFileCache(CacheConfig cacheConfig) {
		return new RegionFileCache(cacheConfig);
	}

	@Override
	public int getBitsPerBlock() {
		return MathHelper.d(ChunkSection.GLOBAL_PALETTE.a());
	}

	@Override
	public boolean hasLightArray() {
		return false;
	}

	@Override
	public boolean hasBlockCount() {
		return true;
	}

	@Override
	public int getMaterialSize() {
		return Block.REGISTRY_ID.a();
	}

	@Override
	public int getCaveAirBlockId() {
		return this.blockIdCaveAir;
	}

	@Override
	public boolean isHoe(Material material) {
		switch (material) {
		case WOODEN_HOE:
		case STONE_HOE:
		case IRON_HOE:
		case GOLDEN_HOE:
		case DIAMOND_HOE:
			return true;

		default:
			return false;
		}
	}

	@Override
	public boolean isAir(int blockId) {
		return this.blockIdAir.contains(blockId);
	}

	@Override
	public boolean isTileEntity(int blockId) {
		return Block.getByCombinedId(blockId).getBlock().isTileEntity();
	}

	@Override
	public boolean canApplyPhysics(Material material) {
		switch (material) {
		case AIR:
		case CAVE_AIR:
		case VOID_AIR:
		case FIRE:
		case WATER:
		case LAVA:
			return true;

		default:
			return false;
		}
	}

	@Override
	public void updateBlockTileEntity(BlockCoords blockCoord, Player player) {
		EntityPlayer entityPlayer = player(player);
		WorldServer world = entityPlayer.getWorldServer();

		TileEntity tileEntity = world.getTileEntity(new BlockPosition(blockCoord.x, blockCoord.y, blockCoord.z));
		if (tileEntity == null) {
			return;
		}

		Packet<?> packet = tileEntity.getUpdatePacket();
		if (packet != null) {
			entityPlayer.playerConnection.sendPacket(packet);
		}
	}

	@Override
	public int getBlockLightLevel(World world, int x, int y, int z) {
		return world(world).getLightLevel(new BlockPosition(x, y, z));
	}

	@Override
	public AbstractBlockState<?> getBlockState(World world, int x, int y, int z) {
		IBlockData blockData = getBlockData(world, x, y, z, false);
		return blockData != null ? new BlockState(x, y, z, world, blockData) : null;
	}

	@Override
	public int loadChunkAndGetBlockId(World world, int x, int y, int z) {
		IBlockData blockData = getBlockData(world, x, y, z, true);
		return blockData != null ? Block.getCombinedId(blockData) : -1;
	}

	@Override
	public boolean sendBlockChange(Player player, Location location) {
		WorldServer world = world(location.getWorld());
		if (!isChunkLoaded(world, location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
			return false;
		}

		BlockPosition position = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		PacketPlayOutBlockChange packet = new PacketPlayOutBlockChange(world, position);
		player(player).playerConnection.sendPacket(packet);

		return true;
	}
}
