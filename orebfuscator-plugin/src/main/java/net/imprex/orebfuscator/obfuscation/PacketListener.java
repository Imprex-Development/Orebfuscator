package net.imprex.orebfuscator.obfuscation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.World;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.lishid.orebfuscator.Orebfuscator;
import com.lishid.orebfuscator.chunkmap.ChunkData;

import net.imprex.orebfuscator.cache.ChunkCacheEntry;
import net.imprex.orebfuscator.config.OrebfuscatorConfig;
import net.imprex.orebfuscator.config.WorldConfig;
import net.imprex.orebfuscator.util.BlockCoords;
import net.imprex.orebfuscator.util.PermissionUtil;

public class PacketListener extends PacketAdapter {

	private final ProtocolManager protocolManager;

	private final OrebfuscatorConfig config;
	private final Obfuscator obfuscator;

	public PacketListener(Orebfuscator orebfuscator) {
		super(orebfuscator, PacketType.Play.Server.MAP_CHUNK);

		this.protocolManager = ProtocolLibrary.getProtocolManager();
		this.protocolManager.addPacketListener(this);

		this.config = orebfuscator.getOrebfuscatorConfig();
		this.obfuscator = orebfuscator.getObfuscator();
	}

	public void unregister() {
		this.protocolManager.removePacketListener(this);
	}

	@Override
	public void onPacketSending(PacketEvent event) {
		Player player = event.getPlayer();
		if (PermissionUtil.canDeobfuscate(player)) {
			return;
		}

		World world = player.getWorld();
		WorldConfig worldConfig = config.world(world);
		if (worldConfig == null || !worldConfig.enabled()) {
			return;
		}

		PacketContainer packet = event.getPacket();
		StructureModifier<Integer> ints = packet.getIntegers();
		StructureModifier<byte[]> byteArray = packet.getByteArrays();
		StructureModifier<Boolean> bools = packet.getBooleans();
		StructureModifier<List<NbtBase<?>>> nbtList = packet.getListNbtModifier();

		List<NbtBase<?>> tileEntityList = nbtList.read(0);

		ChunkData chunkData = new ChunkData();
		chunkData.chunkX = ints.read(0);
		chunkData.chunkZ = ints.read(1);
		chunkData.groundUpContinuous = bools.read(0);
		chunkData.primaryBitMask = ints.read(2);
		chunkData.data = byteArray.read(0);
		chunkData.isOverworld = event.getPlayer().getWorld().getEnvironment() == World.Environment.NORMAL;
		chunkData.blockEntities = getBlockEntities(tileEntityList);

		ChunkCacheEntry chunkEntry = this.obfuscator.obfuscateOrUseCache(world, chunkData);
		if (chunkEntry != null) {
			byteArray.write(0, chunkEntry.getData());

//			if (tileEntityList != null) {
//				removeBlockEntities(tileEntityList, chunkEntry.getRemovedTileEntities());
//				nbtList.write(0, tileEntityList);
//			}
//
//			ProximityHider.addProximityBlocks(player, chunkData.chunkX, chunkData.chunkZ, chunkEntry.getProximityBlocks());
		}
	}

	private static List<NbtCompound> getBlockEntities(List<NbtBase<?>> tileEntityList) {
		List<NbtCompound> tileEntities = new ArrayList<>();

		if (tileEntityList != null) {
			for (NbtBase<?> tileEntity : tileEntityList) {
				tileEntities.add((NbtCompound) tileEntity);
			}
		}

		return tileEntities;
	}

	private static void removeBlockEntities(List<NbtBase<?>> tileEntityList, Set<BlockCoords> removedTileEntities) {
		for (Iterator<NbtBase<?>> iterator = tileEntityList.iterator(); iterator.hasNext();) {
			NbtCompound tileEntity = (NbtCompound) iterator.next();

			int x = tileEntity.getInteger("x");
			int y = tileEntity.getInteger("y");
			int z = tileEntity.getInteger("z");

			BlockCoords position = new BlockCoords(x, y, z);
			if (removedTileEntities.contains(position)) {
				iterator.remove();
			}
		}
	}
}
