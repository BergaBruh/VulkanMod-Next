package net.vulkanmod.vulkan.memory;

import net.vulkanmod.vulkan.device.DeviceManager;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

/** Host-coherent upload bound as a whole storage-buffer range. Retire with freeBuffer(). */
public final class StorageBuffer extends Buffer {
    public StorageBuffer(ByteBuffer bytes) {
        super(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, MemoryTypes.HOST_MEM);
        int size = bytes.remaining();
        if (!bytes.isDirect() || size == 0 || size > Integer.toUnsignedLong(
                DeviceManager.deviceProperties.limits().maxStorageBufferRange())) {
            throw new IllegalArgumentException("Invalid storage buffer upload: " + size + " bytes");
        }
        createBuffer(size);
        data.getByteBuffer(0, size).put(bytes.duplicate());
    }
}
