package com.lishid.orebfuscator.nms.v1_13_R2;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_13_R2.util.CraftChatMessage;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.lishid.orebfuscator.nms.IBlockInfo;

import net.imprex.orebfuscator.config.CacheConfig;
import net.imprex.orebfuscator.config.Config;
import net.imprex.orebfuscator.nms.AbstractNmsManager;
import net.imprex.orebfuscator.nms.AbstractRegionFileCache;
import net.imprex.orebfuscator.nms.v1_13_R2.RegionFileCache;
import net.imprex.orebfuscator.util.BlockCoords;
import net.minecraft.server.v1_13_R2.Block;
import net.minecraft.server.v1_13_R2.BlockPosition;
import net.minecraft.server.v1_13_R2.Chunk;
import net.minecraft.server.v1_13_R2.ChunkProviderServer;
import net.minecraft.server.v1_13_R2.EntityPlayer;
import net.minecraft.server.v1_13_R2.IBlockData;
import net.minecraft.server.v1_13_R2.IChatBaseComponent;
import net.minecraft.server.v1_13_R2.Packet;
import net.minecraft.server.v1_13_R2.PacketPlayOutBlockChange;
import net.minecraft.server.v1_13_R2.TileEntity;
import net.minecraft.server.v1_13_R2.WorldServer;

public class NmsManager extends AbstractNmsManager {

	private final int blockIdCaveAir;
	private final Set<Integer> blockIdAir;
	private final Set<Integer> blockIdSign;
	private final ProtocolManager protocolManager;

	public NmsManager(Config config) {
		super(config);

		this.protocolManager = ProtocolLibrary.getProtocolManager();

		for (Iterator<IBlockData> iterator = Block.REGISTRY_ID.iterator(); iterator.hasNext(); ) {
			IBlockData blockData = iterator.next();
			Material material = CraftBlockData.fromData(blockData).getMaterial();
			int id = Block.getCombinedId(blockData);
			this.registerMaterialId(material, id);
		}

		this.blockIdCaveAir = this.getMaterialIds(Material.CAVE_AIR).iterator().next();
		this.blockIdAir = this
				.convertMaterialsToSet(new Material[] { Material.AIR, Material.CAVE_AIR, Material.VOID_AIR });
		this.blockIdSign = this.convertMaterialsToSet(new Material[] { Material.SIGN, Material.WALL_SIGN });
	}

	@Override
	protected AbstractRegionFileCache<?> createRegionFileCache(CacheConfig cacheConfig) {
		return new RegionFileCache(cacheConfig);
	}

	@Override
	public int getMaterialSize() {
		return Block.REGISTRY_ID.a();
	}

