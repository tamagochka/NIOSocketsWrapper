package my.tamagochka.NIOSocketsWrapper;

public class NIOClient {

    private final static int DEFAULT_BUFFER_SIZE = 1024;

    private final int serverPort;
    private final String serverAddress;
    private final int bufferSize;

    public NIOClient(String serverAddress, int serverPort) {
        this(serverAddress, serverPort, DEFAULT_BUFFER_SIZE);
    }

    public NIOClient(String serverAddress, int serverPort, int bufferSize) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.bufferSize = bufferSize;




    }

}
