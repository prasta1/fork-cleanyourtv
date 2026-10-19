package com.gathin.tvhome.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Every file shipped in assets/themes/ must parse cleanly and actually be a theme. */
public class AllThemesParseTest {

    private static final File DIR = new File("src/main/assets/themes");

    @Test
    public void everyShippedThemeParsesCleanly() throws Exception {
        File[] files = DIR.listFiles((d, name) -> name.endsWith(".xml"));
        assertNotNull(DIR + " is missing", files);
        assertTrue("expected the five shipped themes, found " + files.length, files.length >= 5);

        for (File f : files) {
            List<String> warnings = new ArrayList<>();
            Theme t;
            try (InputStream in = new FileInputStream(f)) {
                t = ThemeParser.parse(in, f.getName(), warnings::add);
            }
            assertNotNull(f.getName() + " did not parse", t);
            assertFalse(f.getName() + " has a blank label", t.label.trim().isEmpty());
            assertTrue(f.getName() + " warnings: " + warnings, warnings.isEmpty());

            // A variant with no block silently inherits its sibling, so check the source text:
            // every shipped theme must spell out both.
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            assertTrue(f.getName() + " has no <dark> block", text.contains("<dark>"));
            assertTrue(f.getName() + " has no <light> block", text.contains("<light>"));

            // A theme that overrides nothing at all is a bug. tvos-black's dark variant is the
            // defaults on purpose, so only require that the theme as a whole differs.
            assertTrue(f.getName() + " overrides nothing", differs(t.dark) || differs(t.light));
        }
    }

    private static boolean differs(Tokens t) {
        try {
            TokensTest.assertTokensEqual("", Defaults.tokens(), t);
            return false;
        } catch (AssertionError e) {
            return true;
        }
    }
}
