package jnr.enxio.channels;

import jnr.ffi.Platform;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class NativeTest {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void closeThrowsOnNativeError() throws Exception {
        // Skip on non-unix
        if (!Platform.getNativePlatform().isUnix()) return;

        FileOutputStream fos = new FileOutputStream("/dev/null");
        FileDescriptor descriptor = fos.getFD();
        Field fdField = descriptor.getClass().getDeclaredField("fd");
        fdField.setAccessible(true);
        int fd = (int)(Integer)fdField.get(descriptor);
        Native.close(fd);
        expectedEx.expect(NativeException.class);
        Native.close(fd);
    }

    @Test
    public void setBlocking() throws Exception {
        // Skip on non-unix
        if (!Platform.getNativePlatform().isUnix()) return;

        Pipe pipe = Pipe.open();
        Pipe.SinkChannel sink = pipe.sink();
//        sink.getClass().getModule().addOpens("sun.nio.ch", NativeTest.class.getModule());
        Field fd1 = sink.getClass().getDeclaredField("fd");
        fd1.setAccessible(true);
        FileDescriptor descriptor = (FileDescriptor) fd1.get(sink);
        Field fdField = descriptor.getClass().getDeclaredField("fd");
        fdField.setAccessible(true);
        int fd = (int)(Integer)fdField.get(descriptor);
        Assert.assertEquals(true, Native.getBlocking(fd));
        Native.setBlocking(fd, false);
        Assert.assertEquals(false, Native.getBlocking(fd));
    }

    @Test
    public void pollSelectorReportsDeviceReady() throws Exception {
        // Skip on non-unix
        if (!Platform.getNativePlatform().isUnix()) return;

        RandomAccessFile file = new RandomAccessFile("/dev/null", "rw");
        try {
            FileDescriptor descriptor = file.getFD();
            Field fdField = descriptor.getClass().getDeclaredField("fd");
            fdField.setAccessible(true);
            int fd = (int)(Integer)fdField.get(descriptor);

            NativeDeviceChannel channel = new NativeDeviceChannel(fd, true);
            channel.configureBlocking(false);
            Selector selector = channel.provider().openSelector();
            try {
                SelectionKey key = channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                Assert.assertEquals(1, selector.selectNow());
                Assert.assertEquals(SelectionKey.OP_READ | SelectionKey.OP_WRITE, key.readyOps());
            } finally {
                selector.close();
            }
        } finally {
            file.close();
        }
    }
}
