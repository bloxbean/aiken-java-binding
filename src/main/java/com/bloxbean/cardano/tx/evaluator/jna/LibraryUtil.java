package com.bloxbean.cardano.tx.evaluator.jna;

import com.sun.jna.Platform;

public class LibraryUtil {
    public static final String AIKEN_NATIVE_LIB_NAME = "aiken_client_native_lib_name";

    public static String getAikenWrapperLib() {
        String customNativeLib = getCustomNativeLibName();
        if(customNativeLib != null && !customNativeLib.isEmpty())
            return customNativeLib;

        String libName = "libaiken_jna_wrapper";

        if (Platform.isMac()) {
            libName += ".dylib";
        } else if (Platform.isAndroid()) {
            libName = "aiken_jna_wrapper";
        } else if (Platform.isLinux() || Platform.isFreeBSD() || Platform.isAIX()) {
            libName += ".so";
        } else if (Platform.isWindows() || Platform.isWindowsCE()) {
            libName = "aiken_jna_wrapper.dll";
        }

        return libName;
    }

    private static String getCustomNativeLibName() {
        String nativeLibName = System.getProperty(AIKEN_NATIVE_LIB_NAME);
        if(nativeLibName == null || nativeLibName.isEmpty()) {
            nativeLibName = System.getenv(AIKEN_NATIVE_LIB_NAME);
        }

        return nativeLibName;
    }
}
