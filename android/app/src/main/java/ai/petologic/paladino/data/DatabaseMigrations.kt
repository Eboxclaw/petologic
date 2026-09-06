package ai.petologic.paladino.data
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
object DatabaseMigrations {
 val ALL=arrayOf(
  object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("CREATE TABLE IF NOT EXISTS embeddings (noteId TEXT NOT NULL PRIMARY KEY, sourceVersion INTEGER NOT NULL, modelId TEXT NOT NULL, vector BLOB NOT NULL)")}},
  object:Migration(2,3){override fun migrate(db:SupportSQLiteDatabase){
   for(table in listOf("notes","messages","tasks","actions","index_outbox"))db.execSQL("ALTER TABLE $table ADD COLUMN sessionId TEXT NOT NULL DEFAULT 'default'")
   db.execSQL("CREATE TABLE IF NOT EXISTS sessions (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL, archived INTEGER NOT NULL, parentId TEXT, modelId TEXT NOT NULL, mode TEXT NOT NULL, optionsJson TEXT NOT NULL)")
   db.execSQL("CREATE TABLE IF NOT EXISTS execution_events (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, taskId TEXT NOT NULL, type TEXT NOT NULL, detail TEXT NOT NULL, createdAt INTEGER NOT NULL)")
   db.execSQL("CREATE INDEX IF NOT EXISTS index_execution_events_sessionId ON execution_events(sessionId)")
   db.execSQL("INSERT OR IGNORE INTO sessions VALUES ('default','First conversation',0,0,NULL,'lfm350','TINY','{}')")
   db.execSQL("INSERT OR REPLACE INTO index_outbox(noteId,version,sessionId) SELECT id,updatedAt,sessionId FROM notes")
  }}
 )
}
