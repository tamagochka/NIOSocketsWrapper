package my.tamagochka.NIOSocketsWrapperExample;

import my.tamagochka.NIOSocketsWrapper.NIOClient;
import my.tamagochka.NIOSocketsWrapper.NIOHost;
import my.tamagochka.NIOSocketsWrapper.NIOServer;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    private static final int NUM_PACKETS = 100000;
    private static final boolean FULL_LOG = true;

    private static Map<SocketChannel, Sender> threads = new HashMap<>();

    public static void main(String[] args) {

        NIOHost host;

        String type = null;
        Scanner in = new Scanner(System.in);
        do {
            System.out.print("What do you have to run [s(server)/c(client)/E(exit)]? ");
            type = in.nextLine();
        } while(!(type.toLowerCase().equals("s") ||
                type.toLowerCase().equals("c") ||
                type.toLowerCase().equals("e") ||
                type.isEmpty()));

        try {
            switch(type.toLowerCase()) {
                case "s":
                    host = new NIOServer(5050);
                    break;
                case "c":
                    host = new NIOClient("127.0.0.1", 5050);
                    break;
                default:
                    return;
            }
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }

        Logger logger = new Logger(FULL_LOG, host);

        host.onAction(sc -> {
            Sender sender = new Sender(sc, host, logger, NUM_PACKETS);
            threads.put(sc, sender);
            sender.start();
        });

        host.onReceive((sc, data) -> {
            logger.logDataReceive(sc, data);
            if(new String(data).contains("Bye.")) { // disconnecting host after it sent "Bye." message
                threads.get(sc).receivingDone();
                logger.logCompleteReceivng();
                if(threads.get(sc).isExchangeDone()) {
                    host.close(sc);
                    logger.logCloseChannel();
                }
            }
        });

        // interrupt Sender thread if channel is break
        host.onBreakChannel(sc -> threads.remove(sc).interrupt());

        logger.logStartHost();
        host.start();
        System.out.println("Type 'exit' to exit.");
        while(type == null || !type.equals("exit")) {
            type = in.nextLine();
        }
        host.finish();
        logger.logStopHost();
    }

}
