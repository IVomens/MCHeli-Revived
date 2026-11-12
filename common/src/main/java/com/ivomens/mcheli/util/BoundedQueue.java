package com.ivomens.mcheli.util;

import java.util.ArrayList;
import java.util.List;

/** Fixed size ring buffer that overwrites its oldest element. */
public class BoundedQueue<T> {

    private final List<T> list;
    private int current;

    public BoundedQueue(int filterLength, T initVal) {
        int size = Math.max(1, filterLength);
        this.list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.list.add(initVal);
        }
    }

    public void clear(T clearVal) {
        for (int i = 0; i < this.size(); i++) {
            this.list.set(i, clearVal);
        }
    }

    public void put(T t) {
        this.list.set(this.current, t);
        this.current = (this.current + 1) % this.size();
    }

    /** The element after the next write slot, i.e. the second oldest one. */
    public T oldest() {
        return this.list.get((this.current + 1) % this.size());
    }

    /** Element by storage slot, not by age. */
    public T get(int i) {
        return this.list.get(i);
    }

    public int size() {
        return this.list.size();
    }
}
