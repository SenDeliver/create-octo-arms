package dev.tentaworks.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Criterion {@code tentaworks:rig_event}: the server fired a Rig event with a number (blocks, seconds, a
 * count; 1 when there is none). Conditions: {@code event} (the name) and an optional {@code number} range, e.g.
 * {@code {"event": "throw_distance", "number": {"min": 20}}}.
 */
public final class RigEventTrigger extends SimpleCriterionTrigger<RigEventTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String event, double number) {
        trigger(player, i -> i.matches(event, number));
    }

    public record Instance(Optional<ContextAwarePredicate> player, String event, MinMaxBounds.Doubles number) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.fieldOf("event").forGetter(Instance::event),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("number", MinMaxBounds.Doubles.ANY).forGetter(Instance::number)
        ).apply(i, Instance::new));

        public boolean matches(String fired, double value) {
            return event.equals(fired) && number.matches(value);
        }
    }
}
