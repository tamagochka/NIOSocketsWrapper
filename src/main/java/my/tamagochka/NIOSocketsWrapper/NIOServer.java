package my.tamagochka.NIOSocketsWrapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NIOServer extends Thread {

    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private final int port;
    private final int bufferSize;

    private Selector selector;

    private final Map<SocketChannel, Queue<byte[]>> dataToSend = new HashMap<>();

    private Sender sender = new Sender();
    private Thread threadSender;

    private boolean serverRunning = false;

    interface Handler {
        void process(SocketChannel sc, Queue<byte[]> notSent, Exception e);
    }

    // events
    private Consumer<SocketChannel> acceptHandler;
    private Handler closeHandler;
    private Handler haltHandler;
    private BiConsumer<Map<SocketChannel, Queue<byte[]>>, IOException> finishHandler;

    private BiConsumer<SocketChannel, byte[]> receiveHandler;
    private BiConsumer<SocketChannel, byte[]> sentHandler;

    private Handler receiveExceptionHandler;
    private Handler sentExceptionHandler;
    private BiConsumer<SocketChannel, IOException> acceptExceptionHandler;

    // events setters
    public void acceptHandler(Consumer<SocketChannel> acceptHandler) {
        this.acceptHandler = acceptHandler;
    }

    public void closeHandler(Handler closeHandler) {
        this.closeHandler = closeHandler;
    }

    public void haltHandler(Handler haltHandler) {
        this.haltHandler = haltHandler;
    }

    public void finishHandler(BiConsumer<Map<SocketChannel, Queue<byte[]>>, IOException> finishHandler) {
        this.finishHandler = finishHandler;
    }

    public void receiveHandler(BiConsumer<SocketChannel, byte[]> receiveHandler) {
        this.receiveHandler = receiveHandler;
    }

    public void sentHandler(BiConsumer<SocketChannel, byte[]> sentHandler) {
        this.sentHandler = sentHandler;
    }

    public void receiveExceptionHandler(Handler receiveExceptionHandler) {
        this.receiveExceptionHandler = receiveExceptionHandler;
    }

    public void sentExceptionHandler(Handler sentExceptionHandler) {
        this.sentExceptionHandler = sentExceptionHandler;
    }

    public void acceptExceptionHandler(BiConsumer<SocketChannel, IOException> acceptExceptionHandler) {
        this.acceptExceptionHandler = acceptExceptionHandler;
    }

    public NIOServer(int port) throws IOException {
        this(port, DEFAULT_BUFFER_SIZE);
    }

    public NIOServer(int port, int bufferSize) throws IOException {
        this.port = port;
        this.bufferSize = bufferSize;
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.configureBlocking(false);
        ssc.bind(new InetSocketAddress(port));
        selector = Selector.open();
        ssc.register(selector, SelectionKey.OP_ACCEPT);
        serverRunning = true;
    }

    private boolean hasDataInChannel(SocketChannel sc, final Map<SocketChannel, Queue<byte[]>> data) {
        synchronized(data) {
            if(data.isEmpty()) return false;
            if(data.get(sc) == null) return false;
            return !data.get(sc).isEmpty();
        }
    }

    public void send(SocketChannel sc, byte[] data) {
        if(threadSender == null) {
            threadSender = new Thread(sender);
            threadSender.start();
        }
        synchronized(dataToSend) {
            Queue<byte[]> queue = dataToSend.computeIfAbsent(sc, k -> new LinkedList<>());
            queue.add(data);
            dataToSend.notify();
        }
    }

    private boolean hasData(final Map<SocketChannel, Queue<byte[]>> data) {
        synchronized(data) {
            if(data.isEmpty()) return false;
            for(SocketChannel sc : data.keySet()) {
                if(hasDataInChannel(sc, data)) return true;
            }
            return false;
        }
    }

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

    private IOException dropConnection(SocketChannel sc) {
        if(sc != null && sc.isConnected()) {
            sc.keyFor(selector).cancel();
            try {
                sc.close();
            } catch(IOException e) {
                return e;
            }
        }
        return null;
    }

    public void close(SocketChannel sc) {
        if(!serverRunning) return;
        Queue<byte[]> notSent = null;
        synchronized(dataToSend) {
            if(dataToSend.get(sc) != null && sc.isConnected()) {
                while(!dataToSend.get(sc).isEmpty()) {
                    try {
                        dataToSend.wait();
                    } catch(InterruptedException e) {
                        break;
                    }
                }
            }
            if(closeHandler != null) {
                notSent = dataToSend.get(sc) != null ? new LinkedList<>(dataToSend.remove(sc)) : null;
            }
        }
        IOException e = dropConnection(sc);
        if(closeHandler != null) {
            closeHandler.process(sc, notSent, e);
        } else if(e != null) e.printStackTrace();
    }

    public void halt(SocketChannel sc) {
        if(!serverRunning) return;
        IOException e = dropConnection(sc);
        if(haltHandler != null) {
            Queue<byte[]> notSent = null;
            synchronized(dataToSend) {
                notSent = dataToSend.get(sc) != null ? new LinkedList<>(dataToSend.remove(sc)) : null;
            }
            haltHandler.process(sc, notSent, e);
        } else {
            synchronized(dataToSend) {
                dataToSend.remove(sc);
            }
            if(e != null) e.printStackTrace();
        }
    }

    private void accept(SelectionKey key) {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = null;
        try {
            sc = ssc.accept();
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_READ);
        } catch(IOException e) {
            if(acceptExceptionHandler != null) {
                acceptExceptionHandler.accept(sc, e);
            } else {
                e.printStackTrace();
            }
            return;
        }
        if(acceptHandler != null)
            acceptHandler.accept(sc);
    }

    private void read(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int numRead = 0;
        try {
            if((numRead = sc.read(buffer)) == -1) {
                halt(sc);
                return;
            }
        } catch(IOException e) {
            if(receiveExceptionHandler != null) {
                Queue<byte[]> notSent = null;
                synchronized(dataToSend) {
                    notSent = dataToSend.get(sc) != null ? new LinkedList<>(dataToSend.get(sc)) : null;
                }
                receiveExceptionHandler.process(sc, notSent, e);
            } else {
                e.printStackTrace();
                halt(sc);
            }
            return;
        }
        byte[] dataCopy = new byte[numRead];
        System.arraycopy(buffer.array(), 0, dataCopy, 0, numRead);
        if(receiveHandler != null) {
            receiveHandler.accept(sc, dataCopy);
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
            } catch(IOException e) {
                if(sentExceptionHandler != null) {
                    Queue<byte[]> notSent = null;
                    synchronized(dataToSend) {
                        queue.addAll(dataToSend.get(sc));
                        dataToSend.replace(sc, queue);
                        notSent = dataToSend.get(sc) != null ? new LinkedList<>(dataToSend.get(sc)) : null;
                    }
                    sentExceptionHandler.process(sc, notSent, e);
                } else {
                    e.printStackTrace();
                    halt(sc);
                }
                return;
            }
            if(sentHandler != null) sentHandler.accept(sc, sent);
        }
        key.interestOps(SelectionKey.OP_READ);
        synchronized(dataToSend) {
            dataToSend.notify();
        }
    }

    public void finish() {
        serverRunning = false;
        if(threadSender != null)
            threadSender.interrupt();
        synchronized(dataToSend) {
            dataToSend.notifyAll();
        }
        selector.wakeup();
    }

    @Override
    public void run() {
        while(serverRunning) {
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
                if(key.isAcceptable()) accept(key);
                else if(key.isReadable()) read(key);
                else if(key.isWritable()) write(key);
            }
        }
        IOException onFinishException = null;
        try {
            selector.close();
        } catch(IOException e) {
            onFinishException = e;
        }
        if(finishHandler != null)
            finishHandler.accept(dataToSend, onFinishException);
        else if(onFinishException != null) onFinishException.printStackTrace();
    }

    private class Sender implements Runnable {

        @Override
        public void run() {
            while(true) {
                synchronized(dataToSend) {
                    if(waitData(dataToSend) != null) {
                        return;
                    }
                    if(Thread.interrupted()) return;
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
