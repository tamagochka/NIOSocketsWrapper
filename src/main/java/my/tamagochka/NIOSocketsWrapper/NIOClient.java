package my.tamagochka.NIOSocketsWrapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NIOClient extends Thread {

    private final static int DEFAULT_BUFFER_SIZE = 1024;

    private final int serverPort;
    private final String serverAddress;
    private final int bufferSize;

    private final SocketChannel sc;
    private final Selector selector;

    public NIOClient(String serverAddress, int serverPort) throws IOException {
        this(serverAddress, serverPort, DEFAULT_BUFFER_SIZE);
    }

    public NIOClient(String serverAddress, int serverPort, int bufferSize) throws IOException {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.bufferSize = bufferSize;
        sc = SocketChannel.open();
        sc.configureBlocking(false);
        selector = Selector.open();
        sc.register(selector, SelectionKey.OP_CONNECT);
        sc.connect(new InetSocketAddress(serverAddress, serverPort));
    }

    private void connect(SelectionKey key) {

    }

    private void read(SelectionKey key) {

    }

    private void write(SelectionKey key) {

    }

    @Override
    public void run() {
        while(true) { // TODO how to stop client?
            try {
                selector.select();
            } catch(IOException e) {
                continue;
            }
            Iterator<SelectionKey> i = selector.selectedKeys().iterator();
            while(i.hasNext()) {
                SelectionKey key = i.next();
                i.remove();
                if(!key.isValid()) continue;
                if(key.isConnectable()) connect(key);
                else if(key.isReadable()) read(key);
                else if(key.isWritable()) write(key);
            }

            // TODO finishing client


        }



    }


}
