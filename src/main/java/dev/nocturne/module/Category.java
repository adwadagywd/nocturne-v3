package dev.nocturne.module;

public enum Category {
    COMBAT("Combat"), RENDER("Render"), MOVEMENT("Movement"), CLIENT("Client");
    public final String label;
    Category(String label) { this.label = label; }
}
