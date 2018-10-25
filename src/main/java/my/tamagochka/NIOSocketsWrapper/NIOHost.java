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
    private Consumer<SocketChannel> onBreakChannel;

    // events setters
    public void onReceive(BiConsumer<SocketChannel, byte[]> onReceive) {
        this.onReceive = onReceive;
    }

    public void onAction(Consumer<SocketChannel> onAction) {
        this.onAction = onAction;
    }

    public void onBreakChannel(Consumer<SocketChannel> onBreakChannel) {
        this.onBreakChannel = onBreakChannel;
    }

    protected NIOHost(int bufferSize) throws IOException {
        this.bufferSize = bufferSize;
        selector = Selector.open();
        hostIsRunning = true;
        sender.start();
    }

    public void send(SocketChannel sc, byte[] data) {
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
        } catch(IOException ignored) {
        }
    }

    public void sendAndClose(SocketChannel sc) {
        synchronized(dataToSend) {
            while(dataToSend.get(sc) != null && !dataToSend.get(sc).isEmpty()) {
                try {
                    dataToSend.wait();
                } catch(InterruptedException ignored) {
                }
            }
        }
        try {
            sc.close();
        } catch(IOException ignored) {
        }
    }

    private void read(SelectionKey key) {
        SocketChannel sc = (SocketChannel)key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int numRead = 0;
        try {
            if((numRead = sc.read(buffer)) == -1) {
                throw new IOException();
            }
        } catch(IOException e) {
            // all data in the dataToSend will be lost
            key.cancel();
            close(sc);
            if(onBreakChannel != null)
                onBreakChannel.accept(sc);
            synchronized(dataToSend) {
                dataToSend.remove(sc);
            }
            return;
        }
        if(onReceive != null) {
            byte[] dataCopy = new byte[numRead];
            System.arraycopy(buffer.array(), 0, dataCopy, 0, numRead);
            onReceive.accept(sc, dataCopy);
        }
    }

    private void write(SelectionKey key) {
        SocketChannel sc = (SocketChannel)key.channel();
        Queue<byte[]> queue;
        synchronized(dataToSend) {
            queue = new LinkedList<>(dataToSend.get(sc));
            dataToSend.get(sc).clear();
        }
        while(!queue.isEmpty()) {
            byte[] sent = queue.poll();
            ByteBuffer buffer = ByteBuffer.wrap(sent);
            try {
                sc.write(buffer); // TODO doesn't return count sent bytes
            } catch(IOException e) {
                // all data in the queue and dataToSend will be lost
                e.printStackTrace();
                key.cancel();
                close(sc);
                if(onBreakChannel != null)
                    onBreakChannel.accept(sc);
                synchronized(dataToSend) {
                    dataToSend.remove(sc);
                }
                return;
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
        } catch(IOException ignored) {
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
