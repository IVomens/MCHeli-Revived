package com.ivomens.mcheli.gametest.fixtures;

import com.ivomens.mcheli.aircraft.AircraftEntity;
import com.ivomens.mcheli.aircraft.PartBoundingBox;
import com.ivomens.mcheli.combat.weapon.WeaponGroup;
import com.ivomens.mcheli.helicopter.HelicopterEntity;
import com.ivomens.mcheli.plane.AbstractPlaneEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.UUID;

/**
 * A state a vehicle is saved in for the save fixtures. Each state knows whether a definition can
 * be in it, how to put a freshly created vehicle into it, and what a vehicle loaded from the
 * fixture must look like.
 */
enum FixtureState {

    FRESH {
        @Override
        void apply(AircraftEntity vehicle) {
        }

        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            return expect(vehicle.damage().damageTaken() == 0, "a fresh vehicle is damaged: " + vehicle.damage().damageTaken());
        }
    },
    DAMAGED {
        @Override
        void apply(AircraftEntity vehicle) {
            vehicle.damage().setDamageTaken(vehicle.damage().maxHp() / 3);
        }

        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            return expect(vehicle.damage().damageTaken() == vehicle.damage().maxHp() / 3,
                    "damage " + vehicle.damage().damageTaken() + ", expected " + vehicle.damage().maxHp() / 3);
        }
    },
    FUELLED {
        @Override
        boolean appliesTo(AircraftEntity vehicle) {
            return vehicle.fuel().capacity() > 0;
        }

        @Override
        void apply(AircraftEntity vehicle) {
            vehicle.fuel().setAmount(vehicle.fuel().capacity() / 2);
        }

        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            return expect(vehicle.fuel().amount() == vehicle.fuel().capacity() / 2,
                    "fuel " + vehicle.fuel().amount() + ", expected " + vehicle.fuel().capacity() / 2);
        }
    },
    TRUNK {
        @Override
        boolean appliesTo(AircraftEntity vehicle) {
            return vehicle.getInventory().getSlots() > 1;
        }

        @Override
        void apply(AircraftEntity vehicle) {
            vehicle.getInventory().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 17));
        }

        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            ItemStack stack = vehicle.getInventory().getStackInSlot(1);
            return expect(stack.is(Items.IRON_INGOT) && stack.getCount() == 17, "trunk slot 1 holds " + stack);
        }
    },
    AMMO_USED {
        @Override
        boolean appliesTo(AircraftEntity vehicle) {
            return vehicle.weapons().count() > 0 && vehicle.weapons().get(0).spec() != null
                    && vehicle.weapons().get(0).spec().ammo().maxAmmo() > 2;
        }

        @Override
        void apply(AircraftEntity vehicle) {
            vehicle.weapons().get(0).setReserveAmmo(1);
        }

        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            WeaponGroup weapon = vehicle.weapons().get(0);
            int total = weapon.ammo() + weapon.reserveAmmo();
            int savedTotal = saved.getIntArray("AcWeaponsAmmo")[0];
            return expect(total == savedTotal && total < weapon.spec().ammo().maxAmmo(),
                    "weapon 0 holds " + total + " rounds, the fixture saved " + savedTotal);
        }
    },
    FOLDED {
        @Override
        boolean appliesTo(AircraftEntity vehicle) {
            if (vehicle instanceof HelicopterEntity heli) {
                return heli.helicopterSpec() != null && heli.helicopterSpec().file().kindFeatures().foldableBlades();
            }
            return vehicle instanceof AbstractPlaneEntity plane && plane.wings().isFitted();
        }

        @Override
        void apply(AircraftEntity vehicle) {
            if (vehicle instanceof HelicopterEntity heli) {
                heli.blades().setState(BLADES_FOLDED);
            } else if (vehicle instanceof AbstractPlaneEntity plane) {
                plane.wings().part().setStatusServer(true);
            }
        }

        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            boolean folded = vehicle instanceof HelicopterEntity heli ? heli.blades().isFolded()
                    : ((AbstractPlaneEntity) vehicle).wings().part().getStatus();
            return expect(folded, "not folded after loading");
        }
    },
    VTOL {
        @Override
        boolean appliesTo(AircraftEntity vehicle) {
            return vehicle instanceof AbstractPlaneEntity plane
                    && plane.planeSpec().file().kindFeatures().vtol().enabled()
                    && plane.nozzles().isFitted() && !plane.planeSpec().file().kindFeatures().vtol().byDefault();
        }

        @Override
        void apply(AircraftEntity vehicle) {
            ((AbstractPlaneEntity) vehicle).nozzles().switchTo(true);
        }

        /**
         * VTOL mode is not part of the save today (PartsComponent saves only the wing and
         * hatch bits), so the vehicle comes back with its nozzles down. The fixture pins that.
         */
        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            return expect(!((AbstractPlaneEntity) vehicle).nozzles().isVertical(),
                    "VTOL mode is now restored from the save");
        }
    },
    ERA_POPPED {
        @Override
        boolean appliesTo(AircraftEntity vehicle) {
            for (PartBoundingBox box : vehicle.partBoxes().boxes()) {
                if (box.isERA()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        void apply(AircraftEntity vehicle) {
            for (PartBoundingBox box : vehicle.partBoxes().boxes()) {
                if (box.isERA()) {
                    box.setEraActive(false);
                    return;
                }
            }
        }

        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            for (PartBoundingBox box : vehicle.partBoxes().boxes()) {
                if (box.isERA()) {
                    return expect(!box.eraActive(), "the first ERA tile is intact again");
                }
            }
            return "no ERA tiles after loading";
        }
    },
    /** A player livery painted on, and the definition's second texture where it has one. */
    LIVERY {
        @Override
        void apply(AircraftEntity vehicle) {
            vehicle.appearance().nextTexture();
            vehicle.appearance().setLiveryId(LIVERY_ID);
        }

        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            return expect(LIVERY_ID.equals(vehicle.appearance().liveryId())
                            && vehicle.appearance().texture().equals(saved.getString("TextureName")),
                    "livery " + vehicle.appearance().liveryId() + " / texture " + vehicle.appearance().texture());
        }
    },
    UAV_PAIRED {
        @Override
        boolean appliesTo(AircraftEntity vehicle) {
            return vehicle.isUAV();
        }

        @Override
        void apply(AircraftEntity vehicle) {
            vehicle.uavLink().setPairedStation(STATION);
        }

        @Override
        String verify(AircraftEntity vehicle, CompoundTag saved) {
            UUID station = vehicle.uavLink().pairedStation();
            return expect(STATION.equals(station), "paired station " + station);
        }
    };

    private static final byte BLADES_FOLDED = 0;
    /** A livery id has the shape of a SHA-256 hash; this one is made up. */
    private static final String LIVERY_ID = "0123456789abcdef".repeat(4);
    private static final UUID STATION = UUID.fromString("00000000-0000-4000-8000-00000000cafe");

    boolean appliesTo(AircraftEntity vehicle) {
        return true;
    }

    abstract void apply(AircraftEntity vehicle);

    /** A message saying what is wrong with a vehicle loaded from this state's fixture, or null. */
    abstract String verify(AircraftEntity vehicle, CompoundTag saved);

    String fileName() {
        return name().toLowerCase(Locale.ROOT);
    }

    private static String expect(boolean condition, String message) {
        return condition ? null : message;
    }
}
