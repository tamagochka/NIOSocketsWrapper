package my.tamagochka.NIOSocketsWrapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class NIOHost extends Thread {

    protected static final int DEFAULT_BUFFER_SIZE = 1024;

    private int bufferSize;

    protected Selector selector;
    private Sender sender = new Sender();
    private final Map<SocketChannel, Queue<byte[]>> dataToSend = new HashMap<>();
    private boolean hostIsRunning = false;

    protected abstract void action(SelectionKey key);

    // events
    private BiConsumer<SocketChannel, byte[]> onReceive;
    protected Consumer<SocketChannel> onAction;

    // events setters
    public void onAction(Consumer<SocketChannel> onAction) {
        this.onAction = onAction;
    }

    public void onReceive(BiConsumer<SocketChannel, byte[]> onReceive) {
        this.onReceive = onReceive;
    }

    protected NIOHost(int bufferSize) throws IOException {
        this.bufferSize = bufferSize;
        selector = Selector.open();
        hostIsRunning = true;
    }

    public void send(SocketChannel sc, byte[] data) {
        if(!sender.isAlive())
            sender.start();
        synchronized(dataToSend) {
            Queue<byte[]> queue = dataToSend.computeIfAbsent(sc, k -> new LinkedList<>());
            queue.add(data);
            dataToSend.notifyAll();
        }
    }

    // check if there is data to send in channel
    private boolean hasDataInChannel(SocketChannel sc, final Map<SocketChannel, Queue<byte[]>> data) {
        synchronized(data) {
            if(data.isEmpty()) return false;
            if(data.get(sc) == null) return false;
            return !data.get(sc).isEmpty();
        }
    }

    // check if there is data to send
    private boolean hasData(final Map<SocketChannel, Queue<byte[]>> data) {
        synchronized(data) {
            if(data.isEmpty()) return false;
            for(SocketChannel sc : data.keySet()) {
                if(hasDataInChannel(sc, data)) return true;
            }
            return false;
        }
    }

    // wait data to send
    private InterruptedException waitData(final Map<SocketChannel, Queue<byte[]>> data) {
        synchronized(data) {
            while(!hasData(data)) {
                try {
                    data.wait();
                } catch(InterruptedException e) {
                    return e;
                }
            }
        }
        return null;
    }

    public void close(SocketChannel sc) {
        try {
            sc.close();
        } catch(IOException e) {
            e.printStackTrace(); // TODO
        }
    }

    private void read(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int numRead = 0;
        try {
            if((numRead = sc.read(buffer)) == -1) {
                // TODO
            }
        } catch(IOException e) {
            // TODO
        }
        if(onReceive != null) {
            byte[] dataCopy = new byte[numRead];
            System.arraycopy(buffer.array(), 0, dataCopy, 0, numRead);
            onReceive.accept(sc, dataCopy);
        }
    }

    private void write(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        Queue<byte[]> queue;
        synchronized(dataToSend) {
            queue = new LinkedList<>(dataToSend.get(sc));
            dataToSend.get(sc).clear();
        }
        while(!queue.isEmpty()) {
            byte[] sent = queue.poll();
            ByteBuffer buffer = ByteBuffer.wrap(sent);
            try {
                sc.write(buffer);
                // TODO if sc.write returned -1?

            } catch(IOException e) {
                // TODO
            }
        }
        key.interestOps(SelectionKey.OP_READ);
        synchronized(dataToSend) {
            dataToSend.notifyAll();
        }
    }

    public void finish() {
        sender.interrupt();
        hostIsRunning = false;
        selector.wakeup();
    }

    @Override
    public void run() {
        while(hostIsRunning) {
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
                if(key.isAcceptable() || key.isConnectable()) action(key);
                else if(key.isReadable()) read(key);
                else if(key.isWritable()) write(key);
            }
        }

        try {
            selector.close();
        } catch(IOException e) {
            // TODO
            e.printStackTrace();
        }
    }

    private class Sender extends Thread {

        @Override
        public void run() {
            while(true) {
                synchronized(dataToSend) {
                    if(waitData(dataToSend) != null || Thread.interrupted()) return;
                    for(SocketChannel sc : dataToSend.keySet()) {
                        SelectionKey key = sc.keyFor(selector);
                        if(hasDataInChannel(sc, dataToSend) && selector.isOpen() && key != null && key.isValid()) {
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    }
                }
                selector.wakeup();
            }
        }
    }

}
