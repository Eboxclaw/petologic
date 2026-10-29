package ai.petologic.skills.security.guard

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SecurityMigrations {
 val MIGRATION_1_2=object:Migration(1,2){
  override fun migrate(db:SupportSQLiteDatabase){
   db.execSQL("CREATE TABLE IF NOT EXISTS `security_knowledge` (`id` TEXT NOT NULL, `revision` INTEGER NOT NULL, `packVersion` INTEGER NOT NULL, `kind` TEXT NOT NULL, `language` TEXT, `canonicalText` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceVersion` TEXT NOT NULL, `trust` TEXT NOT NULL, `validUntil` INTEGER NOT NULL, `evidenceRefsJson` TEXT NOT NULL, `limitationsJson` TEXT NOT NULL, `contentHash` TEXT NOT NULL, PRIMARY KEY(`id`))")
   db.execSQL("CREATE TABLE IF NOT EXISTS `security_vectors` (`namespace` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `ownerRevision` INTEGER NOT NULL, `modelId` TEXT NOT NULL, `modelRevision` TEXT NOT NULL, `contentHash` TEXT NOT NULL, `dimension` INTEGER NOT NULL, `vector` BLOB NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`namespace`, `ownerId`, `ownerRevision`, `modelId`, `modelRevision`))")
   db.execSQL("CREATE INDEX IF NOT EXISTS `index_security_vectors_namespace` ON `security_vectors` (`namespace`)")
   db.execSQL("CREATE INDEX IF NOT EXISTS `index_security_vectors_ownerId` ON `security_vectors` (`ownerId`)")
   db.execSQL("CREATE INDEX IF NOT EXISTS `index_security_vectors_expiresAt` ON `security_vectors` (`expiresAt`)")
   db.execSQL("CREATE TABLE IF NOT EXISTS `security_index_outbox` (`namespace` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `ownerRevision` INTEGER NOT NULL, `operation` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, PRIMARY KEY(`namespace`, `ownerId`))")
   db.execSQL("CREATE INDEX IF NOT EXISTS `index_security_index_outbox_createdAt` ON `security_index_outbox` (`createdAt`)")
   db.execSQL("CREATE TABLE IF NOT EXISTS `threat_edges` (`fromId` TEXT NOT NULL, `toId` TEXT NOT NULL, `relation` TEXT NOT NULL, `packVersion` INTEGER NOT NULL, PRIMARY KEY(`fromId`, `toId`, `relation`))")
   db.execSQL("CREATE INDEX IF NOT EXISTS `index_threat_edges_fromId` ON `threat_edges` (`fromId`)")
   db.execSQL("CREATE INDEX IF NOT EXISTS `index_threat_edges_toId` ON `threat_edges` (`toId`)")
  }
 }
}
