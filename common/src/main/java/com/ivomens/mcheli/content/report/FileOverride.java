package com.ivomens.mcheli.content.report;

/**
 * A content file that replaced another of the same type and name: the later pack's file wins, whole (see
 * docs/ADDON_AUTHORING.md, "Overriding content").
 *
 * @param type       the content type folder, such as {@code weapons}
 * @param name       the name both files share (the file name without extension)
 * @param winnerPack the domain of the pack whose file is used
 * @param winnerFile its path inside that pack
 * @param loserPack  the domain of the pack whose file is replaced
 * @param loserFile  its path inside that pack
 */
public record FileOverride(String type, String name, String winnerPack, String winnerFile, String loserPack,
                           String loserFile) {

    /** Whether one pack holds both files, such as {@code foo.txt} next to {@code foo.yml}. */
    public boolean withinOnePack() {
        return this.winnerPack.equals(this.loserPack);
    }

    /** How the report and the log say it. */
    public String describe() {
        if (withinOnePack()) {
            return this.type + " '" + this.name + "': pack '" + this.winnerPack + "' has two files with this name; '"
                    + this.winnerFile + "' is used and '" + this.loserFile + "' is ignored";
        }
        return this.type + " '" + this.name + "': '" + this.winnerFile + "' in pack '" + this.winnerPack
                + "' replaces '" + this.loserFile + "' in pack '" + this.loserPack + "'";
    }
}
