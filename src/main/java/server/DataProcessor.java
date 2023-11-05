package server;

import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.agrona.nio.NioSelectedKeySet;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.function.ToIntFunction;

/**
 * @author lzn
 * @date 2023/11/03 22:11
 * @description Data processor for handing accept/read/write events and read/write data
 */
@Slf4j
public class DataProcessor implements Runnable, Closeable, ToIntFunction<SelectionKey> {

    private final Selector selector;
    private final NioSelectedKeySet selectedKeySet;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    public final String name = "DataProcessor";
    private volatile boolean isRunning = true;
    private final OneToOneConcurrentArrayQueue<SocketChannel> channelQueue = new OneToOneConcurrentArrayQueue<>(1024);

    public DataProcessor(int readSizeInBytes, int writeSizeInBytes) throws IOException {
        this.readBuffer = ByteBuffer.allocate(readSizeInBytes);
        this.writeBuffer = ByteBuffer.allocateDirect(writeSizeInBytes);
        selector = Selector.open();
        selectedKeySet = SelectorFactory.keySet(selector);
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    public void queueChannel(SocketChannel channel) {
        boolean offer = channelQueue.offer(channel);
        if (!offer) {
            log.info("Unable queuing new channel, close current channel so that the client can reconnect");
            try {
                channel.socket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                checkForNewConnections();
                if (selector.selectNow() > 0) {
                    selectedKeySet.forEach(this);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                selectedKeySet.reset();
            }
        }
    }

    @Override
    public int applyAsInt(SelectionKey key) {
        if (!key.isValid()) {
            return 0;
        }
        if (key.isValid() && key.isReadable()) {
            read(key);
        }

        if (key.isValid() && key.isWritable()) {
            write(key);
        }

        return 0;
    }

    private void checkForNewConnections() {
        if(channelQueue.isEmpty()){
            return;
        }
        SocketChannel channel = channelQueue.poll();
        if (channel != null) {
            try {
                channel.register(selector, SelectionKey.OP_READ);
            } catch (ClosedChannelException e) {
                e.printStackTrace();
            }
        }
    }

    public void read(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            int bytesRead = 0;
            try {
                bytesRead = channel.read(readBuffer);
            } catch (IOException e) {
                processError(key, channel);
            }

            if (bytesRead == -1) {
                // Close socket if the client was already disconnected
                processError(key, channel);
            }

            readBuffer.flip();
            readUnProcessedBytes(readBuffer, readBuffer.remaining(), bytesRead);
            readBuffer.clear();

            String response = "Response from server";
            writeBuffer.put(response.getBytes(), 0, response.length());
            key.interestOps(SelectionKey.OP_WRITE);
        } catch (Exception e) {
            log.error("Error on reading bytes, {}", e.getMessage());
            processError(key, channel);
        }

    }

    public void write(SelectionKey key) {
        log.info("Writing data......................");
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            if (writeBuffer.position() > 0) {
                writeBuffer.flip();
                int bytesWritten = channel.write(writeBuffer);
                if (bytesWritten > 0) {
                    if (writeBuffer.hasRemaining()) {
                        // In case of partial write
                        writeBuffer.compact();
                    } else {
                        writeBuffer.clear();
                        key.interestOps(SelectionKey.OP_READ);
                    }
                }
            } else {
                // No more data need to be written, switch to read interest
                key.interestOps(SelectionKey.OP_READ);
            }
        } catch (IOException e) {
            log.error("Error on writing bytes, {}", e.getMessage());
            processError(key, channel);
        }
    }

    private void readUnProcessedBytes(ByteBuffer readBuffer, int bytesLength, int bytesRead) {
        log.info("Reading request data......................");
    }

    private void processError(SelectionKey key, SocketChannel channel) {
        if (key != null) {
            key.cancel();
        }
        try {
            if (channel != null) {
                InetSocketAddress socketAddress = (InetSocketAddress) channel.getRemoteAddress();
                log.info("The Channel {} has been closed", socketAddress.getAddress().getHostAddress());
                channel.socket().close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        isRunning = false;
        log.info("The Data processor has been closed");
    }
}
