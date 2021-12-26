package com.crazymakercircle.iodemo.zeroCopy;

import com.crazymakercircle.NioDemoConfig;
import com.crazymakercircle.util.IOUtil;
import com.crazymakercircle.util.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.crazymakercircle.config.Constants.BIG_BUFFER_SIZE;


/**
 * 文件传输Server端
 * Created by 尼恩@ 疯创客圈
 */
public class RandomBigFileNioReceiveServer {

    //接受文件路径
    private static final String RECEIVE_PATH = NioDemoConfig.SOCKET_RECEIVE_PATH;

    private Charset charset = Charset.forName("UTF-8");
    private static volatile boolean over = false;


    private boolean isOver() {
        return over;
    }

    /**
     * 服务器端保存的客户端对象，对应一个客户端文件
     */
    static class Client {

        // mmap  映射
        public MappedByteBuffer mappedByteBuffer;

        int step = 1; //1 读取文件名称的长度，2 读取文件名称  ，3 ，读取文件内容的长度， 4 读取文件内容
        //文件名称
        String fileName = null;

        //长度
        long fileSize;
        int fileNameLength;

        //开始传输的时间
        long startTime;

        //客户端的地址
        InetSocketAddress remoteAddress;

        //输出的文件通道
        FileChannel outChannel;

        //接收长度
        long receiveLength;

        public boolean isFinished() {
            return receiveLength >= fileSize;
        }

        protected final AtomicInteger wrotePosition = new AtomicInteger(0);


        public boolean writeMmap(ByteBuffer data) {
            final int msgLen = data.remaining();
            int currentPos = this.wrotePosition.get();

            if (currentPos < this.fileSize) {
                ByteBuffer slice = this.mappedByteBuffer.slice();
                slice.position(currentPos);
                slice.put(data);
                this.wrotePosition.addAndGet(msgLen);
            }

            return true;
        }
    }

    private ByteBuffer buffer = ByteBuffer.allocateDirect(BIG_BUFFER_SIZE);

    //使用Map保存每个客户端传输，当OP_READ通道可读时，根据channel找到对应的对象
    Map<SelectableChannel, Client> clientMap = new HashMap<SelectableChannel, Client>();


