package com.ivomens.mcheli.entity;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

public interface SinglePassenger {

    @Nullable
    Entity getRiddenByEntity();
}
