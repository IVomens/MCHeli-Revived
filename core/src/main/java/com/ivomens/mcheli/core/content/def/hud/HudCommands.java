package com.ivomens.mcheli.core.content.def.hud;

import com.ivomens.mcheli.core.content.def.hud.HudCommand.TxtForm;
import com.ivomens.mcheli.core.content.def.hud.HudOp.Call;
import com.ivomens.mcheli.core.content.def.hud.HudOp.CameraRotation;
import com.ivomens.mcheli.core.content.def.hud.HudOp.Color;
import com.ivomens.mcheli.core.content.def.hud.HudOp.DrawGraduation;
import com.ivomens.mcheli.core.content.def.hud.HudOp.DrawLine;
import com.ivomens.mcheli.core.content.def.hud.HudOp.DrawRadar;
import com.ivomens.mcheli.core.content.def.hud.HudOp.DrawRect;
import com.ivomens.mcheli.core.content.def.hud.HudOp.DrawString;
import com.ivomens.mcheli.core.content.def.hud.HudOp.DrawTexture;
import com.ivomens.mcheli.core.content.def.hud.HudOp.Exit;
import com.ivomens.mcheli.core.content.schema.Codecs;
import com.ivomens.mcheli.core.content.schema.ContentValueException;
import com.ivomens.mcheli.core.content.schema.EntrySchema;
import com.ivomens.mcheli.core.content.schema.Field;
import com.ivomens.mcheli.core.content.schema.Section;
import com.ivomens.mcheli.core.content.schema.TxtTuple;
import com.ivomens.mcheli.core.content.schema.ValueCodec;
import com.ivomens.mcheli.core.content.schema.Values;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The HUD command table: every command with its YAML keys and their spelling, and its TXT keys with the
 * order of their values. The TXT layouts keep the legacy rules for which lines are read: a line with the
 * wrong number of values, or a {@code DrawString} text without quotes, is ignored.
 * <p>
 * The block commands are not in the table, because they hold other commands: YAML {@code If}/{@code Do} and
 * TXT {@code if}/{@code endif}. Neither are the HUD settings, see {@link #IGNORE_AUTO_SCALE}.
 */
public final class HudCommands {

    /** The line of a command in its file, which the readers set before reading its values; not a YAML key. */
    public static final Field<HudOp, Integer> LINE = Field.of("Line", Codecs.INT, 0, HudOp::line);

    /** The spelling of an {@code If} condition, a formula like any other. */
    public static final ValueCodec<String> CONDITION = HudCodecs.FORMULA;

    /**
     * The YAML key of the format version, the first list item of a format 2 file: {@code - SchemaVersion: 2}.
     * A file without it is in the first format.
     */
    public static final String SCHEMA_VERSION = "SchemaVersion";

    /** The YAML spellings of the setting that draws the HUD at its design size; a list item of its own. */
    public static final List<String> IGNORE_AUTO_SCALE = List.of("IgnoreAutoScale", "IgnoreAutoScaleGui",
            "IgnoreScaledGui", "IgnoreAutomaticScale");

    // Exit, Call and Color

    public static final HudCommand<Exit> EXIT = HudCommand.of("Exit", Exit.class,
                    EntrySchema.of("exit", values -> new Exit(values.get(LINE))))
            .txt("exit", values -> values);

    private static final Field<Call, String> CALLED_HUD = Field.of("Hud", HudCodecs.TEXT, null, Call::hud);
    public static final HudCommand<Call> CALL = HudCommand.of("Call", Call.class,
                    EntrySchema.of("call", values -> new Call(values.get(LINE), values.get(CALLED_HUD)), CALLED_HUD)
                            .requires(CALLED_HUD).shorthand(CALLED_HUD))
            .txt("call", values -> values.exactly(1).value(CALLED_HUD));

    private static final Field<Color, List<String>> ARGB = Field.of("Argb", HudCodecs.ARGB, null, Color::argb);
    public static final HudCommand<Color> COLOR = HudCommand.of("Color", Color.class,
                    EntrySchema.of("colour", values -> new Color(values.get(LINE), values.get(ARGB)), ARGB)
                            .requires(ARGB).shorthand(ARGB))
            .txt("color", values -> values.exactly(1, 4).rest(ARGB));

    // DrawString

    private static final Field<DrawString, String> FORMAT = Field.of("Fmt", HudCodecs.TEXT, null,
            DrawString::format).alias("Format").txt(HudCodecs.QUOTED_TEXT);
    private static final Field<DrawString, List<String>> VARIABLES = Field.of("Vars", HudCodecs.NAMES, List.of(),
            DrawString::variables).alias("Variables").alwaysWritten();
    private static final Field<DrawString, HudPoint> STRING_POSITION = position(DrawString::position);
    private static final Field<DrawString, Boolean> CENTERED = Field.of("Center", Codecs.BOOLEAN, false,
            DrawString::centered).alwaysWritten();
    public static final HudCommand<DrawString> DRAW_STRING = HudCommand.of("DrawString", DrawString.class,
                    EntrySchema.of("text", HudCommands::drawString,
                                    Section.of("Text", Function.identity(), FORMAT, VARIABLES), STRING_POSITION,
                                    CENTERED)
                            .requires(STRING_POSITION))
            .txt("drawstring", values -> stringLayout(values).fixed(CENTERED, false))
            .txt("drawcenteredstring", values -> stringLayout(values).fixed(CENTERED, true));

    // DrawRectangle and DrawLine

    private static final Field<DrawRect, HudPoint> RECT_POSITION = position(DrawRect::position);
    private static final Field<DrawRect, HudPoint> RECT_SIZE = size(DrawRect::size);
    public static final HudCommand<DrawRect> DRAW_RECT = HudCommand.of("DrawRectangle", DrawRect.class,
                    EntrySchema.of("rectangle",
                                    values -> new DrawRect(values.get(LINE), values.get(RECT_POSITION),
                                            values.get(RECT_SIZE)),
                                    RECT_POSITION, RECT_SIZE)
                            .requires(RECT_POSITION))
            .alias("DrawRect")
            .txt("drawrect", values -> values.exactly(4).pair(RECT_POSITION).pair(RECT_SIZE));

    private static final Field<DrawLine, List<HudPoint>> POINTS = Field.of("Position", HudCodecs.POINTS, null,
            DrawLine::points).alias("StartPos", "Start");
    private static final Field<DrawLine, String> FACTOR = Field.of("Factor", HudCodecs.FORMULA, "0",
            DrawLine::factor).alias("Fac");
    private static final Field<DrawLine, String> PATTERN = Field.of("Pattern", HudCodecs.FORMULA, "0",
            DrawLine::pattern).alias("Pat");
    private static final Field<DrawLine, Boolean> STRIPED = Field.of("Striped", Codecs.BOOLEAN, false,
            DrawLine::striped).alwaysWritten();
    public static final HudCommand<DrawLine> DRAW_LINE = HudCommand.of("DrawLine", DrawLine.class,
                    EntrySchema.of("line", HudCommands::drawLine, POINTS, FACTOR, PATTERN, STRIPED)
                            .requires(POINTS))
            .txt("drawline", values -> values.accepting(evenCountOfAtLeast(4)).rest(POINTS))
            .txt("drawlinestipple", values -> values.accepting(evenCountOfAtLeast(6))
                    .value(PATTERN).value(FACTOR).rest(POINTS).fixed(STRIPED, true));

    // DrawTexture

    private static final Field<DrawTexture, String> TEXTURE = Field.of("Name", HudCodecs.TEXT, null,
            DrawTexture::texture).alias("Texture");
    private static final Field<DrawTexture, HudPoint> TEXTURE_POSITION = position(DrawTexture::position);
    private static final Field<DrawTexture, HudPoint> TEXTURE_SIZE = size(DrawTexture::size);
    private static final Field<DrawTexture, HudPoint> UV_POSITION = Field.of("UVPos", HudCodecs.POSITION, null,
            DrawTexture::uvPosition);
    private static final Field<DrawTexture, HudPoint> UV_SIZE = Field.of("UVSize", HudCodecs.SIZE, null,
            DrawTexture::uvSize);
    private static final Field<DrawTexture, String> TEXTURE_ROTATION = rotation(DrawTexture::rotation);
    public static final HudCommand<DrawTexture> DRAW_TEXTURE = HudCommand.of("DrawTexture", DrawTexture.class,
                    EntrySchema.of("texture", HudCommands::drawTexture, TEXTURE, TEXTURE_POSITION, TEXTURE_SIZE,
                                    UV_POSITION, UV_SIZE, TEXTURE_ROTATION)
                            .requires(TEXTURE).requires(TEXTURE_POSITION))
            .txt("drawtexture", values -> values.exactly(9, 10).value(TEXTURE).pair(TEXTURE_POSITION)
                    .pair(TEXTURE_SIZE).pair(UV_POSITION).pair(UV_SIZE).value(TEXTURE_ROTATION));

    // DrawRadar, DrawGraduation and CameraRotation

    private static final Field<DrawRadar, HudPoint> RADAR_POSITION = position(DrawRadar::position);
    private static final Field<DrawRadar, HudPoint> RADAR_SIZE = size(DrawRadar::size);
    private static final Field<DrawRadar, String> RADAR_ROTATION = rotation(DrawRadar::rotation);
    private static final Field<DrawRadar, Boolean> ENTITY_RADAR = Field.of("EntityRadar", Codecs.BOOLEAN, false,
            DrawRadar::entityRadar).alwaysWritten();
    public static final HudCommand<DrawRadar> DRAW_RADAR = HudCommand.of("DrawRadar", DrawRadar.class,
                    EntrySchema.of("radar",
                                    values -> new DrawRadar(values.get(LINE), values.get(ENTITY_RADAR),
                                            values.get(RADAR_POSITION), values.get(RADAR_SIZE),
                                            values.get(RADAR_ROTATION)),
                                    RADAR_POSITION, RADAR_SIZE, RADAR_ROTATION, ENTITY_RADAR)
                            .requires(RADAR_POSITION))
            .txt("drawentityradar", values -> radarLayout(values).fixed(ENTITY_RADAR, true))
            .txt("drawenemyradar", values -> radarLayout(values).fixed(ENTITY_RADAR, false));

    private static final Field<DrawGraduation, GraduationType> GRADUATION = Field.of("Type",
            HudCodecs.GRADUATION_TYPE, null, DrawGraduation::type);
    private static final Field<DrawGraduation, HudPoint> GRADUATION_POSITION = position(DrawGraduation::position);
    private static final Field<DrawGraduation, String> GRADUATION_ROTATION = rotation(DrawGraduation::rotation);
    private static final Field<DrawGraduation, String> ROLL = Field.of("Roll", HudCodecs.FORMULA, "0",
            DrawGraduation::roll).alwaysWritten();
    public static final HudCommand<DrawGraduation> DRAW_GRADUATION = HudCommand.of("DrawGraduation",
                    DrawGraduation.class,
                    EntrySchema.of("graduation",
                                    values -> new DrawGraduation(values.get(LINE), values.get(GRADUATION),
                                            values.get(GRADUATION_POSITION), values.get(GRADUATION_ROTATION),
                                            values.get(ROLL)),
                                    GRADUATION, GRADUATION_POSITION, GRADUATION_ROTATION, ROLL)
                            .requires(GRADUATION_POSITION))
            .txt("drawgraduationyaw", values -> graduationLayout(values, GraduationType.YAW))
            .txt("drawgraduationpitch1", values -> graduationLayout(values, GraduationType.PITCH))
            .txt("drawgraduationpitch2", values -> graduationLayout(values, GraduationType.PITCH_ROLL))
            .txt("drawgraduationpitch3", values -> graduationLayout(values, GraduationType.PITCH_ROLL_ALT));

    private static final Field<CameraRotation, HudPoint> CAMERA_POSITION = position(CameraRotation::position);
    public static final HudCommand<CameraRotation> CAMERA_ROTATION = HudCommand.of("CameraRotation",
                    CameraRotation.class,
                    EntrySchema.of("camera rotation",
                                    values -> new CameraRotation(values.get(LINE), values.get(CAMERA_POSITION)),
                                    CAMERA_POSITION)
                            .requires(CAMERA_POSITION))
            .alias("CameraRot")
            .txt("drawcamerarot", values -> values.exactly(2).pair(CAMERA_POSITION));

    /** Every command, in the order the YAML format documents them. */
    public static final List<HudCommand<?>> ALL = List.of(EXIT, CALL, COLOR, DRAW_STRING, DRAW_RECT, DRAW_LINE,
            DRAW_TEXTURE, DRAW_RADAR, DRAW_GRADUATION, CAMERA_ROTATION);

    private static final Map<String, HudCommand<?>> BY_YAML_KEY = new HashMap<>();
    private static final Map<String, TxtForm<?>> BY_TXT_KEY = new HashMap<>();
    private static final Map<Class<?>, HudCommand<?>> BY_TYPE = new HashMap<>();

    static {
        for (HudCommand<?> command : ALL) {
            BY_YAML_KEY.put(command.key(), command);
            command.aliases().forEach(alias -> BY_YAML_KEY.put(alias, command));
            command.txt().forEach(form -> BY_TXT_KEY.put(form.key(), form));
            BY_TYPE.put(command.type(), command);
        }
    }

    private HudCommands() {
    }

    /** The command a YAML key names, by its key or an alias; keys are case sensitive. */
    public static Optional<HudCommand<?>> yaml(String key) {
        return Optional.ofNullable(BY_YAML_KEY.get(key));
    }

    /** The command a lower-case TXT key names, with the layout of its values. */
    public static Optional<TxtForm<?>> txt(String key) {
        return Optional.ofNullable(BY_TXT_KEY.get(key));
    }

    /** The command {@code op} is an instance of; not defined for {@link HudOp.If}. */
    public static HudCommand<?> of(HudOp op) {
        HudCommand<?> command = BY_TYPE.get(op.getClass());
        if (command == null) {
            throw new IllegalArgumentException("not a command of the table: " + op);
        }
        return command;
    }

    /** Every YAML key a command accepts, for suggestions after a typo. */
    public static Set<String> yamlKeys() {
        return BY_YAML_KEY.keySet();
    }

    /** Every TXT key of a command. */
    public static Set<String> txtKeys() {
        return BY_TXT_KEY.keySet();
    }

    private static <O> Field<O, HudPoint> position(Function<O, HudPoint> getter) {
        return Field.of("Position", HudCodecs.POSITION, null, getter).alias("Pos");
    }

    private static <O> Field<O, HudPoint> size(Function<O, HudPoint> getter) {
        return Field.of("Size", HudCodecs.SIZE, null, getter);
    }

    private static <O> Field<O, String> rotation(Function<O, String> getter) {
        return Field.of("Rotation", HudCodecs.FORMULA, "0", getter).alias("Rot").alwaysWritten();
    }

    private static DrawString drawString(Values values) {
        String format = values.get(FORMAT);
        if (format == null) {
            throw new ContentValueException("DrawString needs a 'Text' section with a 'Fmt' key");
        }
        return new DrawString(values.get(LINE), values.get(STRING_POSITION), format, values.get(VARIABLES),
                values.get(CENTERED));
    }

    /** {@code x, y, "format", variables...}; a text that is not in double quotes leaves the line out. */
    private static TxtTuple<DrawString> stringLayout(TxtTuple<DrawString> values) {
        return values.minimum(3).accepting(line -> isQuoted(line[2]))
                .pair(STRING_POSITION).value(FORMAT).rest(VARIABLES);
    }

    private static boolean isQuoted(String text) {
        return text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"");
    }

    private static DrawLine drawLine(Values values) {
        List<HudPoint> points = values.get(POINTS);
        if (points.isEmpty()) {
            throw new ContentValueException("DrawLine needs at least one point in 'Position'");
        }
        return new DrawLine(values.get(LINE), points, values.get(STRIPED), values.get(PATTERN), values.get(FACTOR));
    }

    private static Predicate<String[]> evenCountOfAtLeast(int minimum) {
        return line -> line.length >= minimum && line.length % 2 == 0;
    }

    private static DrawTexture drawTexture(Values values) {
        return new DrawTexture(values.get(LINE), values.get(TEXTURE), values.get(TEXTURE_POSITION),
                values.get(TEXTURE_SIZE), values.get(UV_POSITION), values.get(UV_SIZE), values.get(TEXTURE_ROTATION));
    }

    /** {@code rotation, x, y, width, height}. */
    private static TxtTuple<DrawRadar> radarLayout(TxtTuple<DrawRadar> values) {
        return values.exactly(5).value(RADAR_ROTATION).pair(RADAR_POSITION).pair(RADAR_SIZE);
    }

    /** {@code rotation, roll, x, y}; the TXT key gives the type. */
    private static TxtTuple<DrawGraduation> graduationLayout(TxtTuple<DrawGraduation> values, GraduationType type) {
        return values.exactly(4).value(GRADUATION_ROTATION).value(ROLL).pair(GRADUATION_POSITION)
                .fixed(GRADUATION, type);
    }
}
