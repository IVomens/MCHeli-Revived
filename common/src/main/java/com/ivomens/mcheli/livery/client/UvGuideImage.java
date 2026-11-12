package com.ivomens.mcheli.livery.client;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the labelled UV guide that comes with a livery template: the vehicle texture, faded, with
 * the UV layout of every model part outlined in its own colour, and a column of part names on the
 * right, each joined to its part by a leader line.
 * <p>
 * Plain {@code java.awt}, so it runs headless and off the render thread.
 */
public final class UvGuideImage {

    /** Small textures are scaled up so outlines and labels stay readable. */
    private static final int MIN_TEXTURE_SIZE = 1024;
    private static final int MARGIN_WIDTH = 300;
    private static final int LEADER_GAP = 24;
    private static final int LABEL_HEIGHT = 20;
    private static final int SWATCH_SIZE = 12;
    private static final int HEADER_HEIGHT = 32;
    private static final int PADDING = 8;
    private static final float TEXTURE_OPACITY = 0.35F;
    private static final float GOLDEN_ANGLE = 0.381966F;

    /**
     * One part of the model.
     *
     * @param name raw part name from the model file
     * @param uvs  texture coordinates of the part's quads, 8 floats (4 u/v pairs) per quad
     */
    public record Part(String name, float[] uvs) {
    }

    private record Placed(String label, Color color, Rectangle2D bounds, float[] uvs) {
    }

    private UvGuideImage() {
    }

