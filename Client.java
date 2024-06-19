import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;


public class Client {
    private final DatagramSocket socket;
    LinkedList<Packet> waitingAck = new LinkedList<>();
    HashMap<Integer, byte[]> receivedPackets;
    int lastAck;
    private final byte[] ACK;
    private final byte[] CONNECT;
    private int errorChance = 0;
    private boolean connected = false;
    private int packetTimeout;
    private int receivedFiles = 0;

    public void setErrorChance(int errorChance) {
        this.errorChance = errorChance;
    }

    public void setPacketTimeout(int timeout) {
        this.packetTimeout = timeout;
    }

    public Client(int port) throws SocketException {
        ByteBuffer buffer = ByteBuffer.allocate(Packet.MSG_SIZE);
        buffer.put("ACK".getBytes());
        ACK = buffer.array();
        buffer = ByteBuffer.allocate(Packet.MSG_SIZE);
        buffer.put("CONNECT".getBytes());
        CONNECT = buffer.array();

        socket = new DatagramSocket(port);
        System.out.println("Socked bound to port " + socket.getLocalPort() + ".");
    }

    public void sendFile(InetAddress address, int port, File file) throws IOException {
        socket.setSoTimeout(300);
        int totalPackets = (int) Math.ceil((double) file.length() / 10);
        System.out.println("File size: " + file.length() + " bytes. Total packets to send: " + totalPackets);
        confirmOutgoingConnection(address, port, totalPackets);
        byte[] buf = new byte[Packet.MSG_SIZE];
        FileInputStream fis = new FileInputStream(file);
        int read ;
        int seq = 0;
        while ((read = fis.read(buf)) > 0 || !waitingAck.isEmpty()) {
            if (read > 0) {
                while (read < 10) {
                    buf[read] = 0;
                    read++;
                }
                Packet p = new Packet(buf, seq++);
                System.out.print("Sending packet seq: " + ByteUtils.bytesToInt(p.getSeq()) + ". ");
                //System.out.print(new String(p.getMsg())+" .");
                sendPacket(p, address, port);
                waitingAck.add(p);
            }


            LinkedList<Packet> waitingAckAux = new LinkedList<>(waitingAck);
            for (Packet p : waitingAckAux) {
                if (Duration.between(p.getSentTime(), LocalDateTime.now()).toMillis() > packetTimeout) {
                    System.out.print("Packet timed out. Re-sending seq: " + ByteUtils.bytesToInt(p.getSeq()) + ". ");
                    sendPacket(p, address, port);
                }
            }
            if(!waitingAck.isEmpty()){
                try {
                    DatagramPacket datagramPacket = new DatagramPacket(new byte[Packet.PACKET_BYTES], Packet.PACKET_BYTES);
                    socket.receive(datagramPacket);

                    int ackSeq = Packet.seqOf(datagramPacket.getData());
                    boolean valid = ackSeq != -1;
                    if (Arrays.equals(ACK, Packet.messageOf(datagramPacket.getData())) && valid) {
                        waitingAckAux = new LinkedList<>(waitingAck);
                        for (Packet p : waitingAckAux) {
                            if (ByteUtils.bytesToInt(p.getSeq()) == ackSeq - 1) {
                                waitingAck.remove(p);
                                System.out.println("Received ACK " + ackSeq + " confirmed packet " + ByteUtils.bytesToInt(p.getSeq()) + ". ");
                            }
                        }
                    } else {
                        System.err.println("Invalid CRC on ACK, this shouldn't happen.");
                    }

                } catch (SocketTimeoutException e) {
                    //System.out.println("Packets waiting for ACK: " + waitingAck.size());
                }
            }
        }
        System.out.println("File sent, all ACKs received.");
        connected = false;
    }

    private void confirmOutgoingConnection(InetAddress address, int port, int totalPackets) throws IOException {
        while (!connected) {
            System.out.print("Sending connection request... ");
            sendPacket(new Packet(CONNECT, totalPackets), address, port);
            try {
                DatagramPacket connAck = new DatagramPacket(new byte[Packet.PACKET_BYTES], Packet.PACKET_BYTES);
                socket.receive(connAck);
                if (Arrays.equals(ACK, Packet.messageOf(connAck.getData())) && 0 == Packet.seqOf(connAck.getData())) {
                    System.out.println("Connection confirmed.");
                    connected = true;
                }
            } catch (IOException e) {
                System.out.println("Connection timed out.");
            }
        }
    }

