package dev.hyperears.integration

/**
 * Retail-name catalog for the vivo/iQOO earbud family.
 *
 * A catalog hit selects the family protocol defaults. A concrete adapter may replace those
 * defaults when a model-specific capture proves different wire parameters.
 */
object VivoRetailModelCatalog {
    data class Model(
        val canonicalName: String,
        val aliases: Set<String> = emptySet(),
    ) {
        internal val normalizedNames: Set<String> =
            (aliases + canonicalName).mapTo(linkedSetOf(), ::normalize)
    }

    val models: List<Model> = listOf(
        Model("vivo TWS Air3 Pro"),
        Model("vivo TWS 3e"),
        Model("vivo TWS Air2", aliases = setOf("vivo TWS Air200")),
        Model("vivo TWS 5e"),
        Model("vivo TWS 3 Pro"),
        Model("vivo TWS 3"),
        Model("vivo TWS 2e"),
        Model("vivo TWS 2"),
        Model("vivo TWS 1"),
        Model("vivo TWS A1 Pro"),
        Model("vivo TWS A1"),
        Model("vivo TWS Air Pro"),
        Model("vivo TWS Air"),
        Model("vivo TWS Neo"),
        Model("vivo TWS X1"),
        Model("vivo TWS"),
        Model("iQOO TWS Air Pro"),
        Model("iQOO TWS Air"),
        Model("iQOO TWS 1"),
    )

    private val byNormalizedName = buildMap {
        models.forEach { model ->
            model.normalizedNames.forEach { normalizedName ->
                check(put(normalizedName, model) == null) {
                    "Duplicate vivo retail-name alias: $normalizedName"
                }
            }
        }
    }

    fun find(deviceName: String?): Model? =
        deviceName?.let(::normalize)?.let(byNormalizedName::get)

    fun isFamilyName(deviceName: String?): Boolean {
        val normalized = normalize(deviceName.orEmpty())
        return normalized.startsWith(VIVO_TWS_PREFIX) ||
            normalized.startsWith(IQOO_TWS_PREFIX)
    }

    private fun normalize(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)

    private const val VIVO_TWS_PREFIX = "vivotws"
    private const val IQOO_TWS_PREFIX = "iqootws"
}
