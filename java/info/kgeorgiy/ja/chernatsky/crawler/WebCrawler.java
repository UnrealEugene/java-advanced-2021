package info.kgeorgiy.ja.chernatsky.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

public class WebCrawler implements AdvancedCrawler {
    private static class BFSEntry {
        private final String url;
        private final int depth;

        private BFSEntry(String url, int depth) {
            this.url = url;
            this.depth = depth;
        }

        public String getUrl() {
            return url;
        }

        public int getDepth() {
            return depth;
        }
    }

    private static class LimitedExecutor {
        private final Queue<Runnable> queue;
        private int permitsCount;
        private final ExecutorService executorService;

        private LimitedExecutor(final int permitsCount, final ExecutorService executorService) {
            this.queue = new ArrayDeque<>();
            this.permitsCount = permitsCount;
            this.executorService = executorService;
        }

        public synchronized void submit(Runnable runnable) {
            if (permitsCount > 0) {
                permitsCount--;
                executorService.submit(runnable);
            } else {
                queue.add(runnable);
            }
        }

        public synchronized void release() {
            if (queue.isEmpty()) {
                permitsCount++;
            } else {
                executorService.submit(queue.poll());
            }
        }
    }

    private final Downloader downloader;
    private final ExecutorService downloaders;
    private final ExecutorService extractors;
    private final int perHost;
    private final Map<String, LimitedExecutor> hostMap;

    /**
     * Creates an instance of WebCrawler with given arguments.
     * @param downloader Web pages downloader.
     * @param downloaders Amount of threads to download on.
     * @param extractors  Amount of threads to extract on.
     * @param perHost  Amount of threads per host.
     */
    public WebCrawler(final Downloader downloader, final int downloaders, final int extractors, final int perHost) {
        this.downloader = downloader;
        this.downloaders = Executors.newFixedThreadPool(downloaders);
        this.extractors = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        hostMap = new ConcurrentHashMap<>();
    }

    private void parallelBFS(
            final String startingUrl,
            final int depth,
            final Set<String> downloaded,
            final Map<String, IOException> errors,
            final Predicate<String> isURLAllowed
    ) {
        Phaser phaser = new Phaser(1);
        Set<String> visited = ConcurrentHashMap.newKeySet();
        Queue<BFSEntry> queue = new ConcurrentLinkedQueue<>();
        visited.add(startingUrl);
        queue.add(new BFSEntry(startingUrl, 1));

        int layer = 0;
        while (!queue.isEmpty()) {
            if (queue.peek().getDepth() != layer) {
                phaser.arriveAndAwaitAdvance();
                layer++;
            }

            BFSEntry current = queue.remove();

            String host;
            try {
                host = URLUtils.getHost(current.getUrl());
            } catch (MalformedURLException e) {
                errors.put(current.getUrl(), e);
                continue;
            }

            LimitedExecutor limitedDownloaders =
                    hostMap.computeIfAbsent(host, ignored -> new LimitedExecutor(perHost, downloaders));

            submitDownloadTask(depth, downloaded, errors, isURLAllowed, phaser, visited, queue, current, limitedDownloaders);

            if (queue.isEmpty()) {
                phaser.arriveAndAwaitAdvance();
            }
        }
    }

    private void submitDownloadTask(
            final int depth,
            final Set<String> downloaded,
            final Map<String, IOException> errors,
            final Predicate<String> isURLAllowed,
            final Phaser phaser,
            final Set<String> visited,
            final Queue<BFSEntry> queue,
            final BFSEntry current,
            final LimitedExecutor limitedDownloaders
    ) {
        phaser.register();
        limitedDownloaders.submit(() -> {
            try {
                Document document = downloader.download(current.getUrl());
                downloaded.add(current.getUrl());
                if (current.getDepth() < depth) {
                    submitExtractTask(errors, isURLAllowed, phaser, visited, queue, current, document);
                }
            } catch (IOException e) {
                errors.put(current.getUrl(), e);
            } finally {
                phaser.arriveAndDeregister();
                limitedDownloaders.release();
            }
        });
    }

