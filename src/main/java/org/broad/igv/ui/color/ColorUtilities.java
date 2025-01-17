/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package org.broad.igv.ui.color;


import org.broad.igv.logging.*;
import org.broad.igv.util.ObjectCache;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;


/**
 * Miscellaneous utilities for parsing and manipulating colors.
 *
 * @author Jim Robinson
 */
public class ColorUtilities {

    private static Logger log = LogManager.getLogger(ColorUtilities.class);

    public static ObjectCache<Object, Color> colorCache = new ObjectCache<>(1000);
    private static ObjectCache<Color, Color> slightlyDarkerCache = new ObjectCache<>(1000);
    private static Map<Integer, Color> grayscaleColors = new HashMap();

    // HTML 4.1 color table,  + orange and magenta
    static Map<String, String> colorSymbols = new HashMap();
    private static Map<String, ColorPalette> palettes;
    public static Map<Color, float[]> componentsCache = Collections.synchronizedMap(new HashMap<>());

    static {
        colorSymbols.put("white", "FFFFFF");
        colorSymbols.put("silver", "C0C0C0");
        colorSymbols.put("gray", "808080");
        colorSymbols.put("black", "000000");
        colorSymbols.put("red", "FF0000");
        colorSymbols.put("maroon", "800000");
        colorSymbols.put("yellow", "FFFF00");
        colorSymbols.put("olive", "808000");
        colorSymbols.put("lime", "00FF00");
        colorSymbols.put("green", "008000");
        colorSymbols.put("aqua", "00FFFF");
        colorSymbols.put("teal", "008080");
        colorSymbols.put("blue", "0000FF");
        colorSymbols.put("navy", "000080");
        colorSymbols.put("fuchsia", "FF00FF");
        colorSymbols.put("purple", "800080");
        colorSymbols.put("orange", "FFA500");
        colorSymbols.put("magenta", "FF00FF");
    }

    /**
     * @param idx
     * @return
     * @see #randomColor(int, float)
     */
    private static int[] quasiRandomColor(int idx) {
        int BASE_COL = 40;
        int RAND_COL = 255 - BASE_COL;

        idx += 1;    // avoid 0
        int r = Math.abs(BASE_COL + (idx * 33) % RAND_COL);
        int g = Math.abs(BASE_COL + (idx * 55) % RAND_COL);
        int b = Math.abs(BASE_COL + (idx * 77) % RAND_COL);

        return new int[]{r, g, b};
    }

    /**
     * Port of DChip function of the same name.
     * Calls {@link #randomColor(int, float)} with {@code alpha=1.0}
     *
     * @param idx
     * @return
     */
    public static Color randomColor(int idx) {
        return randomColor(idx, 1.0f);
    }

    /**
     * Generate a color based on {@code idx}. Unpredictable but deterministic (like a hash)
     * Good for generating a set of colors for successive values of {@code idx}.
     * Alpha value is set as specified
     *
     * @param idx
     * @param alpha alpha value of color, from 0.0-1.0
     * @return
     */
    public static Color randomColor(int idx, float alpha) {

        int[] rgb = quasiRandomColor(idx);

        int r = rgb[0];
        int g = rgb[1];
        int b = rgb[2];

        // Reject colors too close to white
        if (r > 200 && g > 200 && b > 200) {
            int tmp = r % 3;
            if (tmp == 0) {
                r = 255 - r;
            } else if (tmp == 1) {
                g = 255 - g;
            } else {
                b = 255 - b;
            }
        }
        return new Color(r, g, b, (int) (255 * alpha));
    }

    public static Color randomDesaturatedColor(float alpha) {
        float hue = (float) Math.random();
        float brightenss = (float) (Math.random() * 0.7);
        Color base = Color.getHSBColor(hue, 0, brightenss);
        if (alpha >= 1) return base;
        else return new Color(base.getRed(), base.getGreen(), base.getBlue(), (int) (alpha * 255));


    }


    /**
     * Method description
     *
     * @param inputColor
     * @param hue
     * @param saturation
     * @param brightness
     * @return
     */
    public static Color adjustHSB(Color inputColor, float hue, float saturation, float brightness) {
        float[] hsbvals = new float[3];
        Color.RGBtoHSB(inputColor.getRed(), inputColor.getGreen(), inputColor.getBlue(), hsbvals);
        return Color.getHSBColor(hue * hsbvals[0], saturation * hsbvals[1], brightness * hsbvals[2]);
    }


