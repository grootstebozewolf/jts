/*
 * Copyright (c) 2026 grootstebozewolf
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jtstest.testbuilder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import javax.imageio.ImageIO;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * Pins A/B draw-tool icons used by
 * {@link JTSTestBuilderToolBar#setFocusGeometry(int)} (index 0 = A /
 * blue, else B / red). No GUI: resource bytes and a red-channel check.
 */
public class DrawToolFocusIconTest extends TestCase {

  private static final String[] STEMS = {
      "DrawLineString",
      "DrawPolygon",
      "DrawCircularString",
      "DrawCompoundCurve",
      "DrawCurvePolygon"
  };

  public DrawToolFocusIconTest(String name) {
    super(name);
  }

  public static void main(String[] args) {
    TestRunner.run(DrawToolFocusIconTest.class);
  }

  public void testFocusBIconsAreRedAndDifferFromA() throws Exception {
    for (int i = 0; i < STEMS.length; i++) {
      String stem = STEMS[i];
      URL aUrl = JTSTestBuilderToolBar.class.getResource(stem + ".png");
      URL bUrl = JTSTestBuilderToolBar.class.getResource(stem + "B.png");
      assertNotNull(stem + ".png missing", aUrl);
      assertNotNull(stem + "B.png missing", bUrl);

      byte[] aBytes = readAll(aUrl);
      byte[] bBytes = readAll(bUrl);
      assertFalse(stem + " A and B resource bytes must differ",
          Arrays.equals(aBytes, bBytes));

      BufferedImage aImg = ImageIO.read(aUrl);
      BufferedImage bImg = ImageIO.read(bUrl);
      assertNotNull(stem + " A did not decode", aImg);
      assertNotNull(stem + " B did not decode", bImg);
      assertTrue(stem + " A should be blue-dominant (focus geometry 0)",
          blueDominant(aImg));
      assertTrue(stem + " B should be red-dominant (focus geometry 1)",
          redDominant(bImg));
    }
  }

  private static boolean blueDominant(BufferedImage img) {
    long[] rgb = visibleMean(img);
    return rgb[2] > rgb[0];
  }

  private static boolean redDominant(BufferedImage img) {
    long[] rgb = visibleMean(img);
    return rgb[0] > rgb[2];
  }

  /**
   * Mean RGB of non-background pixels. Returns {@code {r, g, b}} or
   * zeros when the icon has no visible stroke.
   */
  private static long[] visibleMean(BufferedImage img) {
    long r = 0;
    long g = 0;
    long b = 0;
    long n = 0;
    int w = img.getWidth();
    int h = img.getHeight();
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int argb = img.getRGB(x, y);
        int a = (argb >>> 24) & 0xff;
        int rr = (argb >>> 16) & 0xff;
        int gg = (argb >>> 8) & 0xff;
        int bb = argb & 0xff;
        if (a >= 20 && rr + gg + bb >= 40) {
          r += rr;
          g += gg;
          b += bb;
          n++;
        }
      }
    }
    if (n == 0) {
      return new long[] { 0, 0, 0 };
    }
    return new long[] { r / n, g / n, b / n };
  }

  private static byte[] readAll(URL url) throws IOException {
    InputStream in = url.openStream();
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[256];
      int n = in.read(buf);
      while (n >= 0) {
        out.write(buf, 0, n);
        n = in.read(buf);
      }
      return out.toByteArray();
    }
    finally {
      in.close();
    }
  }
}
