package net.imprex.orebfuscator.cache;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import net.imprex.orebfuscator.Orebfuscator;
import net.imprex.orebfuscator.config.CacheConfig;
import net.imprex.orebfuscator.util.ChunkPosition;

public class ChunkCache {

	private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

	public static final byte[] hash(byte[] configHash, byte[] chunkData) {
		Hasher hasher = HASH_FUNCTION.newHasher();
		hasher.putBytes(configHash);
		hasher.putBytes(chunkData);
		return hasher.hash().asBytes();
	}

	private final CacheConfig cacheConfig;

	private final Cache<ChunkPosition, ChunkCacheEntry> cache;
	private final ChunkCacheSerializer serializer;

	public ChunkCache(Orebfuscator orebfuscator) {
		this.cacheConfig = orebfuscator.getOrebfuscatorConfig().cache();

		this.cache = CacheBuilder.newBuilder()
				.maximumSize(this.cacheConfig.maximumSize())
				.expireAfterAccess(this.cacheConfig.expireAfterAccess(), TimeUnit.MILLISECONDS)
				.removalListener(this::onRemoval).build();

		this.serializer = new ChunkCacheSerializer();
	}

	private void onRemoval(RemovalNotification<ChunkPosition, ChunkCacheEntry> notification) {
		if (notification.wasEvicted()) {
			try {
				this.serializer.write(notification.getKey(), notification.getValue());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private ChunkCacheEntry load(ChunkPosition key) {	
		try {
			return this.serializer.read(key);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public ChunkCacheEntry get(ChunkPosition key, byte[] hash, Function<ChunkPosition, ChunkCacheEntry> mappingFunction) {
		Objects.requireNonNull(mappingFunction);

		// check if live cache entry is present and valid
		ChunkCacheEntry cacheEntry = this.cache.getIfPresent(key);
		if (cacheEntry != null && Arrays.equals(cacheEntry.getHash(), hash)) {
			return cacheEntry;
		}

		// check if disk cache entry is present and valid
		cacheEntry = this.load(key);
		if (cacheEntry != null && Arrays.equals(cacheEntry.getHash(), hash)) {
			this.cache.put(key, Objects.requireNonNull(cacheEntry));
			return cacheEntry;
		}

		// create new entry no valid ones found
		cacheEntry = mappingFunction.apply(key);
		this.cache.put(key, Objects.requireNonNull(cacheEntry));
		return cacheEntry;
	}

	public void invalidate(ChunkPosition key) {
		this.cache.invalidate(key);
		try {
			this.serializer.write(key, null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void invalidateAll(boolean save) {
		if (save) {
			this.cache.asMap().entrySet().removeIf(entry -> {
				try {
					this.serializer.write(entry.getKey(), entry.getValue());
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;
			});
		} else {
			this.cache.invalidateAll();
		}
	}
}
