package org.katacr.kaGameCenter.entity

import org.bukkit.entity.Entity
import java.util.UUID

class RoomEntityOwnershipService {
    data class Record(
        val roomId: String,
        val entityId: UUID,
        val ownerId: UUID,
        val type: String? = null
    )

    private val records = linkedMapOf<UUID, Record>()

    @Synchronized
    fun track(roomId: String, entity: Entity, ownerId: UUID, type: String? = null): Record {
        val record = Record(roomId, entity.uniqueId, ownerId, type)
        records[entity.uniqueId] = record
        return record
    }

    @Synchronized
    fun owner(entityId: UUID): UUID? = records[entityId]?.ownerId

    @Synchronized
    fun roomId(entityId: UUID): String? = records[entityId]?.roomId

    @Synchronized
    fun type(entityId: UUID): String? = records[entityId]?.type

    @Synchronized
    fun record(entityId: UUID): Record? = records[entityId]

    @Synchronized
    fun remove(entityId: UUID): Record? = records.remove(entityId)

    @Synchronized
    fun clearRoom(roomId: String) {
        records.entries.removeIf { it.value.roomId == roomId }
    }

    @Synchronized
    fun clearAll() {
        records.clear()
    }
}