    public void receive() throws IOException {
        DatagramPacket datagramPacket = new DatagramPacket(new byte[Packet.PACKET_BYTES], Packet.PACKET_BYTES);
        int totalPackets = confirmIncomingConnection();
        receivedPackets = new HashMap<>();
        lastAck = -1;
        socket.setSoTimeout(0);
        while (lastAck + 1 < totalPackets) {
            socket.receive(datagramPacket);
            int seq = Packet.seqOf(datagramPacket.getData());
            System.out.print("Packet seq: "+seq+ " received. ");
            if(receivedPackets.get(seq) == null){
                if (Packet.validCRC(datagramPacket.getData())) {
                    System.out.println("Valid CRC. Packet saved.");
                    receivedPackets.put(seq, Packet.messageOf(datagramPacket.getData()));
                    //System.out.println("Saved seq "+seq+": "+new String(Packet.messageOf(datagramPacket.getData())));
                } else {
                    System.out.println("Invalid CRC. Packet discarded.");
                }
            }else{
                System.out.println("Already saved, waiting on packet "+(lastAck+1)+ " to send ACK.");
            }

            for (int i = lastAck + 1; i < totalPackets; i++) {
                if (receivedPackets.get(i) == null) {
                    break;
                } else {
                    InetAddress address = datagramPacket.getAddress();
                    int port = datagramPacket.getPort();
                    System.out.print("Sending ACK: " + (i + 1));
                    sendPacket(new Packet(ACK, i + 1), address, port);
                    lastAck++;
                }
            }
        }

        System.out.println("All packets received. Saving file 'received"+receivedFiles+"'.");
        FileOutputStream output = new FileOutputStream("received"+receivedFiles++, true);
        for (int i = 0; i < totalPackets; i++) {
            if(i < totalPackets-1){
               // System.out.println("writing seq "+i+": "+ new String(receivedPackets.get(i)));
                output.write(receivedPackets.get(i));
            }else{
                byte[] lastPacket = receivedPackets.get(i);
                //System.out.println("Last packet"+Arrays.toString(lastPacket));
                for (int j = 0; j < lastPacket.length; j++) {
                    if(lastPacket[j] == (byte) 0){
                        //System.out.println("last packet: "+ i+": "+ new String(Arrays.copyOf(lastPacket,j)));
                        output.write(Arrays.copyOf(lastPacket,j));
                        break;
                    }
                }
            }
        }

        socket.setSoTimeout(0);
        output.close();
    }

    private int confirmIncomingConnection() throws SocketException {
        DatagramPacket datagramPacket = new DatagramPacket(new byte[Packet.PACKET_BYTES], Packet.PACKET_BYTES);
        boolean waitingConnectionRequest = true;
        while (waitingConnectionRequest) {
            int LISTENING_SOCKET_TIMEOUT = 10000;
            socket.setSoTimeout(LISTENING_SOCKET_TIMEOUT);
            try {
                socket.receive(datagramPacket);
                int totalPackets = Packet.seqOf(datagramPacket.getData());
                if (totalPackets != -1 && Arrays.equals(CONNECT, Packet.messageOf(datagramPacket.getData()))) {
                    System.out.print("Connection request for " + totalPackets + " packets received, sending ack. ");
                    sendPacket(new Packet(ACK, 0), datagramPacket.getAddress(), datagramPacket.getPort());
                    waitingConnectionRequest = false;
                    socket.setSoTimeout(LISTENING_SOCKET_TIMEOUT);
                    return totalPackets;
                }
            } catch (IOException ex) {
                System.out.println("Waiting for connection request...");
            }
        }
        return -1;
    }

    public void close() {
        socket.close();
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    private void sendPacket(Packet packet, InetAddress targetAddress, int targetPort) throws IOException {
        Random r = new Random();
        if (connected && r.nextInt(101) < errorChance) {
            System.out.println("CRC Error triggered!");
            packet.setCrcError();
        } else {
            System.out.print("\n");
            packet.calculateCrc();
        }
        byte[] packetByte = packet.makePacket();
        DatagramPacket datagramPacket = new DatagramPacket(packetByte, packetByte.length, targetAddress, targetPort);
        socket.send(datagramPacket);
    }
}