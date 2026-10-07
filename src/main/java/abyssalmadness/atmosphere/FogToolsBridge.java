package abyssalmadness.atmosphere;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

// Bridge to the optional FogTools mod. It exposes S2C payloads (fogtools:fog_set /
// fog_clear) that its client applies the same way as the /fogtools command, so we
// send those directly. Done by reflection: no compile dependency, and it degrades
// to a no-op if FogTools isn't installed.
public final class FogToolsBridge {
    private static boolean resolved;
    private static boolean available;
    private static Constructor<?> setCtor;    // (float,float,int,boolean,float,int,int)
    private static Constructor<?> clearCtor;   // (int) fadeOut, or no-arg
    private static Method parseColor;          // static int parseColor(String)

    private FogToolsBridge() {
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("fogtools")) {
            return;
        }
        try {
            Class<?> set = Class.forName("org.cotii.fogtools.network.FogSetPayload");
            setCtor = set.getConstructor(float.class, float.class, int.class,
                    boolean.class, float.class, int.class, int.class);
            Class<?> clear = Class.forName("org.cotii.fogtools.network.FogClearPayload");
            clearCtor = clear.getDeclaredConstructors()[0];
            clearCtor.setAccessible(true);
            Class<?> colors = Class.forName("org.cotii.fogtools.FogColors");
            parseColor = colors.getMethod("parseColor", String.class);
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    public static boolean available() {
        resolve();
        return available;
    }

    public static int color(String nameOrHex) {
        resolve();
        if (parseColor == null) {
            return 0xFFFFFF;
        }
        try {
            return (int) parseColor.invoke(null, nameOrHex);
        } catch (Throwable t) {
            return 0xFFFFFF;
        }
    }

    public static void sendSet(ServerPlayer player, float radius, float density, int rgb,
                               boolean sky, float opacity, int fadeIn, int fadeOut) {
        resolve();
        if (!available) {
            return;
        }
        try {
            Object payload = setCtor.newInstance(radius, density, rgb, sky, opacity, fadeIn, fadeOut);
            ServerPlayNetworking.send(player, (CustomPacketPayload) payload);
        } catch (Throwable ignored) {
        }
    }

    public static void sendClear(ServerPlayer player, int fadeOut) {
        resolve();
        if (!available) {
            return;
        }
        try {
            Object payload = clearCtor.getParameterCount() == 0
                    ? clearCtor.newInstance()
                    : clearCtor.newInstance(fadeOut);
            ServerPlayNetworking.send(player, (CustomPacketPayload) payload);
        } catch (Throwable ignored) {
        }
    }
}
