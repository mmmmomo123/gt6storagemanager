package gtsm.client;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** 客户端缓存：附近管理器的范围数据（GUI 镜像 + 画框共用）。 */
@SideOnly(Side.CLIENT)
public final class RangeClientData {
    public static final class Info {
        public final int x, y, z, offsetX, offsetY, offsetZ, radius;
        public final boolean rangeEnabled, showFrame;
        public Info(int aX, int aY, int aZ, int aOX, int aOY, int aOZ, int aRadius, boolean aRangeEnabled, boolean aShowFrame) {
            x = aX; y = aY; z = aZ; offsetX = aOX; offsetY = aOY; offsetZ = aOZ; radius = aRadius;
            rangeEnabled = aRangeEnabled; showFrame = aShowFrame;
        }
    }

    private static final Map<Integer, Info> DATA = new ConcurrentHashMap<Integer, Info>();

    public static void put(int aX, int aY, int aZ, int aOX, int aOY, int aOZ, int aRadius, boolean aRangeEnabled, boolean aShowFrame) {
        DATA.put(key(aX, aY, aZ), new Info(aX, aY, aZ, aOX, aOY, aOZ, aRadius, aRangeEnabled, aShowFrame));
    }

    public static Info get(int aX, int aY, int aZ) {
        return DATA.get(key(aX, aY, aZ));
    }

    public static Collection<Info> all() {
        return DATA.values();
    }

    public static void clear() {
        DATA.clear();
    }

    private static int key(int aX, int aY, int aZ) {
        int h = aX * 3129871 ^ aZ * 116129781 ^ aY;
        return h * h;
    }
}
