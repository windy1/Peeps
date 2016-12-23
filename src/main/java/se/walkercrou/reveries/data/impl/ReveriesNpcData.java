package se.walkercrou.reveries.data.impl;

import com.google.common.collect.Sets;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.manipulator.mutable.common.AbstractData;
import org.spongepowered.api.data.merge.MergeFunction;
import org.spongepowered.api.data.value.mutable.SetValue;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.api.text.Text;
import se.walkercrou.reveries.data.npc.ImmutableNpcData;
import se.walkercrou.reveries.data.npc.NpcData;
import se.walkercrou.reveries.data.NpcKeys;
import se.walkercrou.reveries.trait.NpcTrait;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ReveriesNpcData extends AbstractData<NpcData, ImmutableNpcData> implements NpcData {

    public static final int CONTENT_VERSION = 1;

    private UUID ownerId;
    private Text displayName;
    private double sightRange;
    private Set<NpcTrait> traits;

    public ReveriesNpcData(UUID ownerId, Text displayName, double sightRange, Set<NpcTrait> traits) {
        this.ownerId = ownerId;
        this.displayName = displayName;
        this.sightRange = sightRange;
        this.traits = traits;
        registerGettersAndSetters();
    }

    public ReveriesNpcData() {
        this(null, null, 0, Sets.newHashSet());
    }

    @Override
    public Value<UUID> ownerId() {
        return Sponge.getRegistry().getValueFactory().createValue(NpcKeys.OWNER_ID, this.ownerId);
    }

    @Override
    public Value<Text> displayName() {
        return Sponge.getRegistry().getValueFactory().createValue(NpcKeys.DISPLAY_NAME, this.displayName);
    }

    @Override
    public Value<Double> sightRange() {
        return Sponge.getRegistry().getValueFactory().createValue(NpcKeys.SIGHT_RANGE, this.sightRange);
    }

    @Override
    public SetValue<NpcTrait> traits() {
        return Sponge.getRegistry().getValueFactory().createSetValue(NpcKeys.TRAITS, this.traits);
    }

    @Override
    protected void registerGettersAndSetters() {
        registerFieldGetter(NpcKeys.OWNER_ID, () -> this.ownerId);
        registerFieldSetter(NpcKeys.OWNER_ID, value -> this.ownerId = value);
        registerKeyValue(NpcKeys.OWNER_ID, this::ownerId);

        registerFieldGetter(NpcKeys.DISPLAY_NAME, () -> this.displayName);
        registerFieldSetter(NpcKeys.DISPLAY_NAME, value -> this.displayName = value);
        registerKeyValue(NpcKeys.DISPLAY_NAME, this::displayName);

        registerFieldGetter(NpcKeys.SIGHT_RANGE, () -> this.sightRange);
        registerFieldSetter(NpcKeys.SIGHT_RANGE, value -> this.sightRange = value);
        registerKeyValue(NpcKeys.SIGHT_RANGE, this::sightRange);

        registerFieldGetter(NpcKeys.TRAITS, () -> this.traits);
        registerFieldSetter(NpcKeys.TRAITS, value -> this.traits = value);
        registerKeyValue(NpcKeys.TRAITS, this::traits);
    }

    @Override
    public Optional<NpcData> fill(DataHolder dataHolder, MergeFunction overlap) {
        return Optional.empty();
    }

    @Override
    public Optional<NpcData> from(DataContainer container) {
        if (!container.contains(NpcKeys.OWNER_ID, NpcKeys.DISPLAY_NAME, NpcKeys.TRAITS))
            return Optional.empty();
        this.ownerId = container.getObject(NpcKeys.OWNER_ID.getQuery(), UUID.class).get();
        this.displayName = container.getObject(NpcKeys.DISPLAY_NAME.getQuery(), Text.class).get();
        this.sightRange = container.getDouble(NpcKeys.SIGHT_RANGE.getQuery()).get();
        this.traits = Sets.newHashSet(container.getObjectList(NpcKeys.TRAITS.getQuery(), NpcTrait.class).get());
        return Optional.of(this);
    }

    @Override
    public ReveriesNpcData copy() {
        return new ReveriesNpcData(this.ownerId, this.displayName, this.sightRange, this.traits);
    }

    @Override
    public ReveriesImmutableNpcData asImmutable() {
        return new ReveriesImmutableNpcData(this.ownerId, this.displayName, this.sightRange, this.traits);
    }

    @Override
    public int getContentVersion() {
        return CONTENT_VERSION;
    }

    @Override
    public DataContainer toContainer() {
        return super.toContainer()
            .set(NpcKeys.OWNER_ID, this.ownerId)
            .set(NpcKeys.DISPLAY_NAME, this.displayName)
            .set(NpcKeys.SIGHT_RANGE, this.sightRange)
            .set(NpcKeys.TRAITS, this.traits);
    }

}
