package io.wifi.starrailexpress.client.gui.endfield.menu.model;

public enum MenuAction {
    RESUME("x"),
    ADVANCEMENTS("tile-glory"),
    STATISTICS("tile-survey"),
    OPTIONS("gear-f"),
    WORLD_OPTIONS("tile-map"),
    FRIENDS("tile-friends"),
    FEEDBACK("mail-f"),
    BUGS("warn-f"),
    REPORTING("tile-baker"),
    LINKS("tile-map"),
    DISCONNECT("power"),
    OTHER("radar");

    private final String icon;

    MenuAction(String icon) {
        this.icon = icon;
    }

    public String icon() {
        return icon;
    }

    public boolean isPrimary() {
        return this == ADVANCEMENTS || this == STATISTICS || this == OPTIONS || this == WORLD_OPTIONS;
    }
}
