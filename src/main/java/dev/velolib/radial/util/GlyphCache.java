package dev.velolib.radial.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.velolib.radial.RadialClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class GlyphCache {

    private static List<String> cachedGlyphs;

    private GlyphCache() {}

    public static List<String> getGlyphs() {
        if (cachedGlyphs != null) {
            return cachedGlyphs;
        }

        // Keeps the font's order while skipping characters that appear in several providers
        Set<String> glyphs = new LinkedHashSet<>();

        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Identifier targetFont = Identifier.fromNamespaceAndPath("minecraft", "font/include/default.json");
        Optional<Resource> resourceOpt = manager.getResource(targetFont);

        if (resourceOpt.isEmpty()) {
            targetFont = Identifier.fromNamespaceAndPath("minecraft", "font/default.json");
            resourceOpt = manager.getResource(targetFont);
        }

        if (resourceOpt.isPresent()) {
            try (Reader reader =
                    new BufferedReader(new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8))) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray providers = json.getAsJsonArray("providers");

                for (int i = 0; i < providers.size(); i++) {
                    JsonObject provider = providers.get(i).getAsJsonObject();

                    if (!provider.has("type")
                            || !provider.get("type").getAsString().equals("bitmap")) {
                        continue;
                    }

                    JsonArray chars = provider.getAsJsonArray("chars");

                    for (int j = 0; j < chars.size(); j++) {
                        for (char c : chars.get(j).getAsString().toCharArray()) {
                            if (c != '\u0000' && c != ' ') {
                                glyphs.add(String.valueOf(c));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                RadialClient.LOGGER.error("Failed to parse dynamic glyphs.", e);
            }
        }

        if (glyphs.isEmpty()) {
            RadialClient.LOGGER.error("Glyph cache parsed empty, using fallback list.");

            glyphs.addAll(List.of(
                    "★", "☆", "♥", "♦", "♣", "♠", "☠", "☢", "☣", "⚠", "⚡", "↑", "↓", "←", "→", "↕", "↔", "⟳", "✖", "✔",
                    "⚙", "⌂", "✉", "☺", "☻", "☼", "♀", "♂", "♪", "♫", "►", "◄", "⛄", "⛏"));
        }

        cachedGlyphs = List.copyOf(glyphs);

        return cachedGlyphs;
    }
}
