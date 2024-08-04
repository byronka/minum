package com.renomad.minum.web;

class CountBytesRead{
    private int count;
    public void increment() {count += 1;}

    public void incrementBy(int i) {
        count += i;
    }

    public int getCount() {
        return count;
    }
}