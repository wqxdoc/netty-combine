package com.study.netty.filebase;

/**
 * 文件发送客户端，通过字节流来发送文件，仅实现文件传输部分，
 * 没有对文件传输结束进行处理
 * 应该发送文件发送结束标识，供接受端关闭流。
 */
public class FileClientHandler /*extends SimpleChannelHandler */{

    /*// 每次处理的字节数
    private int readLength = 8;

    @Override public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // 发送文件 sendFile(e.getChannel());
    }

    private void sendFile(Channel channel) throws IOException {
        File file = new File("E:/1.txt");
        FileInputStream fis = new FileInputStream(file);
        int count = 0;
        for (;;) {
            BufferedInputStream bis = new BufferedInputStream(fis);
            byte[] bytes = new byte[readLength];
            int readNum = bis.read(bytes, 0, readLength);
            if (readNum == -1) {
                return;
            }
            sendToServer(bytes, channel, readNum);
            System.out.println("Send count: " + ++count);
        }
    }

    private void sendToServer(byte[] bytes, Channel channel, int length) throws IOException {
        ChannelBuffer buffer = ChannelBuffers.copiedBuffer(bytes, 0, length);
        channel.write(buffer);
    }*/
}
