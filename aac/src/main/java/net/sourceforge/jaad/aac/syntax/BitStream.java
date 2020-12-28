package net.sourceforge.jaad.aac.syntax;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 21.12.18
 * Time: 13:40
 */
public interface BitStream {

    int getPosition();

    int getBitsLeft();

    int readBits(int n);

    int readBit();

    boolean readBool();

    int peekBits(int n);

    void skipBits(int n);

    void skipBit();

    void byteAlign();

    BitStream readSubStream(int n);

    static BitStream open(byte[] data) {
        return new ByteArrayBitStream(data);
    }
}
