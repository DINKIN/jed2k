package org.dkf.jed2k.protocol;

import org.dkf.jed2k.Utils;
import org.dkf.jed2k.exception.ErrorCode;
import org.dkf.jed2k.exception.JED2KException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import static org.dkf.jed2k.protocol.Unsigned.*;

/**
 * this is the same as Container class except it can store only bytes and array uses as container
 * @param <CS>
 */
public class ByteContainer<CS extends UNumber> implements Serializable {
    private static Logger log = LoggerFactory.getLogger(ByteBuffer.class.getName());

    private final CS size;
    private byte[] value;

    public ByteContainer(CS size) {
        this.size = size;
    }

    public ByteContainer(CS size, byte[] value) {
        this.size = size;
        this.value = value;
        this.size.assign(value.length);
    }

    @Override
    public ByteBuffer get(ByteBuffer src) throws JED2KException {
        size.get(src);
        if (size.intValue() > 0) {
            // set limit to 2Kb for buffer limit to avoid OOM
            if (size.intValue() > 2048) {
                log.error("byte buffer size overflow {}", size.intValue());
                throw new JED2KException(ErrorCode.BUFFER_TOO_LARGE);
            }

            value = new byte[size.intValue()];
            try {
                src.get(value);
            } catch(BufferUnderflowException e) {
                throw new JED2KException(ErrorCode.BUFFER_UNDERFLOW_EXCEPTION);
            } catch(Exception e) {
                throw new JED2KException(ErrorCode.BUFFER_GET_EXCEPTION);
            }
        } else {
            log.error("byte buffer incorrect size {}", size.intValue());
        }

        return src;
    }

    @Override
    public ByteBuffer put(ByteBuffer dst) throws JED2KException {
        if (value == null) {
            size.assign(0);
            return size.put(dst);
        } else {
            size.assign(value.length);
            return size.put(dst).put(value);
        }
    }

    public String asString() throws JED2KException {
        try {
            if (value != null)  return new String(value, "UTF-8");
            return "";
        } catch(UnsupportedEncodingException e) {
            throw new JED2KException(e, ErrorCode.UNSUPPORTED_ENCODING);
        }
    }

    public void assignString(final String value) throws JED2KException {
        try {
            this.value = value.getBytes("UTF-8");
            this.size.assign(this.value.length);
        } catch(UnsupportedEncodingException e) {
            throw new JED2KException(e, ErrorCode.UNSUPPORTED_ENCODING);
        }
    }

    public static<CS extends UNumber> ByteContainer<UInt8> fromString8(String value) throws JED2KException {
        try {
            byte[] content = value.getBytes("UTF-8");
            return new ByteContainer<UInt8>(uint8(), content);
        } catch(UnsupportedEncodingException e) {
            throw new JED2KException(e, ErrorCode.UNSUPPORTED_ENCODING);
        }
    }

    public static<CS extends UNumber> ByteContainer<UInt16> fromString16(String value) throws JED2KException {
        try {
            byte[] content = value.getBytes("UTF-8");
            return new ByteContainer<UInt16>(uint16(), content);
        } catch(UnsupportedEncodingException e) {
            throw new JED2KException(e, ErrorCode.UNSUPPORTED_ENCODING);
        }
    }

    public static<CS extends UNumber> ByteContainer<UInt32> fromString32(String value) throws JED2KException {
        try {
            byte[] content = value.getBytes("UTF-8");
            return new ByteContainer<UInt32>(uint32(), content);
        } catch(UnsupportedEncodingException e) {
            throw new JED2KException(e, ErrorCode.UNSUPPORTED_ENCODING);
        }
    }

    @Override
    public String toString() {
        return String.format("%d[%s]", size.intValue(), Utils.byte2String(value));
    }

    @Override
    public int bytesCount() {
        return size.bytesCount() + value.length;
    }

    public CS getSize() {
        return this.size;
    }

    public byte[] getValue() {
        return this.value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof ByteContainer)) return false;
        final ByteContainer<?> other = (ByteContainer<?>) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$size = this.getSize();
        final Object other$size = other.getSize();
        if (this$size == null ? other$size != null : !this$size.equals(other$size)) return false;
        if (!java.util.Arrays.equals(this.getValue(), other.getValue())) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ByteContainer;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $size = this.getSize();
        result = result * PRIME + ($size == null ? 43 : $size.hashCode());
        result = result * PRIME + java.util.Arrays.hashCode(this.getValue());
        return result;
    }
}