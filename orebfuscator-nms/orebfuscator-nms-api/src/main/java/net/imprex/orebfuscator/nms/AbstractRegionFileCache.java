package net.imprex.orebfuscator.nms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.imprex.orebfuscator.config.CacheConfig;
import net.imprex.orebfuscator.util.ChunkPosition;

public abstract class AbstractRegionFileCache<T> {

	protected final ReadWriteLock lock = new ReentrantReadWriteLock(true);
	protected final Map<Path, T> regionFiles = new HashMap<>();

	protected final CacheConfig cacheConfig;

	public AbstractRegionFileCache(CacheConfig cacheConfig) {
		this.cacheConfig = cacheConfig;
	}

	public abstract DataInputStream getInputStream(Path path, ChunkPosition key) throws IOException;

	public abstract DataOutputStream getOutputStream(Path path, ChunkPosition key) throws IOException;

	protected abstract T create(Path path) throws IOException;

	protected abstract void close(T t) throws IOException;

	protected final T get(Path path) throws IOException {
		this.lock.readLock().lock();
		try {
			T t = this.regionFiles.get(path);
			if (t != null) {
				return t;
			}
		} finally {
			this.lock.readLock().unlock();
		}

		if (Files.notExists(path.getParent())) {
			Files.createDirectories(path.getParent());
		}

		if (this.regionFiles.size() >= this.cacheConfig.maximumOpenRegionFiles()) {
			this.clear();
		}

		T t = Objects.requireNonNull(this.create(path));

		this.lock.writeLock().lock();
		try {
			this.regionFiles.putIfAbsent(path, t);
			return this.regionFiles.get(path);
		} finally {
			this.lock.writeLock().unlock();
		}
	}

	public final void close(Path path) throws IOException {
		T t = null;

		this.lock.writeLock().lock();
		try {
			t = this.regionFiles.remove(path);
		} finally {
			this.lock.writeLock().unlock();
		}

		if (t != null) {
			this.close(t);
		}
	}

	public final void clear() {
		this.lock.writeLock().lock();
		try {
			for (T t : this.regionFiles.values()) {
				try {
					if (t != null) {
						this.close(t);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			this.regionFiles.clear();
		} finally {
			this.lock.writeLock().unlock();
		}
	}
}
