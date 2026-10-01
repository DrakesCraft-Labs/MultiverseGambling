package com.chagui68.multiversegambling.world.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chagui68.multiversegambling.world.CasinoLayout;

import org.junit.jupiter.api.Test;

/**
 * Every show is designed facing an audience on its {@code +z}; the frame has to turn it
 * towards the main gate of its pavilion, exactly and without mirroring it.
 */
class StageFrameTest {

    private static final double EPSILON = 1e-9;

    @Test
    void theAudienceSideAlwaysFacesTheMainGate() {
        // Local +z one block forward must land on the side of the gate.
        assertEquals(1.0, StageFrame.facing(CasinoLayout.Edge.SOUTH).worldZ(0, 1), EPSILON);
        assertEquals(-1.0, StageFrame.facing(CasinoLayout.Edge.NORTH).worldZ(0, 1), EPSILON);
        assertEquals(1.0, StageFrame.facing(CasinoLayout.Edge.EAST).worldX(0, 1), EPSILON);
        assertEquals(-1.0, StageFrame.facing(CasinoLayout.Edge.WEST).worldX(0, 1), EPSILON);
    }

    @Test
    void theFrameIsATurnNotAMirror() {
        for (int quarter = 0; quarter < 4; quarter++) {
            StageFrame frame = new StageFrame(quarter);
            // A turn keeps the determinant of the axes at +1; a mirror would flip it to -1.
            double xx = frame.worldX(1, 0);
            double xz = frame.worldZ(1, 0);
            double zx = frame.worldX(0, 1);
            double zz = frame.worldZ(0, 1);
            assertEquals(1.0, xx * zz - xz * zx, EPSILON, "quarter " + quarter + " mirrors the show");
            assertEquals(1.0, xx * xx + xz * xz, EPSILON);
        }
    }

    @Test
    void theYawOfTheFrameLooksAlongItsAudienceAxis() {
        for (int quarter = 0; quarter < 4; quarter++) {
            StageFrame frame = new StageFrame(quarter);
            double yaw = Math.toRadians(frame.yaw());
            // Minecraft looks along (-sin(yaw), cos(yaw)).
            assertEquals(frame.worldX(0, 1), -Math.sin(yaw), EPSILON, "quarter " + quarter);
            assertEquals(frame.worldZ(0, 1), Math.cos(yaw), EPSILON, "quarter " + quarter);
        }
    }

    @Test
    void aWatcherInFrontLooksBackAtTheShow() {
        for (int quarter = 0; quarter < 4; quarter++) {
            StageFrame frame = new StageFrame(quarter);
            double yaw = Math.toRadians(frame.lookYaw(0, 14, 0, 0));
            assertEquals(-frame.worldX(0, 1), -Math.sin(yaw), EPSILON);
            assertEquals(-frame.worldZ(0, 1), Math.cos(yaw), EPSILON);
        }
    }

    @Test
    void theAudienceFansOutInFrontOfTheShow() {
        assertEquals(0.0, ArenaStage.audienceAngle(0, 1), EPSILON, "a single watcher stands straight in front");
        double previous = -Math.PI;
        for (int index = 0; index < 8; index++) {
            double angle = ArenaStage.audienceAngle(index, 8);
            assertTrue(angle > previous, "watchers overlap");
            assertTrue(Math.abs(angle) <= ArenaStage.AUDIENCE_ARC + EPSILON, "a watcher stands behind the show");
            previous = angle;
        }
        assertEquals(-ArenaStage.audienceAngle(7, 8), ArenaStage.audienceAngle(0, 8), EPSILON);
    }
}
