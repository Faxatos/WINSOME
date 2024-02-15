package server.utils;

public class Pair<T, U> { //generic Pair class to return 2 values at once
    private final T first;

    private final U second;

    public Pair() {
        super();
        first = null;
        second = null;
    }
    public Pair(T first, U second) {
        this.first= first;
        this.second= second;
    }

    public T getFirst() {
        return first;
    }

    public U getSecond() {
        return second;
    }
}