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
 * With {@code -Dtentaworks.minimized} as well the window starts minimized (WSLg ignores the focus hints and stacks new windows on top;
 * a minimized client keeps ticking, at the inactivity frame limit, and no stray mouse turns its camera).
 * {@code -Dtentaworks.winpos=x,y} moves the window there (screen coordinates; tools/run_split.sh cascades the clients of parallel shards,
 * a fully covered window may render slower on macOS). Malformed or absent: the window stays where vanilla put it.
 */
@Mixin(Window.class)
public class DevWindowFocusMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J", remap = false))
    private void tentaworks$noFocusOnOpen(CallbackInfo ci) {
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void tentaworks$placeOnOpen(CallbackInfo ci) {
        long window = ((Window) (Object) this).getWindow();
        String pos = System.getProperty("tentaworks.winpos");
        if (pos != null) {
            String[] xy = pos.split(",");
            try {
                GLFW.glfwSetWindowPos(window, Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
            } catch (RuntimeException ignored) {
            }
        }
        if (System.getProperty("tentaworks.minimized") != null) GLFW.glfwIconifyWindow(window);
    }
}
