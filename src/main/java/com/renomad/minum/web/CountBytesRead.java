package com.renomad.minum.web;

class CountBytesRead{
    private long count;
    public void increment() {count += 1;}

    public void incrementBy(long i) {
        count += i;
    }

    public long getCount() {
        return count;
    }
}