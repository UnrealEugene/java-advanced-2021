package info.kgeorgiy.ja.chernatsky.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class HelloUDPClient implements HelloClient {
    private static final int SOCKET_TIMEOUT = 200;

    /**
     * Runs Hello client.
     * This method should return when all requests completed.
     *
     * @param host server host
     * @param port server port
     * @param prefix request prefix
     * @param threads number of request threads
     * @param requests number of requests per thread.
     */
    @Override
    public void run(final String host, final int port, final String prefix, final int threads, final int requests) {
        final SocketAddress address;
        try {
            address = Utils.newInetSocketAddress(host, port);
//            address = new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (final UnknownHostException e) {
            Utils.printError("Bad host name", e);
            return;
        }
        final ExecutorService executors = Executors.newFixedThreadPool(threads);
        final CountDownLatch latch = new CountDownLatch(threads);
        IntStream.range(0, threads)
                .forEach(id -> executors.submit(() -> sendAndReceiveSocket(address, prefix, requests, id, latch)));
        executors.shutdown();
        try {
            latch.await();
        } catch (final InterruptedException e) {
            Utils.betterAwaitTermination(executors, 10 * threads * requests);
        }
    }

    private static void sendAndReceiveSocket(
            final SocketAddress address,
            final String prefix,
            final int requests,
            final int threadId,
            final CountDownLatch latch
    ) {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(SOCKET_TIMEOUT);
            final byte[] buffer = new byte[socket.getReceiveBufferSize()];
            final DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address);
            IntStream.range(0, requests).forEach(requestId -> {
                while (!Thread.interrupted() && !socket.isClosed()) {
                    try {
                        Utils.sendDatagram(socket, packet, Utils.getClientMessage(prefix, threadId, requestId));
                        packet.setData(buffer);
                        final String responseMessage = Utils.receiveDatagram(socket, packet);
                        if (Utils.isValidResponse(responseMessage, threadId, requestId)) {
                            break;
                        }
                    } catch (final IOException e) {
                        Utils.printError("Failed sending or receiving datagram packet", e);
                    }
                }
            });
        } catch (final SocketException e) {
            Utils.printError("Can't create socket to send", e);
        } finally {
            latch.countDown();
        }
    }

    static void commonClientMain(final String[] args, final HelloClient client) {
        if (args == null || args.length != 5 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.out.printf("Usage: java %s <host> <port> <prefix> <threads> <requests>%n", client.getClass().getCanonicalName());
            return;
        }

        try {
            client.run(
                    args[0],
                    Integer.parseInt(args[1]),
                    args[2],
                    Integer.parseInt(args[3]),
                    Integer.parseInt(args[4])
            );
        } catch (final NumberFormatException e) {
            Utils.printError("Argument is not an integer", e);
        }
    }

    /**
     * Main HelloUDPClient method. Creates an instance of HelloUDPClient and calls method
     * {@link #run(String, int, String, int, int)} with given arguments from command line.
     * @param args Command line arguments. Should be contained of 5 elements.
     */
    public static void main(final String[] args) {
        commonClientMain(args, new HelloUDPClient());
    }
}
