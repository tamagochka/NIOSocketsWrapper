package my.tamagochka.NIOSocketsWrapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class NIOServer extends NIOHost {

    public NIOServer(int port) throws IOException {
        this(port, DEFAULT_BUFFER_SIZE);
    }

    public NIOServer(int port, int bufferSize) throws IOException {
        super(bufferSize);
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        ssc.bind(new InetSocketAddress(port));
        ssc.register(selector, SelectionKey.OP_ACCEPT);
    }

    @Override
    protected void action(SelectionKey key) {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = null;
        try {
            sc = ssc.accept();
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_READ);
        } catch(IOException ignored) {
        }
        if(onAction != null)
            onAction.accept(sc);
    }

}
