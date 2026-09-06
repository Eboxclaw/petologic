package ai.petologic.paladino.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ai.petologic.core.*

@Entity(tableName="notes")
data class NoteRow(@PrimaryKey val id:String, val text:String, val sensitivity:String="LOCAL_ONLY", val updatedAt:Long=System.currentTimeMillis(), @ColumnInfo(defaultValue="'default'") val sessionId:String="default") {
 fun domain()=MemoryNote(id,text,Sensitivity.valueOf(sensitivity),updatedAt)
}
@Entity(tableName="messages")
data class MessageRow(@PrimaryKey val id:String, val speaker:String, val text:String, val mode:String="TINY", val createdAt:Long=System.currentTimeMillis(), @ColumnInfo(defaultValue="'default'") val sessionId:String="default")
@Entity(tableName="actions")
data class ActionRow(@PrimaryKey val id:String, val tool:String,val argument:String,val argumentHash:String,val expiresAt:Long,val status:String="PENDING",@ColumnInfo(defaultValue="'default'") val sessionId:String="default")
@Entity(tableName="index_outbox")
data class IndexRow(@PrimaryKey val noteId:String,val version:Long,@ColumnInfo(defaultValue="'default'") val sessionId:String="default")
@Entity(tableName="graph_edges",indices=[Index("sourceId"),Index("fromId"),Index("toId")])
data class EdgeRow(@PrimaryKey val id:String,val fromId:String,val toId:String,val relation:String,val sourceId:String,val sourceVersion:Long)
@Entity(tableName="tasks")
data class TaskRow(@PrimaryKey val id:String,val request:String,val mode:String,val status:String,val createdAt:Long=System.currentTimeMillis(), @ColumnInfo(defaultValue="'default'") val sessionId:String="default")
@Entity(tableName="reminders")
data class ReminderRow(@PrimaryKey val id:String,val text:String,val atMillis:Long,val delivered:Boolean=false)

@Entity(tableName="embeddings")
data class EmbeddingRow(@PrimaryKey val noteId:String,val sourceVersion:Long,val modelId:String,val vector:ByteArray)

@Entity(tableName="sessions")
data class SessionRow(@PrimaryKey val id:String,val title:String,val createdAt:Long=System.currentTimeMillis(),val archived:Boolean=false,val parentId:String?=null,val modelId:String="lfm350",val mode:String="TINY",val optionsJson:String="{}")
@Entity(tableName="execution_events",indices=[Index("sessionId")])
data class ExecutionEventRow(@PrimaryKey val id:String,val sessionId:String,val taskId:String,val type:String,val detail:String,val createdAt:Long=System.currentTimeMillis())

@Dao
interface PaladinoDao {
 @Query("SELECT * FROM sessions ORDER BY createdAt DESC") fun sessions():Flow<List<SessionRow>>
 @Query("SELECT * FROM sessions WHERE id=:id") suspend fun session(id:String):SessionRow?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun session(row:SessionRow)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun event(row:ExecutionEventRow)
 @Query("SELECT * FROM execution_events WHERE sessionId=:id ORDER BY createdAt DESC LIMIT 200") fun events(id:String):Flow<List<ExecutionEventRow>>
 @Query("SELECT * FROM messages WHERE sessionId=:id ORDER BY createdAt, rowid") suspend fun allMessages(id:String):List<MessageRow>
 @Query("SELECT * FROM embeddings WHERE noteId=:id AND sourceVersion=:version AND modelId=:model") suspend fun embedding(id:String,version:Long,model:String):EmbeddingRow?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun embedding(row:EmbeddingRow)
 @Query("DELETE FROM embeddings WHERE noteId=:id") suspend fun deleteEmbedding(id:String)
 @Query("SELECT * FROM notes WHERE sessionId=:sessionId ORDER BY updatedAt DESC") fun observeNotes(sessionId:String="default"):Flow<List<NoteRow>>
 @Query("SELECT * FROM notes WHERE sessionId=:sessionId ORDER BY updatedAt DESC LIMIT 10000") suspend fun notes(sessionId:String="default"):List<NoteRow>
 @Query("SELECT * FROM notes WHERE id=:id") suspend fun note(id:String):NoteRow?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putNote(note:NoteRow)
 @Query("DELETE FROM notes WHERE id=:id") suspend fun deleteNote(id:String)
 @Query("SELECT * FROM messages WHERE sessionId=:sessionId ORDER BY createdAt, rowid") fun messages(sessionId:String="default"):Flow<List<MessageRow>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun message(message:MessageRow)
 @Query("SELECT * FROM messages WHERE sessionId=:sessionId ORDER BY createdAt DESC LIMIT 40") suspend fun recentMessages(sessionId:String="default"):List<MessageRow>
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun putAction(action:ActionRow)
 @Query("SELECT * FROM actions WHERE id=:id") suspend fun action(id:String):ActionRow?
 @Query("UPDATE actions SET status=:status WHERE id=:id") suspend fun actionStatus(id:String,status:String)
 @Query("UPDATE actions SET status='CANCELLED' WHERE status='PENDING'") suspend fun cancelPending()
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun outbox(row:IndexRow)
 @Query("SELECT * FROM index_outbox") suspend fun pendingIndex():List<IndexRow>
 @Query("DELETE FROM index_outbox WHERE noteId=:id AND version=:version") suspend fun indexDone(id:String,version:Long)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun edge(edge:EdgeRow)
 @Query("SELECT * FROM graph_edges WHERE sourceId=:id") suspend fun edges(id:String):List<EdgeRow>
 @Query("DELETE FROM graph_edges WHERE sourceId=:id") suspend fun deleteEdges(id:String)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun task(row:TaskRow)
 @Query("UPDATE tasks SET status=:status WHERE id=:id") suspend fun taskStatus(id:String,status:String)
 @Query("UPDATE tasks SET status='CANCELLED' WHERE status NOT IN ('COMPLETED','CANCELLED','FAILED')") suspend fun recoverTasks()
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun reminder(row:ReminderRow)
 @Query("SELECT * FROM reminders WHERE id=:id") suspend fun reminder(id:String):ReminderRow?
 @Query("UPDATE reminders SET delivered=1 WHERE id=:id") suspend fun delivered(id:String)
}
@Database(entities=[NoteRow::class,MessageRow::class,ActionRow::class,IndexRow::class,EdgeRow::class,TaskRow::class,ReminderRow::class,EmbeddingRow::class,SessionRow::class,ExecutionEventRow::class],version=3,exportSchema=true)
abstract class PaladinoDatabase:RoomDatabase(){abstract fun dao():PaladinoDao}
