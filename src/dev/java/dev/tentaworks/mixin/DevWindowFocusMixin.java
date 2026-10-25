package dev.tentaworks.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Harness clients open their window without taking the keyboard focus, so the person running the suites keeps working.
 * Dev source set only, added to the mixin config by {@link TentaworksMixinPlugin} when {@code -Dtentaworks.nofocus} is set
 * (GLFW focuses a new window on creation unless told otherwise; FML's early window must be off, see tools/setup_run.sh).
 */
@Mixin(Window.class)
public class DevWindowFocusMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J", remap = false))
    private void tentaworks$noFocusOnOpen(CallbackInfo ci) {
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }
}
