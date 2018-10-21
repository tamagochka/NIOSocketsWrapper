package my.tamagochka.NIOSocketsWrapperExample;

import my.tamagochka.NIOSocketsWrapper.NIOHost;
import my.tamagochka.NIOSocketsWrapper.NIOServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class Logger {

    private final boolean fullLog;
    private final NIOHost host;
    private final String host_str;
    private final String another_host_str;

    public Logger(boolean fullLog, NIOHost host) {
        this.fullLog = fullLog;
        this.host = host;
        if(host instanceof NIOServer) {
            host_str = "server";
            another_host_str = "client";
        } else {
            host_str = "client";
            another_host_str = "server";
        }
    }

    public void logCompleteSending() {
        System.out.println("The " + host_str + " had sent data.");
    }

    public void logCompleteReceivng() {
        System.out.println("The " + another_host_str + " had sent data.");
    }


    public void logCloseChannel() {
        System.out.println("The channel to " + another_host_str + " had closed.");
    }

    public void logStartHost() {
        System.out.println("The " + host_str + " had started...");
    }

    public void logStopHost() {
        System.out.println("The " + host_str + " had stopped.");
    }

    public void logDataReceive(SocketChannel sc, byte[] data) {
        if(!fullLog) return;
        logData(sc, data, "received from");
    }

    public void logDataSend(SocketChannel sc, byte[] data) {
        if(!fullLog) return;
        logData(sc, data, "sent to");
    }

    private void logData(SocketChannel sc, byte[] data, String direction) {
        try {
            System.out.println("Data was been " + direction + another_host_str + ": " +
                    ((InetSocketAddress) sc.getRemoteAddress()).getAddress() + ":" +
                    ((InetSocketAddress) sc.getRemoteAddress()).getPort() + " - " +
                    new String(data));
        } catch(IOException e) {
            e.printStackTrace();
        }

    }
}
