package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.AircraftItem;
import com.ivomens.mcheli.aircraft.VehicleSpec;
import com.ivomens.mcheli.aircraft.VehicleTypes;
import com.ivomens.mcheli.api.vehicle.VehicleKind;
import com.ivomens.mcheli.livery.Livery;
import com.ivomens.mcheli.livery.LiveryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Server side of liveries without a client: a mock player uploads an image through
 * {@link LiveryManager}, boards an aircraft as pilot and paints it; a second player who is not the
 * pilot is turned away.
 */
public class LiveryGameTests {

    private static final String VEHICLE = "ah-64";
    private static final int BOARDING_TICKS = 105;

    public static List<TestFunction> liveries() {
        if (!StressTests.SCENARIO.isEmpty()) return List.of();
        return List.of(new TestFunction("mcheli_livery", "livery_upload_and_apply", "mcheli:empty", 400, 0, true,
                LiveryGameTests::uploadAndApply));
    }

    private static void uploadAndApply(GameTestHelper h) {
        LiveryManager manager = LiveryManager.of(h.getLevel().getServer());
        VehicleSpec info = VehicleTypes.registry(VehicleKind.HELICOPTER).get(VEHICLE).orElse(null);
        if (manager == null || info == null || !(VehicleTypes.item(info).orElse(null) instanceof AircraftItem item)) {
            h.fail("livery manager or " + VEHICLE + " missing");
            return;
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ServerLevel level = h.getLevel();
        Vec3 pos = h.absoluteVec(new Vec3(8, 2, 8));
        AircraftEntity aircraft = item.createAircraft(level, pos.x, pos.y, pos.z, new ItemStack(item));
        if (aircraft == null) {
            h.fail("could not create " + VEHICLE);
            return;
        }
        level.addFreshEntity(aircraft);

        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        pilot.moveTo(pos.x + 2, pos.y, pos.z);
        ServerPlayer passenger = h.makeMockServerPlayerInLevel();
        passenger.moveTo(pos.x - 2, pos.y, pos.z);

        Livery livery = upload(manager, pilot);
        // the first clicks may only open the canopy, so keep clicking like a player would
        for (int t = 5; t <= BOARDING_TICKS; t += 10) {
            h.runAfterDelay(t, () -> {
                if (aircraft.seats().seatIdOf(pilot) < 0) {
                    aircraft.boarding().interact(pilot, InteractionHand.MAIN_HAND);
                }
            });
        }
        h.runAfterDelay(BOARDING_TICKS + 5, () -> {
            expect(aircraft.seats().seatIdOf(pilot) == 0, "could not board " + VEHICLE + " as pilot");
            checkApply(manager, aircraft, pilot, passenger, livery);
            checkSaved(level, aircraft, livery);
            checkDelete(manager, pilot, passenger, livery);
            pilot.stopRiding();
            aircraft.discard();
            h.succeed();
        });
    }

    private static Livery upload(LiveryManager manager, ServerPlayer player) {
        byte[] png = noisePng();
        LiveryManager.UploadStatus status = manager.beginUpload(player, VEHICLE, "Test livery", png.length);
        expect(status.state() == LiveryManager.UploadStatus.State.RECEIVING, "upload refused: " + status.message());
        int offset = 0;
        while (offset < png.length) {
            int end = Math.min(png.length, offset + LiveryManager.MAX_CHUNK);
            status = manager.receiveChunk(player, offset, Arrays.copyOfRange(png, offset, end));
            offset = end;
        }
        expect(status.state() == LiveryManager.UploadStatus.State.DONE && status.livery() != null,
                "upload failed: " + status.message());
        expect(png.length > LiveryManager.MAX_CHUNK, "the test image should need several chunks");

        LiveryManager.UploadStatus stray = manager.receiveChunk(player, 0, new byte[16]);
        expect(stray.state() == LiveryManager.UploadStatus.State.FAILED, "a chunk without an upload was accepted");
        return status.livery();
    }

    private static void checkApply(LiveryManager manager, AircraftEntity aircraft, ServerPlayer pilot,
                                   ServerPlayer passenger, Livery livery) {
        LiveryManager.Result denied = manager.apply(passenger, aircraft, livery.id());
        expect(!denied.success() && aircraft.appearance().liveryId().isEmpty(), "a player who is not the pilot applied a livery");

        LiveryManager.Result applied = manager.apply(pilot, aircraft, livery.id());
        expect(applied.success(), "pilot could not apply: " + applied.message());
        expect(livery.id().equals(aircraft.appearance().liveryId()), "synced livery id not set");

        LiveryManager.Result bogus = manager.apply(pilot, aircraft, "not-a-livery");
        expect(!bogus.success() && livery.id().equals(aircraft.appearance().liveryId()), "an invalid id was accepted");
    }

    private static void checkSaved(ServerLevel level, AircraftEntity aircraft, Livery livery) {
        CompoundTag tag = new CompoundTag();
        aircraft.saveWithoutId(tag);
        expect(livery.id().equals(tag.getString("Livery")), "livery not written to NBT");
        Entity copy = aircraft.getType().create(level);
        expect(copy instanceof AircraftEntity, "could not create a copy of the aircraft");
        copy.load(tag);
        expect(livery.id().equals(((AircraftEntity) copy).appearance().liveryId()), "livery not read back from NBT");
        copy.discard();
    }

    private static void checkDelete(LiveryManager manager, ServerPlayer owner, ServerPlayer other, Livery livery) {
        expect(!manager.delete(other, livery.id()).success(), "someone else deleted the livery");
        expect(manager.delete(owner, livery.id()).success(), "owner could not delete the livery");
        expect(manager.store().get(livery.id()).isEmpty(), "deleted livery still stored");
    }

    /** Random pixels compress badly, so the PNG spans several upload chunks. */
    private static byte[] noisePng() {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, 0xFF000000 | random.nextInt(0x1000000));
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
