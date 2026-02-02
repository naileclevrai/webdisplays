package net.montoyo.wd.client.ambilight;

import net.montoyo.wd.utilities.Log;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ShimmerBridge {
    private static final String LIGHT_MANAGER_CLASS = "com.lowdragmc.shimmer.client.light.LightManager";
    private static final String COLOR_POINT_LIGHT_CLASS = "com.lowdragmc.shimmer.client.light.ColorPointLight";
    private static boolean initAttempted = false;
    private static boolean available = false;
    private static boolean loggedFailure = false;

    private static Object lightManager = null;
    private static Method addLight = null;
    private static Method maxFixedLight = null;
    private static Method setColor = null;
    private static Method setPos = null;
    private static Method setEnable = null;
    private static Method remove = null;
    private static Method isRemoved = null;
    private static Method update = null;

    private ShimmerBridge() {
    }

    static boolean isAvailable() {
        if (!initAttempted)
            init();
        return available;
    }

    static Object addLight(Vector3f pos, int color, float radius) {
        if (!isAvailable())
            return null;
        try {
            Object light = addLight.invoke(lightManager, pos, color, radius);
            if (light != null)
                setEnable(light, true);
            return light;
        } catch (Throwable t) {
            logFailure("Failed to add Shimmer light", t);
            return null;
        }
    }

    static void setLightColor(Object light, int color) {
        if (!isAvailable() || light == null)
            return;
        try {
            setColor.invoke(light, color);
        } catch (Throwable t) {
            logFailure("Failed to update Shimmer light color", t);
        }
    }

    static void setLightPos(Object light, Vector3f pos) {
        if (!isAvailable() || light == null)
            return;
        try {
            setPos.invoke(light, pos.x, pos.y, pos.z);
        } catch (Throwable t) {
            logFailure("Failed to update Shimmer light position", t);
        }
    }

    static void setEnable(Object light, boolean enable) {
        if (!isAvailable() || light == null)
            return;
        try {
            setEnable.invoke(light, enable);
        } catch (Throwable t) {
            logFailure("Failed to enable Shimmer light", t);
        }
    }

    static void updateLight(Object light) {
        if (!isAvailable() || light == null || update == null)
            return;
        try {
            update.invoke(light);
        } catch (Throwable t) {
            logFailure("Failed to update Shimmer light", t);
        }
    }

    static void removeLight(Object light) {
        if (!isAvailable() || light == null)
            return;
        try {
            remove.invoke(light);
        } catch (Throwable t) {
            logFailure("Failed to remove Shimmer light", t);
        }
    }

    static boolean isRemoved(Object light) {
        if (!isAvailable() || light == null || isRemoved == null)
            return false;
        try {
            return (boolean) isRemoved.invoke(light);
        } catch (Throwable t) {
            logFailure("Failed to check Shimmer light state", t);
            return false;
        }
    }

    static int maxFixedLight() {
        if (!isAvailable() || maxFixedLight == null)
            return 0;
        try {
            return (int) maxFixedLight.invoke(lightManager);
        } catch (Throwable t) {
            logFailure("Failed to query Shimmer light limits", t);
            return 0;
        }
    }

    private static synchronized void init() {
        if (initAttempted)
            return;
        initAttempted = true;

        if (!ModList.get().isLoaded("shimmer")) {
            available = false;
            return;
        }

        try {
            Class<?> lmClass = Class.forName(LIGHT_MANAGER_CLASS);
            Field instance = lmClass.getField("INSTANCE");
            lightManager = instance.get(null);
            addLight = lmClass.getMethod("addLight", Vector3f.class, int.class, float.class);
            maxFixedLight = lmClass.getMethod("maxFixedLight");

            Class<?> lightClass = Class.forName(COLOR_POINT_LIGHT_CLASS);
            setColor = lightClass.getMethod("setColor", int.class);
            setPos = lightClass.getMethod("setPos", float.class, float.class, float.class);
            setEnable = lightClass.getMethod("setEnable", boolean.class);
            remove = lightClass.getMethod("remove");
            isRemoved = lightClass.getMethod("isRemoved");
            update = lightClass.getMethod("update");

            available = true;
        } catch (Throwable t) {
            available = false;
            logFailure("Shimmer ambilight initialization failed", t);
        }
    }

    private static void logFailure(String msg, Throwable t) {
        if (loggedFailure)
            return;
        loggedFailure = true;
        Log.warningEx(msg, t);
    }
}
