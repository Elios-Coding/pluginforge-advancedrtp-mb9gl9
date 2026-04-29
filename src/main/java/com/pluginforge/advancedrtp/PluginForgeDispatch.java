package com.pluginforge.advancedrtp;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

public final class PluginForgeDispatch {

    private PluginForgeDispatch() {}

    private static final int MAX_DEPTH = 8;
    // Hard cap on TOTAL dispatches in a single server tick window. If the
    // plugin ever enters a runaway loop (e.g. a shadowed vanilla command,
    // a listener cycle, or the AI emits a pathological spec), this trips
    // long before Bukkit's 65536-command fatal limit.
    private static final int MAX_DISPATCH_PER_WINDOW = 200;
    private static final long WINDOW_MS = 1000L;

    private static long windowStart = 0L;
    private static int windowCount = 0;
    private static boolean windowWarned = false;

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<HashSet<String>> SEEN =
        ThreadLocal.withInitial(HashSet::new);

    public static boolean isGeneratedCommandActive(String label, String[] args) {
        String normalized = normalize(label, args);
        return normalized != null && SEEN.get().contains(normalized);
    }

    public static void normal(String cmd) {
        run(cmd, false);
    }

    public static void silent(String cmd) {
        run(cmd, true);
    }

    // Global rate-limit check. Returns true if this dispatch should proceed.
    private static synchronized boolean allowGlobal() {
        long now = System.currentTimeMillis();
        if (now - windowStart > WINDOW_MS) {
            windowStart = now;
            windowCount = 0;
            windowWarned = false;
        }
        if (windowCount >= MAX_DISPATCH_PER_WINDOW) {
            if (!windowWarned) {
                Bukkit.getLogger().warning("[AdvancedRtp] dispatch rate limit hit ("
                    + MAX_DISPATCH_PER_WINDOW + "/s) — dropping further commands this window.");
                windowWarned = true;
            }
            return false;
        }
        windowCount++;
        return true;
    }

    private static void run(String cmd, boolean silent) {
        if (cmd == null) return;
        String trimmed = cmd.trim();
        if (trimmed.isEmpty()) return;
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        if (trimmed.isEmpty()) return;

        // Hard cap on nested dispatches per server thread + dedupe identical
        // commands and labels within the same call stack. This prevents the
        // 65536-command runaway loop before Bukkit re-enters a generated
        // command that shadows a vanilla command (e.g. /op dispatching op X).
        int depth = DEPTH.get();
        if (depth >= MAX_DEPTH || depth > 0) {
            Bukkit.getLogger().warning("[AdvancedRtp] dispatch depth limit hit, dropping: " + trimmed);
            return;
        }
        HashSet<String> seen = SEEN.get();
        String key = normalize(trimmed);
        String labelKey = normalize(firstToken(trimmed));
        if (key == null || seen.contains(key) || seen.contains(labelKey)) {
            // Self-loop detected — silently drop the inner duplicate.
            return;
        }

        if (!allowGlobal()) {
            return;
        }

        seen.add(key);
        seen.add(labelKey);
        DEPTH.set(depth + 1);
        try {
            CommandSender sender = silent ? silentSender() : Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(sender, trimmed);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[AdvancedRtp] dispatch failed for '" + trimmed + "': " + t.getMessage());
        } finally {
            seen.remove(key);
            int d = DEPTH.get() - 1;
            DEPTH.set(d);
            if (d <= 0) {
                SEEN.remove();
                DEPTH.remove();
            }
        }
    }

    private static String firstToken(String cmd) {
        if (cmd == null) return "";
        String cleaned = cmd.trim();
        if (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        int space = cleaned.indexOf(' ');
        return space >= 0 ? cleaned.substring(0, space) : cleaned;
    }

    private static String normalize(String cmd) {
        if (cmd == null) return null;
        String cleaned = cmd.trim();
        if (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        cleaned = cleaned.replaceAll("\s+", " ").trim().toLowerCase(Locale.ROOT);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String normalize(String label, String[] args) {
        if (label == null) return null;
        StringBuilder sb = new StringBuilder(label);
        if (args != null) {
            for (String arg : args) {
                if (arg == null || arg.isEmpty()) continue;
                sb.append(' ').append(arg);
            }
        }
        return normalize(sb.toString());
    }

    // Build a ConsoleCommandSender proxy that delegates everything to the real
    // console sender EXCEPT the sendMessage / sendRawMessage family, which we
    // swallow so command feedback ("Made X a server operator", etc.) never
    // reaches the console log or op-broadcast channel.
    private static CommandSender silentSender() {
        final ConsoleCommandSender real = Bukkit.getConsoleSender();
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if (name.equals("sendMessage")
                        || name.equals("sendRawMessage")
                        || name.equals("sendPlainMessage")
                        || name.equals("sendActionBar")) {
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return Boolean.FALSE;
                    return null;
                }
                return method.invoke(real, args);
            }
        };
        return (CommandSender) Proxy.newProxyInstance(
            PluginForgeDispatch.class.getClassLoader(),
            new Class<?>[] { ConsoleCommandSender.class },
            handler
        );
    }
}
