package net.imprex.orebfuscator.nms.v1_15_R1;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import net.imprex.orebfuscator.config.CacheConfig;
import net.imprex.orebfuscator.nms.AbstractRegionFileCache;
import net.imprex.orebfuscator.util.ChunkPosition;
import net.minecraft.server.v1_15_R1.ChunkCoordIntPair;
import net.minecraft.server.v1_15_R1.RegionFile;
import net.minecraft.server.v1_15_R1.RegionFileCompression;

public class RegionFileCache extends AbstractRegionFileCache<RegionFile> {

	public RegionFileCache(CacheConfig cacheConfig) {
		super(cacheConfig);
	}

	@Override
	public DataInputStream getInputStream(Path path, ChunkPosition key) throws IOException {
		return this.get(path).a(new ChunkCoordIntPair(key.getX(), key.getZ()));
	}

	@Override
	public DataOutputStream getOutputStream(Path path, ChunkPosition key) throws IOException {
		return this.get(path).c(new ChunkCoordIntPair(key.getX(), key.getZ()));
	}

	@Override
	protected RegionFile create(Path path) throws IOException {
		return new RegionFile(path, path.getParent(), RegionFileCompression.b);
	}

	@Override
	protected void close(RegionFile t) throws IOException {
		t.close();
	}
}