    /**
     * Draws the guide.
     *
     * @param texture the vehicle's original texture
     * @param title   shown above the labels, usually the vehicle name
     * @param parts   model parts; parts without any area on the texture are left out
     */
    public static BufferedImage draw(BufferedImage texture, String title, List<Part> parts) {
        int scale = Math.max(1, MIN_TEXTURE_SIZE / Math.max(texture.getWidth(), texture.getHeight()));
        int texWidth = texture.getWidth() * scale;
        int texHeight = texture.getHeight() * scale;
        List<Placed> placed = place(parts, texWidth, texHeight);

        int labelsHeight = HEADER_HEIGHT + placed.size() * LABEL_HEIGHT + PADDING;
        int width = texWidth + LEADER_GAP + MARGIN_WIDTH;
        int height = Math.max(texHeight, labelsHeight);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TEXTURE_OPACITY));
            g.drawImage(texture, 0, 0, texWidth, texHeight, null);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(Color.GRAY);
            g.drawRect(0, 0, texWidth - 1, texHeight - 1);

            for (Placed part : placed) {
                drawOutlines(g, part, texWidth, texHeight);
            }
            drawLabels(g, title, placed, texWidth, height);
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Merges parts of the same label, drops parts with no area, and gives each a colour. */
    private static List<Placed> place(List<Part> parts, int texWidth, int texHeight) {
        List<String> labels = new ArrayList<>();
        List<List<float[]>> uvLists = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String label = friendlyName(parts.get(i).name(), i + 1);
            int index = labels.indexOf(label);
            if (index < 0) {
                labels.add(label);
                uvLists.add(new ArrayList<>());
                index = labels.size() - 1;
            }
            uvLists.get(index).add(parts.get(i).uvs());
        }

        List<Placed> placed = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            float[] uvs = concat(uvLists.get(i));
            Rectangle2D bounds = bounds(uvs, texWidth, texHeight);
            if (bounds != null) {
                Color color = Color.getHSBColor((placed.size() * GOLDEN_ANGLE) % 1.0F, 0.9F, 0.8F);
                placed.add(new Placed(labels.get(i), color, bounds, uvs));
            }
        }
        return placed;
    }

    private static float[] concat(List<float[]> arrays) {
        int length = 0;
        for (float[] array : arrays) {
            length += array.length;
        }
        float[] all = new float[length];
        int offset = 0;
        for (float[] array : arrays) {
            System.arraycopy(array, 0, all, offset, array.length);
            offset += array.length;
        }
        return all;
    }

    /** Pixel bounds of the UVs, clamped to the texture; null when they shrink to a single point. */
    private static Rectangle2D bounds(float[] uvs, int texWidth, int texHeight) {
        double minU = Double.MAX_VALUE;
        double minV = Double.MAX_VALUE;
        double maxU = -Double.MAX_VALUE;
        double maxV = -Double.MAX_VALUE;
        for (int i = 0; i + 1 < uvs.length; i += 2) {
            minU = Math.min(minU, uvs[i]);
            maxU = Math.max(maxU, uvs[i]);
            minV = Math.min(minV, uvs[i + 1]);
            maxV = Math.max(maxV, uvs[i + 1]);
        }
        minU = Math.clamp(minU, 0.0, 1.0);
        maxU = Math.clamp(maxU, 0.0, 1.0);
        minV = Math.clamp(minV, 0.0, 1.0);
        maxV = Math.clamp(maxV, 0.0, 1.0);
        if (!(maxU > minU) && !(maxV > minV)) {
            return null;
        }
        return new Rectangle2D.Double(minU * texWidth, minV * texHeight,
                (maxU - minU) * texWidth, (maxV - minV) * texHeight);
    }

    private static void drawOutlines(Graphics2D g, Placed part, int texWidth, int texHeight) {
        Path2D.Float path = new Path2D.Float();
        float[] uvs = part.uvs();
        for (int quad = 0; quad + 7 < uvs.length; quad += 8) {
            path.moveTo(uvs[quad] * texWidth, uvs[quad + 1] * texHeight);
            for (int corner = 2; corner < 8; corner += 2) {
                path.lineTo(uvs[quad + corner] * texWidth, uvs[quad + corner + 1] * texHeight);
            }
            path.closePath();
        }
        g.setClip(0, 0, texWidth, texHeight);
        g.setColor(withAlpha(part.color(), 200));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(path);
        g.setColor(part.color());
        g.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0F, new float[]{6.0F, 4.0F}, 0.0F));
        g.draw(part.bounds());
        g.setClip(null);
    }

    private static void drawLabels(Graphics2D g, String title, List<Placed> placed, int texWidth, int height) {
        int marginX = texWidth + LEADER_GAP;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.setColor(Color.DARK_GRAY);
        g.drawString(title, marginX, PADDING + 16);

        List<Placed> sorted = new ArrayList<>(placed);
        sorted.sort(Comparator.comparingDouble(part -> part.bounds().getCenterY()));
        int[] desired = new int[sorted.size()];
        for (int i = 0; i < desired.length; i++) {
            desired[i] = (int) Math.round(sorted.get(i).bounds().getCenterY()) - LABEL_HEIGHT / 2;
        }
        int[] tops = spread(desired, LABEL_HEIGHT, HEADER_HEIGHT, height - PADDING);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i < sorted.size(); i++) {
            Placed part = sorted.get(i);
            int centerY = tops[i] + LABEL_HEIGHT / 2;
            Rectangle2D bounds = part.bounds();
            g.setColor(withAlpha(part.color(), 170));
            g.setStroke(new BasicStroke(1.0F));
            g.drawLine((int) Math.round(bounds.getMaxX()), (int) Math.round(bounds.getCenterY()), marginX - 2, centerY);

            g.setColor(part.color());
            g.fillRect(marginX, centerY - SWATCH_SIZE / 2, SWATCH_SIZE, SWATCH_SIZE);
            g.setColor(Color.BLACK);
            int textY = centerY + (metrics.getAscent() - metrics.getDescent()) / 2;
            g.drawString(part.label(), marginX + SWATCH_SIZE + 6, textY);
        }
    }

    /**
     * Moves label tops (sorted by their wanted position) apart so that no two overlap, staying
     * between {@code minTop} and {@code maxBottom} when there is room, and as close to where they
     * want to be as possible.
     */
    static int[] spread(int[] desired, int labelHeight, int minTop, int maxBottom) {
        int count = desired.length;
        int[] tops = new int[count];
        for (int i = 0; i < count; i++) {
            int lowest = i == 0 ? minTop : tops[i - 1] + labelHeight;
            tops[i] = Math.max(desired[i], lowest);
        }
        for (int i = count - 1; i >= 0; i--) {
            int highest = i == count - 1 ? maxBottom - labelHeight : tops[i + 1] - labelHeight;
            tops[i] = Math.min(tops[i], highest);
        }
        // when the column is too short for everything, the top wins and the image grows downwards
        for (int i = 0; i < count; i++) {
            int lowest = i == 0 ? minTop : tops[i - 1] + labelHeight;
            tops[i] = Math.max(tops[i], lowest);
        }
        return tops;
    }

    /**
     * A readable name for a model part: {@code "$rotor0"} becomes "Rotor 0", {@code "body1"}
     * "Body 1", {@code "$weapon0_1"} "Weapon 0 1". Names without any readable characters (often
     * Japanese names in the wrong encoding) become "Part n".
     */
    static String friendlyName(String raw, int number) {
        StringBuilder words = new StringBuilder();
        int letterRun = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '$' || c == '_' || c == '-' || c == '.' || c == ' ') {
                appendSpace(words);
                letterRun = 0;
            } else if (c < 0x20 || c > 0x7E) {
                letterRun = 0;
            } else if (Character.isDigit(c)) {
                if (letterRun >= 2) {
                    appendSpace(words);
                }
                words.append(c);
                letterRun = 0;
            } else {
                words.append(c);
                letterRun = Character.isLetter(c) ? letterRun + 1 : 0;
            }
        }
        String text = words.toString().strip();
        if (text.isEmpty()) {
            return "Part " + number;
        }
        StringBuilder result = new StringBuilder(text.length());
        boolean wordStart = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            result.append(wordStart ? Character.toUpperCase(c) : c);
            wordStart = c == ' ';
        }
        return result.toString();
    }

    private static void appendSpace(StringBuilder words) {
        if (!words.isEmpty() && words.charAt(words.length() - 1) != ' ') {
            words.append(' ');
        }
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
