package utils;

import java.io.Serializable;

public class Pair<A,B> implements Serializable {
    public final A first;
    public final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }
}

//public record Pair<out A, out B> public constructor(first: A, second: B) implements Serializable
//public final val first: A /* compiled code */
//
//public final val second: B /* compiled code */
//
//public final operator fun component1(): A { /* compiled code */ }
//
//public final operator fun component2(): B { /* compiled code */ }
//
//public open fun toString(): kotlin.String { /* compiled code */ }
//        }
