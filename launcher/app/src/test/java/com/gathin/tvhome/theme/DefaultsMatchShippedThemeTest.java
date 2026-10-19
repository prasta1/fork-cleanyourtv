package com.gathin.tvhome.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Defaults} and {@code assets/themes/tvos-black.xml} are two copies of the same look. Nothing
 * at runtime notices if they drift, so this does.
 */
public class DefaultsMatchShippedThemeTest {

    @Test
    public void tvosBlackDarkEqualsDefaults() throws Exception {
        List<String> warnings = new ArrayList<>();
        Theme t;
        try (InputStream in = new FileInputStream(new File("src/main/assets/themes/tvos-black.xml"))) {
            t = ThemeParser.parse(in, "tvos-black", warnings::add);
        }
        assertNotNull("tvos-black.xml did not parse", t);
        assertEquals(ThemeCatalog.DEFAULT_ID, t.id);
        assertTrue("tvos-black.xml produced warnings: " + warnings, warnings.isEmpty());
        TokensTest.assertTokensEqual("tvos-black dark drifted from Defaults.tokens()", Defaults.tokens(), t.dark);
    }
}
