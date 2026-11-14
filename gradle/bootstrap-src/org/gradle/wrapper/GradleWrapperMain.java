package org.gradle.wrapper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Auditable standalone wrapper bootstrap for offline-generated project.
 * Uses Gradle's published distribution and checks its official SHA-256.
 * Replace with the official Gradle wrapper when Gradle is available. */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        Path project = Paths.get(System.getProperty("user.dir"));
        Path propsFile = project.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(propsFile)) { properties.load(in); }
        String url = Objects.requireNonNull(properties.getProperty("distributionUrl"));
        String expected = Objects.requireNonNull(properties.getProperty("distributionSha256Sum"));
        if (!url.equals("https://services.gradle.org/distributions/gradle-8.9-bin.zip") ||
            !expected.equals("d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"))
            throw new SecurityException("Unexpected Gradle binary URL or checksum");
        Path home = Paths.get(System.getProperty("user.home"), ".gradle", "wrapper", "dists", "pm-gradle-8.9");
        Path executable = home.resolve("gradle-8.9/bin/gradle");
        if (!Files.isRegularFile(executable)) {
            Files.createDirectories(home);
            Path zip = home.resolve("gradle-8.9-bin.zip");
            if (!Files.exists(zip)) {
                URLConnection conn = URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(20000); conn.setReadTimeout(120000);
                Path tmp = home.resolve("gradle-downloading.zip");
                try (InputStream in = conn.getInputStream()) { Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); }
                Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(zip)) {
                byte[] bytes = new byte[32768]; int n;
                while ((n = input.read(bytes)) != -1) digest.update(bytes, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b & 0xff));
            if (!hex.toString().equals(expected)) { Files.deleteIfExists(zip); throw new SecurityException("Gradle SHA-256 mismatch"); }
            try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    Path path = home.resolve(entry.getName()).normalize();
                    if (!path.startsWith(home)) throw new SecurityException("Gradle zip path escapes download directory");
                    if (entry.isDirectory()) Files.createDirectories(path);
                    else {
                        Files.createDirectories(path.getParent());
                        Files.copy(zin, path, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zin.closeEntry();
                }
            }
            if (!Files.exists(executable)) throw new IOException("Gradle executable missing after extraction");
            executable.toFile().setExecutable(true);
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) executable = executable.resolveSibling("gradle.bat");
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(executable.toString()); Collections.addAll(cmd, args);
        ProcessBuilder proc = new ProcessBuilder(cmd).directory(project.toFile()).inheritIO();
        System.exit(proc.start().waitFor());
    }
}
