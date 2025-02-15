import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.whitebox.WhiteBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestAllocInNewTLAB {
    // 对于字节数组对象头占用16字节
    private static final int BYTE_ARRAY_OVERHEAD = 16;
    // 我们要测试的对象大小是100kb
    private static final int OBJECT_SIZE = 1024;
    // 字节数组对象名称
    private static final String BYTE_ARRAY_CLASS_NAME = new byte[0].getClass().getName();
    // 需要使用静态field，而不是方法内本地变量，否则编译后循环内的new byte[]全部会被省略，只剩最后一次的
    public static byte[] tmp;

    public static void main(String[] args) throws Exception {
        var whiteBox = WhiteBox.getWhiteBox();
        // 初始化 JFR 记录
        Recording recording = new Recording();
        recording.enable("jdk.ObjectAllocationInNewTLAB");
        // JFR 记录启动
        recording.start();
        // 强制 fullGC 防止接下来程序发生 GC
        whiteBox.fullGC();
        // 同时可以区分出初始化带来的其他线程的TLAB相关的日志
        whiteBox.fullGC();
        // 分配对象，大小1KB
        for (int i = 0; i < 512; ++i) {
            tmp = new byte[OBJECT_SIZE - BYTE_ARRAY_OVERHEAD];
        }
        // 强制 fullGC，回收所有 TLAB
        whiteBox.fullGC();
        // 分配对象，大小100KB
        for (int i = 0; i < 200; ++i) {
            tmp = new byte[OBJECT_SIZE * 100 - BYTE_ARRAY_OVERHEAD];
        }
        whiteBox.fullGC();
        // 将 JFR 记录 dump 到一个文件
        Path dir = Paths.get("child");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path path = Paths.get(dir.toString(), "recording-" + recording.getId() + "-pid" + ProcessHandle.current().pid() + ".jfr");
        // 将 JFR 记录写入文件
        recording.dump(path);

        int countOf1KBObjectAllocationInNewTLAB = 0;
        int countOf100KBObjectAllocationInNewTLAB = 0;
        // 读取文件中的所有 JFR 事件
        for (RecordedEvent event : RecordingFile.readAllEvents(path)) {
            // 获取分配的对象的类型
            String className = event.getString("objectClass.name");
            if (BYTE_ARRAY_CLASS_NAME.equalsIgnoreCase(className)) {
                RecordedFrame recordedFrame = event.getStackTrace().getFrames().get(0);
                // 同时必须是咱们这里的main方法分配的对象，并且是Java堆栈中的main方法
                if (recordedFrame.isJavaFrame() && "main".equalsIgnoreCase(recordedFrame.getMethod().getName())) {
                    // 获取分配对象大小
                    long allocationSize = event.getLong("allocationSize");
                    if ("jdk.ObjectAllocationInNewTLAB".equalsIgnoreCase(event.getEventType().getName())) {
                        if (allocationSize == 102400) {
                            countOf100KBObjectAllocationInNewTLAB++;
                        } else if (allocationSize == 1024) {
                            countOf1KBObjectAllocationInNewTLAB++;
                        }
                    } else {
                        throw new Exception("unexpected size of TLAB event");
                    }
                    System.out.println(event);
                }
            }
        }
        System.out.println("countOf1KBObjectAllocationInNewTLAB: " + countOf1KBObjectAllocationInNewTLAB);
        System.out.println("countOf100KBObjectAllocationInNewTLAB: " + countOf100KBObjectAllocationInNewTLAB);
    }
}