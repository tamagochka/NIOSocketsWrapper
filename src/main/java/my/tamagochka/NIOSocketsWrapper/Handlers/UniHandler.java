package my.tamagochka.NIOSocketsWrapper.Handlers;

public interface UniHandler<T> {
    void process(T t);
}
