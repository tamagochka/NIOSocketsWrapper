package my.tamagochka.NIOSocketsEchoExample;

import my.tamagochka.NIOSocketsWrapper.NIOClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class EchoClient extends NIOClient {

    private static final int NUM_PACKETS = 5000;
    private static final int COUNT_CLIENTS = 3;

    private Map<SocketChannel, Sender> senders = new HashMap<>();
    private Integer ready = 0;

    public EchoClient(String address, int port) throws IOException {
        super(address, port);

        this.onAction(sc -> {
            Sender sender = new Sender(sc);
            senders.put(sc, sender);
            sender.start();
        });

        this.onReceive((sc, data) -> {
            senders.get(sc).next(new String(data));
        });

        for(int i = 1; i < COUNT_CLIENTS; i++)
            this.connect(address, port);
    }

    private class Sender extends Thread {

        private final SocketChannel sc;
        private String sentData;
        private String receivedData;

        public Sender(SocketChannel sc) {
            this.sc = sc;
        }

        @Override
        public void run() {
            for(int i = 0; i < NUM_PACKETS; i++) {
                sentData = String.format("%010d", i);
                send(sc, sentData.getBytes());
                try {
                    System.out.println("The client: " + ((InetSocketAddress)sc.getLocalAddress()).getPort() + " had sent data: " + sentData);
                } catch(IOException e) {
                    e.printStackTrace();
                }
                synchronized(sc) {
                    try {
                        while(receivedData == null)
                            sc.wait();
                    } catch(InterruptedException e) {
                        return;
                    }
                }
                try {
                    System.out.println("The client: " + ((InetSocketAddress)sc.getLocalAddress()).getPort() + " had received data: " + receivedData);
                } catch(IOException e) {
                    e.printStackTrace();
                }
                if(!sentData.equals(receivedData)) System.out.println("Error sent and received data has not equals.");
                receivedData = null;
            }

            send(sc, "Bye.".getBytes());
            sendAndClose(sc);

            synchronized(ready) {
                ready++;
                if(ready >= COUNT_CLIENTS) finish();
            }
        }

        public void next(String data) {
            receivedData = data;
            synchronized(sc) {
                sc.notify();
            }
        }

    }

}
