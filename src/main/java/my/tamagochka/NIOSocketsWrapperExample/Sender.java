package my.tamagochka.NIOSocketsWrapperExample;

import my.tamagochka.NIOSocketsWrapper.NIOHost;

import java.nio.channels.SocketChannel;

public class Sender extends Thread {

    private SocketChannel sc;
    private NIOHost host;
    private Logger logger;

    private int numPackets;

    private boolean transmissionDone = false;
    private boolean receivingDone = false;

    public Sender(SocketChannel sc, NIOHost host, Logger logger, int numPackets) {
        this.sc = sc;
        this.host = host;
        this.logger = logger;
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
            if(Thread.interrupted()) return;
            byte[] data = Integer.toString(i).getBytes();
            host.send(sc, data);
            logger.logDataSend(sc, data);
        }
        byte[] data = "Bye.".getBytes();
        host.send(sc, data);
        logger.logDataSend(sc, data);
        transmissionDone = true;
        logger.logCompleteSending();
        if(isExchangeDone()) {
            host.close(sc);
            logger.logCloseChannel();
        }
    }

}
