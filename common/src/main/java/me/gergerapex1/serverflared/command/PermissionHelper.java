package me.gergerapex1.serverflared.command;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PermissionHelper {
    private PermissionHelper() {
    }

    /**
     * Checks LuckPerms via reflection if present on the server.
     *
     * @param playerUuid The player's UUID
     * @param node       The permission node (e.g., "serverflared.admin")
     * @return Boolean.TRUE if explicitly granted, Boolean.FALSE if explicitly denied, or null if undefined/unavailable
     */
    public static Boolean checkLuckPerms(UUID playerUuid, String node) {
        if (playerUuid == null || node == null) {
            return null;
        }
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object luckPerms = providerClass.getMethod("get").invoke(null);
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, playerUuid);
            if (user == null) {
                return null;
            }
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object permissionData = cachedData.getClass().getMethod("getPermissionData").invoke(cachedData);
            Object tristate = permissionData.getClass().getMethod("checkPermission", String.class).invoke(permissionData, node);
            if (tristate != null) {
                String stateName = tristate.toString();
                if ("TRUE".equalsIgnoreCase(stateName)) {
                    return Boolean.TRUE;
                }
                if ("FALSE".equalsIgnoreCase(stateName)) {
                    return Boolean.FALSE;
                }
            }
        } catch (Throwable ignored) {
            // LuckPerms not installed or not loaded
        }
        return null;
    }

    /**
     * Checks Fabric Permissions API (me.lucko.fabric.api.permissions.v0.Permissions) via reflection if present.
     *
     * @param sourceOrEntity The CommandSourceStack or Entity
     * @param node           The permission node
     * @param defaultOpLevel Fallback OP level
     * @return Boolean result if Fabric Permissions API is available, or null if not available
     */
    public static Boolean checkFabricPermissionsApi(Object sourceOrEntity, String node, int defaultOpLevel) {
        if (sourceOrEntity == null || node == null) {
            return null;
        }
        try {
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            for (Method method : permissionsClass.getMethods()) {
                if ("check".equals(method.getName()) && method.getParameterCount() == 3) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0].isAssignableFrom(sourceOrEntity.getClass())
                            && params[1] == String.class
                            && params[2] == int.class) {
                        Object res = method.invoke(null, sourceOrEntity, node, defaultOpLevel);
                        if (res instanceof Boolean) {
                            return (Boolean) res;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // fabric-permissions-api not present
        }
        return null;
    }
}