	@Override
	public void updateBlockTileEntity(BlockCoords blockCoord, Player player) {
		try {
			EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
			WorldServer world = entityPlayer.getWorldServer();

			TileEntity tileEntity = world.getTileEntity(new BlockPosition(blockCoord.x, blockCoord.y, blockCoord.z));
			if (tileEntity == null) {
				return;
			}

			Packet<?> packet = tileEntity.getUpdatePacket();
			if (packet != null) {
				entityPlayer.playerConnection.sendPacket(packet);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void notifyBlockChange(World world, IBlockInfo blockInfo) {
		IBlockData blockData = ((BlockInfo) blockInfo).getBlockData();
		((CraftWorld) world).getHandle().notify(new BlockPosition(blockInfo.getX(), blockInfo.getY(), blockInfo.getZ()),
				blockData, blockData, 0);
	}

	@Override
	public int getBlockLightLevel(World world, int x, int y, int z) {
		return ((CraftWorld) world).getHandle().getLightLevel(new BlockPosition(x, y, z));
	}

	@Override
	public IBlockInfo getBlockInfo(World world, int x, int y, int z) {
		IBlockData blockData = this.getBlockData(world, x, y, z, false);
		return blockData != null ? new BlockInfo(x, y, z, blockData) : null;
	}

	@Override
	public int loadChunkAndGetBlockId(World world, int x, int y, int z) {
		IBlockData blockData = this.getBlockData(world, x, y, z, true);
		return blockData != null ? Block.getCombinedId(blockData) : -1;
	}

	@Override
	public String getTextFromChatComponent(String json) {
		return CraftChatMessage.fromComponent(IChatBaseComponent.ChatSerializer.a(json));
	}

	@Override
	public boolean isHoe(Material item) {
		return item == Material.WOODEN_HOE || item == Material.STONE_HOE || item == Material.IRON_HOE
				|| item == Material.GOLDEN_HOE || item == Material.DIAMOND_HOE;
	}

	@Override
	public boolean isSign(int combinedBlockId) {
		return this.blockIdSign.contains(combinedBlockId);
	}

	@Override
	public boolean isAir(int combinedBlockId) {
		return this.blockIdAir.contains(combinedBlockId);
	}

	@Override
	public boolean isTileEntity(int combinedBlockId) {
		return Block.getByCombinedId(combinedBlockId).getBlock().isTileEntity();
	}

	@Override
	public int getCaveAirBlockId() {
		return this.blockIdCaveAir;
	}

	@Override
	public int getBitsPerBlock() {
		return 14;
	}

	@Override
	public boolean canApplyPhysics(Material blockMaterial) {
		return blockMaterial == Material.AIR || blockMaterial == Material.CAVE_AIR || blockMaterial == Material.VOID_AIR
				|| blockMaterial == Material.FIRE || blockMaterial == Material.WATER || blockMaterial == Material.LAVA;
	}

	@Override
	public boolean sendBlockChange(Player player, Location location) {
		IBlockData blockData = this.getBlockData(location.getWorld(), location.getBlockX(), location.getBlockY(),
				location.getBlockZ(), false);

		if (blockData == null) {
			return false;
		}

		PacketPlayOutBlockChange packet = new PacketPlayOutBlockChange(((CraftWorld) location.getWorld()).getHandle(),
				new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
		packet.block = blockData;

		((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
		return true;
	}

	@Override
	public void sendMultiBlockChange(Player player, int chunkX, int chunkZ, Location... locations)
			throws IllegalAccessException, InvocationTargetException {
		if (locations.length == 0) {
			return;
		}

		PacketContainer packet = this.protocolManager.createPacket(PacketType.Play.Server.MULTI_BLOCK_CHANGE);
		MultiBlockChangeInfo[] blockInfoArray = new MultiBlockChangeInfo[locations.length];

		int index = 0;
		for (Location location : locations) {
			org.bukkit.block.Block block = location.getBlock();

			if (block == null || location.getBlockX() >> 4 != chunkX || location.getBlockZ() >> 4 != chunkZ) {
				index++;
				continue;
			}

			blockInfoArray[index++] = new MultiBlockChangeInfo(location, WrappedBlockData.createData(block.getType()));
		}

		packet.getChunkCoordIntPairs().write(0, new ChunkCoordIntPair(chunkX, chunkZ));
		packet.getMultiBlockChangeInfoArrays().write(0, blockInfoArray);

		this.protocolManager.sendServerPacket(player, packet);
	}

	@Override
	public boolean hasLightArray() {
		return false;
	}

	@Override
	public boolean hasBlockCount() {
		return true;
	}

	private IBlockData getBlockData(World world, int x, int y, int z, boolean loadChunk) {
		int chunkX = x >> 4;
		int chunkZ = z >> 4;

		ChunkProviderServer chunkProviderServer = ((CraftWorld) world).getHandle().getChunkProvider();

		if (!loadChunk && !chunkProviderServer.isLoaded(chunkX, chunkZ)) {
			return null;
		}

		Chunk chunk = chunkProviderServer.getChunkAt(chunkX, chunkZ, true, true);
		return chunk != null ? chunk.getType(new BlockPosition(x, y, z)) : null;
	}

	private Set<Integer> convertMaterialsToSet(Material[] materials) {
		Set<Integer> ids = new HashSet<>();

		for (Material material : materials) {
			ids.addAll(this.getMaterialIds(material));
		}

		return ids;
	}
}