    public static String colorToString(Color color) {

        StringBuffer buffer = new StringBuffer();
        buffer.append(color.getRed());
        buffer.append(",");
        buffer.append(color.getGreen());
        buffer.append(",");
        buffer.append(color.getBlue());
        return buffer.toString();
    }

    public static Color stringToColor(String string) {
        return stringToColor(string, Color.black);
    }

    public static Color stringToColor(String string, Color defaultColor) {

        if (string == null || string.equals(".")) return defaultColor;

        try {
            Color c = stringToColorNoDefault(string);
            if (c == null) {
                c = defaultColor;
            }
            colorCache.put(string, c);
            return c;
        } catch (NumberFormatException numberFormatException) {
            log.error("Error in color string. ", numberFormatException);
            return defaultColor;
        }
    }

    public static Color stringToColorNoDefault(String string) throws NumberFormatException {
        // Excel will quote color strings, strip all quotes
        string = string.replace("\"", "").replace("'", "");

        Color c = null;
        if (string.contains(",")) {
            if(string.startsWith("rgb(") && string.endsWith(")")) {
                // javascript style string
                string = string.substring(4, string.length() - 1);
            }
            if (string.contains(".")) {
                String[] rgb = string.split(",");
                int red = (int) (255 * Double.parseDouble(rgb[0]));
                int green = (int) (255 * Double.parseDouble(rgb[1]));
                int blue = (int) (255 * Double.parseDouble(rgb[2]));
                c = new Color(red, green, blue);

            } else {
                String[] rgb = string.split(",");
                int red = Integer.parseInt(rgb[0]);
                int green = Integer.parseInt(rgb[1]);
                int blue = Integer.parseInt(rgb[2]);
                c = new Color(red, green, blue);
            }
        } else if (string.startsWith("#")) {
            c = hexToColor(string.substring(1));
        } else {
            try {
                int intValue = Integer.parseInt(string);
                if (intValue >= 0) {
                    c = new Color(intValue);
                }
            } catch (NumberFormatException e) {
                String hexString = colorSymbols.get(string.toLowerCase());
                if (hexString != null) {
                    c = hexToColor(hexString);
                }
            }
        }
        return c;
    }

    private static Color hexToColor(String string) {
        if (string.length() == 6) {
            int red = Integer.parseInt(string.substring(0, 2), 16);
            int green = Integer.parseInt(string.substring(2, 4), 16);
            int blue = Integer.parseInt(string.substring(4, 6), 16);
            return new Color(red, green, blue);
        } else {
            return null;
        }

    }

    public static float[] getRGBColorComponents(Color color) {
        float[] comps = componentsCache.get(color);
        if (comps == null) {
            comps = color.getRGBColorComponents(null);
            componentsCache.put(color, comps);
        }
        return comps;
    }

    public static Color slightlyDarker(Color color) {
        Color d = slightlyDarkerCache.get(color);
        if(d == null) {
            float [] comps = color.getRGBColorComponents(null);
            float factor = 0.95f;
            d = new Color(factor*comps[0], factor*comps[1], factor*comps[2]);
            slightlyDarkerCache.put(color, d);
        }
        return d;
    }


    /**
     * Return  alphas shaded color.  This method is used, rather than the Color constructor, so that
     * the alpha is not lost in postscript output.
     *
     * @param alpha
     * @return
     */
    public static Color getCompositeColor(Color backgroundColor, Color foregroundColor, float alpha) {

        float[] dest = getRGBColorComponents(backgroundColor);
        float[] source = getRGBColorComponents(foregroundColor);

        int r = (int) ((alpha * source[0] + (1 - alpha) * dest[0]) * 255 + 0.5);
        int g = (int) ((alpha * source[1] + (1 - alpha) * dest[1]) * 255 + 0.5);
        int b = (int) ((alpha * source[2] + (1 - alpha) * dest[2]) * 255 + 0.5);
        int a = 255;
        int value = ((a & 0xFF) << 24) |
                ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8) |
                ((b & 0xFF) << 0);

