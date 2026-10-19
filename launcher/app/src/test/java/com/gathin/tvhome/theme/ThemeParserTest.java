package com.gathin.tvhome.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ThemeParserTest {

    private final List<String> warnings = new ArrayList<>();

    private Theme parse(String xml) {
        warnings.clear();
        Theme t = ThemeParser.parse(stream(xml), "fallback-id", warnings::add);
        return t;
    }

    private static ByteArrayInputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static String theme(String body) {
        return "<theme id=\"test\" label=\"Test Theme\">" + body + "</theme>";
    }

    // ---- identity --------------------------------------------------------------------------------

    @Test
    public void readsIdAndLabel() {
        Theme t = parse("<theme id=\"ember\" label=\"Ember\"><dark/></theme>");
        assertEquals("ember", t.id);
        assertEquals("Ember", t.label);
    }

    @Test
    public void missingLabelFallsBackToId() {
        assertEquals("ember", parse("<theme id=\"ember\"><dark/></theme>").label);
    }

    @Test
    public void missingIdFallsBackToTheFileName() {
        assertEquals("fallback-id", parse("<theme label=\"D\"><dark/></theme>").id);
    }

    // ---- resolution over the defaults ------------------------------------------------------------

    @Test
    public void unspecifiedTokensInheritTheDefaults() {
        Theme t = parse(theme("<dark><color name=\"background\">#FF123456</color></dark>"));
        assertEquals(0xFF123456, t.dark.background);
        // Everything else must still be what the launcher ships with.
        TokensTest.assertTokensEqual("a token did not inherit its default",
                withBackground(0xFF123456), t.dark);
    }

    @Test
    public void aVariantWithNoBlockInheritsItsSibling() {
        Theme t = parse(theme("<dark><color name=\"tileSurface\">#FFAABBCC</color></dark>"));
        assertEquals(0xFFAABBCC, t.dark.tileSurface);
        assertEquals(0xFFAABBCC, t.light.tileSurface);
        assertEquals("no warning is owed for an intentionally partial theme", 0, warnings.size());
    }

    @Test
    public void aThemeWithNoVariantsIsAllDefaults() {
        TokensTest.assertTokensEqual("empty theme", Defaults.tokens(), parse(theme("")).dark);
        TokensTest.assertTokensEqual("empty theme", Defaults.tokens(), parse(theme("")).light);
    }

    // ---- each element type -----------------------------------------------------------------------

    @Test
    public void readsEveryElementType() {
        Theme t = parse(theme("<dark>"
                + "<color name=\"background\">#FF010203</color>"
                + "<dimen name=\"tileRadius\">4dp</dimen>"
                + "<number name=\"focusScale\">1.25</number>"
                + "<integer name=\"focusInMs\">99</integer>"
                + "<text name=\"clock\" size=\"40sp\" weight=\"200\" tracking=\"-0.1\"/>"
                + "</dark>"));
        assertEquals(0xFF010203, t.dark.background);
        assertEquals(4f, t.dark.tileRadius, 0.0001f);
        assertEquals(1.25f, t.dark.focusScale, 0.0001f);
        assertEquals(99, t.dark.focusInMs);
        assertEquals(40f, t.dark.clock.sizeSp, 0.0001f);
        assertEquals(200, t.dark.clock.weight);
        assertEquals(-0.1f, t.dark.clock.tracking, 0.0001f);
    }

    @Test
    public void spAndDpAreStoredAsBareNumbers() {
        // Density is applied later, in Skin, so parsing must not bake it in.
        assertEquals(16f, ThemeParser.parseDimen("16dp"), 0.0001f);
        assertEquals(16f, ThemeParser.parseDimen("16sp"), 0.0001f);
        assertNull("px cannot be honoured by a density-independent token",
                ThemeParser.parseDimen("16px"));
        assertNull("a bare number is ambiguous", ThemeParser.parseDimen("16"));
    }

    @Test
    public void textTrackingDefaultsToZero() {
        Theme t = parse(theme("<dark><text name=\"hero\" size=\"48sp\" weight=\"300\"/></dark>"));
        assertEquals(0f, t.dark.hero.tracking, 0.0001f);
    }

    // ---- colour formats --------------------------------------------------------------------------

    @Test
    public void readsEveryColourFormat() {
        assertEquals(Integer.valueOf(0xFFAABBCC), ThemeParser.parseColor("#AABBCC"));
        // #RGB follows the CSS rule: the red digit supplies the alpha as well as the red channel.
        assertEquals(Integer.valueOf(0xAAAABBCC), ThemeParser.parseColor("#ABC"));
        assertEquals(Integer.valueOf(0x88AABBCC), ThemeParser.parseColor("#8ABC"));
        assertEquals(Integer.valueOf(0x11223344), ThemeParser.parseColor("#11223344"));
        assertEquals(Integer.valueOf(0xFF000000), ThemeParser.parseColor("  #000000  "));
    }

    @Test
    public void threeDigitBlackIsTransparentBecauseItsAlphaDigitIsZero() {
        // CSS and android.graphics.Color.parseColor agree here: #000 has alpha 0, not opaque black.
        assertEquals(Integer.valueOf(0x00000000), ThemeParser.parseColor("#000"));
    }

    @Test
    public void sixDigitColoursAreOpaque() {
        // #RRGGBB has no alpha byte. Reading it as transparent would render an invisible launcher,
        // which is the last thing a hand-written theme should be able to do.
        assertEquals(Integer.valueOf(0xFF123456), ThemeParser.parseColor("#123456"));
        Theme t = parse(theme("<dark><color name=\"background\">#123456</color></dark>"));
        assertEquals(0xFF123456, t.dark.background);
    }

    @Test
    public void rejectsUnusableColours() {
        assertNull(ThemeParser.parseColor("blue"));           // names are not supported
        assertNull(ThemeParser.parseColor("#GGGGGG"));
        assertNull(ThemeParser.parseColor("#FFFFF"));         // five digits is nothing
        assertNull(ThemeParser.parseColor("#FFFFFFFFFF"));    // ten digits is nothing
        assertNull(ThemeParser.parseColor(""));
        assertNull(ThemeParser.parseColor(null));
    }

    // ---- resilience ------------------------------------------------------------------------------

    @Test
    public void oneBadTokenStillLoadsTheRest() {
        Theme t = parse(theme("<dark>"
                + "<color name=\"background\">#FF000000</color>"
                + "<color name=\"tileSurface\">not-a-colour</color>"
                + "<color name=\"dockBand\">#FF102030</color>"
                + "</dark>"));
        assertNotNull("a bad colour must not lose the file", t);
        assertEquals(0xFF000000, t.dark.background);
        assertEquals(0xFF102030, t.dark.dockBand);
        assertEquals("the bad token keeps its default", 0xFF1C1C1E, t.dark.tileSurface);
        assertEquals("exactly one warning, naming the token", 1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("tileSurface"));
    }

    @Test
    public void aTokenOfTheWrongTypeIsRefused() {
        // focusScale is a float and textClock is a colour, so neither element applies.
        Theme t = parse(theme("<dark><integer name=\"focusScale\">7</integer>"
                + "<number name=\"textClock\">1.5</number></dark>"));
        assertEquals(1.10f, t.dark.focusScale, 0.0001f);
        assertEquals(0xFFFFFFFF, t.dark.textClock);
        assertEquals(2, warnings.size());
    }

    @Test
    public void anElementMatchingTheTokenTypeIsAccepted() {
        // The counterpart to the test above: an integer into an int token must not be refused.
        Theme t = parse(theme("<dark><integer name=\"focusInMs\">7</integer></dark>"));
        assertEquals(7, t.dark.focusInMs);
        assertEquals(0, warnings.size());
    }

    @Test
    public void unknownTokensAreIgnoredSoNewerFilesStillLoad() {
        Theme t = parse(theme("<dark>"
                + "<color name=\"background\">#FF000000</color>"
                + "<color name=\"telemetryColour\">#FF00FF00</color>"
                + "</dark>"));
        assertNotNull(t);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("telemetryColour"));
    }

    @Test
    public void aGarbageFileIsSkippedRatherThanHalfLoaded() {
        assertNull("truncated mid-token", parse("<theme><dark><color name=\"background\">#FF000000"));
        assertNull("truncated mid-attribute", parse("<theme id=\"ember\""));
        assertNull("truncated before the root closed", parse("<theme id=\"ember\"><dark>"));
    }

    @Test
    public void aFileWithoutAThemeElementIsSkipped() {
        // Otherwise these would appear in the picker under their own filename.
        assertNull(parse(""));
        assertNull(parse("this is not xml at all"));
        assertNull(parse("<themes><dark/></themes>"));
    }

    @Test
    public void aThemeWithNoVariantsIsStillAValidTheme() {
        assertNotNull(parse("<theme id=\"ember\"/>"));
    }

    @Test
    public void aTruncatedFileNeverHangs() {
        // Regression: an event that is neither a start nor an end tag used to spin here forever,
        // which is how a half-written theme file locked up the launcher.
        assertNull(parse("<theme><dark><color name=\"background\">#FF000000"));
        assertNull(parse("<theme><dark><dimen name=\"tileRadius\">16dp"));
        assertNull(parse("<theme><light><text name=\"clock\" size=\"52sp\""));
    }

    @Test
    public void everyBrokenValueWarnsExactlyOnce() {
        parse(theme("<dark>"
                + "<color name=\"background\">#ZZZ</color>"
                + "<integer name=\"focusInMs\">soon</integer>"
                + "<dimen name=\"tileRadius\">16</dimen>"
                + "<text name=\"clock\" size=\"big\"/>"
                + "</dark>"));
        assertEquals(4, warnings.size());
    }

    private static Tokens withBackground(int colour) {
        Tokens t = Defaults.tokens();
        t.background = colour;
        return t;
    }
}
