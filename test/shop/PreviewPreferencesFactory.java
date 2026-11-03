package shop;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** Preview-only settings; never reads or writes the user's preference store. */
public final class PreviewPreferencesFactory implements PreferencesFactory {
    private final Preferences user = new MemoryNode(null, "");
    private final Preferences system = new MemoryNode(null, "");

    @Override public Preferences userRoot() { return user; }
    @Override public Preferences systemRoot() { return system; }

    private static final class MemoryNode extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<String, String>();
        private final Map<String, MemoryNode> children = new HashMap<String, MemoryNode>();

        MemoryNode(MemoryNode parent, String name) { super(parent, name); }
        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
        @Override protected String[] childrenNamesSpi() { return children.keySet().toArray(new String[0]); }
        @Override protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, key -> new MemoryNode(this, key));
        }
        @Override protected void removeNodeSpi() {
            if (parent() != null) ((MemoryNode) parent()).children.remove(name());
            values.clear();
            children.clear();
        }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() { }
    }
}
