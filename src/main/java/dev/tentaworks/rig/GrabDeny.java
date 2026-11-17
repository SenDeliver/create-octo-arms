package dev.tentaworks.rig;

import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The Server Config {@code grab.denied}: mobs never Grabbed, by entity id ({@code modid:entity}) or whole mod ({@code modid:*}).
 * Entries are only compared with a target's own type key, never looked up, so ids of mods that are not installed cost nothing.
 */
public final class GrabDeny {
    private static final Pattern ENTRY = Pattern.compile("[a-z0-9_.-]+:([a-z0-9_./-]+|\\*)");
    public static final String NEW_ENTRY = "modid:entity";
    public static final List<String> DEFAULTS = List.of(
            "minecraft:elder_guardian",
            "born_in_chaos_v1:nightmare_stalker",
            "born_in_chaos_v1:lifestealer",
            "born_in_chaos_v1:lifestealer_true_form",
            "born_in_chaos_v1:supreme_bonescaller",
            "born_in_chaos_v1:supreme_bonescaller_stage_2",
            "born_in_chaos_v1:supreme_bonescaller_not_despawn",
            "born_in_chaos_v1:lord_pumpkinhead",
            "born_in_chaos_v1:lord_pumpkinhead_withouta_horse",
            "born_in_chaos_v1:lord_pumpkinhead_head",
            "born_in_chaos_v1:lord_the_headless",
            "born_in_chaos_v1:sir_the_headless",
            "born_in_chaos_v1:krampus",
            "born_in_chaos_v1:mother_spider",
            "born_in_chaos_v1:scarlet_persecutor");

    public record Matcher(Set<String> ids, Set<String> mods) {
        public boolean denies(String namespace, String path) {
            return mods.contains(namespace) || ids.contains(namespace + ":" + path);
        }
    }

    private record Cached(List<? extends String> raw, Matcher matcher) {
    }

    private static volatile Cached cached = new Cached(List.of(), new Matcher(Set.of(), Set.of()));

    private GrabDeny() {
    }

    /** The config validator: syntax only. */
    public static boolean valid(Object entry) {
        return entry instanceof String s && ENTRY.matcher(s).matches();
    }

    public static Matcher parse(List<? extends String> entries) {
        Set<String> ids = new HashSet<>(), mods = new HashSet<>();
        for (String e : entries) {
            if (!valid(e)) continue;
            if (e.endsWith(":*")) mods.add(e.substring(0, e.length() - 2));
            else ids.add(e);
        }
        return new Matcher(Set.copyOf(ids), Set.copyOf(mods));
    }

    /** Whether the config denies this entity type; parsed again only when the config's list changes (load, reload, sync, edit). */
    public static boolean denied(ResourceLocation type) {
        List<? extends String> raw = TentaworksConfig.value(TentaworksConfig.GRAB_DENIED);
        Cached c = cached;
        if (c.raw != raw && !c.raw.equals(raw)) cached = c = new Cached(raw, parse(raw));
        return c.matcher.denies(type.getNamespace(), type.getPath());
    }
}
