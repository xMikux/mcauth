/*
 * Copyright 2021-2026 MinecraftAuth.me
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.minecraftauth.test.runner;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {

    /** Locate the plugin JAR in the build/libs directory of a submodule. */
    public static Path getPluginJar(String modulePath, String artifactNamePrefix) throws IOException {
        Path libsDir = Paths.get(System.getProperty("rootDir", "."), modulePath, "build", "libs");
        if (!Files.exists(libsDir)) throw new IOException("Libs directory not found: " + libsDir);

        return Files.list(libsDir)
                .filter(p -> p.getFileName().toString().startsWith(artifactNamePrefix))
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .filter(p -> !p.getFileName().toString().contains("-sources"))
                .filter(p -> !p.getFileName().toString().contains("-javadoc"))
                .filter(p -> !p.getFileName().toString().contains("-dev")) // skip fabric dev jars
                .findFirst()
                .orElseThrow(() -> new IOException("No JAR found starting with " + artifactNamePrefix + " in " + libsDir));
    }

    /** Downloads a file from a URL, following redirects. */
    public static void downloadFile(String urlString, Path target) throws IOException {
        System.out.println("Downloading " + urlString + " to " + target);
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "MinecraftAuth-TestRunner/1.0");
        conn.setInstanceFollowRedirects(true);

        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream out = new FileOutputStream(target.toFile())) {
            in.transferTo(out);
        }
    }

    /** Downloads the latest build of a project from PaperMC v3 API. */
    public static void downloadLatestPaperMC(String project, String version, Path target) throws IOException {
        String latestBuildUrl = String.format("https://fill.papermc.io/v3/projects/%s/versions/%s/builds/latest", project, version);
        URL url = URI.create(latestBuildUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "MinecraftAuth-TestRunner/1.0");

        String jsonContent;
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
            jsonContent = scanner.useDelimiter("\\A").next();
        }

        Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonContent);
        if (m.find()) {
            downloadFile(m.group(1), target);
        } else {
            throw new IOException("Could not find download URL in PaperMC v3 API response for " + project + " " + version);
        }
    }

    /** Downloads the latest stable Fabric Installer. */
    public static void downloadLatestFabricInstaller(Path target) throws IOException {
        String version = fetchLatestFabricVersion("installer");
        String url = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/" + version + "/fabric-installer-" + version + ".jar";
        downloadFile(url, target);
    }

    /** Fetches the latest stable Fabric Loader version string. */
    public static String fetchLatestFabricLoaderVersion() throws IOException {
        return fetchLatestFabricVersion("loader");
    }

    /** Downloads the latest Fabric API jar for a given Minecraft version from Modrinth. */
    public static void downloadLatestFabricAPI(String mcVersion, Path target) throws IOException {
        // %5B"mcVersion"%5D → ["mcVersion"] URL-encoded
        String apiUrl = "https://api.modrinth.com/v2/project/fabric-api/version"
                + "?game_versions=%5B%22" + mcVersion + "%22%5D"
                + "&loaders=%5B%22fabric%22%5D";
        URL url = URI.create(apiUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "MinecraftAuth-TestRunner/1.0");

        String jsonContent;
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
            jsonContent = scanner.useDelimiter("\\A").next();
        }

        // Take the first primary file URL from the first version entry
        Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+\\.jar)\"").matcher(jsonContent);
        if (m.find()) {
            downloadFile(m.group(1), target);
        } else {
            throw new IOException("Could not find Fabric API download URL for MC " + mcVersion + " on Modrinth");
        }
    }

    private static String fetchLatestFabricVersion(String endpoint) throws IOException {
        URL url = URI.create("https://meta.fabricmc.net/v2/versions/" + endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "MinecraftAuth-TestRunner/1.0");

        String jsonContent;
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
            jsonContent = scanner.useDelimiter("\\A").next();
        }

        Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"[^}]*\"stable\"\\s*:\\s*true").matcher(jsonContent);
        if (m.find()) {
            return m.group(1);
        }
        throw new IOException("Could not find latest stable Fabric " + endpoint + " version");
    }

    /** Downloads the latest stable NeoForge Installer for a specific Minecraft version. */
    public static String downloadLatestNeoForgeInstaller(String mcVersion, Path target) throws IOException {
        // NeoForge versioning: 1.21.1 -> prefix 21.1.
        String prefix = mcVersion.startsWith("1.") ? mcVersion.substring(2) + "." : mcVersion + ".";
        
        URL url = URI.create("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "MinecraftAuth-TestRunner/1.0");

        String xmlContent;
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
            xmlContent = scanner.useDelimiter("\\A").next();
        }

        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(xmlContent);
        String latest = null;
        while (m.find()) {
            String v = m.group(1);
            if (v.startsWith(prefix)) {
                latest = v;
            }
        }
        
        if (latest != null) {
            String downloadUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + latest + "/neoforge-" + latest + "-installer.jar";
            downloadFile(downloadUrl, target);
            return latest;
        }
        throw new IOException("Could not find latest NeoForge version for Minecraft " + mcVersion);
    }

    /** Setup a temporary server directory. */
    public static Path setupServerDir(String prefix) throws IOException {
        Path dir = Files.createTempDirectory("mcauth-test-" + prefix + "-");
        System.out.println("Setup server directory: " + dir);
        Files.writeString(dir.resolve("eula.txt"), "eula=true\n");
        return dir;
    }

    /** Poll a port until it is open or timeout expires. */
    public static boolean waitForPort(String host, int port, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1000);
                return true;
            } catch (IOException e) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }

    /** Deterministically compute an offline-mode UUID from a username. */
    public static java.util.UUID computeOfflineUUID(String username) {
        return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    /** Write a plugin configuration file, creating parent directories as needed. */
    public static void writePluginConfig(Path configFile, String content) throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, content);
    }

    /** Find an available ephemeral port. */
    public static int findAvailablePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
