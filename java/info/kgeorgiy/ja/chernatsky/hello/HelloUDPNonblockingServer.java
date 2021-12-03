package info.kgeorgiy.ja.chernatsky.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class HelloUDPNonblockingServer implements HelloServer {
    private static class ServerManager {
        private static class BufferAndAddress {
            private final ByteBuffer buffer;
            private final SocketAddress destination;

            BufferAndAddress(final ByteBuffer buffer, final SocketAddress destination) {
                this.buffer = buffer;
                this.destination = destination;
            }

            ByteBuffer getBuffer() {
                return buffer;
            }

            SocketAddress getDestination() {
                return destination;
            }
        }

        final private Queue<ByteBuffer> emptyBuffers;
        final private Queue<BufferAndAddress> buffersToSend;
        final private Selector selector;

        ServerManager(final Selector selector, final int threads) {
            this.emptyBuffers = new ArrayDeque<>();
            this.buffersToSend = new ArrayDeque<>();
            this.selector = selector;
            IntStream.range(0, threads)
                    .forEach(i -> emptyBuffers.add(ByteBuffer.allocate(Utils.BYTE_BUFFER_SIZE)));
        }

        synchronized ByteBuffer takeEmptyBuffer(final SelectionKey key) {
            final ByteBuffer emptyBuffer = emptyBuffers.remove();
            if (emptyBuffers.isEmpty()) {
                key.interestOpsAnd(~SelectionKey.OP_READ);
            }
            return emptyBuffer;
        }

        synchronized void giveEmptyBuffer(final ByteBuffer buffer, final SelectionKey key) {
            emptyBuffers.add(buffer);
            if (emptyBuffers.size() == 1) {
                key.interestOpsOr(SelectionKey.OP_READ);
                selector.wakeup();
            }
        }

        synchronized BufferAndAddress takeBufferToSend(final SelectionKey key) {
            final BufferAndAddress bufferAndAddress = buffersToSend.remove();
            if (buffersToSend.isEmpty()) {
                key.interestOpsAnd(~SelectionKey.OP_WRITE);
            }
            return bufferAndAddress;
        }

        synchronized void giveBufferToSend(final ByteBuffer buffer, final SocketAddress destination, final SelectionKey key) {
            buffersToSend.add(new BufferAndAddress(buffer, destination));
            if (buffersToSend.size() == 1) {
                key.interestOpsOr(SelectionKey.OP_WRITE);
                selector.wakeup();
            }
        }
    }

    private static final int AWAIT_TERMINATION_SECONDS = 10;

    private ExecutorService mainExecutor;
    private ExecutorService executors;
    private Selector selector;
    private ServerManager bufferManager;

    @Override
    public void start(final int port, final int threads) {
        try {
            selector = Selector.open();
        } catch (final IOException e) {
            Utils.printError("Couldn't create selector", e);
            return;
        }

        bufferManager = new ServerManager(selector, threads);

        DatagramChannel channel = null;
        try {
            channel = Utils.newDatagramChannel().bind(new InetSocketAddress(port));
            channel.register(selector, SelectionKey.OP_READ);
        } catch (final IOException e) {
            Utils.printError("Couldn't create datagram channel", e);
            if (channel != null) {
                Utils.closeChannel(channel);
            }
            Utils.closeSelector(selector);
            return;
        }

        mainExecutor = Executors.newSingleThreadExecutor();
        mainExecutor.submit(this::action);
        mainExecutor.shutdown();

        executors = Executors.newFixedThreadPool(threads);
    }

    private void action() {
        final DatagramChannel channel = (DatagramChannel) selector.keys().iterator().next().channel();
        while (selector.isOpen()) {
            try {
                if (selector.select() == 0) {
                    continue;
                }
            } catch (final IOException e) {
                Utils.printError("I/O exception occurred during key selection", e);
                return;
            }

            final Set<SelectionKey> selectionKeySet = selector.selectedKeys();
            final SelectionKey key = selectionKeySet.iterator().next();
            selectionKeySet.remove(key);

            if (key.isWritable()) {
                writeResponseAction(channel, key);
            }
            if (key.isReadable()) {
                readRequestAction(channel, key);
            }
        }
    }

    private void readRequestAction(final DatagramChannel channel, final SelectionKey key) {
        final ByteBuffer buffer = bufferManager.takeEmptyBuffer(key);
        try {
            final SocketAddress sourceAddress = channel.receive(buffer);
            executors.submit(() -> {
                final String receivedMessage = Utils.getStringFromBuffer(buffer);
//                Utils.printReceivedMessage(receivedMessage);
                bufferManager.giveEmptyBuffer(buffer.clear(), key);
                final String messageToSend = Utils.getServerMessage(receivedMessage);
                bufferManager.giveBufferToSend(Utils.newBufferFromString(messageToSend), sourceAddress, key);
            });
        } catch (final IOException e) {
            Utils.printError("Failed receiving datagram", e);
        }
    }

    private void writeResponseAction(final DatagramChannel channel, final SelectionKey key) {
        final ServerManager.BufferAndAddress bufferAndAddress = bufferManager.takeBufferToSend(key);
        try {
            channel.send(bufferAndAddress.getBuffer(), bufferAndAddress.getDestination());
//            Utils.printSentMessage(Utils.getStringFromBuffer(bufferAndAddress.getBuffer()));
        } catch (final IOException e) {
            Utils.printError("Failed sending datagram", e);
        }
    }

    @Override
    public void close() {
        Utils.closeSelectorAndChannels(selector);
        executors.shutdown();
        Utils.betterAwaitTermination(executors, AWAIT_TERMINATION_SECONDS);
        Utils.betterAwaitTermination(mainExecutor, AWAIT_TERMINATION_SECONDS);
    }

    public static void main(final String[] args) {
        HelloUDPServer.commonServerMain(args, new HelloUDPNonblockingServer());
    }
}
