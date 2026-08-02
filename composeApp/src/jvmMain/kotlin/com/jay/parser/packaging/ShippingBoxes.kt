package com.jay.parser.packaging

data class ShippingBox(
    val id: Int,
    val lengthInches: Double,
    val widthInches: Double,
    val heightInches: Double
) {
    val volumeCubicInches: Double = lengthInches * widthInches * heightInches
    val usableVolumeCubicInches: Double = volumeCubicInches * 0.90
    val dimensionsLabel: String = "${lengthInches.clean()} x ${widthInches.clean()} x ${heightInches.clean()}"

    fun fits(length: Double, width: Double, height: Double): Boolean {
        val item = listOf(length, width, height).sorted()
        val box = listOf(lengthInches, widthInches, heightInches).sorted()
        return item.indices.all { item[it] <= box[it] }
    }
}

object ShippingBoxes {
    val all: List<ShippingBox> = listOf(
        ShippingBox(1, 16.0, 14.0, 18.0),
        ShippingBox(2, 15.0, 13.0, 12.0),
        ShippingBox(3, 16.0, 14.0, 13.0),
        ShippingBox(4, 18.0, 12.0, 4.0),
        ShippingBox(5, 16.0, 14.0, 6.0),
        ShippingBox(6, 13.0, 9.0, 6.0),
        ShippingBox(7, 7.0, 5.0, 4.0),
        ShippingBox(8, 8.0, 6.0, 3.0),
        ShippingBox(9, 12.0, 6.0, 4.0),
        ShippingBox(10, 13.0, 10.0, 2.0),
        ShippingBox(11, 17.0, 13.0, 8.0),
        ShippingBox(12, 12.0, 9.0, 4.0),
        ShippingBox(13, 14.0, 12.0, 4.0),
        ShippingBox(14, 22.0, 16.0, 7.0),
        ShippingBox(15, 26.0, 20.0, 10.0)
    )
}

internal fun Double.clean(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()
