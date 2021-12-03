package info.kgeorgiy.ja.chernatsky.arrayset;

import java.util.*;


public class ArraySet<E> extends AbstractSet<E> implements NavigableSet<E> {
    private final List<E> data;
    private final Comparator<? super E> comparator;

    public ArraySet() {
        this(null, List.of());
    }

    public ArraySet(final Collection<? extends E> collection) {
        this(null, List.copyOf(new TreeSet<E>(collection)));
    }

    public ArraySet(final Comparator<? super E> comparator) {
        this(comparator, List.of());
    }

    private ArraySet(final Comparator<? super E> comparator, final List<E> data) {
        this.data = data;
        this.comparator = comparator;
    }

    public ArraySet(final Collection<? extends E> collection, final Comparator<? super E> comparator) {
        final TreeSet<E> set = new TreeSet<>(comparator);
        set.addAll(collection);
        this.data = List.copyOf(set);
        this.comparator = comparator;
    }

    private boolean isIndexValid(final int index) {
        return 0 <= index && index < size();
    }

    private int getShiftedIndex(final E elem, final int ifFound, final int ifNotFound) {
        int i = Collections.binarySearch(data, elem, comparator);
        if (i < 0) {
            i = -i - 1 + ifNotFound;
        } else {
            i += ifFound;
        }
        return isIndexValid(i) ? i : -1;
    }

    private E getShiftedIndexElemOrNull(final E e, final int ifFound, final int ifNotFound) {
        final int index = getShiftedIndex(e, ifFound, ifNotFound);
        return index >= 0 ? data.get(index) : null;
    }

    @Override
    public E lower(final E e) {
        return getShiftedIndexElemOrNull(e, -1, -1);
    }

    @Override
    public E floor(final E e) {
        return getShiftedIndexElemOrNull(e, +0, -1);
    }

    @Override
    public E ceiling(final E e) {
        return getShiftedIndexElemOrNull(e, +0, +0);
    }

    @Override
    public E higher(final E e) {
        return getShiftedIndexElemOrNull(e, +1, +0);
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<E> iterator() {
        return data.iterator();
    }

    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return new ArraySet<>(Collections.reverseOrder(comparator), new ReversedList<>(data));
    }

    @Override
    public Iterator<E> descendingIterator() {
        return descendingSet().iterator();
    }

    @SuppressWarnings("unchecked")
    private int compare(final E left, final E right) {
        return comparator == null ?
                ((Comparable<? super E>) left).compareTo(right) : comparator.compare(left, right);
    }

    @Override
    public NavigableSet<E> subSet(final E fromElement, final boolean fromInclusive, final E toElement, final boolean toInclusive) {
        if (fromInclusive && toInclusive) {
            if (compare(fromElement, toElement) > 0) {
                throw new IllegalArgumentException("fromElement > toElement");
            }
        } else if (compare(fromElement, toElement) >= 0) {
            throw new IllegalArgumentException("fromElement >= toElement");
        }
        final int l = getShiftedIndex(fromElement, fromInclusive ? +0 : +1, +0);
        final int r = getShiftedIndex(toElement, toInclusive ? +0 : -1, -1);
        return new ArraySet<>(comparator, l > r || l < 0 ? List.of() : data.subList(l, r + 1));
    }

    @Override
    public NavigableSet<E> headSet(final E toElement, final boolean inclusive) {
        try {
            return data.isEmpty() ? this : subSet(first(), true, toElement, inclusive);
        } catch (IllegalArgumentException e) {
            return new ArraySet<>(comparator, List.of());
        }
    }

    @Override
    public NavigableSet<E> tailSet(final E fromElement, final boolean inclusive) {
        try {
            return data.isEmpty() ? this : subSet(fromElement, inclusive, last(), true);
        } catch (IllegalArgumentException e) {
            return new ArraySet<>(comparator, List.of());
        }
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<E> subSet(final E fromElement, final E toElement) {
        if (compare(fromElement, toElement) > 0) {
            throw new IllegalArgumentException("fromElement > toElement");
        }
        try {
            return subSet(fromElement, true, toElement, false);
        } catch (IllegalArgumentException e) {
            return new ArraySet<>(comparator, List.of());
        }
    }

    @Override
    public SortedSet<E> headSet(final E toElement) {
        return headSet(toElement, false);
    }

    @Override
    public SortedSet<E> tailSet(final E fromElement) {
        return tailSet(fromElement, true);
    }

    private void checkIfEmpty() {
        if (data.isEmpty()) {
            throw new NoSuchElementException("ArraySet is empty!");
        }
    }

    @Override
    public E first() {
        checkIfEmpty();
        return data.get(0);
    }

    @Override
    public E last() {
        checkIfEmpty();
        return data.get(size() - 1);
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(final Object obj) {
        return Collections.binarySearch(data, (E) obj, comparator) >= 0;
    }

    private static class ReversedList<T> extends AbstractList<T> {
        private final boolean reversed;
        private final List<T> innerList;

        public ReversedList(final List<T> list) {
            if (list instanceof ReversedList) {
                final ReversedList<T> rList = (ReversedList<T>) list;
                this.innerList = rList.innerList;
                this.reversed = !rList.reversed;
            } else {
                this.innerList = list;
                this.reversed = true;
            }
        }

        @Override
        public T get(final int index) {
            return innerList.get(reversed ? size() - 1 - index : index);
        }

        @Override
        public int size() {
            return innerList.size();
        }
    }

    public static void main(String[] args) {
        ArraySet<Integer> set = new ArraySet<>(
                List.of(867655404, 132710026, -989413675),
                (o1, o2) -> 0
        );
        set.subSet(null, false, 12345, true)
                .forEach(System.out::println);
    }
}
