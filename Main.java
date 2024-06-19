import java.io.*;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.InputMismatchException;
import java.util.Scanner;

public class Main {

    private static Client client;

    public static void main(String[] args) throws IOException {
        int option = 0;
        Scanner in = new Scanner(System.in);
        System.out.println("-----------------------------");
        System.out.println("Set a port number for this client or 0 for a randomly available one.");
        int port;
        do {
            port = getPort();
            try {
                client = new Client(port);
            } catch (BindException exception) {
                System.out.println("Port not available. Try another.");
            }
        } while (client == null);

        do {
            printMenu();
            do {
                if (option == -1) {
                    System.out.print("Invalid option. Try again: ");
                }
                try {
                    option = in.nextInt();
                } catch (InputMismatchException ignored) {
                    option = -1;
                    in.nextLine();
                }
                switch (option) {
                    case 1 -> sendFileMenu();
                    case 2 -> receive();
                    case 3 -> {
                    }
                    default -> option = -1;
                }
            } while (option == -1);
        } while (option != 3);

        client.close();
    }

    private static void sendFileMenu() throws IOException {
        InetAddress address = getAddress();
        int port = getPort();
        File file = getFile();
        int errorChance = getErrorChance();
        int timeout = getPacketTimeout();
/*
        InetAddress address = InetAddress.getByName("127.0.0.1");
        int port = 3;
        File file = new File("test.txt");
        int errorChance = 50;
        int timeout = 3000;
*/
        client.setErrorChance(errorChance);
        client.setPacketTimeout(timeout);
        sendFile(address, port, file);
    }

    private static void receive() throws IOException {
        System.out.println("Waiting for connection request on port " + client.getPort()+".");
        client.receive();
    }


    private static void printMenu() {
        System.out.println("-----------------------------");
        System.out.println("1 - Send File.");
        System.out.println("2 - Listen.");
        System.out.println("3 - Exit.");
        System.out.println("-----------------------------");
        System.out.print("Choose an option: ");
    }

    private static InetAddress getAddress() throws UnknownHostException {
        Scanner in = new Scanner(System.in);

        String address;
        System.out.print("Enter the ip address of the target: ");
        do {
            address = in.next();
            if (!validAddress(address)) {
                address = "-1";
                System.out.print("This is not a valid ip address. Try again: ");
            }
        } while (address.equals("-1"));
        return InetAddress.getByName(address);
    }

    private static int getPort() {
        int port;
        Scanner in = new Scanner(System.in);
        System.out.print("Enter the port number: ");
        do {
            try {
                port = in.nextInt();
            } catch (InputMismatchException ignored) {
                port = -1;
                in.nextLine();
            }
            if (port < 0 || port > 65535) {
                port = -1;
                System.out.print("The value must be between 0 and 65535. Try again: ");
            }
        } while (port == -1);
        return port;
    }

    private static int getErrorChance() {
        int errorChance;
        Scanner in = new Scanner(System.in);
        System.out.print("Enter the chance for a CRC error number (between 0 and 100): ");
        do {
            try {
                errorChance = in.nextInt();
            } catch (InputMismatchException ignored) {
                errorChance = -1;
                in.nextLine();
            }
            if (errorChance < 0 || errorChance > 100) {
                errorChance = -1;
                System.out.print("The value must be between 0 and 100. Try again: ");
            }
        } while (errorChance == -1);
        return errorChance;
    }
    private static int getPacketTimeout() {
        int timeout;
        Scanner in = new Scanner(System.in);
        System.out.print("Enter the time for a packet timeout (In milliseconds): ");
        do {
            try {
                timeout = in.nextInt();
            } catch (InputMismatchException ignored) {
                timeout = -1;
                in.nextLine();
            }
            if (timeout < 0 || timeout > 10000) {
                timeout = -1;
                System.out.print("The value must be between 0 and 10000. Try again: ");
            }
        } while (timeout == -1);
        return timeout;
    }
    private static boolean validAddress(String address) {
        String[] bytes = address.split("\\.");
        for (String aByte : bytes) {
            try {
                int b = Integer.parseInt(aByte);
                if (b < 0 || b > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static File getFile() {
        Scanner in = new Scanner(System.in);
        File file;

        String filePathName;
        do {
            System.out.print("Enter file name or path: ");
            filePathName = in.next();

            file = new File(filePathName);
            if (!file.canRead()) {
                System.out.print("File cannot be read. ");
                filePathName = "-1";
            }
        } while (filePathName.equals("-1"));
        return file;
    }

    public static void sendFile(InetAddress address, int port, File file) throws IOException {
        client.sendFile(address, port, file);

    }
}
