package com.study.netty.codec;

/**
 * CodecUtil
 *
 * @version 1.0.0
 * @since 2018-05
 */
public class CodecUtil {

    /**
     * 用于建立十六进制字符的输出
     */
    private static final char[] DIGITS_LOWER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * 用于建立十六进制字符的输出
     */
    private static final char[] DIGITS_UPPER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * 将字节数组转换为十六进制字符数组。
     * <p>
     * 因为使用两个字符表示一个字节，所以返回的char[]长度将是参数byte[]长度的两倍。
     *
     * @param data 用于转换为十六进制字符的byte[]
     * @return 包含十六进制字符的char[]
     */
    public static char[] encodeHex(final byte[] data) {
        return encodeHex(data, false);
    }

    /**
     * 将字节数组转换为十六进制字符数组。
     * <p>
     * 因为使用两个字符表示一个字节，所以返回的char[]长度将是参数byte[]长度的两倍。
     *
     * @param data        用于转换为十六进制字符的byte[]
     * @param toLowerCase <code>true</code> 传换成小写格式 ， <code>false</code> 传换成大写格式
     * @return 包含十六进制字符的char[]
     */
    public static char[] encodeHex(final byte[] data, final boolean toLowerCase) {
        return encodeHex(data, toLowerCase ? DIGITS_LOWER : DIGITS_UPPER);
    }

    /**
     * 将字节数组转换为十六进制字符数组。
     * <p>
     * 因为使用两个字符表示一个字节，所以返回的char[]长度将是参数byte[]长度的两倍。
     *
     * @param data     用于转换为十六进制字符的byte[]
     * @param toDigits 用于控制输出的字母表
     * @return 包含十六进制字符的char[]
     */
    protected static char[] encodeHex(final byte[] data, final char[] toDigits) {
        int l = data.length;
        char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = toDigits[(0xF0 & data[i]) >>> 4];
            out[j++] = toDigits[0x0F & data[i]];
        }
        return out;
    }

    /**
     * 将字节数组转换为十六进制字符串。
     * <p>
     * 因为使用两个字符表示一个字节，所以返回的的字符串长度将是参数byte[]长度的两倍。
     *
     * @param data 用于转换为十六进制字符的byte[]
     * @return 十六进制字符串
     */
    public static String encodeHexStr(final byte[] data) {
        return encodeHexStr(data, false);
    }

    /**
     * 将字节数组转换为十六进制字符串。
     * <p>
     * 因为使用两个字符表示一个字节，所以返回的的字符串长度将是参数byte[]长度的两倍。
     *
     * @param data        用于转换为十六进制字符的byte[]
     * @param toLowerCase <code>true</code> 传换成小写格式 ， <code>false</code> 传换成大写格式
     * @return 十六进制字符串
     */
    public static String encodeHexStr(byte[] data, boolean toLowerCase) {
        return encodeHexStr(data, toLowerCase ? DIGITS_LOWER : DIGITS_UPPER);
    }

    /**
     * 将字节数组转换为十六进制字符串。
     * <p>
     * 因为使用两个字符表示一个字节，所以返回的的字符串长度将是参数byte[]长度的两倍。
     *
     * @param data     用于转换为十六进制字符的byte[]
     * @param toDigits 用于控制输出的字母表
     * @return 十六进制字符串
     */
    protected static String encodeHexStr(byte[] data, char[] toDigits) {
        return new String(encodeHex(data, toDigits));
    }

    /**
     * 将十六进制字符数组转换为字节数组
     *
     * @param data 十六进制char[]
     * @return byte[]
     * @throws RuntimeException 如果源十六进制字符数组的长度是奇数，将抛出运行时异常
     */
    public static byte[] decodeHex(char[] data) {
        int len = data.length;

        if ((len & 0x01) != 0) {
            throw new RuntimeException("Odd number of characters.");
        }

        // 一个byte对应两个十六进制字符，则将byte[]大小设置为char[]大小的一半
        byte[] out = new byte[len >> 1];

        // two characters form the hex value.
        for (int i = 0, j = 0; j < len; i++) {
            int f = toDigit(data[j], j) << 4;
            j++;
            f = f | toDigit(data[j], j);
            j++;
            out[i] = (byte) (f & 0xFF);
        }

        return out;
    }


    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        char[] hexChars = hexString.toCharArray();
        return decodeHex(hexChars);
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    /**
     * 将十六进制字符转换成一个整数。
     *
     * @param ch    要转换成整数的字符
     * @param index 字符在字符数组中的位置
     * @return 一个整数
     * @throws RuntimeException 当ch不是一个合法的十六进制字符时，抛出该异常
     */
    protected static int toDigit(final char ch, final int index) {
        final int digit = Character.digit(ch, 16);
        if (digit == -1) {
            throw new RuntimeException("Illegal hexadecimal character " + ch + " at index " + index);
        }
        return digit;
    }


    public static void main(String[] args) {
        // CodecUtil.encodeHexStr(value.getBytes()).getBytes()
        System.out.println(CodecUtil.encodeHexStr("$@".getBytes()));
        byte[] bytes = CodecUtil.encodeHexStr("$@".getBytes()).getBytes();
        System.out.println(new String(bytes));
    }

}
