package com.ivomens.mcheli.config.client;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Key names and codes as used by the MCHeli config.
 * <p>
 * Config values stay in the legacy LWJGL2 key code space so existing config files keep working:
 * positive values are LWJGL2 keyboard codes, negative values are mouse buttons ({@code button - 100}),
 * and values at or above {@link #GLFW_OFFSET} encode a GLFW key that has no LWJGL2 equivalent.
 * {@link #toGlfw(int)} / {@link #fromGlfw(int)} translate between the two spaces.
 */
public final class KeyNames {

    /** Codes at or above this value are {@code GLFW_OFFSET + glfwKey}. */
    public static final int GLFW_OFFSET = 1000;

    private static final KeyNames[] KEYS = new KeyNames[]{
            new KeyNames(0, -1, "NONE", ""),
            new KeyNames(1, 256, "ESCAPE", "Escape"),
            new KeyNames(2, 49, "1", ""),
            new KeyNames(3, 50, "2", ""),
            new KeyNames(4, 51, "3", ""),
            new KeyNames(5, 52, "4", ""),
            new KeyNames(6, 53, "5", ""),
            new KeyNames(7, 54, "6", ""),
            new KeyNames(8, 55, "7", ""),
            new KeyNames(9, 56, "8", ""),
            new KeyNames(10, 57, "9", ""),
            new KeyNames(11, 48, "0", ""),
            new KeyNames(12, 45, "MINUS", "-"),
            new KeyNames(13, 61, "EQUALS", "="),
            new KeyNames(14, 259, "BACK", "Backspace"),
            new KeyNames(15, 258, "TAB", "Tab"),
            new KeyNames(16, 81, "Q", ""),
            new KeyNames(17, 87, "W", ""),
            new KeyNames(18, 69, "E", ""),
            new KeyNames(19, 82, "R", ""),
            new KeyNames(20, 84, "T", ""),
            new KeyNames(21, 89, "Y", ""),
            new KeyNames(22, 85, "U", ""),
            new KeyNames(23, 73, "I", ""),
            new KeyNames(24, 79, "O", ""),
            new KeyNames(25, 80, "P", ""),
            new KeyNames(26, 91, "LBRACKET", "["),
            new KeyNames(27, 93, "RBRACKET", "]"),
            new KeyNames(28, 257, "RETURN", "Enter"),
            new KeyNames(29, 341, "LCONTROL", "L Control"),
            new KeyNames(30, 65, "A", ""),
            new KeyNames(31, 83, "S", ""),
            new KeyNames(32, 68, "D", ""),
            new KeyNames(33, 70, "F", ""),
            new KeyNames(34, 71, "G", ""),
            new KeyNames(35, 72, "H", ""),
            new KeyNames(36, 74, "J", ""),
            new KeyNames(37, 75, "K", ""),
            new KeyNames(38, 76, "L", ""),
            new KeyNames(39, 59, "SEMICOLON", " ;"),
            new KeyNames(40, 39, "APOSTROPHE", "'"),
            new KeyNames(41, 96, "GRAVE", "`"),
            new KeyNames(42, 340, "LSHIFT", "L Shift"),
            new KeyNames(43, 92, "BACKSLASH", "\\\\"),
            new KeyNames(44, 90, "Z", ""),
            new KeyNames(45, 88, "X", ""),
            new KeyNames(46, 67, "C", ""),
            new KeyNames(47, 86, "V", ""),
            new KeyNames(48, 66, "B", ""),
            new KeyNames(49, 78, "N", ""),
            new KeyNames(50, 77, "M", ""),
            new KeyNames(51, 44, "COMMA", ","),
            new KeyNames(52, 46, "PERIOD", "."),
            new KeyNames(53, 47, "SLASH", "/"),
            new KeyNames(54, 344, "RSHIFT", "R Shift"),
            new KeyNames(55, 332, "MULTIPLY", ""),
            new KeyNames(56, 342, "LMENU", "L Menu/Alt"),
            new KeyNames(57, 32, "SPACE", ""),
            new KeyNames(58, 280, "CAPITAL", "Caps Lock"),
            new KeyNames(59, 290, "F1", ""),
            new KeyNames(60, 291, "F2", ""),
            new KeyNames(61, 292, "F3", ""),
            new KeyNames(62, 293, "F4", ""),
            new KeyNames(63, 294, "F5", ""),
            new KeyNames(64, 295, "F6", ""),
            new KeyNames(65, 296, "F7", ""),
            new KeyNames(66, 297, "F8", ""),
            new KeyNames(67, 298, "F9", ""),
            new KeyNames(68, 299, "F10", ""),
            new KeyNames(69, 282, "NUMLOCK", "Number Lock"),
            new KeyNames(70, 281, "SCROLL", "Scroll Lock"),
            new KeyNames(71, 327, "NUMPAD7", ""),
            new KeyNames(72, 328, "NUMPAD8", ""),
            new KeyNames(73, 329, "NUMPAD9", ""),
            new KeyNames(74, 333, "SUBTRACT", ""),
            new KeyNames(75, 324, "NUMPAD4", ""),
            new KeyNames(76, 325, "NUMPAD5", ""),
            new KeyNames(77, 326, "NUMPAD6", ""),
            new KeyNames(78, 334, "ADD", ""),
            new KeyNames(79, 321, "NUMPAD1", ""),
            new KeyNames(80, 322, "NUMPAD2", ""),
            new KeyNames(81, 323, "NUMPAD3", ""),
            new KeyNames(82, 320, "NUMPAD0", ""),
            new KeyNames(83, 330, "DECIMAL", ""),
            new KeyNames(87, 300, "F11", ""),
            new KeyNames(88, 301, "F12", ""),
            new KeyNames(100, 302, "F13", ""),
            new KeyNames(101, 303, "F14", ""),
            new KeyNames(102, 304, "F15", ""),
            new KeyNames(112, -1, "KANA", ""),
            new KeyNames(121, -1, "CONVERT", ""),
            new KeyNames(123, -1, "NOCONVERT", ""),
            new KeyNames(125, -1, "YEN", "¥"),
            new KeyNames(141, 336, "NUMPADEQUALS", ""),
            new KeyNames(144, -1, "CIRCUMFLEX", "^"),
            new KeyNames(145, -1, "AT", "@"),
            new KeyNames(146, -1, "COLON", " :"),
            new KeyNames(147, -1, "UNDERLINE", "_"),
            new KeyNames(148, -1, "KANJI", ""),
            new KeyNames(149, -1, "STOP", ""),
            new KeyNames(150, -1, "AX", ""),
            new KeyNames(151, -1, "UNLABLED", ""),
            new KeyNames(156, 335, "NUMPADENTER", ""),
            new KeyNames(157, 345, "RCONTROL", "R Control"),
            new KeyNames(179, -1, "NUMPADCOMMA", ""),
            new KeyNames(181, 331, "DIVIDE", ""),
            new KeyNames(183, 283, "SYSRQ", ""),
            new KeyNames(184, 346, "RMENU", "R Menu/Alt"),
            new KeyNames(197, 284, "PAUSE", ""),
            new KeyNames(199, 268, "HOME", ""),
            new KeyNames(200, 265, "UP", "Up Arrow"),
            new KeyNames(201, 266, "PRIOR", "Page Up"),
            new KeyNames(203, 263, "LEFT", "Left Arrow"),
            new KeyNames(205, 262, "RIGHT", "Right Arrow"),
            new KeyNames(207, 269, "END", ""),
            new KeyNames(208, 264, "DOWN", "Down Arrow"),
            new KeyNames(209, 267, "NEXT", "Page Down"),
            new KeyNames(210, 260, "INSERT", ""),
            new KeyNames(211, 261, "DELETE", ""),
            new KeyNames(219, 343, "LMETA", "LWIN [3]"),
            new KeyNames(220, 347, "RMETA", "RWIN [3]"),
            new KeyNames(221, 348, "APPS", ""),
            new KeyNames(222, -1, "POWER", ""),
            new KeyNames(223, -1, "SLEEP", ""),
            new KeyNames(-100, -1, "BUTTON0", "Left Click"),
            new KeyNames(-99, -1, "BUTTON1", "Right Click"),
            new KeyNames(-98, -1, "BUTTON2", "Middle Click"),
            new KeyNames(-97, -1, "BUTTON3", ""),
            new KeyNames(-96, -1, "BUTTON4", ""),
            new KeyNames(-95, -1, "BUTTON5", ""),
            new KeyNames(-94, -1, "BUTTON6", ""),
            new KeyNames(-93, -1, "BUTTON7", ""),
            new KeyNames(-92, -1, "BUTTON8", ""),
            new KeyNames(-91, -1, "BUTTON9", ""),
            new KeyNames(-90, -1, "BUTTON10", ""),
            new KeyNames(-89, -1, "BUTTON11", ""),
            new KeyNames(-88, -1, "BUTTON12", ""),
            new KeyNames(-87, -1, "BUTTON13", ""),
            new KeyNames(-86, -1, "BUTTON14", ""),
            new KeyNames(-85, -1, "BUTTON15", "")
    };

    private static final Int2ObjectOpenHashMap<KeyNames> BY_VALUE = new Int2ObjectOpenHashMap<>();
    private static final Int2IntOpenHashMap LWJGL_TO_GLFW = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap GLFW_TO_LWJGL = new Int2IntOpenHashMap();

    static {
        LWJGL_TO_GLFW.defaultReturnValue(GLFW.GLFW_KEY_UNKNOWN);
        GLFW_TO_LWJGL.defaultReturnValue(Integer.MIN_VALUE);
        for (KeyNames k : KEYS) {
            BY_VALUE.putIfAbsent(k.value, k);
            if (k.value > 0 && k.glfw >= 0) {
                LWJGL_TO_GLFW.put(k.value, k.glfw);
                GLFW_TO_LWJGL.putIfAbsent(k.glfw, k.value);
            }
        }
    }

    private final int value;
    private final int glfw;
    private final String name;
    private final String description;

    /**
     * @param value       the config (LWJGL2) code
     * @param glfw        the GLFW key, or -1 when there is none
     * @param description display text; empty to show {@code name}
     */
    private KeyNames(int value, int glfw, String name, String description) {
        this.value = value;
        this.glfw = glfw;
        this.name = name;
        this.description = description;
    }

    /** Legacy (config) keyboard code to GLFW key code; {@code GLFW_KEY_UNKNOWN} if there is none. */
    public static int toGlfw(int legacy) {
        if (legacy >= GLFW_OFFSET) return legacy - GLFW_OFFSET;
        if (legacy <= 0) return GLFW.GLFW_KEY_UNKNOWN;
        return LWJGL_TO_GLFW.get(legacy);
    }

    /** GLFW key code to legacy (config) code. */
    public static int fromGlfw(int glfwKey) {
        if (glfwKey < 0) return 0;
        int legacy = GLFW_TO_LWJGL.get(glfwKey);
        return legacy != Integer.MIN_VALUE ? legacy : GLFW_OFFSET + glfwKey;
    }

    /** GLFW mouse button to legacy (config) code. */
    public static int fromMouseButton(int button) {
        return button - 100;
    }

    /** The binding of a key mapping as a legacy (config) code; 0 for scancodes and unbound keys. */
    public static int fromMapping(KeyMapping mapping) {
        InputConstants.Key key = mapping.key;
        if (key.getType() == InputConstants.Type.MOUSE) {
            return fromMouseButton(key.getValue());
        }
        if (key.getType() == InputConstants.Type.SCANCODE || key.getValue() < 0) {
            return 0;
        }
        return fromGlfw(key.getValue());
    }

    public static String getDescOrName(int value) {
        KeyNames key = BY_VALUE.get(value);
        if (key != null) return key.description.isEmpty() ? key.name : key.description;
        return value >= GLFW_OFFSET ? glfwDisplayName(value - GLFW_OFFSET) : "";
    }

    public static String getDescOrName(KeyMapping key) {
        return getDescOrName(fromMapping(key));
    }

    private static String glfwDisplayName(int glfwKey) {
        return InputConstants.Type.KEYSYM.getOrCreate(glfwKey).getDisplayName().getString().toUpperCase(Locale.ROOT);
    }
}
