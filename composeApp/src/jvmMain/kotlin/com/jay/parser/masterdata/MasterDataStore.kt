package com.jay.parser.masterdata

import com.jay.parser.mappers.QtyDiscountMapper
import com.jay.parser.models.ItemCatalog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

object MasterDataStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val dataDir: File by lazy {
        appDataDirectory().resolve("master-data")
    }

    @Volatile
    private var cachedBundle: MasterDataBundle? = null

    fun current(): MasterDataBundle {
        cachedBundle?.let { return it }
        return synchronized(this) {
            cachedBundle ?: loadBundle().also { cachedBundle = it }
        }
    }

    fun metadata(): MasterDataMetadata? {
        val file = dataDir.resolve(METADATA_FILE)
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString<MasterDataMetadata>(file.readText())
        }.getOrNull()
    }

    fun importMasterList(file: File): MasterDataImportResult {
        val parsed = MasterListImporter().parse(file)
        val metadata = MasterDataMetadata(
            sourceFilename = file.name,
            importedAt = Instant.now().toString(),
            customerCount = parsed.bundle.customers.size,
            descriptionCount = parsed.bundle.itemCatalog.descriptions.size,
            pricedItemCount = parsed.bundle.itemCatalog.prices.size,
            glAccountCount = parsed.bundle.glAccounts.size,
            qtyDiscountRuleCount = parsed.bundle.qtyDiscountRules.size
        )

        synchronized(this) {
            dataDir.mkdirs()
            backupExistingImport()
            writeBundle(parsed.bundle, metadata)
            cachedBundle = parsed.bundle
        }

        return MasterDataImportResult(
            metadata = metadata,
            warnings = parsed.warnings
        )
    }

    fun restoreBundledDefaults() {
        synchronized(this) {
            if (dataDir.exists()) {
                backupExistingImport()
                listOf(ITEMS_FILE, CUSTOMERS_FILE, GL_ACCOUNTS_FILE, QTY_DISCOUNTS_FILE, METADATA_FILE)
                    .forEach { dataDir.resolve(it).delete() }
            }
            cachedBundle = loadBundle()
        }
    }

    fun dataDirectoryPath(): String = dataDir.absolutePath

    private fun loadBundle(): MasterDataBundle {
        return MasterDataBundle(
            itemCatalog = loadJsonOverrideOrBundled(ITEMS_FILE),
            customers = loadJsonOverrideOrBundled(CUSTOMERS_FILE),
            glAccounts = loadJsonOverrideOrBundled(GL_ACCOUNTS_FILE),
            qtyDiscountRules = loadQtyDiscountRules()
        )
    }

    private inline fun <reified T> loadJsonOverrideOrBundled(filename: String): T {
        val overrideFile = dataDir.resolve(filename)
        if (overrideFile.isFile) {
            return json.decodeFromString(overrideFile.readText())
        }

        val resourcePath = "/data/$filename"
        val text = object {}.javaClass.getResourceAsStream(resourcePath)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Could not find bundled resource $resourcePath")

        return json.decodeFromString(text)
    }

    private fun loadQtyDiscountRules(): List<MasterQtyDiscountRule> {
        val overrideFile = dataDir.resolve(QTY_DISCOUNTS_FILE)
        if (overrideFile.isFile) {
            return json.decodeFromString(overrideFile.readText())
        }
        return QtyDiscountMapper.defaultRules()
    }

    private fun writeBundle(bundle: MasterDataBundle, metadata: MasterDataMetadata) {
        writeTextAtomically(dataDir.resolve(ITEMS_FILE), encodeItemCatalog(bundle.itemCatalog))
        writeTextAtomically(dataDir.resolve(CUSTOMERS_FILE), json.encodeToString(bundle.customers))
        writeTextAtomically(dataDir.resolve(GL_ACCOUNTS_FILE), json.encodeToString(bundle.glAccounts))
        writeTextAtomically(dataDir.resolve(QTY_DISCOUNTS_FILE), json.encodeToString(bundle.qtyDiscountRules))
        writeTextAtomically(dataDir.resolve(METADATA_FILE), json.encodeToString(metadata))
    }

    private fun encodeItemCatalog(catalog: ItemCatalog): String {
        return buildString {
            append("{\n")
            append("  \"prices\": {\n")
            catalog.prices.entries.forEachIndexed { skuIndex, (sku, priceLevels) ->
                append("    ${quoteJson(sku)}: {\n")
                priceLevels.entries.forEachIndexed { levelIndex, (priceLevel, price) ->
                    append("      ${quoteJson(priceLevel)}: ${formatPrice(price)}")
                    if (levelIndex < priceLevels.size - 1) append(",")
                    append("\n")
                }
                append("    }")
                if (skuIndex < catalog.prices.size - 1) append(",")
                append("\n")
            }
            append("  },\n")
            append("  \"descriptions\": {\n")
            catalog.descriptions.entries.forEachIndexed { descriptionIndex, (sku, description) ->
                append("    ${quoteJson(sku)}: ${quoteJson(description)}")
                if (descriptionIndex < catalog.descriptions.size - 1) append(",")
                append("\n")
            }
            append("  }\n")
            append("}\n")
        }
    }

    private fun quoteJson(value: String): String {
        return json.encodeToString(value)
    }

    private fun formatPrice(value: Double): String {
        return BigDecimal.valueOf(value)
            .setScale(3, RoundingMode.HALF_UP)
            .toPlainString()
    }

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
        val existingFiles = listOf(ITEMS_FILE, CUSTOMERS_FILE, GL_ACCOUNTS_FILE, QTY_DISCOUNTS_FILE, METADATA_FILE)
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

    private const val ITEMS_FILE = "items.json"
    private const val CUSTOMERS_FILE = "customers.json"
    private const val GL_ACCOUNTS_FILE = "glAccounts.json"
    private const val QTY_DISCOUNTS_FILE = "qtyDiscounts.json"
    private const val METADATA_FILE = "metadata.json"
}
