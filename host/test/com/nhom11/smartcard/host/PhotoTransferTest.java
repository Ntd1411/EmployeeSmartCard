package com.nhom11.smartcard.host;

import com.nhom11.smartcard.host.provider.PhotoTransfer;
import java.util.Arrays;
import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/** Self-contained transfer tests using a fake APDU channel, not a real simulator. */
public final class PhotoTransferTest {
    public static void main(String[] args) throws Exception {
        testRoundTrip(1);
        testRoundTrip(128);
        testRoundTrip(300);
        testRoundTrip(8192);
        testRoundTrip(16383);
        testRoundTrip(16384);
        testRoundTrip(16385);
        testRoundTrip(PhotoTransfer.MAX_SIZE - 1);
        testRoundTrip(PhotoTransfer.MAX_SIZE);
        testInvalidSize();
        testStatusStopsTransfer();
        testShortReadFails();
        System.out.println("PhotoTransferTest: all 12 checks passed.");
    }

    private static void testRoundTrip(int length) throws Exception {
        byte[] data = new byte[length];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 17);
        }
        if (!PhotoTransfer.roundTrip(new FakeChannel(), data)) {
            throw new AssertionError("Round-trip mismatch for " + length + " bytes");
        }
    }

    private static void testInvalidSize() throws Exception {
        for (int length : new int[] { 0, PhotoTransfer.MAX_SIZE + 1 }) {
            FakeChannel fake = new FakeChannel();
            try {
                PhotoTransfer.roundTrip(fake, new byte[length]);
                throw new AssertionError("Expected size rejection for " + length);
            } catch (IllegalArgumentException expected) {
                if (fake.data != null) {
                    throw new AssertionError("Invalid size sent an APDU", expected);
                }
            }
        }
    }

    private static void testStatusStopsTransfer() throws Exception {
        FakeChannel fake = new FakeChannel();
        fake.failWrite = true;
        try {
            PhotoTransfer.roundTrip(fake, new byte[8]);
            throw new AssertionError("Expected WRITE failure");
        } catch (CardException expected) {
            if (!expected.getMessage().contains("6982") || fake.finished) {
                throw new AssertionError("Did not stop on the failed WRITE", expected);
            }
        }
    }

    private static void testShortReadFails() throws Exception {
        FakeChannel fake = new FakeChannel();
        fake.shortRead = true;
        try {
            PhotoTransfer.roundTrip(fake, new byte[8]);
            throw new AssertionError("Expected short READ failure");
        } catch (CardException expected) {
            if (!expected.getMessage().contains("sai số byte")) {
                throw new AssertionError("Unexpected READ failure", expected);
            }
        }
    }

    private static final class FakeChannel implements PhotoTransfer.Channel {
        private byte[] data;
        private int written;
        private boolean finished;
        private boolean failWrite;
        private boolean shortRead;

        @Override
        public ResponseAPDU transmit(CommandAPDU command) {
            int offset = (command.getP1() << 8) | command.getP2();
            switch (command.getINS()) {
                case 0x10:
                    data = new byte[offset];
                    return response(new byte[0], 0x9000);
                case 0x11:
                    if (failWrite) return response(new byte[0], 0x6982);
                    byte[] chunk = command.getData();
                    if (offset != written || offset + chunk.length > data.length) {
                        return response(new byte[0], 0x6985);
                    }
                    System.arraycopy(chunk, 0, data, offset, chunk.length);
                    written += chunk.length;
                    return response(new byte[0], 0x9000);
                case 0x12:
                    finished = written == data.length;
                    return response(new byte[0], finished ? 0x9000 : 0x6985);
                case 0x13:
                    if (!finished) return response(new byte[0], 0x6985);
                    int count = Math.min(command.getNe(), data.length - offset);
                    if (shortRead) count--;
                    return response(Arrays.copyOfRange(data, offset, offset + count), 0x9000);
                default:
                    return response(new byte[0], 0x6D00);
            }
        }

        private ResponseAPDU response(byte[] data, int sw) {
            byte[] result = Arrays.copyOf(data, data.length + 2);
            result[result.length - 2] = (byte) (sw >> 8);
            result[result.length - 1] = (byte) sw;
            return new ResponseAPDU(result);
        }
    }
}
