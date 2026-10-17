package com.jay.parser.parser

class InactiveCustomerException(
    val customerName: String
) : IllegalStateException(
    "$customerName is no longer an active customer. " +
        "Please onboard them again in Sage to parse their order."
)
