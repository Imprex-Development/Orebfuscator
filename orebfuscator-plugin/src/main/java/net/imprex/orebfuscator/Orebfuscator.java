package net.imprex.orebfuscator;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.imprex.orebfuscator.cache.ChunkCache;
import net.imprex.orebfuscator.config.OrebfuscatorConfig;
import net.imprex.orebfuscator.obfuscation.Obfuscator;
import net.imprex.orebfuscator.obfuscation.PacketListener;
import net.imprex.orebfuscator.proximityhider.ProximityHider;
import net.imprex.orebfuscator.proximityhider.ProximityListener;
import net.imprex.orebfuscator.proximityhider.ProximityPacketListener;
import net.imprex.orebfuscator.util.OFCLogger;

public class Orebfuscator extends JavaPlugin implements Listener {

	private OrebfuscatorConfig config;
	private ChunkCache chunkCache;
	private Obfuscator obfuscator;
	private ProximityHider proximityHider;
	private PacketListener packetListener;
	private ProximityPacketListener proximityPacketListener;

	@Override
	public void onEnable() {
		try {
			// Check if protocolLib is enabled
			if (this.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
				OFCLogger.log("[OFC] ProtocolLib is not found! Plugin cannot be enabled.");
				return;
			}

			// Load configurations
			this.config = new OrebfuscatorConfig(this);

			// Load chunk cache	
			this.chunkCache = new ChunkCache(this);

			// Load obfuscater
			this.obfuscator = new Obfuscator(this);

			// Load proximity hider
			this.proximityHider = new ProximityHider(this);
			if (this.config.proximityEnabled()) {
				this.proximityHider.start();

				this.getServer().getPluginManager().registerEvents(new ProximityListener(this), this);
			}

			// Load packet listener
			this.packetListener = new PacketListener(this);
			this.proximityPacketListener = new ProximityPacketListener(this);
		} catch(Exception e) {
			OFCLogger.log(e);
			OFCLogger.log(Level.SEVERE, "An error occurred by enabling plugin");

			this.getServer().getPluginManager().registerEvent(PluginEnableEvent.class, this, EventPriority.NORMAL, (listener, event) -> {
				PluginEnableEvent enableEvent = (PluginEnableEvent) event;

				if (enableEvent.getPlugin() == this) {
					HandlerList.unregisterAll(listener);
					Bukkit.getPluginManager().disablePlugin(this);
				}
			}, this);
		}
	}

	@Override
	public void onDisable() {
		NmsInstance.get().getRegionFileCache().clear();
		this.chunkCache.invalidateAll(true);
		this.packetListener.unregister();
		this.proximityPacketListener.unregister();
		this.proximityHider.destroy();

		this.getServer().getScheduler().cancelTasks(this);

		this.config = null;
	}

	public void runTask(Runnable runnable) {
		this.getServer().getScheduler().runTask(this, runnable);
	}

	public OrebfuscatorConfig getOrebfuscatorConfig() {
		return this.config;
	}

	public ChunkCache getChunkCache() {
		return this.chunkCache;
	}

	public Obfuscator getObfuscator() {
		return this.obfuscator;
	}

	public ProximityHider getProximityHider() {
		return this.proximityHider;
	}

	public PacketListener getPacketListener() {
		return this.packetListener;
	}

	public ProximityPacketListener getProximityPacketListener() {
		return this.proximityPacketListener;
	}
}