        Color c = colorCache.get(value);
        if (c == null) {
            c = new Color(value);
            colorCache.put(value, c);
        }
        return c;
    }

    /**
     * Return  alphas shaded color for a white background.  This method is used, rather than the Color constructor, so that
     * the alpha is not lost in postscript output.
     *
     * @param source
     * @param alpha
     * @return
     */
    public static Color getCompositeColor(Color source, float alpha) {
        return getCompositeColor(Color.white, source, alpha);
    }


    public static Map<String, ColorPalette> loadPalettes() throws IOException {

        InputStream is = ColorUtilities.class.getResourceAsStream("resources/colorPalettes.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String nextLine;

        palettes = new LinkedHashMap<String, ColorPalette>();
        palleteNames = new ArrayList();

        String currentPalletName = null;
        java.util.List<Color> currentColorList = new ArrayList();
        while ((nextLine = br.readLine()) != null) {
            nextLine = nextLine.trim();
            if (nextLine.length() == 0) continue;
            if (nextLine.startsWith("#")) {
                if (currentPalletName != null) {
                    ColorPalette palette = new ColorPalette(currentPalletName, currentColorList.toArray(new Color[currentColorList.size()]));
                    palettes.put(currentPalletName, palette);
                    palleteNames.add(currentPalletName);
                    currentColorList.clear();
                }
                currentPalletName = nextLine.substring(1);
            } else {
                String[] tokens = nextLine.split(";");
                for (String s : tokens) {
                    // Remove white space
                    s = s.replaceAll(" ", "");
                    Color c = ColorUtilities.stringToColor(s);
                    currentColorList.add(c);
                }
            }

        }

        if (!currentColorList.isEmpty()) {
            ColorPalette palette = new ColorPalette(currentPalletName, currentColorList.toArray(new Color[currentColorList.size()]));
            palettes.put(currentPalletName, palette);
            palleteNames.add(currentPalletName);
        }

        return palettes;
    }

    static int nextPaletteIdx = 0;
    static ArrayList<String> palleteNames = new ArrayList();

    public static ColorPalette getNextPalette() {
        try {
            if (palettes == null) loadPalettes();
            ColorPalette pallete = palettes.get(palleteNames.get(nextPaletteIdx));
            nextPaletteIdx++;
            if (nextPaletteIdx >= palleteNames.size()) {
                nextPaletteIdx = 0;
            }
            return pallete;
        } catch (IOException e) {
            log.error(e);
            return null;
        }

    }

    public static ColorPalette getPalette(String s) {
        try {
            if (palettes == null) loadPalettes();
            return palettes.get(s);
        } catch (IOException e) {
            log.error(e);
            return null;
        }
    }

    public static ColorPalette getDefaultPalette() {
        try {
            if (palettes == null) {
                loadPalettes();
            }
            if (palettes.isEmpty()) {
                return null;
            }
            return palettes.values().iterator().next();
        } catch (IOException e) {
            log.error("Error loading color palletes", e);
            return null;
        }
    }

    public static synchronized Color getGrayscaleColor(int gray) {
        gray = Math.max(0, Math.min(255, gray));
        Color c = grayscaleColors.get(gray);
        if (c == null) {
            c = new Color(gray, gray, gray);
            grayscaleColors.put(gray, c);
        }
        return c;
    }

    /**
     * Return a new Color, same as the old, but with a new alpha value
     *
     * @param oldColor
     * @param newAlpha
     * @return
     */
    public static Color modifyAlpha(Color oldColor, int newAlpha) {
        return new Color(oldColor.getRed(), oldColor.getGreen(), oldColor.getBlue(), newAlpha);
    }

    /**
     * Converts an HSL color value to RGB. Conversion formula
     * adapted from http://en.wikipedia.org/wiki/HSL_color_space.
     *
     * @param h The hue  [0, 360]
     * @param s The saturation  [0, 1]
     * @param l The lightness  [0, 1]
     * @return The RGB representation
     */
    public static int[] hslToRgb(double h, double s, double l) {

        double c = (1 - Math.abs(2 * l - 1)) * s;

        double hprime = h / 60;
        double x = c * (1 - Math.abs(hprime % 2 - 1));
        double r, g, b;

        if (hprime < 1) {
            r = c;
            g = x;
            b = 0;
        } else if (hprime < 2) {
            r = x;
            g = c;
            b = 0;
        } else if (hprime < 3) {
            r = 0;
            g = c;
            b = x;
        } else if (hprime < 4) {
            r = 0;
            g = x;
            b = c;
        } else if (hprime < 5) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }
        double m = l - 0.5 * c;

        return new int[]{
                (int) ((r + m) * 255),
                (int) ((g + m) * 255),
                (int) ((b + m) * 255)
        };
//        int r, g, b;
//
//        if (s == 0) {
//            r = g = b = (int) (255 * l); // achromatic
//        } else {
//            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
//            double p = 2 * l - q;
//            r = (int) (255 * hue2rgb(p, q, h + 1 / 3));
//            g = (int) (255 * hue2rgb(p, q, h));
//            b = (int) (255 * hue2rgb(p, q, h - 1 / 3));
//        }
//
//        return new int[]{r, g, b};
    }


}
