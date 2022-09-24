package utils;

import java.io.Serializable;

public record Pair<A, B>(A first, B second) implements Serializable {
}