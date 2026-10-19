package com.gathin.tvhome.theme;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads a theme file into a {@link Theme}.
 *
 * <p><b>Deliberately free of Android imports</b>, including {@code android.util.Log}: warnings are
 * handed to a caller-supplied sink so this stays unit-testable, and so a test can assert exactly
 * which warnings a broken file produced. In particular this class must never call
 * {@code android.graphics.Color.parseColor} — colours are parsed here, which is also why colour
 * <i>names</i> such as "blue" are not supported.
 *
 * <p>Resolution happens here rather than in {@link Tokens}: each variant starts from
 * {@link Defaults#tokens()} and is overwritten token by token, so a theme file only has to name what
 * it changes. A variant with no block of its own inherits the other one, which then inherits the
 * defaults.
 *
 * <p>Nothing in a theme file is allowed to be load-bearing: a malformed value falls back to its
 * default and the rest of the file still loads, because this runs on a TV's home screen.
 */
public final class ThemeParser {

    private static final Map<String, Field> FIELDS = fieldsByName();

    private ThemeParser() {
    }

    public static Theme parse(InputStream in, String fallbackId) {
        return parse(in, fallbackId, msg -> {
        });
    }

    /**
     * @param fallbackId used as the id when the file does not declare one (the filename)
     * @param warn       receives one message per token that had to fall back; may be null
     * @return the parsed theme, or null if the file is not usable at all
     */
    public static Theme parse(InputStream in, String fallbackId, Consumer<String> warn) {
        String id = null;
        String label = null;
        Tokens dark = null;
        Tokens light = null;
        boolean sawTheme = false;
        boolean closedTheme = false;

        try {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(in, null);

            for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.next()) {
                if (event == XmlPullParser.END_TAG) {
                    if ("theme".equals(p.getName())) closedTheme = true;
                    continue;
                }
                if (event != XmlPullParser.START_TAG) continue;
                switch (p.getName()) {
                    case "theme":
                        sawTheme = true;
                        id = attr(p, "id");
                        label = attr(p, "label");
                        break;
                    case "dark":
                        dark = parseVariant(p, warn);
                        if (dark == null) return null;
                        break;
                    case "light":
                        light = parseVariant(p, warn);
                        if (light == null) return null;
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            return null;   // unparseable: the caller skips this file
        }

        // Anything that is not a complete <theme> element is not a theme: without this, an empty or
        // non-XML file would be listed in the picker under its own filename.
        if (!sawTheme || !closedTheme) return null;

        if (id == null || id.trim().isEmpty()) id = fallbackId;
        if (label == null || label.trim().isEmpty()) label = id;

        // A variant with no block inherits its sibling; with neither, both stay on the defaults.
        if (dark == null && light == null) dark = Defaults.tokens();
        if (dark == null) dark = light.copy();
        if (light == null) light = dark.copy();

        return new Theme(id, label, dark, light);
    }

    /**
     * Reads tokens until the variant element closes, starting from the defaults.
     *
     * @return the tokens, or null if the document ended before the variant was closed — a
     *         truncated file. Note the explicit end-of-document test: an event that is neither a
     *         start nor an end tag would otherwise spin here forever, which is how a half-written
     *         theme file used to hang the parser.
     */
    private static Tokens parseVariant(XmlPullParser p, Consumer<String> warn) throws Exception {
        Tokens t = Defaults.tokens();
        while (true) {
            int event = p.next();
            if (event == XmlPullParser.END_TAG) return t;
            if (event == XmlPullParser.END_DOCUMENT) return null;
            if (event != XmlPullParser.START_TAG) continue;
            applyToken(t, p, warn);
        }
    }

    private static void applyToken(Tokens t, XmlPullParser p, Consumer<String> warn) {
        String name = attr(p, "name");
        if (name == null) {
            warnIt(warn, "<" + p.getName() + "> without a name attribute, ignored");
            return;
        }
        Field f = FIELDS.get(name);
        if (f == null) {
            // A theme written for a later version should still load what we do understand.
            warnIt(warn, "unknown token \"" + name + "\", ignored");
            return;
        }

        String element = p.getName();
        try {
            switch (element) {
                case "color": {
                    Integer c = parseColor(p.nextText());
                    if (c == null) {
                        bad(warn, name, p.getText(), "expected #RGB, #ARGB, #RRGGBB or #AARRGGBB");
                        return;
                    }
                    if (f.getType() != int.class) {
                        wrongType(warn, name, f, element);
                        return;
                    }
                    f.setInt(t, c);
                    return;
                }
                case "integer": {
                    Long v = parseLong(p.nextText());
                    if (v == null) {
                        bad(warn, name, p.getText(), "expected an integer");
                        return;
                    }
                    if (f.getType() != int.class) {
                        wrongType(warn, name, f, element);
                        return;
                    }
                    f.setInt(t, (int) (long) v);
                    return;
                }
                case "number":
                case "dimen": {
                    Float v = element.equals("dimen") ? parseDimen(p.nextText()) : parseFloat(p.nextText());
                    if (v == null) {
                        bad(warn, name, p.getText(),
                                element.equals("dimen") ? "expected a dp or sp length" : "expected a number");
                        return;
                    }
                    if (f.getType() != float.class) {
                        wrongType(warn, name, f, element);
                        return;
                    }
                    f.setFloat(t, v);
                    return;
                }
                case "text": {
                    if (f.getType() != Tokens.TypeSpec.class) {
                        wrongType(warn, name, f, element);
                        return;
                    }
                    Float size = parseDimen(attr(p, "size"));
                    Long weight = parseLong(attr(p, "weight"));
                    if (size == null || weight == null) {
                        bad(warn, name, null, "<text> needs size=\"52sp\" and weight=\"300\"");
                        return;
                    }
                    Float tracking = attr(p, "tracking") == null ? 0f : parseFloat(attr(p, "tracking"));
                    if (tracking == null) {
                        bad(warn, name, attr(p, "tracking"), "expected a number");
                        return;
                    }
                    f.set(t, new Tokens.TypeSpec(size, (int) (long) weight, tracking));
                    return;
                }
                default:
                    warnIt(warn, "unknown element <" + element + "> for token \"" + name + "\", ignored");
            }
        } catch (Exception e) {
            warnIt(warn, "could not apply token \"" + name + "\": " + e);
        }
    }

    // ---- value parsing ---------------------------------------------------------------------------

    /** #RGB, #ARGB, #RRGGBB, #AARRGGBB. Colour names are deliberately not supported. */
    static Integer parseColor(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() < 4 || s.charAt(0) != '#') return null;
        String digits = s.substring(1);
        for (int i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), 16) < 0) return null;
        }
        try {
            switch (digits.length()) {
                case 3:
                    // #RGB: the red digit supplies the alpha as well as the red channel.
                    return (int) Long.parseLong(doubled(digits.charAt(0)) + doubled(digits.charAt(0))
                            + doubled(digits.charAt(1)) + doubled(digits.charAt(2)), 16);
                case 4:
                    // #ARGB: every digit doubles, the first being the alpha.
                    return (int) Long.parseLong(doubled(digits.charAt(0)) + doubled(digits.charAt(1))
                            + doubled(digits.charAt(2)) + doubled(digits.charAt(3)), 16);
                case 6:
                    // #RRGGBB carries no alpha, so it is opaque. Treating it as transparent would
                    // make the most natural way to write a colour produce an invisible launcher.
                    return 0xFF000000 | (int) Long.parseLong(digits, 16);
                case 8:
                    return (int) Long.parseLong(digits, 16);
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Doubles one hex digit, to expand #RGB and #ARGB. */
    private static String doubled(char digit) {
        return new String(new char[]{digit, digit});
    }

    /**
     * A dp or sp length, returned as its bare number. The density is applied later, in {@link Skin},
     * so a parsed theme survives a display change.
     */
    static Float parseDimen(String s) {
        if (s == null) return null;
        s = s.trim();
        String unit = "";
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '.') break;
            unit = c + unit;
        }
        // px is rejected rather than silently treated as dp: the tokens are density-independent and
        // a theme author writing "px" means something the engine cannot honour.
        if (!"dp".equals(unit) && !"sp".equals(unit)) return null;
        return parseFloat(s.substring(0, s.length() - unit.length()));
    }

    static Float parseFloat(String s) {
        if (s == null) return null;
        try {
            return Float.valueOf(Float.parseFloat(s.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Long parseLong(String s) {
        if (s == null) return null;
        try {
            return Long.valueOf(Long.parseLong(s.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- plumbing --------------------------------------------------------------------------------

    private static String attr(XmlPullParser p, String name) {
        return p.getAttributeValue(null, name);
    }

    private static void bad(Consumer<String> warn, String token, String value, String expected) {
        warnIt(warn, "token \"" + token + "\": \"" + value + "\" is not " + expected + ", using the default");
    }

    private static void wrongType(Consumer<String> warn, String token, Field f, String element) {
        warnIt(warn, "token \"" + token + "\" is a " + f.getType().getSimpleName()
                + ", <" + element + "> does not apply, using the default");
    }

    private static void warnIt(Consumer<String> warn, String message) {
        if (warn != null) warn.accept(message);
    }

    private static Map<String, Field> fieldsByName() {
        Map<String, Field> map = new LinkedHashMap<>();
        for (Field f : Tokens.class.getDeclaredFields()) {
            f.setAccessible(true);
            map.put(f.getName(), f);
        }
        return new HashMap<>(map);
    }
}
