package com.elfmcys.yesstevemodel.resource;

/** Minimal replacement for the OpenYSM folder deserializer: only the animation-type keys are needed. */
public final class YSMFolderDeserializer {
    private YSMFolderDeserializer() {
    }

    public static String getAnimKeyFromType(int type) {
        return switch (type) {
            case 1 -> "main";
            case 2 -> "arm";
            case 3 -> "extra";
            case 4 -> "tac";
            case 5 -> "arrow";
            case 6 -> "carryon";
            case 7 -> "parcool";
            case 8 -> "swem";
            case 9 -> "slashblade";
            case 10 -> "tlm";
            case 11 -> "fp_arm";
            case 12 -> "immersive_melodies";
            case 13 -> "irons_spell_books";
            default -> "unknown";
        };
    }
}
