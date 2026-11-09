package be.maartenvandeperre.aidemo;

import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * On Windows, {@code npx} is a batch file ({@code npx.cmd}) and Java's
 * ProcessBuilder cannot start a batch file by its short name. The MCP
 * client then fails to launch the filesystem server, the tool provider
 * silently skips it, and the model answers without any tools ("I don't
 * have access to your files").
 *
 * This source sets {@code cellar.npx=npx.cmd} on Windows only. Its ordinal
 * (275) sits above application.properties (250) and below environment
 * variables (300), so {@code CELLAR_NPX} still overrides it.
 */
public class WindowsNpxConfigSource implements ConfigSource {

    private static final String KEY = "cellar.npx";
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().startsWith("windows");

    @Override
    public Map<String, String> getProperties() {
        return WINDOWS ? Map.of(KEY, "npx.cmd") : Map.of();
    }

    @Override
    public Set<String> getPropertyNames() {
        return getProperties().keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return getProperties().get(propertyName);
    }

    @Override
    public String getName() {
        return "windows-npx";
    }

    @Override
    public int getOrdinal() {
        return 275;
    }
}
