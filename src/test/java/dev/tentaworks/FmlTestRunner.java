package dev.tentaworks;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs the JUnit classes that need Forge and Create loaded (build.gradle {@code fmlTest}) inside a dev server, after mod
 * loading, then exits. Forge 1.20.1 has no FML JUnit launcher. Writes JUnit XML to {@code tentaworks.fmltest.reports}
 * and {@code fmltest.properties} (the {@code @Test} methods found by reflection) for the Gradle check.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class FmlTestRunner {
    private static final Logger LOG = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        String reports = System.getProperty("tentaworks.fmltest.reports");
        if (reports == null) return;
        String[] names = System.getProperty("tentaworks.fmltest.classes", "").split(",");
        event.enqueueWork(() -> {
            int status;
            try {
                status = run(Path.of(reports), names);
            } catch (Throwable t) {
                LOG.error("FMLTEST crashed", t);
                status = 2;
            }
            System.exit(status);
        });
    }

    private static int run(Path reports, String[] names) throws Exception {
        ClassLoader loader = FmlTestRunner.class.getClassLoader();
        List<Class<?>> classes = new ArrayList<>();
        int expected = 0;
        for (String name : names) {
            Class<?> c = Class.forName(name.trim(), true, loader);
            classes.add(c);
            expected += (int) Arrays.stream(c.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Test.class)).count();
        }
        Files.createDirectories(reports);
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(classes.stream().map(DiscoverySelectors::selectClass).toList()).build();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        SummaryGeneratingListener summary = new SummaryGeneratingListener();
        try {
            Launcher launcher = LauncherFactory.create(LauncherConfig.builder().enableTestEngineAutoRegistration(false)
                    .addTestEngines(new JupiterTestEngine()).build());
            thread.setContextClassLoader(loader);
            launcher.execute(request, summary, new LegacyXmlReportGeneratingListener(reports, new PrintWriter(System.out)));
        } finally {
            thread.setContextClassLoader(previous);
        }
        TestExecutionSummary s = summary.getSummary();
        StringWriter failures = new StringWriter();
        s.printFailuresTo(new PrintWriter(failures), 20);
        Files.writeString(reports.resolve("fmltest.properties"), "expected=" + expected + "\nfound=" + s.getTestsFoundCount()
                + "\nsucceeded=" + s.getTestsSucceededCount() + "\nfailed=" + s.getTotalFailureCount() + "\nskipped=" + s.getTestsSkippedCount()
                + "\naborted=" + s.getTestsAbortedCount() + "\n");
        LOG.info("FMLTEST classes={} expected={} found={} succeeded={} failed={} skipped={} aborted={}", classes.size(), expected,
                s.getTestsFoundCount(), s.getTestsSucceededCount(), s.getTotalFailureCount(), s.getTestsSkippedCount(), s.getTestsAbortedCount());
        if (s.getTotalFailureCount() > 0) LOG.error("FMLTEST failures:\n{}", failures);
        return 0;
    }
}
