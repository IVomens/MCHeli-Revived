package com.ivomens.mcheli.content.registry;

/**
 * Names one piece of loaded content: its type and its file name without extension. Names are unique per
 * type, so this is the key run-time bindings (items, client models) are kept under.
 */
public record ContentId(ContentType type, String name) {

    @Override
    public String toString() {
        return this.type.dirName + "/" + this.name;
    }
}
