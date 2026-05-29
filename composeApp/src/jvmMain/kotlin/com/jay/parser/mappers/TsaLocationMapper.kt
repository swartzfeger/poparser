package com.jay.parser.parser

/**
 * Resolves TSA web invoices to the correct Sage customer account by matching
 * the ship-to location against the LOCATION portion of the Customer ID.
 *
 * Important: do not match against the Customer column/name. For TSA, the
 * location hint lives in the Customer ID, e.g. TSA-BOISE.
 */
object TsaLocationMapper {

    data class Entry(
        val customerId: String,
        val customerName: String,
        val terms: String,
        val shipVia: String,
        val priceLevel: String
    )

    private val entries = listOf(
        Entry("TSA-ABILENE", "TSA-ABILENE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ADIRONDACK", "TSA-SARANAC LAKE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-AGUADILLA", "TSA-AGUADILLA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ALBANY", "TSA/ALBANY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ALBUQUERQUE", "TSA-ALBUQUERQUE", "Prepaid", "FEDC", "DISTRIBUTOR"),
        Entry("TSA-ALCOA", "TSA-ALCOA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ALLENTOWN", "TSA-ALLENTOWN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ALLIANCE", "TSA/ALLIANCE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-AMARILLO", "TSA-AMARILLO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ANCHORAGE", "TSA-ANCHORAGE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ARLINGTON", "TSA-ARLINGTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ASPEN", "TSA-ASPEN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ATLANTA", "TSA-ATLANTA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-AURORA", "TSA-AURORA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-AUSTIN 78719", "TSA-AUSTIN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-AUSTIN 78744", "TSA-AUSTIN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BANGOR", "TSA-BANGOR", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BARRIGADA", "TSA-BARRIGADA", "Prepaid", "FEDC", "DISTRIBUTOR"),
        Entry("TSA-BATON ROUGE", "TSA-BATON ROUGE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BELGRADE", "TSA-BELGRADE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BENTONVILLE", "TSA-BENTONVILLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BILLINGS", "TSA-BILLINGS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BIRMINGHAM", "TSA-BIRMINGHAM", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BISMARCK", "TSA-BISMARCK", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BLOUNTVILLE", "TSA-BLOUNTVILLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BOHEMIA", "TSA-BOHEMIA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BOISE", "TSA-BOISE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BURBANK", "TSA-BURBANK", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BURLINGAME", "TSA-BURLINGAME", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-BURLINGTON", "TSA-BURLINGTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CAPITOL HEIGHTS", "TSA-CAPITOL HEIGHTS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CASPER", "TSA-CASPER", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CEDAR RAPIDS", "TSA-CEDAR RAPIDS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CHADRON", "TSA-CHADRON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CHARLESTON", "TSA-CHARLESTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CHARLOTTE", "TSA-CHARLOTTE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CHATTANOOGA", "TSA-CHATTANOOGA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CHEEKTOWAGA", "TSA-CHEEKTOWAGA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CHEYENNE", "TSA-CHEYENNE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CHICAGO", "TSA-CHICAGO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CLEVELAND", "TSA-CLEVELAND", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-COLORADO SPRINGS", "TSA-COLORADO SPRINGS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-COLUMBUS", "TSA-COLUMBUS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-COPPELL", "TSA-COPPELL", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CORAOPOLIS", "TSA-CORAOPOLIS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CORPUS", "TSA-CORPUS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-CORTEZ", "TSA-CORTEZ", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-DALLAS 75220", "TSA-DALLAS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-DALLAS 75235", "TSA-DALLAS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-DAYTONA BEACH", "TSA-DAYTONA BEACH", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-DES MOINES", "TSA-DES MOINES", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-DORAL", "TSA-DORAL", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-DULLES", "TSA-DULLES", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-EAST BOSTON", "TSA-EAST BOSTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-EAST ELMHURST", "TSA-EAST ELMHURST", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-EGG HARBOR", "TSA-EGG HARBOR", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-EGLIN AFB", "TSA-EGLIN AFB", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-EL PASO", "TSA-EL PASO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ERIE", "TSA-ERIE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ERLANGER", "TSA-ERLANGER", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ESCANABA", "TSA-ESCANABA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-EUGENE", "TSA-EUGENE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-FAIRBANKS", "TSA-FAIRBANKS", "Prepaid", "FEDC", "DISTRIBUTOR"),
        Entry("TSA-FLETCHER", "TSA-FLETCHER", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-FLOWOOD", "TSA-FLOWOOD", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-FORT LAUDERDALE", "TSA-FORT LAUDERDALE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-FORT MYERS", "TSA-FORT MYERS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-FRANKLIN", "TSA-FRANKLIN", "Prepaid", "FEDC", "DISTRIBUTOR"),
        Entry("TSA-FREEPORT", "FREEPORT AIRPORT DEVELOPMENT COMPANY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-FRESNO", "TSA-FRESNO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-GOLETA", "TSA-GOLETA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-GRAND JUNCTION", "TSA-GRAND JUNCTION", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-GRAND RAPIDS", "TSA-GRAND RAPIDS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-GREEN BAY", "TSA-GREEN BAY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-GREENSBORO", "TSA-GREENSBORO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-GREER", "TSA-GREER", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-HANOVER", "TSA-HANOVER", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-HAPEVILLE", "TSA-HAPEVILLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-HARLINGEN", "TSA-HARLINGEN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-HILO", "TSA-HILO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-HONOLULU", "TSA-HONOLULU", "Prepaid", "FEDC", "DISTRIBUTOR"),
        Entry("TSA-HORSEHEADS", "TSA-HORSEHEADS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-HOUSTON 77017", "TSA-HOUSTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-HOUSTON 77032", "TSA-HOUSTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-HUNTSVILLE", "TSA-HUNTSVILLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-INDIANAPOLIS", "TSA-INDIANAPOLIS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-JACKSON", "TSA-JACKSON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-JACKSONVILLE", "TSA-JACKSONVILLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-JAMAICA", "TSA-JAMAICA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-KAHULUI", "TSA-KAHULUI", "Prepaid", "SPECIAL", "DISTRIBUTOR"),
        Entry("TSA-KAILUA-KONA", "TSA-KAILUA-KONA", "Prepaid", "SPECIAL", "DISTRIBUTOR"),
        Entry("TSA-KANSAS CITY", "TSA-KANSAS CITY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-KILLEEN", "TSA-KILLEEN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-KNOXVILLE", "TSA-KNOXVILLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LANSING", "TSA-LANSING", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LAREDO", "TSA-LAREDO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LAS VEGAS", "TSA-LAS VEGAS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LEXINGTON", "TSA-LEXINGTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LIHUE", "TSA-LIHUE", "Prepaid", "FEDC", "DISTRIBUTOR"),
        Entry("TSA-LINCOLN", "TSA-LINCOLN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LINTHICUM", "TSA-LINTHICUM", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LITTLE ROCK", "TSA-LITTLE ROCK", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LONDONDERRY", "TSA-LONDONDERRY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LOS ANGELES", "TSA-LOS ANGELES", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LOUISVILLE", "TSA-LOUISVILLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LUBBOCK", "TSA-LUBBOCK", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-LYNCHBURG", "TSA-LYNCHBURG", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MADISON", "TSA-MADISON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MASSENA", "TSA-MASSENA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MCALLEN", "TSA-MCALLEN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MEDFORD", "TSA-MEDFORD", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MEMPHIS", "TSA-MEMPHIS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MIAMI", "TSA-MIAMI", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MIDDLEBURG", "TSA-MIDDLEBURG HEIGHTS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MIDDLETOWN", "TSA-MIDDLETOWN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MIDLAND", "TSA-MIDLAND", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MILWAUKEE", "TSA/MILWAUKEE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MOBILE", "TSA-MOBILE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MOLINE", "TSA-MOLINE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MORRISVILLE", "TSA-MORRISVILLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-MYRTLE BEACH", "TSA-MYRTLE BEACH", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-N CHARLESTON", "TSA-NORTH CHARLESTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-N CHARLESTON CHS", "TSA-N CHARLESTON CHS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-NASHVILLE", "TSA-NASHVILLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-NASSAU", "TSA-NASSAU", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-NEW WINDSOR", "TSA-NEW WINDSOR", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-NORFOLK", "TSA-NORFOLK", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-NORTH CANTON", "TSA-NORTH CANTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-NORTH CHARLESTON", "TSA-NORTH CHARLESTON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-NORTH PLATTE", "TSA-NORTH PLATTE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-NORTH SYRACUSE", "TSA-NORTH SYRACUSE", "Prepaid", "SPECIAL", "DISTRIBUTOR"),
        Entry("TSA-OAKLAND", "TSA-OAKLAND", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-OKLAHOMA CITY", "TSA-OKLAHOMA CITY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-OMAHA", "TSA-OMAHA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ONTARIO", "TSA-ONTARIO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ORANJESTAD", "TSA-ORANJESTAD ARUBA", "Prepaid", "FEDIE", "DISTRIBUTOR"),
        Entry("TSA-ORLANDO", "TSA-ORLANDO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-PAGO PAGO", "TSA-PAGO PAGO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-PALM SPRINGS", "TSA-PALM SPRINGS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-PANAMA CITY", "TSA-PANAMA CITY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-PENSACOLA", "TSA-PENSACOLA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-PHILADELPHIA", "TSA-PHILADELPHIA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-PHOENIX", "TSA-PHOENIX", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-PORTLAND 04102", "TSA-PORTLAND", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-PORTLAND 97218", "TSA-PORTLAND", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-PROVO", "TSA-PROVO / PVU", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-RAPID CITY", "TSA-RAPID CITY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-REDMOND", "TSA-REDMOND", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-RENO", "TSA/RENO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-RICHMOND", "TSA-RICHMOND", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ROCHESTER", "TSA-ROCHESTER", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ROMULUS", "TSA-ROMULUS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ROSEMONT", "TSA-ROSEMONT", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SACRAMENTO", "TSA-SACRAMENTO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SAINT ANN", "TSA-SAINT ANN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SAINT ROSE", "TSA-SAINT ROSE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SAIPAN", "TSA-SAIPAN", "Prepaid", "USPS", "DISTRIBUTOR"),
        Entry("TSA-SALT LAKE CITY", "TSA-SALT LAKE CITY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SAN ANTONIO", "TSA-SAN ANTONIO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SAN DIEGO", "TSA-SAN DIEGO", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SAN JOSE", "TSA-SAN JOSE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SAN JUAN", "TSA-SAN JUAN", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SAVANNAH", "TSA-SAVANNAH", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SCOTTSBLUFF", "TSA-SCOTTSBLUFF", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SEATTLE", "TSA-SEATTLE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SIOUX FALLS", "TSA-SIOUX FALLS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SOUTH BEND", "TSA-SOUTH BEND", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SOUTH BURLINGTON", "TSA-SOUTH BURLINGTON", "Net 30 Days", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SPOKANE", "TSA-SPOKANE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SPRINGFIELD", "TSA-SPRINGFIELD", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-SPRINGFIELD VA", "TSA-SPRINGFIELD VA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ST CROIX", "TSA-ST CROIX", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ST PAUL", "TSA-ST PAUL", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ST PETERSBURG", "TSA-ST PETERSBURG", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-ST THOMAS", "TSA-ST THOMAS", "Prepaid", "UPS INTX", "DISTRIBUTOR"),
        Entry("TSA-STERLING", "TSA-STERLING", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-TALLAHASSEE", "TSA-TALLAHASSEE", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-TAMPA", "TSA-TAMPA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-TINTON FALLS", "TSA-TINTON FALLS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-TRAVERSE CITY", "TSA-TRAVERSE CITY", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-TUCSON", "TSA-TUCSON", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-TULSA", "TSA-TULSA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-UNION", "TSA-UNION", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-VANDALIA", "TSA-VANDALIA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-VERNAL", "TSA-VERNAL", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-WARWICK", "TSA-WARWICK", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-WASHINGTON DC", "TSA-WASHINGTON DC", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-WENDOVER", "TSA-WENDOVER", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-WEST COLUMBIA", "TSA-WEST COLUMBIA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-WEST PALM BEACH", "TSA-WEST PALM BEACH", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-WEYERS CAVE", "TSA-WEYERS CAVE", "Prepaid", "UPS Ground", "DIST + 100%"),
        Entry("TSA-WHITE PLAINS", "TSA-WHITE PLAINS", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-WICHITA", "TSA-WICHITA", "Prepaid", "UPS GRNC", "DISTRIBUTOR"),
        Entry("TSA-WINDSOR LOCKS", "TSA-WINDSOR LOCKS", "Prepaid", "UPS GRNC", "DISTRIBUTOR")
    )

    @Suppress("UNUSED_PARAMETER")
    fun resolveByAccountLocation(city: String?, state: String?, zip: String?): Entry? {
        return resolveByLocation(city, zip)
    }

    private fun resolveByLocation(city: String?, zip: String?): Entry? {
        val normalizedCity = normalizeLocation(city)
        val zip5 = zip?.trim()?.take(5).orEmpty()

        if (normalizedCity.isBlank()) return null

        val cityAliases = aliasesFor(normalizedCity)

        val matchingLocations = entries.filter { entry ->
            val idLocation = normalizeLocation(stripTsaPrefixAndZip(entry.customerId))
            cityAliases.any { alias ->
                idLocation == alias ||
                        idLocation.endsWith(alias) ||
                        alias.endsWith(idLocation)
            }
        }

        if (matchingLocations.isEmpty()) return null

        if (zip5.isNotBlank()) {
            matchingLocations.firstOrNull { entry ->
                Regex("""\b$zip5\b""").containsMatchIn(entry.customerId)
            }?.let { return it }

            val zipSpecificForSameCity = matchingLocations.filter { entry ->
                Regex("""\b\d{5}\b""").containsMatchIn(entry.customerId)
            }

            if (zipSpecificForSameCity.isNotEmpty()) {
                return null
            }
        }

        return matchingLocations
            .sortedWith(
                compareBy<Entry> { it.customerId.length }
                    .thenBy { it.customerId }
            )
            .firstOrNull()
    }


    private fun stripTsaPrefixAndZip(customerId: String): String {
        return customerId
            .uppercase()
            .removePrefix("TSA-")
            .replace(Regex("""\b\d{5}\b"""), "")
            .trim()
    }

    private fun aliasesFor(normalizedCity: String): Set<String> {
        val aliases = mutableSetOf(normalizedCity)

        when (normalizedCity) {
            "NORTHCHARLESTON" -> aliases.add("NCHARLESTON")
            "CORPUSCHRISTI" -> aliases.add("CORPUS")
            "SAINTANN" -> aliases.add("STANN")
            "SAINTROSE" -> aliases.add("STROSE")
            "SAINTPAUL" -> aliases.add("STPAUL")
            "SAINTPETERSBURG" -> aliases.add("STPETERSBURG")
            "SOUTHBURLINGTON" -> aliases.add("SBURLINGTON")
            "WESTCOLUMBIA" -> aliases.add("COLUMBIA")
        }

        return aliases
    }

    private fun normalizeLocation(value: String?): String {
        return value
            ?.uppercase()
            ?.replace("&", " AND ")
            ?.replace(Regex("""[^A-Z0-9]+"""), "")
            ?.trim()
            .orEmpty()
    }
}
