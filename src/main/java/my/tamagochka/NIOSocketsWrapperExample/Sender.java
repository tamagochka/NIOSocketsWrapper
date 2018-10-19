package my.tamagochka.NIOSocketsWrapperExample;

import my.tamagochka.NIOSocketsWrapper.NIOHost;

import java.nio.channels.SocketChannel;

public class Sender extends Thread {

    private SocketChannel sc;
    private NIOHost host;

    private int numPackets;

    private boolean transmissionDone = false;
    private boolean receivingDone = false;

    public Sender(SocketChannel sc, NIOHost host, int numPackets) {
        this.sc = sc;
        this.host = host;
        this.numPackets = numPackets;
    }

    public void receivingDone() {
        receivingDone = true;
    }

    public boolean isExchangeDone() {
        return transmissionDone && receivingDone;
    }

    @Override
    public void run() {
        for(int i = 0; i < numPackets; i++) {
            host.send(sc, Integer.toString(i).getBytes());
        }
        host.send(sc, "Bye.".getBytes());
        transmissionDone = true;
        if(isExchangeDone()) {
            host.close(sc);
        }
    }

}
