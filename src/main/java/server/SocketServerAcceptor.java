package server;

import lombok.extern.slf4j.Slf4j;
import org.agrona.nio.NioSelectedKeySet;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.ToIntFunction;

/**
 * @author lzn
 * @date 2023/10/30 21:04
 * @description Nio tcp socket acceptor
 */
@Slf4j
public class SocketServerAcceptor implements Runnable, Closeable, ToIntFunction<SelectionKey> {

    private Selector selector;
    private NioSelectedKeySet selectedKeySet;
    private volatile boolean isRunning = true;
    private long selectTimeMillis;
    public final String name = "SocketServerAcceptor";
    public DataProcessor dataProcessor;

    public SocketServerAcceptor(int startPort, int readSizeInBytes, int writeSizeInBytes, long selectTimeMillis) {
        try {
            dataProcessor = new DataProcessor(readSizeInBytes, writeSizeInBytes);
            this.selectTimeMillis = selectTimeMillis;
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.socket().bind(new InetSocketAddress(startPort));
            serverSocket.configureBlocking(false);

            // Open the Multiplexer(selector) to handle channel -> epoll
            selector = Selector.open();
            selectedKeySet = SelectorFactory.keySet(selector);
            // Register serverSocketChannel to the selector
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            log.info("The server started........");

            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                // Blocked for selectTimeMillis, waiting for the ongoing socket coming
                selector.select(selectTimeMillis);
                selectedKeySet.forEach(this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int applyAsInt(SelectionKey key) {
        if (!key.isValid()) {
            return 0;
        }
        if (key.isAcceptable()) {
            ServerSocketChannel socketChannel = (ServerSocketChannel) key.channel();
            // Get current socket channel for given connection
            try {
                SocketChannel channel = socketChannel.accept();
                Socket socket = channel.socket();
                channel.configureBlocking(false);
                log.info("The Client {} has been connected............", socket.getRemoteSocketAddress());
                dataProcessor.queueChannel(channel);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return 0;
    }

    public void init() {
        new Thread(this, name).start();
        new Thread(dataProcessor, dataProcessor.name).start();
    }

    @Override
    public void close() {
        isRunning = false;
        log.info("The Socket server has been closed");
    }

    public static void main(String[] args) {
        SocketServerAcceptor socketAcceptor = new SocketServerAcceptor(9000, 16 * 1024, 16 * 1024, 1000);
        socketAcceptor.init();
    }
}
