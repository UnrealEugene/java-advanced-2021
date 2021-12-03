package info.kgeorgiy.ja.chernatsky.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ParallelMapperImpl implements ParallelMapper {
    private class MapperWorker<R> {
        private final List<R> result;
        private int tasksRemaining;
        private RuntimeException exception;

        <T> MapperWorker(final Function<? super T, ? extends R> mapper,
                         final List<? extends T> list) {
            result = new ArrayList<>(Collections.nCopies(list.size(), null));
            tasksRemaining = list.size();

            final Thread currentThread = Thread.currentThread();

            IntStream.range(0, list.size())
                    .forEach(id -> addTask(() -> {
                        if (closed) {
                            onClosed(currentThread);
                            return;
                        }
                        try {
                            result.set(id, mapper.apply(list.get(id)));
                        } catch (final RuntimeException e) {
                            onExceptionOccurred(e);
                        } finally {
                            onTaskEnded();
                        }
                    }));

            if (closed) {
                onClosed(currentThread);
            }
        }

        private synchronized void onClosed(final Thread currentThread) {
            if (tasksRemaining > 0) {
                currentThread.interrupt();
                tasksRemaining = 0;
            }
        }

        private synchronized void onExceptionOccurred(final RuntimeException e) {
            if (exception == null) {
                exception = e;
            } else {
                exception.addSuppressed(e);
            }
        }

        private synchronized void onTaskEnded() {
            if (--tasksRemaining == 0) {
                notify();
            }
        }

        synchronized List<R> getResult() throws InterruptedException {
            while (tasksRemaining > 0) {
                wait();
            }
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }

    private final List<Thread> threads;
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private boolean closed;

    private synchronized void addTask(final Runnable task) {
        tasks.add(task);
        notify();
    }

    private synchronized Runnable pollTask() throws InterruptedException {
        while (tasks.isEmpty()) {
            wait();
        }
        return tasks.poll();
    }

    /**
     * Creates an instance of {@code ParallelMapperImpl} with
     * {@code threadsCount} threads for its use.
     * @param threadsCount Amount of threads created.
     */
    public ParallelMapperImpl(final int threadsCount) {
        final Runnable template = () -> {
            try {
                while (!Thread.interrupted()) {
                    pollTask().run();
                }
            } catch (final InterruptedException ignored) { }
        };
        threads = Stream.generate(() -> new Thread(template))
                .limit(threadsCount)
                .peek(Thread::start)
                .collect(Collectors.toList());
    }

    /**
     * Maps function {@code mapper} over elements of specified {@code list}.
     * Mapping for each element performs in parallel.
     *
     * @throws InterruptedException if calling thread was interrupted
     * @throws IllegalStateException when object is already closed
     * @param mapper Mapper function.
     * @param list Given list.
     * @return list contained of mapped elements.
     */
    @Override
    public <T, R> List<R> map(final Function<? super T, ? extends R> mapper,
                              final List<? extends T> list) throws InterruptedException {
        if (closed) {
            throw new IllegalStateException("Action on closed ParallelMapperImpl");
        }
        return new MapperWorker<R>(mapper, list).getResult();
    }

    /** Stops all threads. All unfinished mappings leave in undefined state. */
    @Override
    public void close() {
        closed = true;
        threads.forEach(Thread::interrupt);
        for (final Thread thread : threads) {
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (final InterruptedException ignored) { }
            }
        }
        synchronized (this) {
            tasks.forEach(Runnable::run);
        }
    }
}

