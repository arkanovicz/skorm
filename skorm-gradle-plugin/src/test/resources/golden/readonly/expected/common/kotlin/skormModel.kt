package shapes.model

import kotlinx.datetime.*
import com.republicate.kson.Json

open class Counts(): Json.MutableObject() {
    val addresses: Int
        get() = getInt("addresses")!!
    val friends: Int
        get() = getInt("friends")!!
}

