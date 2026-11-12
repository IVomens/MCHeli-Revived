/**
 * The {@code :core} module: everything of the mod that needs no Minecraft. It compiles against Java, JOML and
 * the {@code javax.annotation} annotations only, so its code runs and is unit-tested without the game, and
 * the architecture rules check that it uses no Minecraft, Mojang or loader class.
 * <ul>
 *   <li>{@link com.ivomens.mcheli.core.sim}: the vehicle physics;</li>
 *   <li>{@link com.ivomens.mcheli.core.ballistics} and {@link com.ivomens.mcheli.core.guidance}: how
 *       projectiles fly and how seekers lock and steer;</li>
 *   <li>{@link com.ivomens.mcheli.core.hud}: the HUD variables, frame and formula compiler;</li>
 *   <li>{@link com.ivomens.mcheli.core.content.schema}: the content schema engine, and the definitions that
 *       need no Minecraft;</li>
 *   <li>{@link com.ivomens.mcheli.core.wingman.brain}: the wingman autopilot and the airfield and mission
 *       model it decides on;</li>
 *   <li>{@link com.ivomens.mcheli.core.math} and {@link com.ivomens.mcheli.core.target}: small value types
 *       and helpers the rest share.</li>
 * </ul>
 * Common code adapts the game to these types at its boundary; nothing here depends back on it.
 */
package com.ivomens.mcheli.core;
