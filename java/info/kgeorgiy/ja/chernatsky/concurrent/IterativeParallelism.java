package info.kgeorgiy.ja.chernatsky.concurrent;

import info.kgeorgiy.java.advanced.concurrent.AdvancedIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class IterativeParallelism implements AdvancedIP {
    private final ParallelMapper parallelMapper;

    /**
     * Creates an instance without using {@code ParallelMapper} object.
     */
    public IterativeParallelism() {
        this(null);
    }

    /**
     * Creates an instance with using {@code ParallelMapper} object.
     */
    public IterativeParallelism(final ParallelMapper parallelMapper) {
        this.parallelMapper = parallelMapper;
    }

    private <T, U, R> R parallelJob(
            final int threads,
            final List<? extends T> list,
            final Function<Stream<? extends T>, U> innerFunction,
            final Function<Stream<U>, R> outerFunction
    ) throws InterruptedException {
        final int actualThreads = Math.min(threads, list.size());
        final List<Stream<? extends T>> splitList = split(list, actualThreads);
        final List<U> results = parallelMapper == null
                ? parallelMap(innerFunction, splitList)
                : parallelMapper.map(innerFunction, splitList);
        return outerFunction.apply(results.stream());
    }

    private static <U, T> List<U> parallelMap(
            final Function<Stream<? extends T>, U> innerFunction,
            final List<Stream<? extends T>> splitList
    ) throws InterruptedException {
        final List<U> results = new ArrayList<>(Collections.nCopies(splitList.size(), null));
        final List<Thread> threadList = IntStream.range(0, splitList.size())
                .mapToObj(id -> new Thread(() -> results.set(id, innerFunction.apply(splitList.get(id)))))
                .peek(Thread::start)
                .collect(Collectors.toList());
        joinThreads(threadList);
        return results;
    }

    private static <T> List<Stream<? extends T>> split(final List<? extends T> list, final int parts) {
        if (parts == 0) {
            return Collections.emptyList();
        }
        final int quotient = list.size() / parts;
        final int maxBorder = list.size() % parts * (quotient + 1);
        final IntUnaryOperator nextBorder = i -> i + quotient + (i < maxBorder ? 1 : 0);
        return IntStream.iterate(0, nextBorder)
                .limit(parts)
                .mapToObj(i -> list.subList(i, nextBorder.applyAsInt(i)).stream())
                .collect(Collectors.toList());
    }

    private static void joinThreads(List<Thread> threadList) throws InterruptedException {
        InterruptedException exception = null;
        for (int k = 0; k < threadList.size(); k++) {
            try {
                threadList.get(k).join();
            } catch (final InterruptedException e) {
                if (exception == null) {
                    exception = new InterruptedException("Threads execution was interrupted");
                    for (int l = k + 1; l < threadList.size(); l++) {
                        threadList.get(l).interrupt();
                    }
                }
                k--;
                exception.addSuppressed(e);
            }
        }
        if (exception != null) {
            throw exception;
        }
    }


    private <T, R> R parallelMonoid(
            final int threads,
            final List<? extends T> list,
            final Function<? super T, R> mapper,
            final BinaryOperator<R> accumulator
    ) throws InterruptedException {
        return parallelJob(
                threads,
                list,
                s -> s.map(mapper)
                        .reduce(accumulator)
                        .orElse(null),
                s -> s.reduce(accumulator)
                        .orElse(null)
        );
    }

    private <T, R> R parallelMonoid(
            final int threads,
            final List<? extends T> list,
            final Function<T, R> mapper,
            final Monoid<R> monoid
    ) throws InterruptedException {
        return parallelJob(
                threads,
                list,
                s -> s.map(mapper).reduce(monoid.getIdentity(), monoid.getOperator()),
                s -> s.reduce(monoid.getIdentity(), monoid.getOperator())
        );
    }

    private <T, R> List<R> parallelListJob(
            final int threads,
            final List<? extends T> list,
            final Function<Stream<? extends T>, ? extends Stream<? extends R>> function
    ) throws InterruptedException {
        return parallelJob(
                threads,
                list,
                function,
                s -> s.flatMap(Function.identity())
                        .collect(Collectors.toList())
        );
    }


    private static String join(final Stream<?> stream) {
        return stream.map(Objects::toString)
                .collect(Collectors.joining());
    }

    /**
     * Join values to string.
     *
     * @param threads number of concurrent threads.
     * @param list    values to join.
     * @return list of joined result of {@link #toString()} call on each value.
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public String join(final int threads, final List<?> list) throws InterruptedException {
        return parallelJob(threads, list, IterativeParallelism::join, IterativeParallelism::join);
    }

    /**
     * Filters values by predicate.
     *
     * @param threads   number of concurrent threads.
     * @param list      values to filter.
     * @param predicate filter predicate.
     * @param <T>       value type.
     * @return list of values satisfying given predicated. Order of values is preserved.
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T> List<T> filter(
            final int threads,
            final List<? extends T> list,
            final Predicate<? super T> predicate
    ) throws InterruptedException {
        return parallelListJob(
                threads,
                list,
                s -> s.filter(predicate)
        );
    }

    /**
     * Maps values.
     *
     * @param threads  number of concurrent threads.
     * @param list     values to filter.
     * @param function mapper function.
     * @param <T>      initial value type.
     * @param <U>      new value type.
     * @return list of values mapped by given function.
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T, U> List<U> map(
            final int threads,
            final List<? extends T> list,
            final Function<? super T, ? extends U> function
    ) throws InterruptedException {
        return parallelListJob(
                threads,
                list,
                s -> s.map(function)
        );
    }

    /**
     * Returns minimum value.
     *
     * @param threads    number or concurrent threads.
     * @param list       values to get minimum of.
     * @param comparator value comparator.
     * @param <T>        value type.
     * @return minimum of given values
     * @throws InterruptedException             if executing thread was interrupted.
     * @throws java.util.NoSuchElementException if no values are given.
     */
    @Override
    public <T> T minimum(
            final int threads,
            final List<? extends T> list,
            final Comparator<? super T> comparator
    ) throws InterruptedException {
        return parallelMonoid(
                threads,
                list,
                Function.identity(),
                BinaryOperator.minBy(comparator)
        );
    }

    /**
     * Returns maximum value.
     *
     * @param threads    number or concurrent threads.
     * @param list       values to get maximum of.
     * @param comparator value comparator.
     * @param <T>        value type.
     * @return maximum of given values
     * @throws InterruptedException             if executing thread was interrupted.
     * @throws java.util.NoSuchElementException if no values are given.
     */
    @Override
    public <T> T maximum(
            final int threads,
            final List<? extends T> list,
            final Comparator<? super T> comparator
    ) throws InterruptedException {
        return minimum(threads, list, comparator.reversed());
    }

    /**
     * Returns whether all values satisfies predicate.
     *
     * @param threads   number or concurrent threads.
     * @param list      values to test.
     * @param predicate test predicate.
     * @param <T>       value type.
     * @return whether all values satisfies predicate or {@code true}, if no values are given
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T> boolean all(
            final int threads,
            final List<? extends T> list,
            final Predicate<? super T> predicate
    ) throws InterruptedException {
        return parallelMonoid(threads, list, predicate::test, Boolean::logicalAnd);
    }

    /**
     * Returns whether any of values satisfies predicate.
     *
     * @param threads   number or concurrent threads.
     * @param list      values to test.
     * @param predicate test predicate.
     * @param <T>       value type.
     * @return whether any value satisfies predicate or {@code false}, if no values are given
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T> boolean any(
            final int threads,
            final List<? extends T> list,
            final Predicate<? super T> predicate
    ) throws InterruptedException {
        return parallelMonoid(threads, list, predicate::test, Boolean::logicalOr);
    }

    /**
     * Performs a reduction on the elements of given list, using the provided
     * {@link Monoid} and returns the reduced value.
     *
     * @param threads number of concurrent threads.
     * @param list    values to reduce on.
     * @param monoid  given monoid.
     * @param <T>     value type.
     * @return result of reduction on elements of list.
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T> T reduce(
            final int threads,
            final List<T> list,
            final Monoid<T> monoid
    ) throws InterruptedException {
        return parallelMonoid(threads, list, Function.identity(), monoid);
    }

    /**
     * Maps given values with given function and then performs a reduction on
     * the elements, using the provided {@link Monoid}, and returns the reduced value.
     *
     * @param threads  number of concurrent threads.
     * @param list     values to map and reduce on.
     * @param function mapper function.
     * @param monoid   given monoid.
     * @param <T>      value type.
     * @return result of reduction on mapped elements of list.
     * @throws InterruptedException if executing thread was interrupted.
     */
    @Override
    public <T, R> R mapReduce(
            final int threads,
            final List<T> list,
            final Function<T, R> function,
            final Monoid<R> monoid
    ) throws InterruptedException {
        return parallelMonoid(threads, list, function, monoid);
    }

    public static void main(final String[] args) throws InterruptedException {
        System.out.println(new IterativeParallelism(new ParallelMapperImpl(2)).join(3, List.of(1, 2, 3, 4)));
    }
}
