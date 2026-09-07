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
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object MasterDataStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val dataDir: File by lazy {
        appDataDirectory().resolve("master-data")
    }

    private val bundledMasterDataMetadata: BundledMasterDataMetadata by lazy {
        loadBundledJson(BUNDLED_METADATA_FILE)
    }

    @Volatile
    private var cachedBundle: MasterDataBundle? = null

    @Volatile
    private var bundledRevisionCheckComplete: Boolean = false

    fun current(): MasterDataBundle {
        cachedBundle?.let { return it }
        return synchronized(this) {
            retireOlderImportIfNeeded()
            cachedBundle ?: loadBundle().also { cachedBundle = it }
        }
    }

    fun metadata(): MasterDataMetadata? {
        return synchronized(this) {
            retireOlderImportIfNeeded()
            readImportedMetadata()
        }
    }

    fun bundledSourceFilename(): String = bundledMasterDataMetadata.sourceFilename

    fun activeMasterListVersion(): String {
        return synchronized(this) {
            retireOlderImportIfNeeded()
            val revision = readImportedMetadata()
                ?.let(::importedRevision)
                ?: LocalDate.parse(bundledMasterDataMetadata.revision)

            revision.format(MASTER_LIST_VERSION_FORMAT)
        }
    }

    fun importMasterList(file: File): MasterDataImportResult {
        val parsed = MasterListImporter().parse(file)
        val importedAt = Instant.now()
        val metadata = MasterDataMetadata(
            sourceFilename = file.name,
            importedAt = importedAt.toString(),
            customerCount = parsed.bundle.customers.size,
            descriptionCount = parsed.bundle.itemCatalog.descriptions.size,
            pricedItemCount = parsed.bundle.itemCatalog.prices.size,
            glAccountCount = parsed.bundle.glAccounts.size,
            qtyDiscountRuleCount = parsed.bundle.qtyDiscountRules.size,
            sourceRevision = revisionFromFilename(file.name)?.toString()
                ?: importedAt.atZone(ZoneOffset.UTC).toLocalDate().toString(),
            bundledRevisionAtImport = bundledMasterDataMetadata.revision
        )

        synchronized(this) {
            dataDir.mkdirs()
            backupExistingImport()
            writeBundle(parsed.bundle, metadata)
            cachedBundle = parsed.bundle
            bundledRevisionCheckComplete = true
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
                importedDataFiles().forEach { it.delete() }
            }
            cachedBundle = loadBundle()
            bundledRevisionCheckComplete = true
        }
    }

    fun dataDirectoryPath(): String = dataDir.absolutePath

    private fun retireOlderImportIfNeeded() {
        if (bundledRevisionCheckComplete) return

        try {
            val metadata = readImportedMetadata() ?: return
            val importedRevision = importedRevision(metadata) ?: return
            val bundledRevision = runCatching {
                LocalDate.parse(bundledMasterDataMetadata.revision)
            }.getOrNull() ?: return

            if (!importedRevision.isBefore(bundledRevision)) return

            backupExistingImport()
            importedDataFiles().forEach { file ->
                check(!file.isFile || file.delete()) {
                    "Could not retire outdated master-data file ${file.name}"
                }
            }
            cachedBundle = null
        } finally {
            bundledRevisionCheckComplete = true
        }
    }

    private fun readImportedMetadata(): MasterDataMetadata? {
        val file = dataDir.resolve(METADATA_FILE)
        if (!file.isFile) return null

        return runCatching {
            json.decodeFromString<MasterDataMetadata>(file.readText())
        }.getOrNull()
    }

    private fun importedRevision(metadata: MasterDataMetadata): LocalDate? {
        return metadata.sourceRevision
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: revisionFromFilename(metadata.sourceFilename)
            ?: runCatching {
                Instant.parse(metadata.importedAt).atZone(ZoneOffset.UTC).toLocalDate()
            }.getOrNull()
    }

    private fun revisionFromFilename(filename: String): LocalDate? {
        val match = MASTER_LIST_DATE_PATTERN.find(filename) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val rawYear = match.groupValues[3].toIntOrNull() ?: return null
        val year = if (rawYear < 100) 2000 + rawYear else rawYear

        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun importedDataFiles(): List<File> =
        listOf(ITEMS_FILE, CUSTOMERS_FILE, GL_ACCOUNTS_FILE, QTY_DISCOUNTS_FILE, METADATA_FILE)
            .map { dataDir.resolve(it) }

    private fun loadBundle(): MasterDataBundle {
        return MasterDataBundle(
            itemCatalog = loadItemCatalog(),
            customers = loadJsonOverrideOrBundled(CUSTOMERS_FILE),
            glAccounts = loadJsonOverrideOrBundled(GL_ACCOUNTS_FILE),
            qtyDiscountRules = loadQtyDiscountRules()
        )
    }

    private fun loadItemCatalog(): ItemCatalog {
        val bundled = loadBundledJson<ItemCatalog>(ITEMS_FILE)
        val overrideFile = dataDir.resolve(ITEMS_FILE)
        if (!overrideFile.isFile) return bundled

        val imported = json.decodeFromString<ItemCatalog>(overrideFile.readText())
        return imported.copy(
            qtyDiscountIds = bundled.qtyDiscountIds + imported.qtyDiscountIds
        )
    }

    private inline fun <reified T> loadJsonOverrideOrBundled(filename: String): T {
        val overrideFile = dataDir.resolve(filename)
        if (overrideFile.isFile) {
            return json.decodeFromString(overrideFile.readText())
        }

        return loadBundledJson(filename)
    }

    private inline fun <reified T> loadBundledJson(filename: String): T {
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
            append("  },\n")
            append("  \"qtyDiscountIds\": {\n")
            catalog.qtyDiscountIds.entries.forEachIndexed { index, (sku, qtyDiscountId) ->
                append("    ${quoteJson(sku)}: ${quoteJson(qtyDiscountId)}")
                if (index < catalog.qtyDiscountIds.size - 1) append(",")
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
        val existingFiles = importedDataFiles().filter { it.isFile }

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
    private const val BUNDLED_METADATA_FILE = "masterDataRevision.json"
    private val MASTER_LIST_VERSION_FORMAT = DateTimeFormatter.ofPattern("MM.dd.yy")
    private val MASTER_LIST_DATE_PATTERN =
        Regex("""(?<!\d)(\d{1,2})[._-](\d{1,2})[._-](\d{2}|\d{4})(?!\d)""")
}
