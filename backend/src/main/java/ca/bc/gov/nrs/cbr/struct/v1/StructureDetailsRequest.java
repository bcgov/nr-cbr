package ca.bc.gov.nrs.cbr.struct.v1;

/**
 * An edit to the Details tab's Structure card — legacy's fields above the bridge and culvert ones.
 *
 * @param builtByCode         {@code BUILT_BY_CODE} ("Built For"), optional
 * @param yearFabricated      {@code YEAR_FABRICATED}: not after this year or Year Installed
 * @param yearBuilt           {@code YEAR_BUILT} ("Year Superstructure Installed")
 * @param inventoryAddedYear  {@code INVENTORY_ADDED_YEAR}: required for the sources IR, INSR, ITM,
 *                            IFS and IIO
 * @param sourceCode          {@code STRUCTURE_SOURCE_CODE}: required once the structure has one
 * @param endOfDesignLifeYear {@code ORIGINAL_REPLACEMENT_DATE}, a year
 * @param asBuiltInfoOnFile   {@code AS_BUILT_INFO_PRESENT_IND}, required
 * @param installationCost    {@code INSTALLATION_COST}: whole dollars, 0 to 999,999
 * @param materialCost        {@code MATERIAL_COST}: whole dollars, 0 to 999,999
 */
public record StructureDetailsRequest(
    String builtByCode,
    Integer yearFabricated,
    Integer yearBuilt,
    Integer inventoryAddedYear,
    String sourceCode,
    Integer endOfDesignLifeYear,
    Boolean asBuiltInfoOnFile,
    Long installationCost,
    Long materialCost) {}
