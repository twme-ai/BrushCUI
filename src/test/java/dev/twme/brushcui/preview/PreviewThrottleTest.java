package dev.twme.brushcui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.twme.brushcui.geometry.BlockPos;
import dev.twme.brushcui.geometry.BrushShape;
import dev.twme.brushcui.preview.PreviewThrottle.Decision;

class PreviewThrottleTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final ThrottleSettings SETTINGS = new ThrottleSettings(5, 6, 40);
    private static final int COST = 5;

    @Test
    void firstPreviewIsSentImmediatelyAndNotRepeated() {
        PreviewThrottle throttle = new PreviewThrottle();
        PreviewTarget target = at(0);

        assertEquals(Decision.SEND, throttle.decide(100, target, UpdateMode.FOLLOW, SETTINGS, COST));
        throttle.markSent(100, target, COST);
        for (long tick = 101; tick < 200; tick++) {
            assertEquals(Decision.NONE, throttle.decide(tick, target, UpdateMode.FOLLOW, SETTINGS, COST));
        }
    }

    @Test
    void followModeIsLimitedByMinimumInterval() {
        PreviewThrottle throttle = new PreviewThrottle();
        throttle.decide(0, at(0), UpdateMode.FOLLOW, SETTINGS, COST);
        throttle.markSent(0, at(0), COST);

        int sends = 0;
        for (long tick = 1; tick <= 100; tick++) {
            PreviewTarget moving = at((int) tick); // moves one block every tick
            if (throttle.decide(tick, moving, UpdateMode.FOLLOW, SETTINGS, COST) == Decision.SEND) {
                throttle.markSent(tick, moving, COST);
                sends++;
            }
        }
        assertEquals(20, sends); // 100 ticks / 5 tick interval
    }

    @Test
    void settleModeWaitsUntilMovementStops() {
        PreviewThrottle throttle = new PreviewThrottle();
        throttle.decide(0, at(0), UpdateMode.SETTLE, SETTINGS, COST);
        throttle.markSent(0, at(0), COST);

        for (long tick = 1; tick <= 50; tick++) {
            assertEquals(Decision.NONE, throttle.decide(tick, at((int) tick), UpdateMode.SETTLE, SETTINGS, COST));
        }
        // Stopped at x = 50 on tick 50; settles after 6 ticks.
        for (long tick = 51; tick < 56; tick++) {
            assertEquals(Decision.NONE, throttle.decide(tick, at(50), UpdateMode.SETTLE, SETTINGS, COST));
        }
        assertEquals(Decision.SEND, throttle.decide(56, at(50), UpdateMode.SETTLE, SETTINGS, COST));
    }

    @Test
    void settleModeSendsShapeChangesWithoutWaiting() {
        PreviewThrottle throttle = new PreviewThrottle();
        throttle.decide(0, at(0), UpdateMode.SETTLE, SETTINGS, COST);
        throttle.markSent(0, at(0), COST);

        PreviewTarget rotated = new PreviewTarget(WORLD,
                new BrushShape.Cuboid(new BlockPos(0, 0, 0), new BlockPos(9, 2, 4)));
        assertEquals(Decision.SEND, throttle.decide(5, rotated, UpdateMode.SETTLE, SETTINGS, COST));
    }

    @Test
    void clearsOnceWhenNothingShouldBeShown() {
        PreviewThrottle throttle = new PreviewThrottle();
        assertEquals(Decision.NONE, throttle.decide(0, null, UpdateMode.FOLLOW, SETTINGS, 1));

        throttle.decide(1, at(0), UpdateMode.FOLLOW, SETTINGS, COST);
        throttle.markSent(1, at(0), COST);
        assertEquals(Decision.CLEAR, throttle.decide(2, null, UpdateMode.FOLLOW, SETTINGS, 1));
        throttle.markCleared();
        assertEquals(Decision.NONE, throttle.decide(3, null, UpdateMode.FOLLOW, SETTINGS, 1));
    }

    @Test
    void handshakeResendWaitsForDelayThenResendsSameTarget() {
        PreviewThrottle throttle = new PreviewThrottle();
        throttle.decide(0, at(0), UpdateMode.SETTLE, SETTINGS, COST);
        throttle.markSent(0, at(0), COST);

        throttle.requestResend(100, 5);
        assertEquals(Decision.NONE, throttle.decide(102, at(0), UpdateMode.SETTLE, SETTINGS, COST));
        assertEquals(Decision.SEND, throttle.decide(105, at(0), UpdateMode.SETTLE, SETTINGS, COST));
    }

    @Test
    void resetAfterWorldChangeResends() {
        PreviewThrottle throttle = new PreviewThrottle();
        throttle.decide(0, at(0), UpdateMode.FOLLOW, SETTINGS, COST);
        throttle.markSent(0, at(0), COST);

        throttle.reset();
        assertEquals(Decision.SEND, throttle.decide(10, at(0), UpdateMode.FOLLOW, SETTINGS, COST));
    }

    @Test
    void tokenBucketCapsMessagesPerSecond() {
        ThrottleSettings tight = new ThrottleSettings(1, 0, 16);
        PreviewThrottle throttle = new PreviewThrottle();
        int messages = 0;
        for (long tick = 0; tick < 200; tick++) {
            PreviewTarget moving = at((int) tick);
            if (throttle.decide(tick, moving, UpdateMode.FOLLOW, tight, 16) == Decision.SEND) {
                throttle.markSent(tick, moving, 16);
                messages += 16;
            }
        }
        // One send from the initial full bucket, then one per 20 ticks refill (16 per second):
        // ticks 0, 20, ..., 180.
        assertEquals(16 * 10, messages);
    }

    private static PreviewTarget at(int x) {
        return new PreviewTarget(WORLD, new BrushShape.Cuboid(new BlockPos(x, 0, 0), new BlockPos(x + 4, 2, 9)));
    }
}
