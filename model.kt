// multiplatform library objects

open class Model {
    private var initialized = false
    fun init() {
        assert(!initialized)
        //...
        initialized = true
    
    }
    private _entities = mutableMapOf<String, Entity>();
    internal fun addEntity(entity: Entity) {
        assert(!initialized)
        _entities.add(entity.name, entity)
    }
}

open class Entity(val model: Model) {
    init {
        model.addEntity(this)
    }
}

open class Instance(entity: Entity)

// specific platform usage

class SomeModel: Model

class SomeEntity: Entity

class SomeInstance: Instance {
    companion object: SomeEntity
}

val someModel = SomeModel()
someModel.init()

