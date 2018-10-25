package my.tamagochka.NIOSocketsEchoExample;

import my.tamagochka.NIOSocketsWrapper.NIOServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class EchoServer extends NIOServer {

    public EchoServer(int port) throws IOException {
        super(port);

        this.onReceive((sc, data) -> {
            String data_str = null;
            try {
                data_str = new String(data);
                System.out.println("The data had received from client: " +
                        ((InetSocketAddress)sc.getRemoteAddress()).getAddress() + ":" +
                        ((InetSocketAddress)sc.getRemoteAddress()).getPort() + " - " +
                        data_str);
                if(data_str.equals("Bye.")) {
                    System.out.println("The client: " +
                            ((InetSocketAddress)sc.getRemoteAddress()).getAddress() + ":" +
                            ((InetSocketAddress)sc.getRemoteAddress()).getPort() + " had disconnected.");
                    this.close(sc);
                    return;
                }
            } catch(IOException e) {
                e.printStackTrace();
            }
            this.send(sc, data);
        });
    }

}
