package my.tamagochka.NIOSocketsEchoExample;

import my.tamagochka.NIOSocketsWrapper.NIOHost;

import java.io.IOException;
import java.util.Scanner;

public class Main {

    private static final int PORT = 5050;
    private static final String ADDRESS = "127.0.0.1";

    public static void main(String[] args) {

        String type = null;
        Scanner in = new Scanner(System.in);
        do {
            System.out.print("What do you have to run [s(server)/c(client)/E(exit)]? ");
            type = in.nextLine().toLowerCase();
        } while(!"sce".contains(type));
        NIOHost echo;
        try {
            switch(type) {
                case "s":
                    echo = new EchoServer(PORT);
                    break;
                case "c":
                    echo = new EchoClient(ADDRESS, PORT);
                    break;
                default:
                    return;
            }
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }
        echo.start();

        try {
            echo.join();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }

/*
        do {
            System.out.println("Type 'exit' to exit.");
            type = in.nextLine().toLowerCase();
        } while(!type.equals("exit"));
*/

    }

}
