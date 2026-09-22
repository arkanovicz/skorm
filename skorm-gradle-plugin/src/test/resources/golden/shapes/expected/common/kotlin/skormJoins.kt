package shapes.model


// forward foreign key
suspend fun ShapesDatabase.MainSchema.Friendship.a(): ShapesDatabase.MainSchema.Person = entity.retrieve("a", this)
// reverse foreign key
suspend fun ShapesDatabase.MainSchema.Person.aFriendships(): Sequence<ShapesDatabase.MainSchema.Friendship> = entity.query("aFriendships", this)
// forward foreign key
suspend fun ShapesDatabase.MainSchema.Friendship.b(): ShapesDatabase.MainSchema.Person = entity.retrieve("b", this)
// reverse foreign key
suspend fun ShapesDatabase.MainSchema.Person.bFriendships(): Sequence<ShapesDatabase.MainSchema.Friendship> = entity.query("bFriendships", this)
// forward foreign key
suspend fun ShapesDatabase.MainSchema.Gift.friendship(): ShapesDatabase.MainSchema.Friendship = entity.retrieve("friendship", this)
// reverse foreign key
suspend fun ShapesDatabase.MainSchema.Friendship.gifts(): Sequence<ShapesDatabase.MainSchema.Gift> = entity.query("gifts", this)
// forward foreign key
suspend fun ShapesDatabase.MainSchema.Address.owner(): ShapesDatabase.MainSchema.Person = entity.retrieve("owner", this)
// reverse foreign key
suspend fun ShapesDatabase.MainSchema.Person.ownerAddresses(): Sequence<ShapesDatabase.MainSchema.Address> = entity.query("ownerAddresses", this)
// forward foreign key
suspend fun ShapesDatabase.MainSchema.Address.backup(): ShapesDatabase.MainSchema.Person? = entity.retrieve("backup", this)
// forward foreign key
suspend fun ShapesDatabase.MainSchema.Address.code(): ShapesDatabase.MainSchema.Country = entity.retrieve("code", this)
// reverse foreign key
suspend fun ShapesDatabase.MainSchema.Country.addresses(): Sequence<ShapesDatabase.MainSchema.Address> = entity.query("addresses", this)
// left to right n-n join
suspend fun ShapesDatabase.MainSchema.Person.addresses(): Sequence<ShapesDatabase.MainSchema.Address> = entity.query("addresses", this)
// right to left n-n join
suspend fun ShapesDatabase.MainSchema.Address.persons(): Sequence<ShapesDatabase.MainSchema.Person> = entity.query("persons", this)
// forward foreign key
suspend fun ShapesDatabase.OtherSchema.Badge.person(): ShapesDatabase.OtherSchema.Person = entity.retrieve("person", this)
// reverse foreign key
suspend fun ShapesDatabase.OtherSchema.Person.badges(): Sequence<ShapesDatabase.OtherSchema.Badge> = entity.query("badges", this)
