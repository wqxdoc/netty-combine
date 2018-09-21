package com.study.netty.filebase;

/**
 * 文件传输接收端，没有处理文件发送结束关闭流的情景
 */
public class FileServerHandler /*extends SimpleChannelHandler*/ {
    /*private File file = new File("F:/2.txt");
    private FileOutputStream fos;

    public FileServerHandler() {
        try {
            if (!file.exists()) {
                file.createNewFile();
            } else {
                file.delete();
                file.createNewFile();
            }

            fos = new FileOutputStream(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ChannelBuffer buffer = (ChannelBuffer) e.getMessage();
        int length = buffer.readableBytes();
        buffer.readBytes(fos, length);
        fos.flush();
        buffer.clear();
    }*/
}
