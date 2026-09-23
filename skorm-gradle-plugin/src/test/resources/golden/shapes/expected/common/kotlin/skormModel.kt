package shapes.model

import kotlinx.datetime.*
import com.republicate.kson.Json
import shapes.model.ShapesDatabase.MainSchema.Level
import shapes.model.ShapesDatabase.MainSchema.PersonKind
import shapes.model.ShapesDatabase.MainSchema.PersonNature




// attribute main.personCount
suspend fun ShapesDatabase.MainSchema.`personCount`() = eval<Int>("personCount")



// attribute main.oldestBirth
suspend fun ShapesDatabase.MainSchema.`oldestBirth`() = eval<LocalDate?>("oldestBirth")



// attribute main.summary
suspend fun ShapesDatabase.MainSchema.`summary`() = retrieve<Json.Object>("summary")



// attribute main.everybody
suspend fun ShapesDatabase.MainSchema.`everybody`() = query<ShapesDatabase.MainSchema.Person>("everybody")



// attribute Person.mainAddress
suspend fun ShapesDatabase.MainSchema.Person.`mainAddress`() = retrieve<ShapesDatabase.MainSchema.Address?>("mainAddress")



// attribute Person.forget
suspend fun ShapesDatabase.MainSchema.Person.`forget`() = perform("forget")

open class Counts(): Json.MutableObject() {
    val addresses: Int
        get() = getInt("addresses")!!
    val friends: Int
        get() = getInt("friends")!!
}


// attribute Person.counts
suspend fun ShapesDatabase.MainSchema.Person.`counts`() = retrieve<Counts>("counts") as Counts



// attribute Person.cities
suspend fun ShapesDatabase.MainSchema.Person.`cities`() = query<String>("cities")

