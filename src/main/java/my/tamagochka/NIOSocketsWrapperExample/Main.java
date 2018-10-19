package my.tamagochka.NIOSocketsWrapperExample;

import my.tamagochka.NIOSocketsWrapper.NIOServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    private static final int NUM_PACKETS = 10000;
    private static final boolean FULL_LOG = true;

    private static Map<SocketChannel, ClientHandler> threads = new HashMap<>();

    private static ServerHandler serverHandler;

    private static class ServerLogger {

        private enum Dirrection {
            RECEIVING, SENDING;
        }

        private final boolean FULL_LOG;

        public ServerLogger(boolean full_log) {
            FULL_LOG = full_log;
        }

        public void log(SocketChannel sc, String data, Dirrection dirrection) {
            if(FULL_LOG) {
                String dir;
                switch(dirrection) {
                    case RECEIVING:
                        dir = "received from";
                        break;
                    case SENDING:
                        dir = "sent to";
                        break;
                    default:
                        dir = "received from";
                        break;
                }
                try {
                    System.out.println("Data was been " + dir + " client: " +
                            ((InetSocketAddress) sc.getRemoteAddress()).getAddress() + ":" +
                            ((InetSocketAddress) sc.getRemoteAddress()).getPort() + " - " +
                            data);
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }


    private static class ClientHandler extends Thread {

        private SocketChannel sc;
        private NIOServer server;
        private boolean TransmissionDone = false;
        private boolean ReceiveDone = false;
        ServerLogger logger = new ServerLogger(FULL_LOG);

        public ClientHandler(SocketChannel sc, NIOServer server) {
            this.sc = sc;
            this.server = server;
        }

        public void setReceiveDone(boolean ReceiveDone) {
            this.ReceiveDone = ReceiveDone;
        }

        public boolean checkEndOfExchange() {
            return TransmissionDone && ReceiveDone;
        }

        @Override
        public void run() {
            for(int i = 0; i < NUM_PACKETS; i++) {
/*
                if(Thread.interrupted()) {
                    break;
                }
*/
                String str = Integer.toString(i);
                server.send(sc, str.getBytes());
                logger.log(sc, str, ServerLogger.Dirrection.SENDING);
            }
            server.send(sc, "Bye.".getBytes());
            logger.log(sc, "Bye.", ServerLogger.Dirrection.SENDING);

            TransmissionDone = true;
            System.out.println("The server has completed the transmission.");
            if(checkEndOfExchange()) {
                try {
                    sc.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
                // server.close(sc); // close socket after data transmission
                System.out.println("The channel to client was closed.");
            }
        }
    }

    private static class ServerHandler extends Thread{

        private boolean TransmissionDone = false;
        private boolean ReceiveDone = false;

        private SocketChannel sc;
        private NIOServer client;

        public ServerHandler(SocketChannel sc, NIOServer client) {
            this.sc = sc;
            this.client = client;
        }

        public void setReceiveDone(boolean ReceiveDone) {
            this.ReceiveDone = ReceiveDone;
        }

        public boolean checkEndOfExchange() {
            return TransmissionDone && ReceiveDone;
        }

        public void run () {
            for(int i = 0; i < NUM_PACKETS; i++) {
                client.send(sc, Integer.toString(i).getBytes());
                if(FULL_LOG) System.out.println("The data was been sent to server: " + i);
            }
            client.send(sc, "Bye.".getBytes());
            if(FULL_LOG) System.out.println("The data was been sent to server: Bye.");
            TransmissionDone = true;
            System.out.println("The client has completed the transmission.");
            if(checkEndOfExchange()) {
                client.close(sc);
                System.out.println("The channel to server was closed.");
            }

        }
    }


    public static void main(String[] args) {

        NIOServer server;
        NIOServer client;

        System.out.print("What do you have to run (s(server)/c(client))? ");
        String type = null;
        Scanner in = new Scanner(System.in);
        type = in.nextLine();












        if(type.toLowerCase().equals("s")) { // server start

            server = NIOServer.NIOServerFabric(); //new NIOServer(5050);

            ServerLogger logger = new ServerLogger(FULL_LOG);

            server.acceptHandler(sc -> {
                ClientHandler clientHandler = new ClientHandler(sc, server);
                threads.put(sc, clientHandler);
                clientHandler.start();
            });

//            server.sentExceptionHandler((sc, notSent, e) -> threads.get(sc).interrupt());

            server.receiveHandler((sc, data) -> {
                logger.log(sc, new String(data), ServerLogger.Dirrection.RECEIVING);
                if(new String(data).contains("Bye.")) { // disconnecting client after it sent "Bye." message
                    threads.get(sc).setReceiveDone(true);
                    System.out.println("The client has completed the transmission.");
                    if(threads.get(sc).checkEndOfExchange()) {
                        // server.close(sc);
                        try {
                            sc.close();
                        } catch(IOException e) {
                            e.printStackTrace();
                        }
                        System.out.println("The channel to client was closed.");
                    }
                }
            });

/*
            server.finishHandler((notSent, e) -> {
                for(Thread thread : threads.values()) {
                    thread.interrupt();
                }
            });
*/

            try {
                server.launch(5050);
            } catch(IOException e) {
                e.printStackTrace();
                return;
            }

            System.out.println("The server has been started...");
            server.start();

            System.out.println("Type 'exit' to exit.");
            while(type == null || !type.equals("exit")) {
                type = in.nextLine();
            }
            server.finish();
            System.out.println("The server was stopped.");














        } else if(type.toLowerCase().equals("c")) { // client start ////////////////////

            client = NIOServer.NIOClientFabric();//new NIOClient("127.0.0.1", 5050);

            client.connectHandler(sc -> {
                serverHandler = new ServerHandler(sc, client);
                serverHandler.start();
            });






//            boolean TransmissionDone = false;
            client.receiveHandler((sc, data) -> {
                String str = new String(data);
                if(FULL_LOG) System.out.println("Data was been received from server: " + str);
                if(str.contains("Bye.")) {
                    serverHandler.setReceiveDone(true);

                    System.out.println("The server has completed the transmission.");
                    if(serverHandler.checkEndOfExchange()) {
                        client.close(sc);
                        System.out.println("The channel to server was closed.");
                    }



                }




            });


            try {
                client.connect("127.0.0.1", 5050);
            } catch(IOException e) {
                e.printStackTrace();
                return;
            }


            System.out.println("The client has been started...");
            client.start();




            System.out.println("Type 'exit' to exit.");
            while(type == null || !type.equals("exit")) {
                type = in.nextLine();
            }
//            server.finish();

            client.finish();

            System.out.println("The client was stopped.");




        }








    }
}
