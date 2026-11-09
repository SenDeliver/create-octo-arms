package dev.tentaworks.rig;

import dev.tentaworks.TentaworksIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Damage type {@code tentaworks:impact} ({@code data/tentaworks/damage_type/impact.json}): a Thrown mob hitting a block or a mob. */
public final class ImpactDamage {
    public static final ResourceKey<DamageType> KEY = ResourceKey.create(Registries.DAMAGE_TYPE, TentaworksIds.id(TentaworksIds.IMPACT));

    private ImpactDamage() {
    }

    /** {@code thrown} is the direct source, {@code wearer} the attacker, so loot, XP and protection rules see the wearer. */
    public static DamageSource source(Level level, @Nullable Entity thrown, @Nullable Entity wearer) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KEY), thrown, wearer);
    }
}
