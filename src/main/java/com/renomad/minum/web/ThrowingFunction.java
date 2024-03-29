package com.renomad.minum.web;

@FunctionalInterface
public interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;

}