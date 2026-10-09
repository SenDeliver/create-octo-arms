package dev.tentaworks.rig;

public enum RigState {
    STOWED,
    ACTIVE,
    SETTING_DOWN,
    FALLING,
    CRAWL;

    public boolean carries() {
        return this == ACTIVE || this == SETTING_DOWN || this == CRAWL;
    }

    public boolean drains() {
        return this == ACTIVE || this == SETTING_DOWN;
    }
}
