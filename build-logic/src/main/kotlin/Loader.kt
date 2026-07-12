enum class Loader(val loader: String) {
	FABRIC("fabric"),
	NEOFORGE("neoforge"),
	FORGE("forge"),
	DEOBFUSCATED("deobfuscated"),
	SPIGOT("spigot");

	fun from(version: String): Loader {
		return entries.find { it.loader == version }!!
	}
}
