package ai.petologic.core
import java.text.Normalizer
import java.util.Locale

/** BERT uncased basic + greedy WordPiece tokenization for the pinned MiniLM vocabulary. */
class WordPiece(private val vocab:Map<String,Int>,private val maxTokens:Int=256){
 fun encode(text:String):LongArray {
  val clean=Normalizer.normalize(text.lowercase(Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
  val tokens=mutableListOf(101L)
  for(match in Regex("[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N}\\p{C}]").findAll(clean)){
   val word=match.value
   val parts=mutableListOf<Long>()
   if(word.length>100)parts+=100L
   else{
    var start=0
    while(start<word.length){
     var end=word.length;var found:Int?=null
     while(end>start){found=vocab[(if(start>0)"##" else "")+word.substring(start,end)];if(found!=null)break;end--}
     if(found==null){parts.clear();parts+=100L;break}
     parts+=found.toLong();start=end
    }
   }
   for(p in parts){if(tokens.size>=maxTokens-1)break;tokens+=p}
   if(tokens.size>=maxTokens-1)break
  }
  tokens+=102L;return tokens.toLongArray()
 }
}
