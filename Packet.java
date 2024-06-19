import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.zip.CRC32;

public class Packet {

    private byte[] msg;
    private byte[] crc;
    private byte[] seq;
    LocalDateTime sentTime;
    public static final int PACKET_BYTES = 22;
    public static final int MSG_SIZE = 10;
    private static final int CRC_SIZE = 8;
    public static final int SEQ_SIZE = 4;

    public void setCrcError() {
        crc = new byte[8];
    }

    public void calculateCrc() {
        CRC32 crc32 = new CRC32();
        crc32.update(msg);
        crc = ByteUtils.longToBytes(crc32.getValue());
    }

    public Packet(byte[] msg, int seq) {
        this.msg = Arrays.copyOf(msg,MSG_SIZE);
        this.seq = ByteUtils.intToBytes(seq);
        calculateCrc();
    }

    public static boolean validCRC(byte[] received){
        if (received.length != PACKET_BYTES){
            return false;
        }
        byte[] msg = Arrays.copyOfRange(received,0,MSG_SIZE);
        byte[] crc = Arrays.copyOfRange(received,MSG_SIZE,MSG_SIZE+CRC_SIZE);
        CRC32 crc32 = new CRC32();
        crc32.update(msg);

        return ByteUtils.bytesToLong(crc) == crc32.getValue();
    }

    public byte[] getSeq() {
        return seq;
    }

    public byte[] makePacket(){
        byte[] result = new byte[msg.length+crc.length+ seq.length];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.put(msg);
        buffer.put(crc);
        buffer.put(seq);
        result = buffer.array();
        sentTime = LocalDateTime.now();
        return result;
    }

    public LocalDateTime getSentTime() {
        return sentTime;
    }

    public static byte[] messageOf(byte[] received){
        return Arrays.copyOfRange(received,0,MSG_SIZE);
    }

    public static int seqOf(byte[] received){
        return ByteUtils.bytesToInt(Arrays.copyOfRange(received,MSG_SIZE+CRC_SIZE,PACKET_BYTES));
    }
    @Override
    public String toString() {
        return "Packet{" +
                "msg=" + Arrays.toString(msg) +" "+ new String(msg, 0, msg.length) + //"\n"+
                ", crc=" + Arrays.toString(crc) + " "+ByteUtils.bytesToLong(crc)+//"\n"+
                ", seq=" + Arrays.toString(seq) + " "+ByteUtils.bytesToInt(seq)+//"\n"+
                '}';
    }
}
