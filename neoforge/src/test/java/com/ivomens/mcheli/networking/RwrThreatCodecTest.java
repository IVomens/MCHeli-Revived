package com.ivomens.mcheli.networking;

import com.ivomens.mcheli.combat.guidance.ThreatSeeker;
import com.ivomens.mcheli.combat.weapon.net.RwrSignal;
import com.ivomens.mcheli.combat.weapon.net.RwrThreat;
import com.ivomens.mcheli.combat.weapon.net.RwrThreatKind;
import com.ivomens.mcheli.vehicle.sensor.RWRThreatEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** An RWR threat keeps the seeker of its missile over the network, and a seeker the client does not know is refused. */
class RwrThreatCodecTest {

    @Test
    void everySeekerSurvivesARoundTrip() {
        for (ThreatSeeker seeker : ThreatSeeker.values()) {
            RwrThreat threat = new RwrThreat(42, new RwrThreatKind(RWRThreatEvent.EMITTER_MISSILE,
                    RWRThreatEvent.MODE_MSL_ACTIVE, seeker), "MSL", -35.5F, new RwrSignal(0.75F, 1.0F));
            ByteBuf buf = Unpooled.buffer();
            try {
                RwrThreat.CODEC.encode(buf, threat);
                assertEquals(threat, RwrThreat.CODEC.decode(buf));
                assertEquals(0, buf.readableBytes(), seeker.name());
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void anUnknownSeekerIsRefused() {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeByte(RWRThreatEvent.EMITTER_MISSILE);
            buf.writeByte(RWRThreatEvent.MODE_MSL_ACTIVE);
            buf.writeByte(ThreatSeeker.values().length);
            assertThrows(DecoderException.class, () -> RwrThreatKind.CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }
}
