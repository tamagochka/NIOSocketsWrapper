package my.tamagochka.NIOSocketsWrapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NIOClient extends NIOHost {

    public NIOClient(String address, int port) throws IOException {
        this(address, port, DEFAULT_BUFFER_SIZE);
    }

    public NIOClient(String address, int port, int bufferSize) throws IOException {
        super(bufferSize);
        connect(address, port);
    }

    @Override
    protected void action(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        try {
            sc.finishConnect();
        } catch(IOException ignored) {
        }
        key.interestOps(SelectionKey.OP_READ);
        if(onAction != null)
            onAction.accept(sc);
    }

    public void connect(String address, int port) throws IOException {
        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.register(selector, SelectionKey.OP_CONNECT);
        sc.connect(new InetSocketAddress(address, port));
    }

}
