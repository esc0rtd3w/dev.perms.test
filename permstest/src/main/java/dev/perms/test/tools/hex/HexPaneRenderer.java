package dev.perms.test.tools.hex;

import java.util.Locale;

/** Renders offset, hex, and ASCII panes from the same byte window. */
public final class HexPaneRenderer {
    public static final int BYTES_PER_ROW = 16;

    public static final class RenderedPanes {
        public final String offsets;
        public final String hex;
        public final String ascii;

        RenderedPanes(String offsets, String hex, String ascii) {
            this.offsets = offsets;
            this.hex = hex;
            this.ascii = ascii;
        }
    }

    private HexPaneRenderer() {
    }

    public static RenderedPanes render(long baseOffset, byte[] bytes, long selectedOffset, int selectedLength) {
        StringBuilder offsets = new StringBuilder();
        StringBuilder hex = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        byte[] data = bytes == null ? new byte[0] : bytes;
        if (data.length == 0) {
            return new RenderedPanes("Offset\n", "Hex\n", "ASCII\n");
        }
        offsets.append("Offset\n");
        hex.append("00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F\n");
        ascii.append("ASCII\n");
        for (int row = 0; row < data.length; row += BYTES_PER_ROW) {
            long rowOffset = baseOffset + row;
            offsets.append(formatOffset(rowOffset)).append('\n');
            for (int i = 0; i < BYTES_PER_ROW; i++) {
                int index = row + i;
                if (index < data.length) {
                    long absolute = baseOffset + index;
                    String cell = String.format(Locale.US, "%02X", data[index] & 0xff);
                    if (isSelected(absolute, selectedOffset, selectedLength)) {
                        hex.append('[').append(cell).append(']');
                    } else {
                        hex.append(' ').append(cell).append(' ');
                    }
                } else {
                    hex.append("   ");
                }
                if (i + 1 < BYTES_PER_ROW) hex.append(' ');
            }
            hex.append('\n');
            for (int i = 0; i < BYTES_PER_ROW; i++) {
                int index = row + i;
                if (index < data.length) {
                    long absolute = baseOffset + index;
                    char c = printable(data[index] & 0xff);
                    if (isSelected(absolute, selectedOffset, selectedLength)) {
                        ascii.append('[').append(c).append(']');
                    } else {
                        ascii.append(' ').append(c).append(' ');
                    }
                } else {
                    ascii.append("   ");
                }
            }
            ascii.append('\n');
        }
        return new RenderedPanes(offsets.toString(), hex.toString(), ascii.toString());
    }

    public static String formatOffset(long value) {
        return String.format(Locale.US, "0x%08X", value);
    }

    private static boolean isSelected(long absolute, long selectedOffset, int selectedLength) {
        if (selectedOffset < 0L || selectedLength <= 0) return false;
        return absolute >= selectedOffset && absolute < selectedOffset + selectedLength;
    }

    private static char printable(int b) {
        return b >= 32 && b <= 126 ? (char) b : '.';
    }
}
