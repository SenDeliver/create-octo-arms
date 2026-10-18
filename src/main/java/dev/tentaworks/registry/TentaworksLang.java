package dev.tentaworks.registry;

import dev.tentaworks.TentaworksIds;

import static dev.tentaworks.Tentaworks.REGISTRATE;

public final class TentaworksLang {
    private TentaworksLang() {
    }

    private static void tooltip(String id, String summary, String... conditionBehaviour) {
        String base = "item." + TentaworksIds.MOD_ID + "." + id + ".tooltip";
        REGISTRATE.addRawLang(base + ".summary", summary);
        for (int i = 0; i + 1 < conditionBehaviour.length; i += 2) {
            REGISTRATE.addRawLang(base + ".condition" + (i / 2 + 1), conditionBehaviour[i]);
            REGISTRATE.addRawLang(base + ".behaviour" + (i / 2 + 1), conditionBehaviour[i + 1]);
        }
    }

    private static void rigTooltip(String id, String tier) {
        tooltip(id, "A _Backtank_ fitted with four _Tentacles_. Worn, it carries you over _floors, walls and ceilings_ on its _Air_. " + tier,
                "When Worn", "_Double Jump_ to activate. Move where you look, _Space_ up, _Shift_ down. _Double Jump_ again to be _Set Down_.",
                "Sneak + Use in the air", "Opens the _Rig Settings_: _Standing Height_, _Speed_ and _Activation Mode_.",
                "When Placed", "Refills under a spinning _shaft_ like any Backtank. Picked up again with its _Air_ and settings.");
    }

    private static void enchantment(String id, String name, String description) {
        String key = "enchantment." + TentaworksIds.MOD_ID + "." + id;
        REGISTRATE.addRawLang(key, name);
        REGISTRATE.addRawLang(key + ".desc", description);
    }

