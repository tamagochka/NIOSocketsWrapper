package my.tamagochka.NIOSocketsWrapper;

import my.tamagochka.NIOSocketsWrapper.Handlers.BiHandler;

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
    private final SocketChannel sc; // TODO assign the sc with address and process only address without sc

    private Sender sender = new Sender();
    private Thread threadSender;

    private boolean clientIsRunning = false;

    private final Queue<byte[]> dataToSend = new LinkedList<>();

    private BiHandler<SocketChannel, byte[]> receiveHandler;
//    private BiHandler<SocketChannel, Selector> disconnectingHandler;
//    private BiHandler<SocketChannel, Exception> receiveExceptionHandler;
//    private TriHandler<SocketChannel, Queue<byte[]>, IOException> closeHandler;
//    private TriHandler<SocketChannel, Queue<byte[]>, IOException> haltHandler;

    public void receiveHandler(BiHandler<SocketChannel, byte[]> receiveHandler) {
        this.receiveHandler = receiveHandler;
    }

/*
    public void disconnectingHandler(BiHandler<SocketChannel, Selector> disconnectingHandler) {
        this.disconnectingHandler = disconnectingHandler;
    }
*/

/*
    public void receiveExceptionHandler(BiHandler<SocketChannel, Exception> receiveExceptionHandler) {
        this.receiveExceptionHandler = receiveExceptionHandler;
    }
*/

/*
    public void closeHandler(TriHandler<SocketChannel, Queue<byte[]>, IOException> closeHandler) {
        this.closeHandler = closeHandler;
    }
*/

/*
    public void haltHandler(TriHandler<SocketChannel, Queue<byte[]>, IOException> haltHandler) {
        this.haltHandler = haltHandler;
    }
*/

    public NIOClient(String serverAddress, int serverPort) throws IOException {
        this(serverAddress, serverPort, DEFAULT_BUFFER_SIZE);
    }

    public NIOClient(String serverAddress, int serverPort, int bufferSize) throws IOException {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.bufferSize = bufferSize;
        clientIsRunning = true;
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

/*
    private IOException dropConnection() {
        if(threadSender != null) threadSender.interrupt();
        if(sc != null*/
/* && sc.isConnected()*//*
) {
            try {
                sc.close(); // sc.keyFor(selector).cancel();
                selector.close();
            } catch(IOException e) {
                return e;
            }
        }
        return null;
    }
*/

    public void close() {
/*
        if(!clientIsRunning) return;
        Queue<byte[]> notSent = null;
        synchronized(dataToSend) {
            while(!dataToSend.isEmpty()) {
                try {
                    dataToSend.wait();
                } catch(InterruptedException e) {
                    break;
                }
            }
            if(closeHandler != null)
                notSent = new LinkedList<>(dataToSend);
        }


        System.out.printf("shutdown process is activated");


        clientIsRunning = false;
        IOException e = dropConnection();
        if(closeHandler != null) {
            closeHandler.process(sc, notSent, e);
        } else if(e != null) e.printStackTrace();
*/
        threadSender.interrupt();
        try {
            sc.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

/*
    public void halt() {
        if(!clientIsRunning) return;
        clientIsRunning = false;
        IOException e = dropConnection();
        if(haltHandler != null) {
            Queue<byte[]> notSent = null;
            synchronized(dataToSend) {
                notSent = new LinkedList<>(dataToSend);
            }
            haltHandler.process(sc, notSent, e);
        } else if(e != null) e.printStackTrace();
    }
*/

    private void read(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int numRead = 0;
        try {
            if((numRead = sc.read(buffer)) == -1) {
                // TODO
/*
                if(disconnectingHandler != null)
                    disconnectingHandler.process(sc, selector);
                else {
                    halt();
                    return;
                }
*/
            }
        } catch(IOException e) {
            // TODO
/*
            if(receiveExceptionHandler != null) {

            } else {
                e.printStackTrace();

            }
*/
        }

        if(receiveHandler != null) {
            byte[] dataCopy = new byte[numRead];
            System.arraycopy(buffer.array(), 0, dataCopy, 0, numRead);
            receiveHandler.process(sc, dataCopy);
        }
    }

    public void send(byte[] data) {
        if(threadSender == null) {
            threadSender = new Thread(sender);
            threadSender.start();
        }
        synchronized(dataToSend) {
            dataToSend.add(data);
            dataToSend.notifyAll();
        }
    }

    private void write(SelectionKey key) {
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
                // TODO if write == -1


            } catch(IOException e) {
                // TODO process exception
                e.printStackTrace();
            }
        }
        key.interestOps(SelectionKey.OP_READ);
        synchronized(dataToSend) {
            dataToSend.notifyAll();
        }
    }

    public void finish() {
        if(threadSender != null)
            threadSender.interrupt();
        clientIsRunning = false;
        selector.wakeup();
    }

    @Override
    public void run() {
        while(clientIsRunning) {
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
        }
        try {
            selector.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private class Sender implements Runnable {

        @Override
        public void run() {
            while(true) {
                synchronized(dataToSend) {
                    while(dataToSend.isEmpty()) {
                        try {
                            dataToSend.wait();
                        } catch(InterruptedException e) {
                            return;
                        }
                    }
                    if(Thread.interrupted()) return;
                    SelectionKey key = sc.keyFor(selector);
                    if(key != null && key.isValid() && selector.isOpen())
                        key.interestOps(SelectionKey.OP_WRITE);
                }
                selector.wakeup();
            }
        }

    }

}
