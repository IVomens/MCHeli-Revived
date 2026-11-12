package com.ivomens.mcheli.combat.projectile.client;

import com.ivomens.mcheli.client.model.IModelCustom;

/** A bullet model and the texture name it is drawn with ({@code textures/bullets/<name>.png}). */
public class BulletModel {

    public final String name;
    public final IModelCustom model;

    public BulletModel(String name, IModelCustom model) {
        this.name = name;
        this.model = model;
    }
}
