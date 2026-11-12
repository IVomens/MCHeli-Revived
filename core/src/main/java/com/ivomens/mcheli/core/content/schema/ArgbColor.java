package com.ivomens.mcheli.core.content.schema;

import java.util.List;

/**
 * A colour with alpha, each channel from 0 to 1 (values outside are clamped, as {@code Color4f} does).
 * <p>
 * YAML writes it as hex, {@code '#AARRGGBB'} or {@code '#RRGGBB'}, or as a list {@code [a, r, g, b]}. The
 * emitter uses hex when that gives the same channels back; legacy TXT colours, written as 0 to 255 per
 * channel and scaled slightly differently, can need the list to come back exactly.
 */
public record ArgbColor(float a, float r, float g, float b) {

    public static final ArgbColor WHITE = new ArgbColor(1.0F, 1.0F, 1.0F, 1.0F);

    /** The factor legacy TXT files scale a 0 to 255 channel by; not exactly 1/255. */
    private static final float TXT_CHANNEL_SCALE = 0.003921569F;

    public static final ValueCodec<ArgbColor> CODEC = ValueCodec.of(
            ArgbColor::fromYaml,
            text -> ofArgb(parseHex(text)),
            ArgbColor::toYaml).shaped(ValueShape.either(
                    ValueShape.text("^\\s*(#|0[xX])?[0-9A-Fa-f]+\\s*$"),
                    ValueShape.tuple(4, ValueShape.number()))
            .note("A colour: hex '#AARRGGBB' or '#RRGGBB', or a list [a, r, g, b] of numbers from 0 to 1."));

    public ArgbColor {
        a = clamp(a);
        r = clamp(r);
        g = clamp(g);
        b = clamp(b);
    }

    public static ArgbColor ofArgb(int argb) {
        return new ArgbColor(((argb >> 24) & 0xFF) / 255.0F, ((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F);
    }

    /** Parses {@code 0xRRGGBB}, {@code #RRGGBB} or {@code RRGGBB}, with or without alpha in front. */
    public static int parseHex(String text) {
        if (text == null || text.isBlank()) {
            throw new ContentValueException("expected a colour such as '#FFAA00' but got an empty value");
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = "0x" + hex.substring(1);
        }
        if (!hex.startsWith("0x") && !hex.startsWith("0X")) {
            hex = "0x" + hex;
        }
        try {
            return (int) Long.decode(hex).longValue();
        } catch (NumberFormatException e) {
            throw new ContentValueException("expected a colour such as '#FFAA00' but got \"" + text + "\"");
        }
    }

    /** One channel of a legacy TXT colour, written as 0 to 255. */
    public static float txtChannel(String text, ReadContext context) {
        return TXT_CHANNEL_SCALE * Codecs.clamp(Codecs.parseInt(text), 0, 255, context);
    }

    /** The channels rounded to 0 to 255, packed as ARGB. */
    public int argb() {
        return channel(this.a) << 24 | channel(this.r) << 16 | channel(this.g) << 8 | channel(this.b);
    }

    private static ArgbColor fromYaml(Object raw) {
        if (raw instanceof List<?> channels) {
            if (channels.size() != 4) {
                throw new ContentValueException("a colour list needs four values [a, r, g, b], got "
                        + channels.size());
            }
            return new ArgbColor(Codecs.number(channels.get(0)).floatValue(),
                    Codecs.number(channels.get(1)).floatValue(), Codecs.number(channels.get(2)).floatValue(),
                    Codecs.number(channels.get(3)).floatValue());
        }
        return ofArgb(parseHex(Codecs.text(raw)));
    }

    private static Object toYaml(ArgbColor color) {
        int argb = color.argb();
        if (ofArgb(argb).equals(color)) {
            return String.format("#%06X", argb);
        }
        return new InlineList(List.of(color.a, color.r, color.g, color.b));
    }

    private static int channel(float value) {
        return (int) Math.round(value * 255.0);
    }

    private static float clamp(float value) {
        return value > 1.0F ? 1.0F : Math.max(value, 0.0F);
    }
}
