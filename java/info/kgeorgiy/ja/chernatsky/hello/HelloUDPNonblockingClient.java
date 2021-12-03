package info.kgeorgiy.ja.chernatsky.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

public class HelloUDPNonblockingClient implements HelloClient {
    private static class DatagramChannelAttachment {
        private final ByteBuffer buffer;
        private final int threadId;
        private int requestId;

        private DatagramChannelAttachment(final int threadId) {
            this.threadId = threadId;
            this.buffer = ByteBuffer.allocate(Utils.BYTE_BUFFER_SIZE);
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }

        public int getThreadId() {
            return threadId;
        }

        public int getRequestId() {
            return requestId;
        }

        public void incrementRequestId() {
            requestId++;
        }
    }

    private static final int SELECT_TIMEOUT = 100;

    @Override
    public void run(final String host, final int port, final String prefix, final int threads, final int requests) {
        final SocketAddress address;
        try {
            address = Utils.newInetSocketAddress(host, port);
        } catch (final UnknownHostException e) {
            Utils.printError("Bad host name", e);
            return;
        }

        try (final Selector selector = Selector.open()) {
            try {
                for (int threadId = 0; threadId < threads; threadId++) {
                    DatagramChannel channel = null;
                    try {
                        channel = Utils.newDatagramChannel().connect(address);
                        channel.register(selector, SelectionKey.OP_WRITE, new DatagramChannelAttachment(threadId));
                    } catch (final IOException e) {
                        Utils.printError("Couldn't create datagram channels", e);
                        if (channel != null) {
                            Utils.closeChannel(channel);
                        }
                        return;
                    }
                }
                action(selector, prefix, requests);
            } finally {
                Utils.closeSelectorChannels(selector);
            }
        } catch (final IOException e) {
            Utils.printError("Couldn't create or close selector", e);
        }
    }

    private void action(final Selector selector, final String prefix, final int requests) {
        while (!Thread.interrupted() && !selector.keys().isEmpty()) {
            try {
                if (selector.select(SELECT_TIMEOUT) == 0) {
                    selector.keys().forEach(key -> key.interestOpsOr(SelectionKey.OP_WRITE));
                    continue;
                }
            } catch (final IOException e) {
                Utils.printError("I/O exception occurred during key selection", e);
                return;
            }

            final Set<SelectionKey> selectionKeySet = selector.selectedKeys();
            for (final Iterator<SelectionKey> i = selectionKeySet.iterator(); i.hasNext(); ) {
                final SelectionKey key = i.next();
                i.remove();

                final DatagramChannel channel = (DatagramChannel) key.channel();
                final DatagramChannelAttachment attachment = (DatagramChannelAttachment) key.attachment();
                if (key.isReadable()) {
                    readResponseAction(channel, attachment, key, requests);
                }
                if (key.isValid() && key.isWritable()) {
                    writeRequestAction(channel, attachment, key, prefix);
                }
            }
        }
    }

    private void writeRequestAction(
            final DatagramChannel channel,
            final DatagramChannelAttachment attachment,
            final SelectionKey key,
            final String prefix
    ) {
        final String message = Utils.getClientMessage(prefix, attachment.getThreadId(), attachment.getRequestId());
        try {
            channel.write(Utils.newBufferFromString(message));
//            Utils.printSentMessage(message);
        } catch (final IOException e) {
            Utils.printError("Failed sending datagram", e);
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    private void readResponseAction(
            final DatagramChannel channel,
            final DatagramChannelAttachment attachment,
            final SelectionKey key,
            final int requests
    ) {
        final ByteBuffer buffer = attachment.getBuffer();
        try {
            buffer.clear();
            channel.receive(buffer);
        } catch (final IOException e) {
            Utils.printError("Failed receiving datagram", e);
            return;
        }

        final String message = Utils.getStringFromBuffer(buffer);
//        Utils.printReceivedMessage(message);
        if (Utils.isValidResponse(message, attachment.getThreadId(), attachment.getRequestId())) {
            attachment.incrementRequestId();
        }

        if (attachment.getRequestId() == requests) {
            Utils.closeChannel(channel);
        } else {
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    public static void main(final String[] args) {
        HelloUDPClient.commonClientMain(args, new HelloUDPNonblockingClient());
    }
}
