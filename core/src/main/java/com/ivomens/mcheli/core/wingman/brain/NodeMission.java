package com.ivomens.mcheli.core.wingman.brain;

import com.ivomens.mcheli.core.wingman.mission.MissionNode;

import java.util.List;
import javax.annotation.Nullable;

/** A node-list mission and how far along it the wingman is. */
public final class NodeMission {

    private final List<MissionNode> nodes;
    private int index;

    public NodeMission(List<MissionNode> nodes) {
        this.nodes = List.copyOf(nodes);
    }

    public List<MissionNode> nodes() {
        return this.nodes;
    }

    public int size() {
        return this.nodes.size();
    }

    /** Index of the current node; equal to {@link #size()} once the list is done. */
    public int index() {
        return this.index;
    }

    /** The current node, or null once the list is done. */
    public @Nullable MissionNode current() {
        return this.index < this.nodes.size() ? this.nodes.get(this.index) : null;
    }

    void advance() {
        this.index++;
    }
}
