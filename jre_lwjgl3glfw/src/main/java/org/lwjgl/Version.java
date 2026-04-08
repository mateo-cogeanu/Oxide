package org.lwjgl;

public final class Version {
    public static final int VERSION_MAJOR = 3;
    public static final int VERSION_MINOR = 4;
    public static final int VERSION_REVISION = 1;
    public static final BuildType BUILD_TYPE = BuildType.STABLE;

    private static final String VERSION_PLAIN =
            VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_REVISION + BUILD_TYPE.postfix;
    private static final String VERSION = VERSION_PLAIN + VersionImpl.find();

    private Version() {}

    public static void main(String[] args) {
        System.out.println(VERSION);
        System.err.println(VERSION_PLAIN);
    }

    public static String getVersion() {
        return VERSION;
    }

    static String createImplementation(String implementationVersion, String implementationBuild) {
        if (implementationVersion == null || implementationBuild == null) return "";

        String implementation = "+" + (
                implementationBuild.startsWith("build") && implementationBuild.length() > 6
                        ? implementationBuild.substring(6)
                        : implementationBuild
        );

        if (implementationVersion.contains("SNAPSHOT") || implementationVersion.contains("snapshot")) {
            return "-snapshot" + implementation;
        }
        return implementation;
    }

    static String findImplementationFromManifest() {
        return null;
    }

    public enum BuildType {
        ALPHA("a"),
        BETA("b"),
        RC("-rc"),
        STABLE("");

        final String postfix;

        BuildType(String postfix) {
            this.postfix = postfix;
        }
    }
}
