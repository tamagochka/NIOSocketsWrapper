package my.tamagochka.NIOSocketsWrapperExample;

import my.tamagochka.NIOSocketsWrapper.NIOHost;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    private static final int NUM_PACKETS = 10000;
    private static final boolean FULL_LOG = true;

    private static Map<SocketChannel, Sender> threads = new HashMap<>();

    private static Sender serverHandler;


    public static void main(String[] args) {

        NIOHost server;
        NIOHost client;

        System.out.print("What do you have to run (s(server)/c(client))? ");
        String type = null;
        Scanner in = new Scanner(System.in);
        type = in.nextLine();












        if(type.toLowerCase().equals("s")) { // server start

            server = NIOHost.NIOServerFabric(); //new NIOServer(5050);

            Logger logger = new Logger(FULL_LOG, server);

            server.acceptHandler(sc -> {
//                ClientHandler sender = new ClientHandler(sc, server);
                Sender sender = new Sender(sc, server, NUM_PACKETS);
                threads.put(sc, sender);
                sender.start();
            });

//            server.sentExceptionHandler((sc, notSent, e) -> threads.get(sc).interrupt());

            server.receiveHandler((sc, data) -> {
                logger.logDataTransmission(sc, new String(data), "123");
                if(new String(data).contains("Bye.")) { // disconnecting client after it sent "Bye." message
                    threads.get(sc).receivingDone();
                    System.out.println("The client has completed the transmission.");
                    if(threads.get(sc).isExchangeDone()) {
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

            client = NIOHost.NIOClientFabric();//new NIOClient("127.0.0.1", 5050);

            client.connectHandler(sc -> {
                serverHandler = new Sender(sc, client, NUM_PACKETS);//new ServerHandler(sc, client);
                serverHandler.start();
            });






//            boolean TransmissionDone = false;
            client.receiveHandler((sc, data) -> {
                String str = new String(data);
                if(FULL_LOG) System.out.println("Data was been received from server: " + str);
                if(str.contains("Bye.")) {
                    serverHandler.receivingDone();

                    System.out.println("The server has completed the transmission.");
                    if(serverHandler.isExchangeDone()) {
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
