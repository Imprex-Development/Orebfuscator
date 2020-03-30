package net.imprex.orebfuscator.nms.v1_9_R2;

import org.bukkit.World;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;

import net.imprex.orebfuscator.nms.AbstractBlockState;
import net.minecraft.server.v1_9_R2.Block;
import net.minecraft.server.v1_9_R2.BlockPosition;
import net.minecraft.server.v1_9_R2.IBlockData;
import net.minecraft.server.v1_9_R2.WorldServer;

public class BlockState extends AbstractBlockState<IBlockData> {

	public BlockState(int x, int y, int z, World world, IBlockData state) {
		super(x, y, z, world, state);
	}

	@Override
	public int getBlockId() {
		return Block.getCombinedId(this.state);
	}

	@Override
	public void notifyBlockChange() {
		WorldServer worldServer = ((CraftWorld) this.world).getHandle();
		worldServer.notify(new BlockPosition(this.x, this.y, this.z), this.state, this.state, 0);
	}
}
