package com.gathin.tvhome.theme;

/**
 * The built-in fallback token set: exactly what the launcher looked like before themes existed.
 *
 * <p>This is the reason the launcher still renders when every theme file is missing, unparseable or
 * corrupt. It is deliberately duplicated by {@code assets/themes/tvos-black.xml}, which is the file
 * users copy to make their own theme.
 *
 * <p><b>Those two copies can drift, and nothing at runtime would notice</b> — {@link Defaults}
 * would simply win and "tvOS Black" would stop being the reference theme it claims to be. The
 * guard is {@code DefaultsMatchShippedThemeTest}, which parses the shipped file and compares it
 * against this class field by field.
 */
public final class Defaults {

    private Defaults() {
    }

    public static Tokens tokens() {
        Tokens t = new Tokens();

        // colour
        t.background = 0xFF000000;
        t.tileSurface = 0xFF1C1C1E;
        t.dockBand = 0xFF141416;
        t.dockBandEdge = 0x1FFFFFFF;
        t.pillFillTop = 0x2EFFFFFF;
        t.pillFillBottom = 0x14FFFFFF;
        t.pillStroke = 0x38FFFFFF;
        t.pillFocusFill = 0xF5FFFFFF;
        t.inputRowFocus = 0xF5FFFFFF;
        t.textHero = 0xFFFFFFFF;
        t.textClock = 0xFFFFFFFF;
        t.textAmPm = 0xB3FFFFFF;
        t.textDate = 0xA6FFFFFF;
        t.textGreeting = 0x8CFFFFFF;
        t.clockDivider = 0x33FFFFFF;
        t.textTileLabel = 0xFFFFFFFF;
        t.textTileName = 0xF2FFFFFF;
        t.textPill = 0xFFFFFFFF;
        t.textPillDim = 0x73FFFFFF;
        t.textPillFocus = 0xFF111111;
        t.textPillFocusDim = 0x99111111;
        t.glassFillTop = 0x3DFFFFFF;
        t.glassFillBottom = 0x12FFFFFF;
        t.glassSpecular = 0x66FFFFFF;
        t.glassRim = 0xCCFFFFFF;
        t.glassGlow = 0x30FFFFFF;
        t.statusDot = 0xFF34C759;
        t.separator = 0x1FFFFFFF;
        t.panelFillTop = 0xF22A2A2E;
        t.panelFillBottom = 0xF5161618;
        t.panelStroke = 0x33FFFFFF;
        t.pickerScrim = 0xCC000000;

        // ambient
        t.ambientCore = 0x5C;
        t.ambientEdge = 0x26;
        t.ambientFallback = 0xFF444444;
        t.ambientSaturation = 1.1f;
        t.ambientValueFloor = 0.5f;

        // shape, dp
        t.tileRadius = 16f;
        t.dockBandRadius = 26f;
        t.pillRadius = 21f;
        t.pillHeight = 42f;
        t.glassRadius = 20f;
        t.glassStroke = 1.5f;
        t.glassInset = 6f;
        t.panelRadius = 20f;
        t.inputRowRadius = 13f;

        // motion
        t.springStiffness = 260f;
        t.springDamping = 0.58f;
        t.focusScale = 1.10f;
        t.focusOvershoot = 1.6f;
        t.entranceStagger = 0.06f;
        t.focusInMs = 260;
        t.focusOutMs = 180;
        t.glassInMs = 180;
        t.glassOutMs = 160;
        t.heroMs = 260;
        t.heroSwapMs = 120;
        t.ambientMs = 600;
        t.entranceMs = 420;

        // typography
        t.clock = new Tokens.TypeSpec(52f, 300, -0.04f);
        t.amPm = new Tokens.TypeSpec(15f, 500, 0.08f);
        t.date = new Tokens.TypeSpec(14f, 400, 0.14f);
        t.greeting = new Tokens.TypeSpec(12f, 500, 0.22f);
        t.hero = new Tokens.TypeSpec(48f, 300, -0.02f);
        t.pill = new Tokens.TypeSpec(15f, 500, 0.01f);
        t.tileName = new Tokens.TypeSpec(14f, 500, 0.02f);
        t.tileLabel = new Tokens.TypeSpec(13f, 400, 0f);

        return t;
    }
}
