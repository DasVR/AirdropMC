package dev.oblivionsanctum.airdrop;

import org.bukkit.Material;

import java.util.Locale;

final class MaterialResolver {
    private MaterialResolver() {}

    static Material resolveModernOnly(String rawName) {
        if (rawName == null) return null;
        String normalized = rawName.trim();
        if (normalized.isEmpty()) return null;
        normalized = normalized.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return Material.getMaterial(normalized);
    }
}
