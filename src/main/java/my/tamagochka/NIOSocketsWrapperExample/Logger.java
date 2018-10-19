package my.tamagochka.NIOSocketsWrapperExample;

import my.tamagochka.NIOSocketsWrapper.NIOHost;

import java.nio.channels.SocketChannel;

public class Logger {

    private final boolean fullLog;
    private final NIOHost host;

    public Logger(boolean fullLog, NIOHost host) {
        this.fullLog = fullLog;
        this.host = host;
    }

    public void logDataTransmission(SocketChannel sc, String data, String dirrection) {
        if(!fullLog) return;

/*
        try {
            System.out.println("Data was been " + dir + " client: " +
                    ((InetSocketAddress) sc.getRemoteAddress()).getAddress() + ":" +
                    ((InetSocketAddress) sc.getRemoteAddress()).getPort() + " - " +
                    data);
        } catch(IOException e) {
            e.printStackTrace();
        }
*/



    }
}
