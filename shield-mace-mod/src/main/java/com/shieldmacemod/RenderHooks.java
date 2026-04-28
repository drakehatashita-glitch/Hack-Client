package com.shieldmacemod;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * Visual / render hacks: ESP boxes, tracers, X-Ray ore highlights and
 * name-tag wallhacks.
 *
 * NOTE: Minecraft 1.21.11 replaced the legacy {@code BufferRenderer} +
 * {@code ShaderProgramKeys} pipeline with a brand-new render-pipeline
 * system. Re-implementing line / box drawing on top of that new pipeline
 * is a substantial undertaking that did not fit in this build, so the
 * actual rendering is currently a no-op. The toggles still flip cleanly
 * and emit chat-status messages so the rest of the cheat surface keeps
 * working — this stub exists so the mod compiles and ships, and it can
 * be filled in later without touching anything else.
 */
public final class RenderHooks {
    private RenderHooks() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RenderHooks::onWorldRender);
    }

    private static void onWorldRender(WorldRenderContext ctx) {
        // intentionally empty — see class javadoc
    }
}
