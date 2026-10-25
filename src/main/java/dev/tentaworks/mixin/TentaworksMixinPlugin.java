package dev.tentaworks.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Adds the dev-only mixins (not in the released jar) to the config when their classes are present and asked for:
 * {@code -Dtentaworks.nofocus} (harness clients open without taking the focus). Does nothing in a release.
 */
public final class TentaworksMixinPlugin implements IMixinConfigPlugin {
    private String mixinPackage;

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        if (System.getProperty("tentaworks.nofocus") == null) return null;
        String name = "DevWindowFocusMixin";
        String path = (mixinPackage + "." + name).replace('.', '/') + ".class";
        ClassLoader loader = TentaworksMixinPlugin.class.getClassLoader();
        return loader != null && loader.getResource(path) != null ? List.of(name) : null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
