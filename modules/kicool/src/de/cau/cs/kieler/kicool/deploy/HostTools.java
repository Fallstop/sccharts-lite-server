/*
 * sccharts-lite: locating the host tools the compiler processors spawn.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package de.cau.cs.kieler.kicool.deploy;

import java.io.File;

/**
 * Where the deploy processors find the external tools they run.
 *
 * <p>The C compiler comes from the {@code sccharts.cc} system property or the {@code SCCHARTS_CC}
 * environment variable, so the client that launches the server can point at a compiler that is not
 * on PATH (for example a downloaded toolchain). {@code gcc} on PATH is the fallback.
 *
 * <p>The Java tools are taken from the runtime that is executing the server whenever it ships them,
 * so a bundled runtime with {@code jdk.compiler} and {@code jdk.jartool} needs no JDK on PATH.
 * Otherwise the bare tool names are used and PATH decides.
 */
public final class HostTools {

    private HostTools() {
    }

    public static String cCompiler() {
        String configured = System.getProperty("sccharts.cc");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("SCCHARTS_CC");
        }
        return configured == null || configured.isBlank() ? "gcc" : configured.trim();
    }

    public static String javaLauncher() {
        return runtimeTool("java");
    }

    public static String javaCompiler() {
        return runtimeTool("javac");
    }

    public static String jarTool() {
        return runtimeTool("jar");
    }

    /** The named tool from the running JVM's {@code bin} directory, or its bare name when absent. */
    static String runtimeTool(String name) {
        String home = System.getProperty("java.home");
        if (home != null && !home.isEmpty()) {
            boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
            File tool = new File(new File(home, "bin"), windows ? name + ".exe" : name);
            if (tool.isFile()) {
                return tool.getAbsolutePath();
            }
        }
        return name;
    }
}
