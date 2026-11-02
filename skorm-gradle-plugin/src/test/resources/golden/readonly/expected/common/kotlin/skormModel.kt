@file:OptIn(kotlin.time.ExperimentalTime::class) // Instant is experimental below Kotlin 2.3

package shapes.model

import kotlinx.datetime.*
import kotlin.time.Instant
import com.republicate.kson.Json

open class Counts(): Json.MutableObject() {
    val addresses: Int
        get() = getInt("addresses")!!
    val friends: Int
        get() = getInt("friends")!!
}

open class Profile(): Json.MutableObject() {
    val name: String
        get() = getString("name")!!
    val born: LocalDate?
        get() = getLocalDate("born")
}

open class LastSeen(): Json.MutableObject() {
    val seen: LocalDateTime?
        get() = getLocalDateTime("seen")
}

