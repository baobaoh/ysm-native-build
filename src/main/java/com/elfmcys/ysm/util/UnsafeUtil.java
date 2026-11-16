package com.elfmcys.ysm.util;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

public class UnsafeUtil {
    private static final Unsafe UNSAFE;

    /**
     * java.nio.DirectByteBuffer#address 的字段偏移。
     * 直接走 Unsafe 读字段，不依赖 LWJGL 的 MemoryUtil：
     * 构建期的索引工具是脱离 MC 运行的独立 JVM，那里没有 lwjgl.dll。
     */
    private static final long BUFFER_ADDRESS_OFFSET;

    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);

            UNSAFE = (Unsafe) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Couldn't obtain reference to sun.misc.Unsafe", e);
        }
        try {
            // JDK21 起 Buffer 直接持有 address 字段；更早的版本在 DirectByteBuffer 上
            BUFFER_ADDRESS_OFFSET = findAddressOffset();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Couldn't resolve buffer address offset", e);
        }
    }

    private static long findAddressOffset() throws ReflectiveOperationException {
        for (String className : new String[]{"java.nio.Buffer", "java.nio.DirectByteBuffer"}) {
            try {
                return UNSAFE.objectFieldOffset(Class.forName(className).getDeclaredField("address"));
            } catch (NoSuchFieldException ignored) {
                // 换下一个候选
            }
        }
        throw new NoSuchFieldException("address");
    }

    public static Unsafe getUnsafe() {
        return UNSAFE;
    }

    /**
     * 取 DirectByteBuffer 当前 position 处的原生地址。
     * 等价于 JDK22+ 的 {@code MemorySegment.ofBuffer(buffer).address()}（JDK21 上该 API 仍是 preview）。
     */
    public static long addressOf(ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Buffer is not direct");
        }
        return UNSAFE.getLong(buffer, BUFFER_ADDRESS_OFFSET) + buffer.position();
    }
}
