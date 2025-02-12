import jdk.test.whitebox.WhiteBox;

public class TestWhiteBox {

    public static void main(String[] args) throws InterruptedException {
        var whiteBox = WhiteBox.getWhiteBox();
        // 获取 ReservedCodeCacheSize 这个 JVM flag 的值
        var reservedCodeCacheSize = whiteBox.getUintxVMFlag("ReservedCodeCacheSize");
        System.out.println("reservedCodeCacheSize:" + reservedCodeCacheSize);
        // 打印堆内存各项指标
        whiteBox.printHeapSizes();
        // 执行full GC
        whiteBox.fullGC();
        // 保持进程不退出，保证日志打印完整
        Thread.currentThread().join();

    }
}
