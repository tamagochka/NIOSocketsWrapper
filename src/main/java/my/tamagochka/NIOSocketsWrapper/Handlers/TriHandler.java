package my.tamagochka.NIOSocketsWrapper.Handlers;

public interface TriHandler<T1, T2, T3> {
    void process(T1 t1, T2 t2, T3 t3);
}
