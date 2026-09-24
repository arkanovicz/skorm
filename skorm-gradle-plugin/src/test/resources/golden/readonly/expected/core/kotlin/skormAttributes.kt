package shapes.model

import com.republicate.skorm.core.*
import kotlinx.datetime.*
import com.republicate.kson.Json


// attribute registrations for core, called from initialize(): the read-only database's, then the mutable one's when it exists
internal actual fun ShapesDatabase.registerAttributes() {
    // forward foreign key attribute
    ShapesDatabase.main.entity("friendship").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("a", "SELECT * FROM main.person LEFT JOIN main.base_vip USING (person_id) WHERE person.person_id = {a_id};", ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Friendship>("aFriendships", "SELECT * FROM main.friendship WHERE friendship.a_id = {person_id};", ShapesDatabase.MainSchema.Friendship)
    // forward foreign key attribute
    ShapesDatabase.main.entity("friendship").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("b", "SELECT * FROM main.person LEFT JOIN main.base_vip USING (person_id) WHERE person.person_id = {b_id};", ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Friendship>("bFriendships", "SELECT * FROM main.friendship WHERE friendship.b_id = {person_id};", ShapesDatabase.MainSchema.Friendship)
    // forward foreign key attribute
    ShapesDatabase.main.entity("gift").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Friendship>("friendship", "SELECT * FROM main.friendship WHERE friendship.a_id = {a_id} AND friendship.b_id = {b_id};", ShapesDatabase.MainSchema.Friendship)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("friendship").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Gift>("gifts", "SELECT * FROM main.gift WHERE gift.a_id = {a_id} AND gift.b_id = {b_id};", ShapesDatabase.MainSchema.Gift)
    // forward foreign key attribute
    ShapesDatabase.main.entity("address").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("owner", "SELECT * FROM main.person LEFT JOIN main.base_vip USING (person_id) WHERE person.person_id = {owner};", ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("ownerAddresses", "SELECT * FROM main.address WHERE address.owner = {person_id};", ShapesDatabase.MainSchema.Address)
    // forward foreign key attribute
    ShapesDatabase.main.entity("address").instanceAttributes.nullableRowAttribute<ShapesDatabase.MainSchema.Person>("backup", "SELECT * FROM main.person LEFT JOIN main.base_vip USING (person_id) WHERE person.person_id = {backup};", ShapesDatabase.MainSchema.Person)
    // forward foreign key attribute
    ShapesDatabase.main.entity("address").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Country>("country", "SELECT * FROM main.country WHERE country.code = {code};", ShapesDatabase.MainSchema.Country)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("country").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("addresses", "SELECT * FROM main.address WHERE address.code = {code};", ShapesDatabase.MainSchema.Address)
    // left to right n-n join attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("addresses", "SELECT towards_table.* FROM main.person_address AS join_table JOIN main.address AS towards_table ON towards_table.address_id = join_table.address_id WHERE join_table.person_id = {person_id};", ShapesDatabase.MainSchema.Address)
    // right to left n-n join attribute
    ShapesDatabase.main.entity("address").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Person>("persons", "SELECT towards_table.* FROM main.person_address AS join_table JOIN (SELECT * FROM main.person LEFT JOIN main.base_vip USING (person_id)) AS towards_table ON towards_table.person_id = join_table.person_id WHERE join_table.address_id = {address_id};", ShapesDatabase.MainSchema.Person)
    // forward foreign key attribute
    ShapesDatabase.other.entity("badge").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("person", "SELECT * FROM main.person LEFT JOIN main.base_vip USING (person_id) WHERE person.person_id = {person_id};", ShapesDatabase.MainSchema.Person)
    // reverse foreign key attribute
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.OtherSchema.Badge>("badges", "SELECT * FROM other.badge WHERE badge.person_id = {person_id};", ShapesDatabase.OtherSchema.Badge)

    // attribute main.personCount
    ShapesDatabase.main.scalarAttribute<Int>("personCount", """
        SELECT count(*) FROM person;""".trimIndent())

    // attribute main.oldestBirth
    ShapesDatabase.main.scalarAttribute<LocalDate?>("oldestBirth", """
        SELECT min(born) FROM person;""".trimIndent())

    // attribute main.summary
    ShapesDatabase.main.rowAttribute<Json.MutableObject>("summary", """
        SELECT count(*) AS persons FROM person;""".trimIndent())

    // attribute main.everybody
    ShapesDatabase.main.rowSetAttribute<ShapesDatabase.MainSchema.Person>("everybody", """
        SELECT * FROM person;""".trimIndent(), ShapesDatabase.MainSchema.Person::new)

    // attribute Person.mainAddress
    ShapesDatabase.main.entity("person").instanceAttributes.nullableRowAttribute<ShapesDatabase.MainSchema.Address>("mainAddress", """
        SELECT * FROM address WHERE owner = {person_id} LIMIT 1;""".trimIndent(), ShapesDatabase.MainSchema.Address::new)

    // attribute Person.counts
    ShapesDatabase.main.entity("person").instanceAttributes.rowAttribute<Counts>("counts", """
        SELECT (SELECT count(*) FROM address WHERE owner = {person_id}) addresses,
             (SELECT count(*) FROM friendship WHERE a_id = {person_id}) friends;""".trimIndent(), ::Counts)

    // attribute Person.cities
    ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<Json.MutableObject>("cities", """
        SELECT city FROM address WHERE owner = {person_id};""".trimIndent())
}
