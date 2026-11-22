package com.nhom11.smartcard.host.provider;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/** Experimental byte transfer only; not secure for real employee photographs. */
public final class PhotoTransfer {
    public static final int MAX_SIZE = 8192;
    private static final int CHUNK_SIZE = 128;

    private PhotoTransfer() {
    }

    @FunctionalInterface
    public interface Channel {
        ResponseAPDU transmit(CommandAPDU command) throws CardException;
    }

    /** Writes then reads back a dummy payload, checking every SW and response length. */
    public static boolean roundTrip(Channel channel, byte[] bytes) throws CardException {
        if (channel == null || bytes == null || bytes.length < 1 || bytes.length > MAX_SIZE) {
            throw new IllegalArgumentException("Dữ liệu thử phải có từ 1 đến 8192 byte.");
        }
        requireSuccess(channel.transmit(new CommandAPDU(0x80, 0x10, bytes.length >> 8, bytes.length & 0xff)), "BEGIN");
        for (int offset = 0; offset < bytes.length; offset += CHUNK_SIZE) {
            int length = Math.min(CHUNK_SIZE, bytes.length - offset);
            byte[] chunk = Arrays.copyOfRange(bytes, offset, offset + length);
            requireSuccess(channel.transmit(new CommandAPDU(0x80, 0x11, offset >> 8, offset & 0xff, chunk)), "WRITE");
        }
        requireSuccess(channel.transmit(new CommandAPDU(0x80, 0x12, 0, 0)), "FINISH");
        ByteArrayOutputStream readback = new ByteArrayOutputStream(bytes.length);
        for (int offset = 0; offset < bytes.length; offset += CHUNK_SIZE) {
            int length = Math.min(CHUNK_SIZE, bytes.length - offset);
            ResponseAPDU response = channel.transmit(new CommandAPDU(0x80, 0x13, offset >> 8, offset & 0xff, length));
            requireSuccess(response, "READ");
            byte[] chunk = response.getData();
            if (chunk.length != length) {
                throw new CardException("READ trả sai số byte tại offset " + offset + ".");
            }
            readback.write(chunk, 0, chunk.length);
        }
        return Arrays.equals(bytes, readback.toByteArray());
    }

    private static void requireSuccess(ResponseAPDU response, String step) throws CardException {
        if (response == null || response.getSW() != 0x9000) {
            String sw = response == null ? "không có phản hồi" : String.format("SW=%04X", response.getSW());
            throw new CardException(step + " thất bại: " + sw + ".");
        }
    }
}
