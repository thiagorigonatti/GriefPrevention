package me.thiagorigonatti.griefprevention.util;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

public final class MessageLocalization {

    private static final String DEFAULT_LOCALE = "en";

    private static final Pattern BUNDLED_MESSAGE_FILE_PATTERN =
            Pattern.compile("messages_[A-Za-z]+(?:_[A-Za-z]+)*\\.properties");

    // Prevents this utility class from being instantiated.
    private MessageLocalization() {
        throw new AssertionError("Instantiation of an utility class.");
    }

    // Finds all bundled message files inside the plugin JAR.
    private static @NotNull List<String> getBundledMessageFiles() {
        try {
            Path jarPath = Path.of(GriefPrevention.class.getProtectionDomain().getCodeSource().getLocation().toURI());

            try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                return jarFile.stream().filter(entry -> !entry.isDirectory()).map(ZipEntry::getName)
                        .filter(MessageLocalization::isBundledMessageFile).sorted().toList();
            }
        } catch (IOException | URISyntaxException e) {
            GriefPrevention.instance.getLogger().severe("""
                    Unable to scan bundled message files from jar.
                    %s
                    """.formatted(e.getMessage()));
        }

        return List.of();
    }

    // Checks whether a file is a bundled message file.
    private static boolean isBundledMessageFile(@NotNull String name) {
        return name.equals("messages.properties") || BUNDLED_MESSAGE_FILE_PATTERN.matcher(name).matches();
    }

    // Converts a locale to the format used by the message file names.
    private static @NotNull String normalizeLocale(@Nullable String locale) {
        if (locale == null || locale.isBlank()) return DEFAULT_LOCALE;

        return locale.trim().replace('-', '_');
    }

    // Creates the language folder and copies missing bundled message files.
    private static void ensureLanguageFiles() {
        Path languageFolder = Path.of(DataStore.languageFolderPath);

        if (Files.notExists(languageFolder)) {
            try {
                Files.createDirectories(languageFolder);
            } catch (IOException e) {
                GriefPrevention.instance.getLogger().severe("""
                        "Unable to create language folder at "%s"
                        %s
                        """.formatted(languageFolder, e.getMessage()));
                return;
            }
        }

        for (String fileName : getBundledMessageFiles()) {
            copyBundledLanguageFileIfMissing(fileName, languageFolder.resolve(fileName));
        }
    }

    // Loads the configured locale and applies its values to all messages.
    public static @NotNull String applyConfiguredLocaleToMessages(@Nullable String locale, @NotNull Messages[] messages) {
        ensureLanguageFiles();

        String normalizedLocale = normalizeLocale(locale);
        Path messageFile = getExternalMessageFile(normalizedLocale);

        if (Files.notExists(messageFile)) {
            GriefPrevention.instance.getLogger()
                    .warning("""
                            Language file "%s" was not found. Falling back to "%s".
                            """.formatted(messageFile.getFileName(), DEFAULT_LOCALE));

            normalizedLocale = DEFAULT_LOCALE;
            messageFile = getExternalMessageFile(normalizedLocale);
        }

        Properties activeMessages = loadProperties(messageFile);

        Properties fallbackMessages = normalizedLocale.equals(DEFAULT_LOCALE)
                ? activeMessages
                : loadProperties(getExternalMessageFile(DEFAULT_LOCALE));

        for (Messages message : messages) {
            String messageValue = activeMessages.getProperty(message.name());

            if (messageValue == null)
                messageValue = fallbackMessages.getProperty(message.name());

            message.setDefaultValue(messageValue);
        }

        return normalizedLocale;
    }

    // Returns the path of the external file for the given locale.
    private static @NotNull Path getExternalMessageFile(@NotNull String locale) {
        return Path.of(DataStore.languageFolderPath, "messages_" + normalizeLocale(locale) + ".properties");
    }

    // Copies a bundled message file when it does not exist externally.
    private static void copyBundledLanguageFileIfMissing(@NotNull String resourceName, @NotNull Path targetFile) {
        if (Files.exists(targetFile)) return;

        try (InputStream inputStream = GriefPrevention.class.getClassLoader().getResourceAsStream(resourceName)) {

            if (inputStream == null) {
                GriefPrevention.instance.getLogger()
                        .severe("Unable to find bundled language file \"" + resourceName + "\" inside the plugin JAR.");
                return;
            }

            Path parentFile = targetFile.getParent();

            if (parentFile != null && Files.notExists(parentFile)) {
                try {
                    Files.createDirectories(parentFile);
                } catch (IOException e) {
                    GriefPrevention.instance.getLogger().severe("""
                            Unable to create language folder at "%s"
                            %s
                            """.formatted(parentFile, e.getMessage()));
                    return;
                }
            }

            Files.copy(inputStream, targetFile);
        } catch (IOException e) {
            GriefPrevention.instance.getLogger().severe("""
                    Unable to copy bundled language file to "%s"
                    %s
                    """.formatted(targetFile, e.getMessage()));
        }
    }

    // Loads a UTF-8 properties file into memory.
    private static @NotNull Properties loadProperties(@NotNull Path file) {
        Properties properties = new Properties();

        if (Files.notExists(file)) return properties;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {

            properties.load(reader);
        } catch (IOException e) {
            GriefPrevention.instance.getLogger().severe("""
                    Unable to read language file at "%s"
                    %s
                    """.formatted(file, e.getMessage()));
        }

        return properties;
    }
}
