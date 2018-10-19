package my.tamagochka.NIOSocketsWrapper;

import my.tamagochka.NIOSocketsWrapper.Handlers.BiHandler;
import my.tamagochka.NIOSocketsWrapper.Handlers.UniHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class NIOServer extends Thread {

    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private String serverAddress;
    private int port;
    private int bufferSize;

    private Selector selector;

    private final Map<SocketChannel, Queue<byte[]>> dataToSend = new HashMap<>();

    private Sender sender = new Sender();
    private Thread threadSender;

    private boolean serverIsRunning = false;

//    interface exceptionHandler extends TriHandler<SocketChannel, Queue<byte[]>, Exception> {}


    // events
    private UniHandler<SocketChannel> acceptHandler;
    private UniHandler<SocketChannel> connectHandler;
//    private exceptionHandler closeHandler;
//    private exceptionHandler haltHandler;
//    private BiHandler<Map<SocketChannel, Queue<byte[]>>, IOException> finishHandler;

    private BiHandler<SocketChannel, byte[]> receiveHandler;
//    private BiHandler<SocketChannel, byte[]> sentHandler;

//    private exceptionHandler receiveExceptionHandler;
//    private exceptionHandler sentExceptionHandler;
//    private BiHandler<SocketChannel, IOException> acceptExceptionHandler;

    // events setters
    public void acceptHandler(UniHandler<SocketChannel> acceptHandler) {
        this.acceptHandler = acceptHandler;
    }

    public void connectHandler(UniHandler<SocketChannel> connectHandler) {
        this.connectHandler = connectHandler;
    }

/*
    public void closeHandler(exceptionHandler closeHandler) {
        this.closeHandler = closeHandler;
    }
*/

/*
    public void haltHandler(exceptionHandler haltHandler) {
        this.haltHandler = haltHandler;
    }
*/

/*
    public void finishHandler(BiHandler<Map<SocketChannel, Queue<byte[]>>, IOException> finishHandler) {
        this.finishHandler = finishHandler;
    }
*/

    public void receiveHandler(BiHandler<SocketChannel, byte[]> receiveHandler) {
        this.receiveHandler = receiveHandler;
    }

/*
    public void sentHandler(BiHandler<SocketChannel, byte[]> sentHandler) {
        this.sentHandler = sentHandler;
    }
*/

/*
    public void receiveExceptionHandler(exceptionHandler receiveExceptionHandler) {
        this.receiveExceptionHandler = receiveExceptionHandler;
    }
*/

/*
    public void sentExceptionHandler(exceptionHandler sentExceptionHandler) {
        this.sentExceptionHandler = sentExceptionHandler;
    }
*/

/*
    public void acceptExceptionHandler(BiHandler<SocketChannel, IOException> acceptExceptionHandler) {
        this.acceptExceptionHandler = acceptExceptionHandler;
    }
*/

    public static NIOServer NIOClientFabric(int bufferSize){
        return new NIOServer(HostType.CLIENT, bufferSize);
    }

    public static NIOServer NIOClientFabric(){
        return NIOClientFabric(DEFAULT_BUFFER_SIZE);
    }


    public static NIOServer NIOServerFabric(){
        return new NIOServer(HostType.SERVER, DEFAULT_BUFFER_SIZE);
    }

    public static NIOServer NIOServerFabric(int bufferSize){
        return new NIOServer(HostType.SERVER, bufferSize);
    }

    private enum HostType {
        CLIENT, SERVER;
    }

    private HostType hostType = HostType.SERVER;


/*
    private NIOServer(HostType hostType, String serverAddress, int port) throws IOException { // TODO delete later
        this(hostType, serverAddress, port, DEFAULT_BUFFER_SIZE);
    }
*/

//    private NIOServer(HostType hostType, String serverAddress, int port, int bufferSize) throws IOException {
    private NIOServer(HostType hostType, int bufferSize) {
        this.hostType = hostType;
        this.bufferSize = bufferSize;



/*
        switch(hostType) {


            case SERVER:
                ServerSocketChannel ssc = ServerSocketChannel.open();
                ssc.configureBlocking(false);
                ssc.bind(new InetSocketAddress(port));
                ssc.register(selector, SelectionKey.OP_ACCEPT);
                serverIsRunning = true;
                break;
            case CLIENT:
                SocketChannel sc = SocketChannel.open();
                sc.configureBlocking(false);
                sc.register(selector, SelectionKey.OP_CONNECT);
                sc.connect(new InetSocketAddress(serverAddress, port));
                break;
        }
*/
    }

    public void launch(int port) throws IOException {
        if(hostType == HostType.SERVER) {
            selector = Selector.open();
            this.port = port;
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking(false);
            ssc.bind(new InetSocketAddress(port));
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            serverIsRunning = true;
        }
    }

    public void connect(String serverAddress, int serverPort) throws IOException {
        if(hostType == HostType.CLIENT) {
            selector = Selector.open();
            this.serverAddress = serverAddress;
            this.port = serverPort;
            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_CONNECT);
            sc.connect(new InetSocketAddress(serverAddress, port));
            serverIsRunning = true;
        }
    }

    private void connection(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        try {
            sc.finishConnect();
        } catch(IOException e) {
            // TODO
            e.printStackTrace();
        }
        key.interestOps(SelectionKey.OP_READ);
        if(connectHandler != null)
            connectHandler.process(sc);
    }

    public void send(SocketChannel sc, byte[] data) {
        if(threadSender == null) {
            threadSender = new Thread(sender);
            threadSender.start();
        }
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

/*
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
*/

    public void close(SocketChannel sc) {
/*
        if(!serverIsRunning) return;
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
*/
        try {
            sc.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

/*
    public void halt(SocketChannel sc) {
        if(!serverIsRunning) return;
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
*/

    private void accept(SelectionKey key) {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = null;
        try {
            sc = ssc.accept();
            sc.configureBlocking(false);
            sc.register(selector, SelectionKey.OP_READ);
        } catch(IOException e) {
            // TODO
/*
            if(acceptExceptionHandler != null) {
                acceptExceptionHandler.process(sc, e);
            } else {
                e.printStackTrace();
            }
            return;
*/
        }
        if(acceptHandler != null)
            acceptHandler.process(sc);
    }

    private void read(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int numRead = 0;
        try {
            if((numRead = sc.read(buffer)) == -1) {
                // TODO
/*
                halt(sc);
                return;
*/
            }
        } catch(IOException e) {
            // TODO
/*
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
*/
        }
        if(receiveHandler != null) {
            byte[] dataCopy = new byte[numRead];
            System.arraycopy(buffer.array(), 0, dataCopy, 0, numRead);
            receiveHandler.process(sc, dataCopy);
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
                // TODO
/*
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
*/
            }
//            if(sentHandler != null) sentHandler.process(sc, sent);
        }
        key.interestOps(SelectionKey.OP_READ);
        synchronized(dataToSend) {
            dataToSend.notifyAll();
        }
    }

    public void finish() {
        if(threadSender != null)
            threadSender.interrupt();
        serverIsRunning = false;
/*
        synchronized(dataToSend) {
            dataToSend.notifyAll();
        }
*/
        selector.wakeup();
    }

    @Override
    public void run() {
        while(serverIsRunning) {
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
                else if(key.isConnectable()) connection(key);
                else if(key.isReadable()) read(key);
                else if(key.isWritable()) write(key);
            }
        }

        try {
            selector.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
/*
        IOException onFinishException = null;
        try {
            selector.close();
        } catch(IOException e) {
            onFinishException = e;
        }
        if(finishHandler != null)
            finishHandler.process(dataToSend, onFinishException);
        else if(onFinishException != null) onFinishException.printStackTrace();
*/
    }

    private class Sender implements Runnable {

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
