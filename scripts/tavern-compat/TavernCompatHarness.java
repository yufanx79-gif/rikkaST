import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;

/**
 * Pure-JVM verification harness for task-3:
 * 1) ModeInjectionSerializer.importPresetBundleFromString(猫神 preset)
 * 2) TavernExtensionsKt.installAllFromZipFile(all-needs.zip) + buildRuntimeExtensionsJson()
 *
 * Compiled against already-built debug classes, so it never invokes Gradle assemble/package.
 */
public final class TavernCompatHarness {
    private static final String PRESET_PATH = environment("CAT_GOD_PRESET", "E:/Localsend download/【小猫之神·喵赛亚】1.5.json");
    private static final String ALL_NEEDS_PATH = environment("ALL_NEEDS_ZIP", "E:/Localsend download/all-needs.zip");
    private static final String TEMP_ROOT = System.getProperty("java.io.tmpdir") + "/rikka-tavern-compat-jvm";

    public static void main(String[] args) throws Exception {
        verifyPreset();
        verifyAllNeedsInstall();
    }

    private static String environment(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void verifyPreset() throws Exception {
        System.out.println("=== PRESET IMPORT ===");
        String json = Files.readString(new File(PRESET_PATH).toPath(), StandardCharsets.UTF_8);
        Class<?> serializer = Class.forName("me.rerere.rikkahub.data.export.ModeInjectionSerializer");
        Object instance = serializer.getField("INSTANCE").get(null);
        Method target = null;
        for (Method m : serializer.getMethods()) {
            if (m.getName().startsWith("importPresetBundleFromString")) {
                target = m;
                break;
            }
        }
        if (target == null) throw new IllegalStateException("importPresetBundleFromString not found");
        Object boxed = target.invoke(instance, json, "【小猫之神·喵赛亚】1.5");
        Object value = resultValue(boxed);
        if (value == null) {
            Throwable error = resultException(boxed);
            throw new IllegalStateException("preset import failed", error);
        }
        Object injections = invokeGetter(value, "getInjections");
        Object enabled = invokeGetter(value, "getPresetScripts");
        Object regex = invokeGetter(value, "getRegexScripts");
        Object raw = invokeGetter(value, "getRawPresetJson");
        Object warnings = invokeGetter(value, "getWarnings");
        Object unsupported = invokeGetter(value, "getUnsupportedExtensions");
        System.out.println("presetGroup=" + invokeGetter(value, "getPresetGroup"));
        System.out.println("injections=" + ((List<?>) injections).size());
        long enabledCount = ((List<?>) injections).stream().filter(item -> {
            try {
                return Boolean.TRUE.equals(invokeGetter(item, "getEnabled"));
            } catch (Exception e) {
                return false;
            }
        }).count();
        System.out.println("enabledInjections=" + enabledCount);
        System.out.println("presetScripts=" + ((List<?>) enabled).size());
        System.out.println("scriptsFieldState=" + invokeGetter(value, "getScriptsFieldState"));
        System.out.println("regexScripts=" + ((List<?>) regex).size());
        System.out.println("regexFieldState=" + invokeGetter(value, "getRegexFieldState"));
        System.out.println("unsupportedExtensions=" + unsupported);
        try {
            Class<?> stateClass = Class.forName("me.rerere.rikkahub.data.export.PresetSubfieldState");
            Object state = invokeGetter(value, "getRegexFieldState");
            Method merge = serializer.getMethod(
                "mergePresetRegexScripts", List.class, String.class, List.class, stateClass);
            Object pair = merge.invoke(instance, new java.util.ArrayList<>(), "verify-preset", regex, state);
            Object mergedList = ((kotlin.Pair<?, ?>) pair).getFirst();
            Object mergedAdded = ((kotlin.Pair<?, ?>) pair).getSecond();
            System.out.println("mergedRegexScripts=" + ((List<?>) mergedList).size() + " mergedAdded=" + mergedAdded);
        } catch (Throwable mergeError) {
            System.out.println("mergedRegexScripts=ERROR " + mergeError);
        }
        System.out.println("warnings=" + warnings);
        String rawText = raw == null ? "" : raw.toString();
        System.out.println("rawPresetBytes=" + rawText.getBytes(StandardCharsets.UTF_8).length);
        System.out.println("rawHasSPreset=" + rawText.contains("\"SPreset\""));
        System.out.println("rawHasRegexScripts=" + rawText.contains("\"regex_scripts\""));
        System.out.println("rawHasTavernHelper=" + rawText.contains("\"tavern_helper\""));
        System.out.println("PRESET_IMPORT_PASS=" + (
            ((List<?>) injections).size() == 45
                && ((List<?>) enabled).size() == 1
                && ((List<?>) regex).size() == 14
                && ((List<?>) unsupported).isEmpty()
                && ((List<?>) warnings).isEmpty()
                && rawText.contains("\"SPreset\"")
                && rawText.contains("\"regex_scripts\"")
                && rawText.contains("\"tavern_helper\"")
        ));
    }

    private static void verifyAllNeedsInstall() throws Exception {
        System.out.println("=== ALL-NEEDS INSTALL ===");
        File dest = new File(TEMP_ROOT, "extensions-" + System.nanoTime());
        dest.mkdirs();
        Class<?> kt = Class.forName("me.rerere.rikkahub.data.st.extensions.TavernExtensionsKt");
        Method install = kt.getMethod("installAllFromZipFile", File.class, File.class, String.class);
        Object boxed = install.invoke(null, new File(ALL_NEEDS_PATH), dest, "all-needs");
        Object value = resultValue(boxed);
        if (value == null) {
            Throwable error = resultException(boxed);
            throw new IllegalStateException("all-needs install failed", error);
        }
        List<?> installed = (List<?>) value;
        System.out.println("dest=" + dest.getAbsolutePath());
        System.out.println("installedCount=" + installed.size());
        boolean jsrInstalled = false;
        for (Object info : installed) {
            String folder = String.valueOf(invokeGetter(info, "getFolder"));
            String display = String.valueOf(invokeGetter(info, "getDisplayName"));
            String version = String.valueOf(invokeGetter(info, "getVersion"));
            String js = String.valueOf(invokeGetter(info, "getJs"));
            System.out.println("installed: folder=" + folder + " display=" + display + " version=" + version + " js=" + js);
            if (folder.toLowerCase().contains("slash-runner")
                    || display.toLowerCase().contains("slash-runner")
                    || display.contains("酒馆助手")) {
                jsrInstalled = true;
            }
        }
        Method list = kt.getMethod("listExtensions", File.class);
        Object extensions = list.invoke(null, dest);
        Method runtimeJson = kt.getMethod("buildRuntimeExtensionsJson", List.class, java.util.Set.class);
        Object json = runtimeJson.invoke(null, extensions, new HashSet<String>());
        String runtimeText = String.valueOf(json);
        System.out.println("runtimeExtensionsCount=" + ((List<?>) extensions).size());
        System.out.println("runtimeJsonBytes=" + runtimeText.getBytes(StandardCharsets.UTF_8).length);
        System.out.println("runtimeHasJsrFolder=" + runtimeText.contains("JS-Slash-Runner"));
        System.out.println("runtimeHasJsrDisplay=" + runtimeText.contains("酒馆助手"));
        System.out.println("runtimeHasJsrJsDist=" + runtimeText.contains("\"js\":\"dist/index.js\""));
        int jsrIdx = runtimeText.indexOf("JS-Slash-Runner");
        if (jsrIdx >= 0) {
            System.out.println("runtimeJsrEntry=" + runtimeText.substring(Math.max(0, jsrIdx - 40), Math.min(runtimeText.length(), jsrIdx + 260)));
        }
        System.out.println("jsrInstalled=" + jsrInstalled);
        System.out.println("ALL_NEEDS_INSTALL_PASS=" + (installed.size() == 5 && jsrInstalled && runtimeText.contains("JS-Slash-Runner")));
    }

    private static Object invokeGetter(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    /** Kotlin Result is an inline value class: unbox-impl() gives the underlying value when boxed. */
    private static Object resultValue(Object boxed) throws Exception {
        if (boxed == null) return null;
        Object underlying;
        try {
            underlying = boxed.getClass().getMethod("unbox-impl").invoke(boxed);
        } catch (NoSuchMethodException ignored) {
            underlying = boxed;
        }
        if (underlying != null && underlying.getClass().getName().equals("kotlin.Result$Failure")) return null;
        return underlying;
    }

    private static Throwable resultException(Object boxed) throws Exception {
        if (boxed == null) return null;
        Object underlying;
        try {
            underlying = boxed.getClass().getMethod("unbox-impl").invoke(boxed);
        } catch (NoSuchMethodException ignored) {
            underlying = boxed;
        }
        if (underlying == null || !underlying.getClass().getName().equals("kotlin.Result$Failure")) return null;
        return (Throwable) underlying.getClass().getField("exception").get(underlying);
    }
}


