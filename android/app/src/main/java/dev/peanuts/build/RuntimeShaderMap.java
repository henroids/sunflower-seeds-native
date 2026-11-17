package dev.peanuts.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads the shipped profile without starting the packer's native engine in the launcher process. */
final class RuntimeShaderMap {
    private RuntimeShaderMap() {}

    static boolean compilesAtRunTime(InputStream tools, String titleId) throws IOException {
        return !runtimeKeys(tools, titleId).isEmpty();
    }

    private static Set<Long> runtimeKeys(InputStream tools, String titleId) throws IOException {
        Set<Long> keys = new HashSet<>();
        String name = "dm/games/" + titleId + "/shader_map.tsv";
        try (ZipInputStream zip = new ZipInputStream(tools)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (!name.equals(e.getName())) continue;
                BufferedReader r = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.startsWith("#")) continue;
                    String[] cols = line.split("\t");
                    if (cols.length >= 5 && cols[4].startsWith("/glslc_capture/")) {
                        try { keys.add(Long.parseUnsignedLong(cols[3], 16)); }
                        catch (NumberFormatException ex) { throw new IOException("Invalid run-time shader key in " + name, ex); }
                    }
                }
                return keys;
            }
        }
        throw new IOException("The app's shader profile is missing: " + titleId + ". Reinstall the app and tap Build again.");
    }

    /** Check the pack itself: an adopted job may have lost its shaders_missing count (issue #81). Reads only the library's entry table. */
    static int missing(InputStream tools, String titleId, File library) throws IOException {
        Set<Long> keys = runtimeKeys(tools, titleId);
        if (keys.isEmpty()) return 0;
        // dmsh_abi_vk.h: 128-byte header; entry_count/off at 72/76; 64-byte entries, key at 0 and module count at 17.
        try (RandomAccessFile f = new RandomAccessFile(library, "r")) {
            if (f.length() < 128 || Integer.reverseBytes(f.readInt()) != 0x4B56534D || Integer.reverseBytes(f.readInt()) != 1)
                throw new IOException("Cannot read the run-time shader library: " + library.getName());
            byte[] game = new byte[16]; f.seek(40); f.readFully(game);
            if (!titleId.equalsIgnoreCase(new String(game, StandardCharsets.US_ASCII)))
                throw new IOException("The shader library belongs to another game.");
            f.seek(72);
            long count = Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()));
            long offset = Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()));
            if (offset < 128 || offset > f.length() || count > (f.length() - offset) / 64)
                throw new IOException("The shader library's entry table is incomplete.");
            for (long i = 0; i < count && !keys.isEmpty(); i++) {
                f.seek(offset + i * 64);
                long key = Long.reverseBytes(f.readLong());
                f.seek(offset + i * 64 + 17);
                if (f.readUnsignedByte() != 0) keys.remove(key);
            }
        }
        return keys.size();
    }
}
