package io.wifi.starrailexpress.client.gui.endfield.menu.model;

public record PlayerCard(String name, long daysPlayed, float dayProgress, String world,
                         String status, String position, String mode, String connection,
                         String heading, String daysPlayedLabel, String hint) {
}