    private void submitExtractTask(
            final Map<String, IOException> errors,
            final Predicate<String> isURLAllowed,
            final Phaser phaser,
            final Set<String> visited,
            final Queue<BFSEntry> queue,
            final BFSEntry current,
            final Document document
    ) {
        phaser.register();
        extractors.submit(() -> {
            try {
                List<String> links = document.extractLinks();
                links.stream()
                        .filter(isURLAllowed)
                        .filter(visited::add)
                        .map(url -> new BFSEntry(url, current.getDepth() + 1))
                        .forEach(queue::add);
            } catch (IOException e) {
                errors.put(current.getUrl(), e);
            } finally {
                phaser.arriveAndDeregister();
            }
        });
    }

    private Result download(String url, int depth, Set<String> hosts) {
        Set<String> downloaded = ConcurrentHashMap.newKeySet();
        Map<String, IOException> errors = new ConcurrentHashMap<>();

        parallelBFS(url, depth, downloaded, errors, hosts == null ? u -> true : u -> {
            try {
                return hosts.contains(URLUtils.getHost(u));
            } catch (MalformedURLException e) {
                return false;
            }
        });

        downloaded.removeAll(errors.keySet());
        return new Result(new ArrayList<>(downloaded), errors);
    }

    /**
     * Downloads web site up to specified depth.
     *
     * @param url start <a href="http://tools.ietf.org/html/rfc3986">URL</a>.
     * @param depth download depth.
     * @return download result.
     */
    @Override
    public Result download(String url, int depth) {
        return download(url, depth, (Set<String>) null);
    }

    /**
     * Downloads web site up to specified depth.
     *
     * @param url start <a href="http://tools.ietf.org/html/rfc3986">URL</a>.
     * @param depth download depth.
     * @param hosts domains to follow, pages on another domains should be ignored.
     * @return download result.
     */
    @Override
    public Result download(String url, int depth, List<String> hosts) {
        Set<String> hostsSet = ConcurrentHashMap.newKeySet();
        hostsSet.addAll(hosts);

        return download(url, depth, hostsSet);
    }

    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(1, TimeUnit.MINUTES))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Closes this web-crawler, relinquishing any allocated resources.
     */
    @Override
    public void close() {
        shutdownAndAwaitTermination(downloaders);
        shutdownAndAwaitTermination(extractors);
    }

    private static int parseArg(final String[] args, final int index, final int defaultValue) {
        return index < args.length ? Integer.parseInt(args[index]) : defaultValue;
    }

    /**
     * Main WebCrawler method. Creates an instance of WebCrawler with given arguments from command line.
     * Uses {@link CachingDownloader} by default.
     * @param args Command line arguments. Should be contained of 1-5 elements.
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0 || args.length > 5 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Usage: WebCrawler url [depth [downloads [extractors [perHost]]]]");
            return;
        }

        final int depth = parseArg(args, 1, 2);
        final int downloads = parseArg(args, 2, 6);
        final int extractors = parseArg(args, 3, 6);
        final int perHost = parseArg(args, 4, 2);

        try (final Crawler crawler = new WebCrawler(new CachingDownloader(), downloads, extractors, perHost)) {
            final Result result = crawler.download(args[0], depth);
            System.out.printf("Downloaded: %d pages%n", result.getDownloaded().size());
            result.getDownloaded()
                    .forEach(url -> System.out.printf("\tURL: %s%n", url));
            System.out.printf("Errors: %d%n", result.getErrors().size());
            result.getErrors()
                    .forEach((key, value) -> System.out.printf("\tURL: %s%n\tError: %s%n", key, value.getMessage()));
        } catch (final IOException e) {
            System.err.printf("Crawler error occurred: %s%n", e.getMessage());
        }
    }

    // ;)
}
