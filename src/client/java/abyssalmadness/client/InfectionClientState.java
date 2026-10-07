package abyssalmadness.client;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side mirror of every player's infection level (0..100). */
public final class InfectionClientState {
    private static final Map<UUID, Integer> LEVELS = new ConcurrentHashMap<>();
    /** Who the shader paints with the warm palette rather than the cold one. */
    private static final Set<UUID> WARM = ConcurrentHashMap.newKeySet();

    private InfectionClientState() {
    }

    public static void set(UUID player, int level, boolean warm) {
        if (level <= 0) {
            LEVELS.remove(player);
            WARM.remove(player);
            return;
        }
        LEVELS.put(player, level);
        if (warm) {
            WARM.add(player);
        } else {
            WARM.remove(player);
        }
    }

    public static boolean isWarm(UUID player) {
        return WARM.contains(player);
    }

    public static int get(UUID player) {
        return LEVELS.getOrDefault(player, 0);
    }

    public static boolean any() {
        return !LEVELS.isEmpty();
    }

    public static void clear() {
        LEVELS.clear();
        WARM.clear();
    }
}
