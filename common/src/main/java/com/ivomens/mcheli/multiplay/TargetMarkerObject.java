package com.ivomens.mcheli.multiplay;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

public interface TargetMarkerObject {

    static TargetMarkerObject fromEntity(Entity target) {
        return new TargetMarkerObject.EntityWrapper(target);
    }

    double getX();

    double getY();

    double getZ();

    @Nullable
    default Entity getEntity() {
        return null;
    }

    class EntityWrapper implements TargetMarkerObject {

        private final Entity target;

        public EntityWrapper(Entity entity) {
            this.target = entity;
        }

        @Override
        public double getX() {
            return this.target.getX();
        }

        @Override
        public double getY() {
            return this.target.getY();
        }

        @Override
        public double getZ() {
            return this.target.getZ();
        }

        @Override
        public Entity getEntity() {
            return this.target;
        }
    }
}
