package com.ivomens.mcheli.gametest;

import com.ivomens.mcheli.multiplay.OperatorRequests;
import com.ivomens.mcheli.multiplay.net.ScreenshotChunkHandler;
import com.ivomens.mcheli.multiplay.net.ScreenshotChunkMessage;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Server-side checks of the operator tools in the multiplay package. */
public class MultiplayGameTests {

    public static List<TestFunction> multiplay() {
        if (!StressTests.SCENARIO.isEmpty()) return List.of();
        return List.of(new TestFunction("mcheli_multiplay", "screenshot_upload_needs_request", "mcheli:empty", 20, 0, true,
                MultiplayGameTests::screenshotUploadNeedsRequest));
    }

    /** A screenshot chunk is ignored unless an operator asked that player for one, and a request covers one upload. */
    @SuppressWarnings("removal")
    private static void screenshotUploadNeedsRequest(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        OperatorRequests requests = OperatorRequests.of(h.getLevel().getServer());
        requests.resetUpload();
        // a total far larger than the chunk keeps the upload open, so nothing is written to disk
        new ScreenshotChunkHandler().handle(new ScreenshotChunkMessage(0, 1024, new byte[16]), player);
        if (requests.upload() != null) {
            h.fail("an unrequested upload was accepted");
            return;
        }
        requests.requestScreenshot(player);
        new ScreenshotChunkHandler().handle(new ScreenshotChunkMessage(0, 1024, new byte[16]), player);
        if (requests.upload() == null) {
            h.fail("a requested upload was refused");
            return;
        }
        requests.resetUpload();
        new ScreenshotChunkHandler().handle(new ScreenshotChunkMessage(0, 1024, new byte[16]), player);
        boolean reused = requests.upload() != null;
        requests.resetUpload();
        player.discard();
        if (reused) {
            h.fail("one request allowed a second upload");
        } else {
            h.succeed();
        }
    }
}
