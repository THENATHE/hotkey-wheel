package com.thenathe.hotkeywheel.qa;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Runs before the fixtures in profiles with Sorted. Sorted 0.1.44 opens its first-run guide over whatever screen is
 * showing once a world has loaded; marking the guides seen keeps them from replacing a fixture's wheel or editor.
 */
public final class QaSetupTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("sorted")) return;
        context.runOnClient(client -> {
            try {
                Class<?> onboarding = Class.forName("com.voxelsprout.common.onboarding.Onboarding");
                Class<?> provider = Class.forName("com.voxelsprout.common.onboarding.OnboardingProvider");
                for (Object guide : (List<?>) onboarding.getMethod("providers").invoke(null)) {
                    provider.getMethod("markDone").invoke(guide);
                }
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot mark Sorted's first-run guide seen", failure);
            }
        });
    }
}
