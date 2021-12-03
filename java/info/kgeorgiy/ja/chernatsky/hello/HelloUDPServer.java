package info.kgeorgiy.ja.chernatsky.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class HelloUDPServer implements HelloServer {
    private ExecutorService executors;
    private DatagramSocket socket;

    /**
     * Starts a new Hello server.
     * This method should return immediately.
     *
     * @param port server port.
     * @param threads number of working threads.
     */
    @Override
    public void start(final int port, final int threads) {
        try {
            executors = Executors.newFixedThreadPool(threads);
            socket = new DatagramSocket(port);

            final int bufferSize = socket.getReceiveBufferSize();
            final Runnable template = () -> {
                final DatagramPacket packet = new DatagramPacket(new byte[bufferSize], bufferSize);
                while (!socket.isClosed()) {
                    try {
                        final String receivedMessage = Utils.receiveDatagram(socket, packet);
                        Utils.sendDatagram(socket, packet, Utils.getServerMessage(receivedMessage));
                    } catch (final IOException e) {
                        Utils.printError("Failed sending or receiving datagram packet", e);
                    }
                }
            };

            IntStream.range(0, threads).forEach(id -> executors.submit(template));
            executors.shutdown();
        } catch (final SocketException e) {
            Utils.printError("Can't create socket to receive", e);
        }
    }

    /**
     * Stops server and deallocates all resources.
     */
    @Override
    public void close() {
        socket.close();
        Utils.betterAwaitTermination(executors, 1);
    }

    static void commonServerMain(final String[] args, final HelloServer server) {
        if (args == null || args.length != 2 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.out.printf("Usage: java %s <port> <threads>", server.getClass().getCanonicalName());
            return;
        }

        try {
            server.start(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        } catch (final NumberFormatException e) {
            Utils.printError("Argument is not an integer", e);
        }
    }

    /**
     * Main HelloUDPServer method. Creates an instance of HelloUDPServer and calls method
     * {@link #start(int, int)} with given arguments from command line.
     * @param args Command line arguments. Should be contained of 2 elements.
     */
    public static void main(final String[] args) {
        commonServerMain(args, new HelloUDPServer());
    }
}
