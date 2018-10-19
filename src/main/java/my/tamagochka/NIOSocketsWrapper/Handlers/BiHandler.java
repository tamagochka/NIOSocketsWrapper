package my.tamagochka.NIOSocketsWrapper.Handlers;

public interface BiHandler<T1, T2> {
    void process(T1 t1, T2 t2);
}
