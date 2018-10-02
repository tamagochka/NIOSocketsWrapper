package my.tamagochka.NIOSocketsWrapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    private static Map<SocketChannel, Sender> threads = new HashMap<>();

    private static  class Sender extends Thread {

        private SocketChannel sc;
        private NIOServer server;

        public Sender(SocketChannel sc, NIOServer server) {
            this.sc = sc;
            this.server = server;
        }

        @Override
        public void run() {
            for(int i = 0; i < 100000; i++) {
                if(Thread.interrupted()) {
                    break;
                }
                String str = Integer.toString(i);
                server.send(sc, str.getBytes());
            }
            server.close(sc);
        }
    }


    public static void main(String[] args) {
        NIOServer server;
        try {
            server = new NIOServer(5050);
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }

        server.acceptHandler(sc -> {
            Sender sender = new Sender(sc, server);
            threads.put(sc, sender);
            sender.start();
        });

        server.sentExceptionHandler((sc, notSent, e) -> threads.get(sc).interrupt());

        server.receiveHandler((sc, data) -> {
            try {
                System.out.println(((InetSocketAddress) sc.getRemoteAddress()).getAddress() + ":" +
                        ((InetSocketAddress) sc.getRemoteAddress()).getPort() + " - " +
                        new String(data));
            } catch(IOException e) {
                e.printStackTrace();
            }
        });

        server.finishHandler((notSent, e) -> {
            for(Thread thread : threads.values()) {
                thread.interrupt();
            }
        });

        server.start();

        String str = null;
        Scanner in = new Scanner(System.in);
        while(str == null || !str.equals("exit")) {
            str = in.nextLine();
        }

        server.finish();



    }
}
