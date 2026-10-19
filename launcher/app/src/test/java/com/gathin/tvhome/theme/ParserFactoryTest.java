package com.gathin.tvhome.theme;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * The theme engine is only unit-testable because it stays off the Android framework. This test
 * guards the one seam that is easy to break: the XML parser the theme files are read with.
 */
public class ParserFactoryTest {

    @Test
    public void xmlPullParserIsAvailableOnTheTestClasspath() throws Exception {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        assertNotNull(parser);
    }
}
