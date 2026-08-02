package com.jay.parser.packaging

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

object PackagingDataStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val dataDir: File by lazy {
        appDataDirectory().resolve("packaging-data")
    }

    @Volatile
    private var cachedProducts: Map<String, ProductPackaging>? = null

    fun current(): Map<String, ProductPackaging> {
        cachedProducts?.let { return it }
        return synchronized(this) {
            cachedProducts ?: loadProducts().also { cachedProducts = it }
        }
    }

    fun metadata(): PackagingDataMetadata? {
        val file = dataDir.resolve(METADATA_FILE)
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<PackagingDataMetadata>(file.readText()) }.getOrNull()
    }

    fun importCsv(file: File): PackagingDataImportResult {
        val parsed = PackagingCsvImporter().parse(file)
        val metadata = createMetadata(file.name, parsed.products)

        synchronized(this) {
            dataDir.mkdirs()
            backupExistingImport()
            writeTextAtomically(dataDir.resolve(PRODUCTS_FILE), json.encodeToString(parsed.products))
            writeTextAtomically(dataDir.resolve(METADATA_FILE), json.encodeToString(metadata))
            cachedProducts = parsed.products
        }

        return PackagingDataImportResult(metadata, parsed.warnings)
    }

    fun restoreBundledDefaults() {
        synchronized(this) {
            if (dataDir.exists()) {
                backupExistingImport()
                listOf(PRODUCTS_FILE, METADATA_FILE).forEach { dataDir.resolve(it).delete() }
            }
            cachedProducts = loadProducts()
        }
    }

    fun dataDirectoryPath(): String = dataDir.absolutePath

    private fun loadProducts(): Map<String, ProductPackaging> {
        val overrideFile = dataDir.resolve(PRODUCTS_FILE)
        if (overrideFile.isFile) {
            return json.decodeFromString(overrideFile.readText())
        }

        val text = object {}.javaClass.getResourceAsStream("/data/$PRODUCTS_FILE")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not find bundled resource /data/$PRODUCTS_FILE")
        return json.decodeFromString(text)
    }

    private fun createMetadata(
        sourceFilename: String,
        products: Map<String, ProductPackaging>
    ): PackagingDataMetadata = PackagingDataMetadata(
        sourceFilename = sourceFilename,
        importedAt = Instant.now().toString(),
        productCount = products.size,
        dimensionedProductCount = products.values.count { it.hasDimensions },
        weightedProductCount = products.values.count { it.weightPounds != null },
        completeProductCount = products.values.count { it.hasDimensions && it.weightPounds != null }
    )

    private fun writeTextAtomically(file: File, text: String) {
        file.parentFile.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text)
        Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }

    private fun backupExistingImport() {
        val existingFiles = listOf(PRODUCTS_FILE, METADATA_FILE)
            .map { dataDir.resolve(it) }
            .filter { it.isFile }
        if (existingFiles.isEmpty()) return

        val backupDir = dataDir.resolve("backups")
            .resolve(Instant.now().toString().replace(":", "-"))
        backupDir.mkdirs()
        existingFiles.forEach { source ->
            Files.copy(
                source.toPath(),
                backupDir.resolve(source.name).toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun appDataDirectory(): File {
        val os = System.getProperty("os.name").lowercase()
        val home = File(System.getProperty("user.home"))
        return when {
            os.contains("mac") -> home.resolve("Library/Application Support/PO Parser")
            os.contains("win") -> {
                val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                if (appData != null) File(appData).resolve("PO Parser") else home.resolve("AppData/Roaming/PO Parser")
            }
            else -> home.resolve(".po-parser")
        }
    }

    private const val PRODUCTS_FILE = "productPackaging.json"
    private const val METADATA_FILE = "metadata.json"
}
