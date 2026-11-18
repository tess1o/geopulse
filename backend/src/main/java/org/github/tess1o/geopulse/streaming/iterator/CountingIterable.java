package org.github.tess1o.geopulse.streaming.iterator;

import java.util.Iterator;

/** Passes elements through unchanged and counts how many were read. */
public final class CountingIterable<T> implements Iterable<T> {
    private final Iterable<T> source;
    private long count;

    public CountingIterable(Iterable<T> source) {
        this.source = source;
    }

    public long count() {
        return count;
    }

    @Override
    public Iterator<T> iterator() {
        Iterator<T> iterator = source.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                T next = iterator.next();
                count++;
                return next;
            }
        };
    }
}
