package info.kgeorgiy.ja.chernatsky.hello;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("UnnecessaryLocalVariable")
public class Utils {
    static final int BYTE_BUFFER_SIZE = 2048;
    static final Pattern RESPONSE_REGEX = Pattern.compile("[^\\d]*([\\d]+)[^\\d]+([\\d]+)[^\\d]*");

    static void betterAwaitTermination(final ExecutorService pool, final int seconds) {
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(seconds, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(seconds, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (final InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    synchronized static void printReceivedMessage(final String message) {
        printMessage("Received: " + message);
    }

    synchronized static void printSentMessage(final String message) {
        printMessage("Sent: " + message);
    }

    static void sendDatagram(final DatagramSocket socket, final DatagramPacket packet, final String message) throws IOException {
        packet.setData(message.getBytes(StandardCharsets.UTF_8));
        socket.send(packet);
//        printSentMessage(message);
    }

    static void sendDatagram(final DatagramChannel channel, final ByteBuffer buffer, final SocketAddress address) throws IOException {
        channel.send(buffer, address);
    }

    static ByteBuffer newBufferFromString(final String str) {
        return ByteBuffer.wrap(str.getBytes(StandardCharsets.UTF_8));
    }

    static String receiveDatagram(final DatagramSocket socket, final DatagramPacket packet) throws IOException {
        socket.receive(packet);
        final String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
//        printReceivedMessage(message);
        return message;
    }

    static SocketAddress receiveDatagram(final DatagramChannel channel, final ByteBuffer buffer) throws IOException {
        buffer.clear();
        return channel.receive(buffer);
    }

    static String getStringFromBuffer(final ByteBuffer buffer) {
        buffer.flip();
        final String message = StandardCharsets.UTF_8.decode(buffer).toString();
        return message;
    }

    static boolean isValidResponse(final String responseMessage, final int threadId, final int requestId) {
        final Matcher matcher = RESPONSE_REGEX.matcher(responseMessage);
        return matcher.matches() &&
                matcher.group(1).equals(Integer.toString(threadId)) &&
                matcher.group(2).equals(Integer.toString(requestId));
    }

    synchronized static void printError(final String message, final Exception e) {
        System.err.println(message + ": " + e.getMessage());
    }

    synchronized static void printMessage(final String message) {
        System.out.println(message);
    }

    static InetSocketAddress newInetSocketAddress(final String host, final int port) throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(host), port);
    }

    static DatagramChannel newDatagramChannel() throws IOException {
        return (DatagramChannel) DatagramChannel.open()
                .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                .configureBlocking(false);
    }

    static String getClientMessage(final String prefix, final int threadId, final int requestId) {
        return prefix + threadId + "_" + requestId;
    }

    static String getServerMessage(final String receivedMessage) {
        return "Hello, " + receivedMessage;
    }

    static void closeChannel(final SelectableChannel channel) {
        try {
            channel.close();
        } catch (final IOException e) {
            printError("Couldn't close datagram channel", e);
        }
    }

    static void closeSelectorChannels(final Selector selector) {
        selector.keys().forEach(key -> closeChannel(key.channel()));
    }

    static void closeSelector(final Selector selector) {
        try {
            selector.close();
        } catch (final IOException e) {
            printError("Couldn't close selector", e);
        }
    }

    static void closeSelectorAndChannels(final Selector selector) {
        closeSelectorChannels(selector);
        closeSelector(selector);
    }
}
