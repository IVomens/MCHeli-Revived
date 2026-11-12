package com.ivomens.mcheli.wingman.net;

import com.ivomens.mcheli.networking.Codecs;
import com.ivomens.mcheli.core.wingman.airfield.MarkerType;
import com.ivomens.mcheli.core.wingman.mission.MissionType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** Field codecs and bounds shared by the wingman messages. */
final class WingmanCodecs {

    /** Marker, base, route and runway ids. */
    static final int ID_LENGTH = 256;
    /** Aircraft and mission route names. */
    static final int NAME_LENGTH = 256;
    /** Short labels: wingman states and weapon types. */
    static final int STATE_LENGTH = 64;
    /** One mission node in text form, such as {@code flyto:120,80,-40}. */
    static final int NODE_LENGTH = 256;
    /** Entries of the lists the planner screens show (aircraft, routes, markers). */
    static final int MAX_LIST = 4096;
    static final int MAX_WAYPOINTS = 1024;
    static final int MAX_NODES = 1024;
    static final int MAX_WEAPONS = 64;

    static final StreamCodec<ByteBuf, String> ID = Codecs.utf(ID_LENGTH);
    static final StreamCodec<ByteBuf, String> NAME = Codecs.utf(NAME_LENGTH);
    static final StreamCodec<ByteBuf, String> STATE = Codecs.utf(STATE_LENGTH);
    static final StreamCodec<ByteBuf, MarkerType> MARKER_TYPE = Codecs.enumOf(MarkerType.class);
    static final StreamCodec<ByteBuf, List<String>> WAYPOINT_IDS = Codecs.list(ID, MAX_WAYPOINTS);
    static final StreamCodec<ByteBuf, List<String>> MISSION_NODES = Codecs.list(Codecs.utf(NODE_LENGTH), MAX_NODES);
    static final StreamCodec<ByteBuf, List<String>> WEAPONS = Codecs.list(STATE, MAX_WEAPONS);
    static final StreamCodec<ByteBuf, List<MissionType>> MISSION_TYPES =
            Codecs.list(Codecs.enumOf(MissionType.class), MissionType.values().length);

    private WingmanCodecs() {
    }

    static <T> StreamCodec<ByteBuf, List<T>> listOf(StreamCodec<ByteBuf, T> element) {
        return Codecs.list(element, MAX_LIST);
    }
}
