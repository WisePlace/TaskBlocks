package com.taskblocks.client;

// Shared numeric version comparison: "1.10.0" is correctly newer than
// "1.9.0" (unlike a naive string comparison). Used by the update
// checker and the default scripts installer.
public class VersionUtil {

    public static boolean isNewer(String candidate, String current) {
        String[] candidateParts = candidate.split("\\.");
        String[] currentParts = current.split("\\.");
        int length = Math.max(candidateParts.length, currentParts.length);

        for (int i = 0; i < length; i++) {
            int candidateNum = parsePart(candidateParts, i);
            int currentNum = parsePart(currentParts, i);
            if (candidateNum != currentNum) return candidateNum > currentNum;
        }
        return false;
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}