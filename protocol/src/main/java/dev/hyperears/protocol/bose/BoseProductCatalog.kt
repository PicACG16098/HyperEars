package dev.hyperears.protocol.bose

/** Publicly documented Bose BMAP headset subset eligible for HyperEars integration. */
object BoseProductCatalog {
    enum class Category {
        HEADPHONES,
        EARBUDS,
    }

    data class Product(
        val productId: Int,
        val codename: String,
        val displayName: String,
        val category: Category,
    )

    val QUIETCOMFORT_35 =
        Product(0x400C, "wolfcastle", "Bose QuietComfort 35", Category.HEADPHONES)
    val HEARPHONES =
        Product(0x4015, "stetson", "Bose Hearphones", Category.HEADPHONES)
    val QUIETCOMFORT_35_II =
        Product(0x4020, "baywolf", "Bose QuietComfort 35 II", Category.HEADPHONES)
    val PROFLIGHT =
        Product(0x4021, "atlas", "Bose ProFlight", Category.HEADPHONES)
    val NC_HEADPHONES_700 = Product(
        0x4024,
        "goodyear",
        "Bose Noise Cancelling Headphones 700",
        Category.HEADPHONES,
    )
    val HEARPHONES_II =
        Product(0x402B, "beanie", "Bose Hearphones II", Category.HEADPHONES)
    val QUIETCOMFORT_45 =
        Product(0x4039, "duran", "Bose QuietComfort 45", Category.HEADPHONES)
    val QUIETCOMFORT_ULTRA_HEADPHONES = Product(
        0x4066,
        "lonestarr",
        "Bose QuietComfort Ultra Headphones",
        Category.HEADPHONES,
    )
    val QUIETCOMFORT_HEADPHONES = Product(
        0x4075,
        "prince",
        "Bose QuietComfort Headphones",
        Category.HEADPHONES,
    )
    val QUIETCOMFORT_ULTRA_HEADPHONES_2 = Product(
        0x4082,
        "wolverine",
        "Bose QuietComfort Ultra Headphones (2nd Gen)",
        Category.HEADPHONES,
    )

    val SOUNDSPORT =
        Product(0x4012, "ice", "Bose SoundSport", Category.EARBUDS)
    val SOUNDSPORT_PULSE =
        Product(0x4013, "flurry", "Bose SoundSport Pulse", Category.EARBUDS)
    val QUIETCONTROL_30 =
        Product(0x4014, "powder", "Bose QuietControl 30", Category.EARBUDS)
    val SOUNDSPORT_FREE =
        Product(0x4018, "levi", "Bose SoundSport Free", Category.EARBUDS)
    val SPORT_EARBUDS =
        Product(0x402D, "revel", "Bose Sport Earbuds", Category.EARBUDS)
    val QUIETCOMFORT_EARBUDS =
        Product(0x402F, "lando", "Bose QuietComfort Earbuds", Category.EARBUDS)
    val SPORT_OPEN_EARBUDS =
        Product(0x403A, "gwen", "Bose Sport Open Earbuds", Category.EARBUDS)
    val QUIETCOMFORT_ULTRA_EARBUDS_2 = Product(
        0x4062,
        "edith",
        "Bose QuietComfort Ultra Earbuds (2nd Gen)",
        Category.EARBUDS,
    )
    val QUIETCOMFORT_EARBUDS_II = Product(
        0x4064,
        "smalls",
        "Bose QuietComfort Earbuds II",
        Category.EARBUDS,
    )
    val ULTRA_OPEN_EARBUDS =
        Product(0x4068, "serena", "Bose Ultra Open Earbuds", Category.EARBUDS)
    val QUIETCOMFORT_ULTRA_EARBUDS = Product(
        0x4072,
        "scotty",
        "Bose QuietComfort Ultra Earbuds",
        Category.EARBUDS,
    )

    val products: List<Product> = listOf(
        QUIETCOMFORT_35,
        HEARPHONES,
        QUIETCOMFORT_35_II,
        PROFLIGHT,
        NC_HEADPHONES_700,
        HEARPHONES_II,
        QUIETCOMFORT_45,
        QUIETCOMFORT_ULTRA_HEADPHONES,
        QUIETCOMFORT_HEADPHONES,
        QUIETCOMFORT_ULTRA_HEADPHONES_2,
        SOUNDSPORT,
        SOUNDSPORT_PULSE,
        QUIETCONTROL_30,
        SOUNDSPORT_FREE,
        SPORT_EARBUDS,
        QUIETCOMFORT_EARBUDS,
        SPORT_OPEN_EARBUDS,
        QUIETCOMFORT_ULTRA_EARBUDS_2,
        QUIETCOMFORT_EARBUDS_II,
        ULTRA_OPEN_EARBUDS,
        QUIETCOMFORT_ULTRA_EARBUDS,
    )

    private val byProductId = products.associateBy(Product::productId)

    init {
        require(byProductId.size == products.size) {
            "Bose BMAP product IDs must be unique"
        }
    }

    fun find(productId: Int): Product? = byProductId[productId]
}
