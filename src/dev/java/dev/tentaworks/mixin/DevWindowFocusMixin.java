package dev.tentaworks.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Harness clients open their window without taking the keyboard focus, so the person running the suites keeps working.
 * Dev source set only, added to the mixin config by {@link TentaworksMixinPlugin} when {@code -Dtentaworks.nofocus} is set
 * (GLFW focuses a new window on creation unless told otherwise; FML's early window must be off, see tools/setup_run.sh).
 * A redirect, not an inject: Mixin 0.8.5 (Forge 47) refuses an {@code @Inject} inside a constructor at an INVOKE point.
 */
@Mixin(Window.class)
public class DevWindowFocusMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J", remap = false))
    private long tentaworks$noFocusOnOpen(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        return ImmediateWindowHandler.setupMinecraftWindow(width, height, title, monitor);
    }
}
