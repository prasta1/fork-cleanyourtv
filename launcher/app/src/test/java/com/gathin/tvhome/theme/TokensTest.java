package com.gathin.tvhome.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Field;

/**
 * {@link Tokens} is a wide, hand-maintained struct, so these tests are deliberately generic: they
 * walk it with reflection rather than asserting one field at a time. A token added without a
 * matching {@code copy()} line fails here instead of quietly resetting that colour on half the
 * themes.
 */
public class TokensTest {

    /** Every token, across colour, ambient, shape, motion and typography. */
    private static final int TOKEN_COUNT = 73;

    @Test
    public void tokenCountMatchesTheDocumentedSet() {
        assertEquals("a token was added or removed; update the spec, docs/themes.md and this count",
                TOKEN_COUNT, fieldCount());
    }

    @Test
    public void everyTokenHasAValue() {
        for (Field f : tokensFields()) {
            Tokens t = Defaults.tokens();
            Object v = read(f, t);
            if (f.getType() == Tokens.TypeSpec.class) {
                assertTrue(f.getName() + " is null", v != null);
            } else {
                // Lengths, weights and a tracking of 0 are legitimately zero, so this only asserts
                // the default set is actually populated rather than left as a zeroed struct.
                assertTrue(f.getName() + " is 0", ((Number) v).floatValue() != 0f || f.getName().endsWith("tracking"));
            }
        }
    }

    @Test
    public void defaultsMatchTheLookTheLauncherShipsWith() {
        Tokens t = Defaults.tokens();
        assertEquals(0xFF000000, t.background);
        assertEquals(0xFF1C1C1E, t.tileSurface);
        assertEquals(0xFF141416, t.dockBand);
        assertEquals(0xFFFFFFFF, t.textPill);
        assertEquals(0xFF111111, t.textPillFocus);
        assertEquals(0xCC000000, t.pickerScrim);
        assertEquals(16f, t.tileRadius, 0.0001f);
        assertEquals(1.10f, t.focusScale, 0.0001f);
        assertEquals(52f, t.clock.sizeSp, 0.0001f);
        assertEquals(300, t.clock.weight);
    }

    @Test
    public void twoCallsReturnIndependentSets() {
        Tokens a = Defaults.tokens();
        Tokens b = Defaults.tokens();
        assertNotSame(a, b);
        assertTokensEqual("Defaults.tokens() is not deterministic", a, b);
    }

    /** copy() must carry every token, not just the ones whose values happen to be equal. */
    @Test
    public void copyCarriesEveryToken() throws Exception {
        for (Field f : tokensFields()) {
            Tokens source = new Tokens();
            Object sentinel = sentinelFor(f);
            f.set(source, sentinel);
            Tokens copy = source.copy();
            assertEquals("copy() dropped " + f.getName(), sentinel, read(f, copy));
        }
    }

    @Test
    public void copyIsIndependentOfItsSource() {
        Tokens source = Defaults.tokens();
        Tokens copy = source.copy();
        copy.background = 0xFF112233;
        copy.tileRadius = 3f;
        copy.clock = new Tokens.TypeSpec(1f, 100, 0f);
        assertEquals(0xFF000000, source.background);
        assertEquals(16f, source.tileRadius, 0.0001f);
        assertEquals(52f, source.clock.sizeSp, 0.0001f);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static Field[] tokensFields() {
        return Tokens.class.getDeclaredFields();
    }

    private static int fieldCount() {
        return tokensFields().length;
    }

    private static Object read(Field f, Tokens t) {
        try {
            return f.get(t);
        } catch (IllegalAccessException e) {
            throw new AssertionError("cannot read " + f.getName(), e);
        }
    }

    /** A value distinguishable from both zero and the default, so a missed copy() line shows up. */
    private static Object sentinelFor(Field f) {
        Class<?> k = f.getType();
        if (k == int.class) return 0x7FABCDEF;
        if (k == float.class) return 123.456f;
        if (k == Tokens.TypeSpec.class) return new Tokens.TypeSpec(7.5f, 700, 0.125f);
        fail("unsupported token type " + k);
        return null;
    }

    /** Field-by-field equality across the whole struct. */
    static void assertTokensEqual(String message, Tokens expected, Tokens actual) {
        for (Field f : tokensFields()) {
            Object e = read(f, expected);
            Object a = read(f, actual);
            if (e instanceof Tokens.TypeSpec) {
                Tokens.TypeSpec es = (Tokens.TypeSpec) e;
                Tokens.TypeSpec as = (Tokens.TypeSpec) a;
                if (as == null) {
                    fail(message + ": " + f.getName() + " is null");
                }
                if (es.sizeSp != as.sizeSp || es.weight != as.weight || es.tracking != as.tracking) {
                    fail(message + ": " + f.getName() + " expected " + es.sizeSp + "/" + es.weight
                            + "/" + es.tracking + " but was " + as.sizeSp + "/" + as.weight + "/" + as.tracking);
                }
            } else if (!e.equals(a)) {
                fail(message + ": " + f.getName() + " expected " + e + " but was " + a);
            }
        }
    }
}
