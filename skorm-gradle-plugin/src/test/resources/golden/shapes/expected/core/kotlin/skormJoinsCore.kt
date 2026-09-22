package shapes.model

import com.republicate.skorm.core.*


// attributes declaration for core
fun ShapesDatabase.initJoins() {
   // forward foreign key attribute
   ShapesDatabase.main.entity("friendship").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("a", "SELECT * FROM main.person WHERE person.person_id = {a_id};", ShapesDatabase.MainSchema.Person)
   // reverse foreign key attribute
   ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Friendship>("aFriendships", "SELECT * FROM main.friendship WHERE friendship.a_id = {person_id};", ShapesDatabase.MainSchema.Friendship)
   // forward foreign key attribute
   ShapesDatabase.main.entity("friendship").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("b", "SELECT * FROM main.person WHERE person.person_id = {b_id};", ShapesDatabase.MainSchema.Person)
   // reverse foreign key attribute
   ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Friendship>("bFriendships", "SELECT * FROM main.friendship WHERE friendship.b_id = {person_id};", ShapesDatabase.MainSchema.Friendship)
   // forward foreign key attribute
   ShapesDatabase.main.entity("gift").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Friendship>("friendship", "SELECT * FROM main.friendship WHERE friendship.a_id = {a_id} AND friendship.b_id = {b_id};", ShapesDatabase.MainSchema.Friendship)
   // reverse foreign key attribute
   ShapesDatabase.main.entity("friendship").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Gift>("gifts", "SELECT * FROM main.gift WHERE gift.a_id = {a_id} AND gift.b_id = {b_id};", ShapesDatabase.MainSchema.Gift)
   // forward foreign key attribute
   ShapesDatabase.main.entity("address").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Person>("owner", "SELECT * FROM main.person WHERE person.person_id = {owner};", ShapesDatabase.MainSchema.Person)
   // reverse foreign key attribute
   ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("ownerAddresses", "SELECT * FROM main.address WHERE address.owner = {person_id};", ShapesDatabase.MainSchema.Address)
   // forward foreign key attribute
   ShapesDatabase.main.entity("address").instanceAttributes.nullableRowAttribute<ShapesDatabase.MainSchema.Person>("backup", "SELECT * FROM main.person WHERE person.person_id = {backup};", ShapesDatabase.MainSchema.Person)
   // forward foreign key attribute
   ShapesDatabase.main.entity("address").instanceAttributes.rowAttribute<ShapesDatabase.MainSchema.Country>("code", "SELECT * FROM main.country WHERE country.code = {code};", ShapesDatabase.MainSchema.Country)
   // reverse foreign key attribute
   ShapesDatabase.main.entity("country").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("addresses", "SELECT * FROM main.address WHERE address.code = {code};", ShapesDatabase.MainSchema.Address)
  // left to right n-n join attribute
  ShapesDatabase.main.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Address>("addresses", "SELECT towards_table.* FROM main.person_address AS join_table JOIN main.address AS towards_table ON towards_table.address_id = join_table.address_id WHERE join_table.person_id = {person_id};", ShapesDatabase.MainSchema.Address)
  // right to left n-n join attribute
  ShapesDatabase.main.entity("address").instanceAttributes.rowSetAttribute<ShapesDatabase.MainSchema.Person>("persons", "SELECT towards_table.* FROM main.person_address AS join_table JOIN main.person AS towards_table ON towards_table.person_id = join_table.person_id WHERE join_table.address_id = {address_id};", ShapesDatabase.MainSchema.Person)
   // forward foreign key attribute
   ShapesDatabase.other.entity("badge").instanceAttributes.rowAttribute<ShapesDatabase.OtherSchema.Person>("person", "SELECT * FROM main.person WHERE person.person_id = {person_id};", ShapesDatabase.OtherSchema.Person)
   // reverse foreign key attribute
   ShapesDatabase.other.entity("person").instanceAttributes.rowSetAttribute<ShapesDatabase.OtherSchema.Badge>("badges", "SELECT * FROM other.badge WHERE badge.person_id = {person_id};", ShapesDatabase.OtherSchema.Badge)
}
