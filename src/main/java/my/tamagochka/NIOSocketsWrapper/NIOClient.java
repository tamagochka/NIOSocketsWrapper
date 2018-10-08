package my.tamagochka.NIOSocketsWrapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class NIOClient extends Thread {

    private final static int DEFAULT_BUFFER_SIZE = 1024;

    private final int serverPort;
    private final String serverAddress;
    private final int bufferSize;

    private final Selector selector;
    private final SocketChannel sc;

    private Sender sender = new Sender();
    private Thread threadSender;

    private final Queue<byte[]> dataToSend = new LinkedList<>();



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
        SocketChannel sc = (SocketChannel) key.channel();
        try {
            sc.finishConnect();
        } catch(IOException e) {
            // TODO how to process this exception?
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    private void read(SelectionKey key) {
        // TODO call event lambda function for process received data
    }

    public void send(byte[] data) {
        // TODO added data to queue for sending
        if(threadSender == null) {
            threadSender = new Thread(sender);
            threadSender.start();
        }
        synchronized(dataToSend) {
            dataToSend.add(data);
            dataToSend.notify();
        }
    }

    private void write(SelectionKey key) {
        // TODO needed queue for sending data
        SocketChannel sc = (SocketChannel) key.channel();
        Queue<byte[]> queue;
        synchronized(dataToSend) {
            queue = new LinkedList<>(dataToSend);
            dataToSend.clear();
        }

        while(!queue.isEmpty()) {
            byte[] sent = queue.poll();
            ByteBuffer buffer = ByteBuffer.wrap(sent);
            try {
                sc.write(buffer);
            } catch(IOException e) {
                // TODO process exception
            }
            System.out.println("The data was been sent to server: " + new String(sent));


        }
        key.interestOps(SelectionKey.OP_READ);
        synchronized(dataToSend) {
            dataToSend.notify();
        }
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

    private class Sender implements Runnable {

        @Override
        public void run() {
            while(true) {
                synchronized(dataToSend) {
                    try {
                        dataToSend.wait();
                    } catch(InterruptedException e) {
                        return;
                    }
                    if(Thread.interrupted()) return;
                    if(!dataToSend.isEmpty()) {
                        SelectionKey key = sc.keyFor(selector);
                        if(key != null && key.isValid() && selector.isOpen())
                            key.interestOps(SelectionKey.OP_WRITE);
                    }
                }
                selector.wakeup();
            }
        }
    }

}
