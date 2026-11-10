package dev.tentaworks.registry;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigTier;
import dev.tentaworks.rig.TentacleRigItem;

import static dev.tentaworks.Tentaworks.REGISTRATE;

public final class TentaworksLang {
    public static final String FAR_USE = "far_use";
    public static final String GRAB = "grab";

    private TentaworksLang() {
    }

    /** The action-bar line an Actuator shows when it refuses: {@code tentaworks.refusal.<area>.<why>}. */
    public static String refusalKey(String area, String why) {
        return TentaworksIds.MOD_ID + ".refusal." + area + "." + why;
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

    private static void advancement(String id, String title, String description) {
        String key = "advancement." + TentaworksIds.MOD_ID + "." + id;
        REGISTRATE.addRawLang(key, title);
        REGISTRATE.addRawLang(key + ".desc", description);
    }

    private static void refusal(String area, String why, String text) {
        REGISTRATE.addRawLang(refusalKey(area, why), text);
    }

    public static void init() {
        REGISTRATE.addRawLang("itemGroup.tentaworks", "Octo Arms");
        tooltip(TentaworksIds.TENTACLE, "One pneumatic arm of a _Tentacle Rig_. Four of them, a _Backtank_ and a _Precision Mechanism_ make a Rig on _Mechanical Crafters_.");
        rigTooltip(TentaworksIds.COPPER_TENTACLE_RIG, "Copper: _shorter Reach_.");
        rigTooltip(TentaworksIds.NETHERITE_TENTACLE_RIG, "Netherite: _longer Reach_, uses _half the Air_.");
        tooltip(TentaworksIds.ACTUATOR, "A pneumatic arm that _acts_ instead of walking. Fit _two_ to a finished _Tentacle Rig_ and it gains hands.",
                "When Fitted", "Put a Rig between two Actuators in one row of a _crafting table_. The Rig keeps its _Air_, _enchantments_, _settings_ and _damage_.",
                "Each Actuator", "Does what its hand does, _farther_: mining, placing, hitting and using out to the Rig's _Reach_. With a tool in each hand, both work at once.",
                "Grab key", "A _free_ Actuator takes a mob; a big one or a Ship needs _both_. Tap _Grab_ to set it down where you look, hold it and scroll to move it, _Attack_ Throws it.");

        enchantment(TentaworksIds.FAR_REACH, "Far Reach", "Longer Tentacles: more Reach per level, so the Rig grips, hangs and climbs from farther away.");
        enchantment(TentaworksIds.THRIFT, "Thrift", "The Rig uses less Air per level while holding, moving and climbing.");
        enchantment(TentaworksIds.SWIFT, "Swift", "The Rig carries you faster: more Speed and a higher top Speed per level.");
        enchantment(TentaworksIds.DEFT, "Deft", "The Actuators use no Air.");
        enchantment(TentaworksIds.SILENT_STEP, "Silent Step", "The Rig carries you in silence: no Rig sounds, and Sculk Sensors, Shriekers and Wardens do not notice you while it is out.");

        REGISTRATE.addRawLang("advancement.tentaworks.doc_rig", "Doc's Rig");
        REGISTRATE.addRawLang("advancement.tentaworks.doc_rig.desc", "Get a Tentacle Rig: four Tentacles on a Backtank");
        REGISTRATE.addRawLang(TentacleRigItem.octoKey(RigTier.COPPER), "Copper Octo Rig");
        REGISTRATE.addRawLang(TentacleRigItem.octoKey(RigTier.NETHERITE), "Netherite Octo Rig");
        REGISTRATE.addRawLang("advancement.tentaworks.fitted", "Fitted");
        REGISTRATE.addRawLang("advancement.tentaworks.fitted.desc", "Fit two Actuators to a Rig: now it has hands");
        refusal(FAR_USE, "no_sight", "Out of sight: the Actuator does not reach through blocks");
        refusal(FAR_USE, "players", "Far Use does not reach players on this server");
        advancement("hang_in_there", "Hang In There", "Hang under a ceiling for a whole minute without letting go");
        advancement("spider_man", "Spider, Man", "Kill a spider with the Rig out: eight legs lose to four Tentacles");
        advancement("power_of_the_sun", "The Power of the Sun", "Grab a Blaze and keep the sun at an Actuator's length");
        advancement("fridge_mover", "Fridge Mover", "Grab a Ship and set it down 50 blocks from where you took it");
        advancement("intelligence_is_a_privilege", "Intelligence Is a Privilege", "Put Far Reach, Thrift, Swift, Silent Step and Deft on one Rig");
        advancement("special_delivery", "Special Delivery", "Throw a mob so it lands 20 blocks or more from where you threw it");
        refusal(GRAB, "no_air", "No Air: the Actuators hang limp");
        refusal(GRAB, "hands_full", "Hands full: empty a hand to Grab");
        refusal(GRAB, "two_hands", "Needs both Actuators: empty both hands");
        refusal(GRAB, "not_living", "Only living mobs can be Grabbed");
        refusal(GRAB, "player", "Players can't be Grabbed");
        refusal(GRAB, "boss", "Too mighty to Grab: a boss");
        refusal(GRAB, "riding", "Can't Grab something riding or ridden");
        refusal(GRAB, "held", "Someone else already holds it");
        refusal(GRAB, "owned", "That's someone else's animal");
        refusal(GRAB, "too_far", "Out of Reach");
        refusal(GRAB, "no_sight", "Out of sight");
        refusal(GRAB, "claimed", "Protected: you can't Grab that here");
        refusal(GRAB, "ships_off", "Ship Grab is off on this server");
        refusal(GRAB, "scale", "Can't Grab a scaled Ship");
        refusal(GRAB, "own", "You stand on that Ship and nothing else holds you");
        refusal(GRAB, "mass", "Too heavy for this Rig");
        refusal(GRAB, "crewed", "Someone is on board");
        REGISTRATE.addRawLang("tentaworks.rig.deft_idle", "Deft does nothing until Actuators are fitted");
        REGISTRATE.addRawLang("death.attack.tentaworks.impact", "%1$s was hit by an Impact from %2$s");
        REGISTRATE.addRawLang("death.attack.tentaworks.impact.player", "%1$s was hit by an Impact while fighting %2$s");
        REGISTRATE.addRawLang("death.attack.tentaworks.impact.item", "%1$s was hit by an Impact from %2$s using %3$s");
        REGISTRATE.addRawLang("tentaworks.rig.worn_out", "Worn out: repair it with %s at an anvil, or by Mending");
        REGISTRATE.addRawLang("tentaworks.rig.worn_out.warning", "Tentacle Rig worn out: repair it");
        REGISTRATE.addRawLang("tentaworks.rig.one_at_a_time", "One Rig at a time: take off the Rig you wear first");
        REGISTRATE.addRawLang("tentaworks.rig.back.not_armor", "In the back slot: no armor, armor enchantments idle");
        REGISTRATE.addRawLang("tentaworks.rig.back.idle_enchants", "Protection, Thorns and other armor enchantments do nothing in the back slot");
        REGISTRATE.addRawLang("tentaworks.rig.back.inert", "In the back slot: does nothing while a Rig is in the chest slot");
        REGISTRATE.addRawLang("tentaworks.rig.back.off", "In the back slot: disabled on this server");
        REGISTRATE.addRawLang("tentaworks.rig.back.refused_off", "The back slot takes no Rig on this server");
        REGISTRATE.addRawLang("tentaworks.rig.wears_any_slot", "Wears from hits in the chest slot and in the back slot");

        REGISTRATE.addRawLang("key.categories.tentaworks", "Octo Arms");
        REGISTRATE.addRawLang("key.tentaworks.settings", "Rig Settings (worn Rig)");
        REGISTRATE.addRawLang("key.tentaworks.toggle", "Toggle Tentacle Rig (key-only mode)");
        REGISTRATE.addRawLang("key.tentaworks.grab", "Grab (Actuators)");

        REGISTRATE.addRawLang("tentaworks.stats.air", "Air:");
        REGISTRATE.addRawLang("tentaworks.stats.air.value", "%s / %s");
        REGISTRATE.addRawLang("tentaworks.stats.reach", "Reach:");
        REGISTRATE.addRawLang("tentaworks.stats.reach.value", "%s blocks");
        REGISTRATE.addRawLang("tentaworks.stats.top_speed", "Top Speed:");
        REGISTRATE.addRawLang("tentaworks.stats.top_speed.value", "%s b/s");
        REGISTRATE.addRawLang("tentaworks.stats.air_use", "Air use:");
        REGISTRATE.addRawLang("tentaworks.stats.air_use.value", "%s%%");
        REGISTRATE.addRawLang("tentaworks.stats.actuators", "Actuators fitted");
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
        REGISTRATE.addRawLang(gui + "far_use", "Far Use");
        REGISTRATE.addRawLang(gui + "far_use.always", "Always");
        REGISTRATE.addRawLang(gui + "far_use.rig_out", "Rig out only");
        REGISTRATE.addRawLang(gui + "far_use.off", "Off");
        String hud = "tentaworks.hud.";
        REGISTRATE.addRawLang(hud + "holding", "Holding %s");
        REGISTRATE.addRawLang(hud + "ship", "Ship");
        REGISTRATE.addRawLang(hud + "heavy", "heavy");
        REGISTRATE.addRawLang(hud + "no_air", "No Air: Actuators limp");
        REGISTRATE.addRawLang(hud + "set_down", "Set Down");
        REGISTRATE.addRawLang(hud + "throw", "Throw");
        REGISTRATE.addRawLang(hud + "distance", "Distance");

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
        REGISTRATE.addRawLang(cfg + "firstPerson", "First person");
        REGISTRATE.addRawLang(cfg + "firstPerson.limbOpacity", "Limb opacity");
        REGISTRATE.addRawLang(cfg + "hud", "HUD");
        REGISTRATE.addRawLang(cfg + "hud.crosshair", "Actuator crosshair marks");
        REGISTRATE.addRawLang(cfg + "hud.hints", "Key hints");
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
        REGISTRATE.addRawLang(cfg + "air.capacityFactor", "Rig Air capacity");
        REGISTRATE.addRawLang(cfg + "air.farUse", "Far Use: Air per action");
        REGISTRATE.addRawLang(cfg + "air.grab", "Grab: ticks per Air");
        REGISTRATE.addRawLang(cfg + "air.throw", "Throw: Air per Throw");
        REGISTRATE.addRawLang(cfg + "air.dualWield", "Dual Wield: Air per action");
        REGISTRATE.addRawLang(cfg + "farUse", "Far Use");
        REGISTRATE.addRawLang(cfg + "farUse.players", "Far Use on players");
        REGISTRATE.addRawLang(cfg + "actuators", "Actuators");
        REGISTRATE.addRawLang(cfg + "actuators.dualWield", "Dual Wield");
        REGISTRATE.addRawLang(cfg + "grab", "Grab");
        REGISTRATE.addRawLang(cfg + "grab.tamedOthers", "Grab others' animals");
        REGISTRATE.addRawLang(cfg + "grab.ships", "Grab Ships");
        REGISTRATE.addRawLang(cfg + "grab.massCap", "Ship mass cap");
        REGISTRATE.addRawLang(cfg + "grab.massCap.copper", "Copper mass cap");
        REGISTRATE.addRawLang(cfg + "grab.massCap.netherite", "Netherite mass cap");
        REGISTRATE.addRawLang(cfg + "grab.throwSpeed", "Throw speed");
        REGISTRATE.addRawLang(cfg + "grab.throwSpeed.copper", "Copper Throw speed");
        REGISTRATE.addRawLang(cfg + "grab.throwSpeed.netherite", "Netherite Throw speed");
        REGISTRATE.addRawLang(cfg + "grab.heavySpeedFactor", "Speed while holding heavy");
        REGISTRATE.addRawLang(cfg + "impact", "Impact");
        REGISTRATE.addRawLang(cfg + "impact.damagePerSpeed", "Damage per speed");
        REGISTRATE.addRawLang(cfg + "impact.minSpeed", "Minimum speed");
        REGISTRATE.addRawLang(cfg + "impact.cap", "Damage cap");
        REGISTRATE.addRawLang(cfg + "crawl", "Crawl");
        REGISTRATE.addRawLang(cfg + "crawl.speed", "Crawl speed");
        REGISTRATE.addRawLang(cfg + "enchant", "Enchantments");
        REGISTRATE.addRawLang(cfg + "enchant.farReachPerLevel", "Far Reach per level");
        REGISTRATE.addRawLang(cfg + "enchant.thriftPerLevel", "Thrift per level");
        REGISTRATE.addRawLang(cfg + "enchant.swiftPerLevel", "Swift per level");
        REGISTRATE.addRawLang(cfg + "ships", "Ships");
        REGISTRATE.addRawLang(cfg + "ships.grip", "Rigs grip Ships");
        REGISTRATE.addRawLang(cfg + "curios", "Curios");
        REGISTRATE.addRawLang(cfg + "curios.backSlot", "Rig in the back slot");
        REGISTRATE.addRawLang(cfg + "animation.walkWhileHeld.tooltip", "Vanilla walk animation (swinging arms and legs) while the Rig carries you or a Ship carries you with it. Off: the body hangs still");
        REGISTRATE.addRawLang(cfg + "movement.forward.tooltip", "What W and S follow while the Rig carries you. Look: the camera, look down and W takes you down. Flat: level only, like walking or creative flight; jump and sneak change the height");
        REGISTRATE.addRawLang(cfg + "reach.copper.tooltip", "Grip Reach of a Copper Rig, blocks. Far Reach adds to it, up to 16 in total");
        REGISTRATE.addRawLang(cfg + "reach.netherite.tooltip", "Grip Reach of a Netherite Rig, blocks. Far Reach adds to it, up to 16 in total");
        REGISTRATE.addRawLang(cfg + "speed.max.tooltip", "Top Rig speed, blocks per second. Caps the Speed in every player's Rig Settings; Swift raises both on its Rig");
        REGISTRATE.addRawLang(cfg + "air.idle.tooltip", "Copper Rig held still (Idle, Hang): ticks per 1 Air");
        REGISTRATE.addRawLang(cfg + "air.move.tooltip", "Copper Rig moving level or down: ticks per 1 Air");
        REGISTRATE.addRawLang(cfg + "air.climb.tooltip", "Copper Rig climbing (net upward): ticks per 1 Air");
        REGISTRATE.addRawLang(cfg + "air.netheriteFactor.tooltip", "Air use of a Netherite Rig relative to Copper");
        REGISTRATE.addRawLang(cfg + "air.capacityFactor.tooltip", "Max Air of a Rig relative to a Backtank of the same Capacity");
        REGISTRATE.addRawLang(cfg + "air.farUse.tooltip", "Copper Rig: Air per Far Use (one block or mob action past arm's length)");
        REGISTRATE.addRawLang(cfg + "air.grab.tooltip", "Copper Rig holding a Grab: ticks per 1 Air");
        REGISTRATE.addRawLang(cfg + "air.throw.tooltip", "Copper Rig: Air per Throw");
        REGISTRATE.addRawLang(cfg + "air.dualWield.tooltip", "Copper Rig: Air per Dual Wield action (a block broken or a hit the off-hand item helped with)");
        REGISTRATE.addRawLang(cfg + "actuators.dualWield.tooltip", "Dual Wield: with both Actuators free, the off-hand item works along with the main-hand one (mining speed, melee damage)");
        REGISTRATE.addRawLang(cfg + "farUse.players.tooltip", "Far Use may hit and use on players (the PvP rule still applies)");
        REGISTRATE.addRawLang(cfg + "grab.tamedOthers.tooltip", "Grab may take another player's tamed, owned or leashed animal");
        REGISTRATE.addRawLang(cfg + "grab.ships.tooltip", "Grab may take Sable Ships (also needs Rigs grip Ships). Ignored without Sable");
        REGISTRATE.addRawLang(cfg + "grab.massCap.copper.tooltip", "Heaviest Ship a Copper Rig can Grab, Sable mass");
        REGISTRATE.addRawLang(cfg + "grab.massCap.netherite.tooltip", "Heaviest Ship a Netherite Rig can Grab, Sable mass");
        REGISTRATE.addRawLang(cfg + "grab.throwSpeed.copper.tooltip", "Throw speed of a Copper Rig, blocks per tick");
        REGISTRATE.addRawLang(cfg + "grab.throwSpeed.netherite.tooltip", "Throw speed of a Netherite Rig, blocks per tick");
        REGISTRATE.addRawLang(cfg + "grab.heavySpeedFactor.tooltip", "Walking and Rig Speed while both Actuators hold a heavy target, relative to normal");
        REGISTRATE.addRawLang(cfg + "impact.damagePerSpeed.tooltip", "Impact damage per block per tick of speed above the minimum, dealt once per Throw");
        REGISTRATE.addRawLang(cfg + "impact.minSpeed.tooltip", "Slowest Thrown speed that deals Impact damage, blocks per tick");
        REGISTRATE.addRawLang(cfg + "impact.cap.tooltip", "Most Impact damage one Throw can deal");
        REGISTRATE.addRawLang(cfg + "hud.crosshair.tooltip", "Marks around the crosshair: what the Actuators reach and hold, the Grab cue, Dual Wield");
        REGISTRATE.addRawLang(cfg + "hud.hints.tooltip", "Key hints for the first two holds of a game session: the Grab key beside the crosshair and, after a Grab, the keys for Set Down, Throw and Distance above the hotbar");
        REGISTRATE.addRawLang(cfg + "firstPerson.limbOpacity.tooltip", "Opacity of every Limb (Tentacles and Actuators) in first person: 1.0 solid, 0.1 faint");
        REGISTRATE.addRawLang(cfg + "crawl.speed.tooltip", "Descent speed with no Air left (Crawl), blocks per second");
        REGISTRATE.addRawLang(cfg + "enchant.farReachPerLevel.tooltip", "Far Reach: extra Reach per level, blocks");
        REGISTRATE.addRawLang(cfg + "enchant.thriftPerLevel.tooltip", "Thrift: Air use cut per level (0.15 = -15 %). Level III stays above zero");
        REGISTRATE.addRawLang(cfg + "enchant.swiftPerLevel.tooltip", "Swift: extra Speed per level (0.15 = +15 %)");
        REGISTRATE.addRawLang(cfg + "ships.grip.tooltip", "Rigs grip Sable Ships like world blocks. Ignored without Sable");
        REGISTRATE.addRawLang(cfg + "curios.backSlot.tooltip", "A Rig in the Curios back slot works (Air, Reach, Rig Settings) but is not armor. Off: the back slot refuses Rigs and one already there does nothing. Ignored without Curios");
    }
}
