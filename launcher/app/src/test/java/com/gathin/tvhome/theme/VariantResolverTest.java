package com.gathin.tvhome.theme;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * The rule that decides whether the launcher follows the TV's day/night setting or holds the variant
 * the user actually chose. Getting this wrong is subtle and annoying — a home screen that ignores
 * the system theme, or one that re-themes itself at sunset — so it is pinned here rather than left
 * to {@link ThemeStore} plumbing.
 */
public class VariantResolverTest {

    @Test
    public void systemFollowsTheTv() {
        assertEquals(VariantResolver.DARK, VariantResolver.resolve(true, null, true));
        assertEquals(VariantResolver.LIGHT, VariantResolver.resolve(true, null, false));
    }

    @Test
    public void systemIgnoresAPinnedVariant() {
        assertEquals(VariantResolver.DARK, VariantResolver.resolve(true, VariantResolver.DARK, true));
        assertEquals(VariantResolver.LIGHT, VariantResolver.resolve(true, VariantResolver.LIGHT, false));
    }

    @Test
    public void anExplicitChoiceIsPinned() {
        // Chosen while the TV was dark; the system then flips to light and the launcher holds.
        assertEquals(VariantResolver.DARK, VariantResolver.resolve(false, VariantResolver.DARK, false));
        assertEquals(VariantResolver.LIGHT, VariantResolver.resolve(false, VariantResolver.LIGHT, true));
    }

    @Test
    public void anUnusablePinFallsBackToTheSystem() {
        assertEquals(VariantResolver.LIGHT, VariantResolver.resolve(false, null, false));
        assertEquals(VariantResolver.DARK, VariantResolver.resolve(false, null, true));
        assertEquals(VariantResolver.DARK, VariantResolver.resolve(false, "", true));
        assertEquals(VariantResolver.LIGHT, VariantResolver.resolve(false, "DARK", false));
        assertEquals(VariantResolver.LIGHT, VariantResolver.resolve(false, "sepia", false));
    }

    @Test
    public void aThemeCanAnswerForEitherVariant() {
        String xml = "<theme id=\"x\">"
                + "<dark><color name=\"background\">#FF000000</color></dark>"
                + "<light><color name=\"background\">#FFFFFFFF</color></light>"
                + "</theme>";
        Theme t = ThemeParser.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "x");
        assertEquals(0xFF000000, t.forVariant(VariantResolver.DARK).background);
        assertEquals(0xFFFFFFFF, t.forVariant(VariantResolver.LIGHT).background);
        assertEquals("an unknown variant must not crash the launcher",
                0xFF000000, t.forVariant("sepia").background);
    }
}
