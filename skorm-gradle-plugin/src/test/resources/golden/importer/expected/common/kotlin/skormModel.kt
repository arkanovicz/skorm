package importer.model

import kotlinx.datetime.*
import com.republicate.kson.Json

open class Subject(): net.arkad.model.ArkadDatabase.ArkadSchema.MessageImpl() {
    val votes: Int
        get() = getInt("votes")!!
}