    public static void init() {
        REGISTRATE.addRawLang("itemGroup.tentaworks", "Octo Arms");
        tooltip(TentaworksIds.TENTACLE, "One pneumatic arm of a _Tentacle Rig_. Four of them, a _Backtank_ and a _Precision Mechanism_ make a Rig on _Mechanical Crafters_.");
        rigTooltip(TentaworksIds.COPPER_TENTACLE_RIG, "Copper: _shorter Reach_.");
        rigTooltip(TentaworksIds.NETHERITE_TENTACLE_RIG, "Netherite: _longer Reach_, uses _half the Air_.");

        enchantment(TentaworksIds.FAR_REACH, "Far Reach", "Longer Tentacles: more Reach per level, so the Rig grips, hangs and climbs from farther away.");
        enchantment(TentaworksIds.THRIFT, "Thrift", "The Rig uses less Air per level while holding, moving and climbing.");
        enchantment(TentaworksIds.SWIFT, "Swift", "The Rig carries you faster: more Speed and a higher top Speed per level.");

        REGISTRATE.addRawLang("advancement.tentaworks.doc_rig", "Doc's Rig");
        REGISTRATE.addRawLang("advancement.tentaworks.doc_rig.desc", "Get a Tentacle Rig: four Tentacles on a Backtank");
        REGISTRATE.addRawLang("tentaworks.rig.worn_out", "Worn out: repair it with %s at an anvil, or by Mending");
        REGISTRATE.addRawLang("tentaworks.rig.worn_out.warning", "Tentacle Rig worn out: repair it");

        REGISTRATE.addRawLang("key.categories.tentaworks", "Octo Arms");
        REGISTRATE.addRawLang("key.tentaworks.settings", "Rig Settings (worn Rig)");
        REGISTRATE.addRawLang("key.tentaworks.toggle", "Toggle Tentacle Rig (key-only mode)");

        REGISTRATE.addRawLang("tentaworks.stats.air", "Air:");
        REGISTRATE.addRawLang("tentaworks.stats.air.value", "%s / %s");
        REGISTRATE.addRawLang("tentaworks.stats.reach", "Reach:");
        REGISTRATE.addRawLang("tentaworks.stats.reach.value", "%s blocks");
        REGISTRATE.addRawLang("tentaworks.stats.top_speed", "Top Speed:");
        REGISTRATE.addRawLang("tentaworks.stats.top_speed.value", "%s b/s");
        REGISTRATE.addRawLang("tentaworks.stats.air_use", "Air use:");
        REGISTRATE.addRawLang("tentaworks.stats.air_use.value", "%s%%");
        REGISTRATE.addRawLang("tentaworks.goggles.rig_block", "Tentacle Rig");
        REGISTRATE.addRawLang("tentaworks.goggles.percent", "(%s%%)");
        REGISTRATE.addRawLang("tentaworks.goggles.full", "Full");
        REGISTRATE.addRawLang("tentaworks.goggles.filling", "Filling: +%s Air/s");
        REGISTRATE.addRawLang("tentaworks.goggles.no_shaft", "Not filling: needs a turning shaft above");
        REGISTRATE.addRawLang("tentaworks.display_source.rig_air", "Rig Air");
        REGISTRATE.addRawLang("tentaworks.display_source.rig_air.display", "Show");
        REGISTRATE.addRawLang("tentaworks.display_source.rig_air.air", "Air");
        REGISTRATE.addRawLang("tentaworks.display_source.rig_air.percent", "Percent");
        REGISTRATE.addRawLang("tentaworks.display_source.rig_air.progress_bar", "Progress Bar");

        String gui = "tentaworks.gui.rig_settings.";
        REGISTRATE.addRawLang(gui + "title", "Rig Settings: %s");
        REGISTRATE.addRawLang(gui + "standing_height", "Standing Height");
        REGISTRATE.addRawLang(gui + "standing_height.hint", "How high the Rig lifts you above the floor on activation");
        REGISTRATE.addRawLang(gui + "speed", "Speed");
        REGISTRATE.addRawLang(gui + "speed.hint", "How fast the Rig carries you while Active");
        REGISTRATE.addRawLang(gui + "activation", "Activation");
        REGISTRATE.addRawLang(gui + "activation.double_jump", "Double Jump");
        REGISTRATE.addRawLang(gui + "activation.key", "Key only");
        REGISTRATE.addRawLang(gui + "blocks", "%s blocks");
        REGISTRATE.addRawLang(gui + "blocks_per_second", "%s b/s");
        REGISTRATE.addRawLang(gui + "limits", "Reach %s, top Speed %s b/s");
        REGISTRATE.addRawLang(gui + "toggle_key", "Toggle key: %s");
        REGISTRATE.addRawLang(gui + "toggle_unbound", "Bind \"Toggle Tentacle Rig\" in Controls");
        REGISTRATE.addRawLang(gui + "double_jump_hint", "Double Jump: activate, Set Down, let go");

        String cfg = TentaworksIds.MOD_ID + ".configuration.";
        REGISTRATE.addRawLang(cfg + "title", "Octo Arms: Config");
        REGISTRATE.addRawLang(cfg + "section." + TentaworksIds.MOD_ID + ".client.toml.title", "Client (this computer)");
        REGISTRATE.addRawLang(cfg + "section." + TentaworksIds.MOD_ID + ".server.toml.title", "Server (this world)");
        REGISTRATE.addRawLang(cfg + "animation", "Animation");
        REGISTRATE.addRawLang(cfg + "animation.walkWhileHeld", "Walk animation while carried");
        REGISTRATE.addRawLang(cfg + "movement", "Movement");
        REGISTRATE.addRawLang(cfg + "movement.forward", "W and S follow");
        REGISTRATE.addRawLang(cfg + "movement.forward.look", "Look");
        REGISTRATE.addRawLang(cfg + "movement.forward.flat", "Flat");
        REGISTRATE.addRawLang(cfg + "reach", "Reach");
        REGISTRATE.addRawLang(cfg + "reach.copper", "Copper Reach");
        REGISTRATE.addRawLang(cfg + "reach.netherite", "Netherite Reach");
        REGISTRATE.addRawLang(cfg + "speed", "Speed");
        REGISTRATE.addRawLang(cfg + "speed.max", "Top Speed");
        REGISTRATE.addRawLang(cfg + "air", "Air");
        REGISTRATE.addRawLang(cfg + "air.idle", "Idle: ticks per Air");
        REGISTRATE.addRawLang(cfg + "air.move", "Moving: ticks per Air");
        REGISTRATE.addRawLang(cfg + "air.climb", "Climbing: ticks per Air");
        REGISTRATE.addRawLang(cfg + "air.netheriteFactor", "Netherite Air factor");
        REGISTRATE.addRawLang(cfg + "crawl", "Crawl");
        REGISTRATE.addRawLang(cfg + "crawl.speed", "Crawl speed");
        REGISTRATE.addRawLang(cfg + "enchant", "Enchantments");
        REGISTRATE.addRawLang(cfg + "enchant.farReachPerLevel", "Far Reach per level");
        REGISTRATE.addRawLang(cfg + "enchant.thriftPerLevel", "Thrift per level");
        REGISTRATE.addRawLang(cfg + "enchant.swiftPerLevel", "Swift per level");
        REGISTRATE.addRawLang(cfg + "ships", "Ships");
        REGISTRATE.addRawLang(cfg + "ships.grip", "Rigs grip Ships");
        REGISTRATE.addRawLang(cfg + "animation.walkWhileHeld.tooltip", "Vanilla walk animation (swinging arms and legs) while the Rig carries you or a Ship carries you with it. Off: the body hangs still");
        REGISTRATE.addRawLang(cfg + "movement.forward.tooltip", "What W and S follow while the Rig carries you. Look: the camera, look down and W takes you down. Flat: level only, like walking or creative flight; jump and sneak change the height");
        REGISTRATE.addRawLang(cfg + "reach.copper.tooltip", "Grip Reach of a Copper Rig, blocks. Far Reach adds to it, up to 16 in total");
        REGISTRATE.addRawLang(cfg + "reach.netherite.tooltip", "Grip Reach of a Netherite Rig, blocks. Far Reach adds to it, up to 16 in total");
        REGISTRATE.addRawLang(cfg + "speed.max.tooltip", "Top Rig speed, blocks per second. Caps the Speed in every player's Rig Settings; Swift raises both on its Rig");
        REGISTRATE.addRawLang(cfg + "air.idle.tooltip", "Copper Rig held still (Idle, Hang): ticks per 1 Air");
        REGISTRATE.addRawLang(cfg + "air.move.tooltip", "Copper Rig moving level or down: ticks per 1 Air");
        REGISTRATE.addRawLang(cfg + "air.climb.tooltip", "Copper Rig climbing (net upward): ticks per 1 Air");
        REGISTRATE.addRawLang(cfg + "air.netheriteFactor.tooltip", "Air use of a Netherite Rig relative to Copper");
        REGISTRATE.addRawLang(cfg + "crawl.speed.tooltip", "Descent speed with no Air left (Crawl), blocks per second");
        REGISTRATE.addRawLang(cfg + "enchant.farReachPerLevel.tooltip", "Far Reach: extra Reach per level, blocks");
        REGISTRATE.addRawLang(cfg + "enchant.thriftPerLevel.tooltip", "Thrift: Air use cut per level (0.15 = -15 %). Level III stays above zero");
        REGISTRATE.addRawLang(cfg + "enchant.swiftPerLevel.tooltip", "Swift: extra Speed per level (0.15 = +15 %)");
        REGISTRATE.addRawLang(cfg + "ships.grip.tooltip", "Rigs grip Sable Ships like world blocks. Ignored without Sable");
    }
}
