package se.walkercrou.reveries;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.GameRegistry;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.mutable.DisplayNameData;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppedEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Identifiable;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import se.walkercrou.reveries.cmd.CommandRegistrar;
import se.walkercrou.reveries.data.impl.NpcDataManipulatorBuilder;
import se.walkercrou.reveries.data.impl.ReveriesNpcData;
import se.walkercrou.reveries.data.npc.ImmutableNpcData;
import se.walkercrou.reveries.data.npc.NpcData;
import se.walkercrou.reveries.event.EntityListener;
import se.walkercrou.reveries.event.NpcListener;
import se.walkercrou.reveries.property.NpcProperty;
import se.walkercrou.reveries.property.NpcPropertyRegistryModule;
import se.walkercrou.reveries.trait.NpcTrait;
import se.walkercrou.reveries.trait.NpcTraitRegistryModule;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;

@Plugin(id = "reveries", authors = { "windy" })
public final class Reveries {

    public static Reveries INSTANCE;

    @Inject public Logger log;
    @Inject public PluginContainer self;
    @Inject public Game game;

    private final CommandRegistrar commands = new CommandRegistrar(this);
    private final EntityListener entityListener = new EntityListener();
    private final NpcListener npcListener = new NpcListener();

    private final Map<UUID, NpcMonitor> monitors = Maps.newHashMap();

    @Listener
    public void onPreInit(GamePreInitializationEvent event) {
        this.log.info("Initializing...");
        INSTANCE = this;

        GameRegistry reg = this.game.getRegistry();
        reg.registerModule(NpcTrait.class, new NpcTraitRegistryModule());
        reg.registerModule(NpcProperty.class, new NpcPropertyRegistryModule());

        this.game.getDataManager().register(
            ReveriesNpcData.class, ImmutableNpcData.class, new NpcDataManipulatorBuilder());
    }

    @Listener
    public void onStart(GameStartedServerEvent event) {
        this.log.info("Starting...");

        for (World world : this.game.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                Optional<NpcData> npcData = entity.get(NpcData.class);
                if (npcData.isPresent())
                    System.out.println("NPC : " + npcData);
            }
        }

        if (init())
            this.log.info("Started.");
    }

    @Listener
    public void onReload(GameReloadEvent event) {
        this.log.info("Reloading...");
        this.commands.deregister();
        EventManager events = this.game.getEventManager();
        events.unregisterListeners(this.entityListener);
        events.unregisterListeners(this.npcListener);
        if (init())
            this.log.info("Reloaded.");
    }

    public boolean init() {
        EventManager events = this.game.getEventManager();
        events.registerListeners(this, this.entityListener);
        events.registerListeners(this, this.npcListener);
        this.commands.register();
        return true;
    }

    @Listener
    public void onStop(GameStoppedEvent event) {
        this.log.info("Stopped.");
    }

    public Living createAndSpawn(EntityType entityType, Location<World> location, Cause cause)
        throws NpcSpawnException {

        checkNotNull(entityType, "null entity type");
        checkNotNull(location, "null location");
        checkNotNull(cause, "null cause");
        Optional<Identifiable> owner = cause.get(NamedCause.OWNER, Identifiable.class);
        checkArgument(owner.isPresent(), "cause owner must be identifiable");

        World world = location.getExtent();
        Living entity = (Living) world.createEntity(entityType, location.getPosition());
        if (!world.spawnEntity(entity, cause) || !Living.class.isAssignableFrom(entityType.getEntityClass()))
            throw new NpcSpawnException("could not spawn NPC");

        Text displayName = Messages.DEFAULT_DISPLAY_NAME;
        entity.offer(new ReveriesNpcData(owner.get().getUniqueId(), displayName, 0, Sets.newHashSet()));
        if (entity.supports(DisplayNameData.class))
            entity.offer(entity.getOrCreate(DisplayNameData.class).get().set(Keys.DISPLAY_NAME, displayName));

        return entity;
    }

    public boolean isMonitoring(Living living) {
        return this.monitors.containsKey(living.getUniqueId());
    }

    public void monitor(Living living) {
        checkArgument(living.get(NpcData.class).isPresent(), "entity is not NPC");
        checkArgument(!isMonitoring(living), "entity being monitored already");
        NpcMonitor monitor = new NpcMonitor(this, living);
        this.monitors.put(living.getUniqueId(), monitor);
        Sponge.getScheduler().createTaskBuilder()
            .delayTicks(1)
            .intervalTicks(1)
            .execute(monitor)
            .submit(this);
    }

}