    public void startServer() throws IOException {
        // 1、获取Selector选择器
        Selector selector = Selector.open();

        // 2、获取通道
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        ServerSocket serverSocket = serverChannel.socket();

        // 3.设置为非阻塞
        serverChannel.configureBlocking(false);
        // 4、绑定连接
        InetSocketAddress address
                = new InetSocketAddress(NioDemoConfig.SOCKET_SERVER_PORT);
        serverSocket.bind(address);
        // 5、将通道注册到选择器上,并注册的IO事件为：“接收新连接”
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        Logger.tcfo("serverChannel is linstening...");
        // 6、轮询感兴趣的I/O就绪事件（选择键集合）
        while (selector.select() > 0 && !isOver()) {
            if (null == selector.selectedKeys()) continue;
            // 7、获取选择键集合
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                // 8、获取MappedByteBuffer单个的选择键，并处理
                SelectionKey key = it.next();
                if (null == key) continue;

                // 9、判断key是具体的什么事件，是否为新连接事件
                if (key.isAcceptable()) {
                    // 10、若接受的事件是“新连接”事件,就获取客户端新连接
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    SocketChannel socketChannel = server.accept();
                    if (socketChannel == null) continue;
                    // 11、客户端新连接，切换为非阻塞模式
                    socketChannel.configureBlocking(false);
                    socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);

                    // 12、将客户端新连接通道注册到selector选择器上
                    SelectionKey selectionKey =
                            socketChannel.register(selector, SelectionKey.OP_READ);
                    // 余下为业务处理
                    Client client = new Client();
                    client.remoteAddress
                            = (InetSocketAddress) socketChannel.getRemoteAddress();
                    clientMap.put(socketChannel, client);
                    Logger.debug(socketChannel.getRemoteAddress() + "连接成功...");

                } else if (key.isReadable()) {
                    processData(key);
                }
                // NIO的特点只会累加，已选择的键的集合不会删除
                // 如果不删除，下一次又会被select函数选中
                it.remove();
            }
        }
    }


    /**
     * 处理客户端传输过来的数据
     */
    private void processData(SelectionKey key) throws IOException {

        SocketChannel socketChannel = (SocketChannel) key.channel();
        int num = 0;
        Client client = clientMap.get(key.channel());

        buffer.clear();
        while (4 != client.step) {
            num = socketChannel.read(buffer);
             if (num <= 0) {
                continue;
            }
            Logger.cfo("收到的字节数 = " + num);

            //切换到读模式
            buffer.flip();

            readBuffer(client, buffer);
            buffer.clear();

        }


        transferData(socketChannel, client);
        Logger.cfo("零复制 over，client.fileLength ：" + client.fileSize);
        client.mappedByteBuffer.force();

        finished(client);
        over = true;
    }

    private void transferData(SocketChannel socketChannel, Client client) throws IOException {
        Logger.cfo("零复制开始位置：" + client.wrotePosition);
        while (client.wrotePosition.get() < client.fileSize) {


            buffer.clear();
            int num = socketChannel.read(buffer);
             if (num <= 0) {
                continue;
            }

            //切换到读模式
            buffer.flip();
            // 追加内容

            ByteBuffer slice = buffer.slice();
            int transferLen = slice.remaining();
            Logger.cfo("transferLen ：" + transferLen);

            client.writeMmap(slice);
            Logger.cfo("收到的字节数 = " + num +"/"+ client.wrotePosition.get() +"/"+ client.fileSize);
        }

        Logger.cfo("transfer 传输结束");

    }

    private void readBuffer(Client client, ByteBuffer buffer) {
        while (len(buffer) > 0) {   //客户端发送过来的，首先处理文件名长度
            if (1 == client.step) {
                int fileNameLengthByteLen = len(buffer);
                Logger.cfo("读取文件名称长度之前，可读取的字节数 = " + fileNameLengthByteLen);
                Logger.cfo("读取文件名称长度之前，buffer.remaining() = " + buffer.remaining());
                Logger.cfo("读取文件名称长度之前，buffer.capacity() = " + buffer.capacity());
                Logger.cfo("读取文件名称长度之前，buffer.limit() = " + buffer.limit());
                Logger.cfo("读取文件名称长度之前，buffer.position() = " + buffer.position());


                //获取文件名称长度
                client.fileNameLength = buffer.getInt();

                Logger.cfo("读取文件名称长度之后，buffer.remaining() = " + buffer.remaining());
                Logger.cfo("读取文件名称长度 = " + client.fileNameLength);

                client.step = 2;

            } else if (2 == client.step) {
                Logger.cfo("step 2");

                if (len(buffer) < client.fileNameLength) {
                    Logger.cfo("出现半包问题，需要更加复杂的拆包方案");
                    throw new RuntimeException("出现半包问题，需要更加复杂的拆包方案");
                }
                byte[] fileNameBytes = new byte[client.fileNameLength];


                buffer.get(fileNameBytes);


                // 文件名
                String fileName = new String(fileNameBytes, charset);
                Logger.cfo("读取文件名称 = " + fileName);

                File directory = new File(RECEIVE_PATH);
                if (!directory.exists()) {
                    directory.mkdir();
                }
                Logger.info("NIO  传输目标dir：", directory);

                client.fileName = fileName;
                String fullName = directory.getAbsolutePath() + File.separatorChar + fileName;
                Logger.info("NIO  传输目标文件：", fullName);

                File file = new File(fullName.trim());

                try {
                    if (!file.exists()) {
                        file.createNewFile();

                    }

                    FileChannel fileChannel = new RandomAccessFile(file, "rw").getChannel();
                    client.outChannel = fileChannel;
                } catch (IOException e) {
                    e.printStackTrace();
                }

                client.step = 3;

            } else if (3 == client.step) {
                Logger.cfo("step 3");

                //客户端发送过来的，首先处理文件内容长度

                if (len(buffer) < 4) {
                    Logger.cfo("出现半包问题，需要更加复杂的拆包方案");
                    throw new RuntimeException("出现半包问题，需要更加复杂的拆包方案");
                }
                //获取文件内容长度
                client.fileSize = buffer.getInt();
                Logger.cfo("读取文件内容长度 = " + client.fileSize);

                //  创建 mmap  映射
                try {
                    client.mappedByteBuffer = client.outChannel.map(FileChannel.MapMode.READ_WRITE, 0, client.fileSize);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                Logger.cfo("文件映射完成");
                Logger.cfo("读取文件内容长度之后，buffer.remaining() = " + buffer.remaining());

                client.startTime = System.currentTimeMillis();
                if (len(buffer) > 0) {

//                    // 写入文件
                    // 追加内容

                    ByteBuffer slice = buffer.slice();
                    client.writeMmap(slice);
                }

                client.step = 4;


            }
        }
    }


    private void finished(Client client) {
        IOUtil.unmap(client.mappedByteBuffer);
        IOUtil.closeQuietly(client.outChannel);
        Logger.info("上传完毕");
        Logger.debug("文件接收成功,File Name：" + client.fileName);
        Logger.debug(" Size：" + IOUtil.getFormatFileSize(client.fileSize));
        long endTime = System.currentTimeMillis();
        Logger.debug("NIO IO 传输毫秒数：" + (endTime - client.startTime));
    }


    /**
     * 入口
     *
     * @param args
     */
    public static void main(String[] args) throws Exception {
        RandomBigFileNioReceiveServer server = new RandomBigFileNioReceiveServer();
        server.startServer();
    }

    private static int len(ByteBuffer buffer) {

        Logger.cfo(" >>>  buffer left：" + buffer.remaining());
        return buffer.remaining();
    }

}