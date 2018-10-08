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
        NIOClient client;

        System.out.print("What do you have to run (s(server)/c(client))? ");
        String str = null;
        Scanner in = new Scanner(System.in);
        str = in.nextLine();
        if(str.toLowerCase().equals("s")) {

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

/*
            server.receiveHandler((sc, data) -> {
                try {
                    System.out.println("The data was been received from client: " +
                            ((InetSocketAddress) sc.getRemoteAddress()).getAddress() + ":" +
                            ((InetSocketAddress) sc.getRemoteAddress()).getPort() + " - " +
                            new String(data));
                } catch(IOException e) {
                    e.printStackTrace();
                }
            });
*/

            server.finishHandler((notSent, e) -> {
                for(Thread thread : threads.values()) {
                    thread.interrupt();
                }
            });

            server.start();
            System.out.println("The server has been started...");
            while(str == null || !str.equals("exit")) {
                str = in.nextLine();
            }
            server.finish();

        } else if(str.toLowerCase().equals("c")) {

            try {
                client = new NIOClient("127.0.0.1", 5050);
            } catch(IOException e) {
                e.printStackTrace();
                return;
            }

            client.start();
            System.out.println("The client has been started...");
            for(int i = 0; i < 100000; i++) {
//                System.out.println("The data was been sent to server: " + i);
                client.send(Integer.toString(i).getBytes());
            }


        }








    }
}
