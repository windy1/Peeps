package se.walkercrou.peeps.cmd;

import static se.walkercrou.peeps.Messages.DEFAULT_DISPLAY_NAME;
import static se.walkercrou.peeps.Messages.NONE;
import static se.walkercrou.peeps.Messages.NOT_AN_NPC;
import static se.walkercrou.peeps.Messages.NO_LOCATION;
import static se.walkercrou.peeps.Messages.SKIN_NOT_FOUND;
import static se.walkercrou.peeps.Messages.SPAWN_FAILED;
import static se.walkercrou.peeps.Messages.SPAWN_SUCCESS;
import static se.walkercrou.peeps.Messages.UNSUPPORTED_PROP_TYPE;
import static se.walkercrou.peeps.Messages.UPDATED_PROPS;
import static se.walkercrou.peeps.Messages.UPDATED_TRAITS;
import static se.walkercrou.peeps.Messages.VERSION;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.mutable.DisplayNameData;
import org.spongepowered.api.data.manipulator.mutable.entity.SkinData;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextElement;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import se.walkercrou.peeps.NpcSpawnException;
import se.walkercrou.peeps.Peeps;
import se.walkercrou.peeps.data.NpcKeys;
import se.walkercrou.peeps.data.base.NpcData;
import se.walkercrou.peeps.property.NpcProperty;
import se.walkercrou.peeps.property.PropertyException;
import se.walkercrou.peeps.trait.NpcTrait;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CommandExecutors {

    private final Peeps plugin;

    public CommandExecutors(Peeps plugin) {
        this.plugin = plugin;
    }

    public CommandResult showVersion(CommandSource src, CommandContext context) {
        PluginContainer container = this.plugin.self;
        src.sendMessage(VERSION, ImmutableMap.of(
            "plugin.name", Text.of(container.getName()),
            "plugin.version", Text.of(container.getVersion().get())));
        return CommandResult.success();
    }

    public CommandResult createNpc(CommandSource src, CommandContext context) throws CommandException {
        EntityType entityType = context.<EntityType>getOne("entityType").orElse(EntityTypes.HUMAN);
        Optional<Location<World>> loc = context.getOne("location");
        if (!loc.isPresent() && !(src instanceof Player))
            throw new CommandException(NO_LOCATION);
        Location<World> location = loc.orElse(((Player) src).getLocation());
        Text displayName = context.<String>getOne("displayName").map(this::parseText).orElse(DEFAULT_DISPLAY_NAME);
        try {
            Living npc = this.plugin.createAndSpawn(entityType, location, Cause.source(this.plugin).owner(src).build());
            NpcData npcData = npc.get(NpcData.class).get();
            npc.offer(npcData.set(NpcKeys.DISPLAY_NAME, Optional.of(displayName)));
            if (npc.supports(DisplayNameData.class))
                npc.offer(npc.getOrCreate(DisplayNameData.class).get().set(Keys.DISPLAY_NAME, displayName));

            Map<String, TextElement> info = Maps.newHashMap();
            info.put("npc.owner", Text.of(npcData.ownerId().get()));
            info.put("npc.entity.type", Text.of(npc.getType().getId()));
            info.put("npc.entity.uuid", Text.of(npc.getUniqueId()));
            info.put("npc.displayName", npcData.displayName().get().orElse(NONE));
            src.sendMessage(SPAWN_SUCCESS, info);

            return CommandResult.success();
        } catch (NpcSpawnException e) {
            throw new CommandException(SPAWN_FAILED);
        }
    }

    public CommandResult updateTraits(CommandSource src, CommandContext context) throws CommandException {
        Entity npc = context.<Entity>getOne("npc").get();
        NpcData npcData = npc.get(NpcData.class).orElseThrow(() -> new CommandException(NOT_AN_NPC));
        Set<NpcTrait> npcTraits = npcData.traits().get();
        int updates = 0;
        for (NpcTrait trait : this.plugin.game.getRegistry().getAllOf(NpcTrait.class)) {
            Optional<Boolean> value = context.getOne(trait.getId());
            if (value.isPresent()) {
                updates++;
                if (value.get())
                    npcTraits.add(trait);
                else
                    npcTraits.remove(trait);
            }
        }
        npc.offer(npcData.set(NpcKeys.TRAITS, npcTraits));
        src.sendMessage(UPDATED_TRAITS, ImmutableMap.of("amount", Text.of(updates)));
        return CommandResult.success();
    }

    @SuppressWarnings("unchecked")
    public CommandResult updateProperties(CommandSource src, CommandContext context) throws CommandException {
        Entity entity = context.<Entity>getOne("npc").get();
        NpcData npcData = entity.get(NpcData.class).orElseThrow(() -> new CommandException(NOT_AN_NPC));
        int updates = 0;
        if (!(entity instanceof Living))
            throw new CommandException(NOT_AN_NPC);
        Living npc = (Living) entity;

        for (NpcProperty prop : this.plugin.game.getRegistry().getAllOf(NpcProperty.class)) {
            String propId = prop.getId();
            System.out.println("checking " + propId);
            if (context.hasAny(propId)) {
                System.out.println("propId = " + propId);
                Object value = context.getOne(propId).get();
                if (!prop.supports(value))
                    throw new CommandException(UNSUPPORTED_PROP_TYPE);
                try {
                    if (prop.set(npc, value, src))
                        updates++;
                } catch (PropertyException e) {
                    throw new CommandException(e.getText());
                }
            }
        }

        src.sendMessage(UPDATED_PROPS, ImmutableMap.of("amount", Text.of(updates)));
        return CommandResult.success();
    }

    private void setNpcSkinByPlayerName(CommandSource src, Entity npc, String playerName) {
        this.plugin.game.getServer().getGameProfileManager().get(playerName).whenComplete((profile, thrown) -> {
            if (thrown != null) {
                src.sendMessage(SKIN_NOT_FOUND);
                return;
            }
            npc.offer(npc.getOrCreate(SkinData.class).get().set(Keys.SKIN_UNIQUE_ID, profile.getUniqueId()));
            src.sendMessage(UPDATED_PROPS, ImmutableMap.of("amount", Text.of(1)));
        });
    }

    private Text parseText(String text) {
        return TextSerializers.FORMATTING_CODE.deserialize(text);
    }

}
