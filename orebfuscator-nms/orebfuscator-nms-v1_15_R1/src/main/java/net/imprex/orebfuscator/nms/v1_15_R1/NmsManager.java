/**
 * @author lishid
 * @author Aleksey Terzi
 *
 */

package net.imprex.orebfuscator.nms.v1_15_R1;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_15_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_15_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_15_R1.util.CraftChatMessage;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.lishid.orebfuscator.nms.IBlockInfo;
import com.lishid.orebfuscator.nms.IChunkCache;
import com.lishid.orebfuscator.nms.INBT;
import com.lishid.orebfuscator.nms.INmsManager;
import com.lishid.orebfuscator.types.ConfigDefaults;

import net.imprex.orebfuscator.util.BlockCoords;
import net.minecraft.server.v1_15_R1.Block;
import net.minecraft.server.v1_15_R1.BlockPosition;
import net.minecraft.server.v1_15_R1.Chunk;
import net.minecraft.server.v1_15_R1.ChunkProviderServer;
import net.minecraft.server.v1_15_R1.IBlockData;
import net.minecraft.server.v1_15_R1.IChatBaseComponent;
import net.minecraft.server.v1_15_R1.Packet;
import net.minecraft.server.v1_15_R1.PacketPlayOutBlockChange;
import net.minecraft.server.v1_15_R1.TileEntity;

public class NmsManager implements INmsManager {

	private final ConfigDefault configDefaults;
	private final ProtocolManager protocolManager;

	private int maxLoadedCacheFiles;

	public NmsManager() {
		this.protocolManager = ProtocolLibrary.getProtocolManager();
		this.configDefaults = new ConfigDefault();
	}

	@Override
	public ConfigDefaults getConfigDefaults() {
		return this.configDefaults;
	}

	@Override
	public void setMaxLoadedCacheFiles(int value) {
		this.maxLoadedCacheFiles = value;
	}

	@Override
	public INBT createNBT() {
		return new NBT();
	}

	@Override
	public IChunkCache createChunkCache() {
		return new ChunkCache(this.maxLoadedCacheFiles);
	}

	@Override
	public void updateBlockTileEntity(BlockCoords blockCoord, Player player) {
		try {
			CraftWorld world = (CraftWorld) player.getWorld();
			TileEntity tileEntity = world.getHandle().getTileEntity(new BlockPosition(blockCoord.x, blockCoord.y, blockCoord.z));

			if (tileEntity == null) {
				return;
			}

			Packet<?> packet = tileEntity.getUpdatePacket();
			if (packet != null) {
				((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
			}

			// TODO check if this work
//			PacketContainer packet = this.protocolManager.createPacket(PacketType.Play.Server.TILE_ENTITY_DATA);
//			packet.getBlockPositionModifier().write(0, new com.comphenix.protocol.wrappers.BlockPosition(blockCoord.x, blockCoord.y, blockCoord.z));
//			packet.getIntegers().write(0, tileEntity.getBlock().h());
//
//			NbtCompound nbt = NbtFactory.ofCompound("");
//			tileEntity.save((NBTTagCompound) nbt);
//			packet.getNbtModifier().write(0, nbt);
//
//			protocolManager.sendServerPacket(player, packet);
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
		return this.configDefaults.BLOCK_ID_SIGNS.contains(combinedBlockId);
	}

	@Override
	public boolean isAir(int combinedBlockId) {
		return this.configDefaults.BLOCK_ID_AIRS.contains(combinedBlockId);
	}

	@Override
	public boolean isTileEntity(int combinedBlockId) {
		return Block.getByCombinedId(combinedBlockId).getBlock().isTileEntity();
	}

	@Override
	public int getCaveAirBlockId() {
		return this.configDefaults.BLOCK_ID_CAVE_AIR;
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
	public Set<Integer> getMaterialIds(Material material) {
		return this.configDefaults.materialIds.get(material);
	}

	@Override
	public boolean sendBlockChange(Player player, Location location) {
		IBlockData blockData = this.getBlockData(location.getWorld(), location.getBlockX(), location.getBlockY(),
				location.getBlockZ(), false);

		if (blockData == null)
			return false;

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

		protocolManager.sendServerPacket(player, packet);
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

		if (!loadChunk && !chunkProviderServer.isLoaded(chunkX, chunkZ))
			return null;

		Chunk chunk = chunkProviderServer.getChunkAt(chunkX, chunkZ, true);
		return chunk != null ? chunk.getType(new BlockPosition(x, y, z)) : null;
	}
}