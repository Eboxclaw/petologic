package ai.petologic.security

data class BoundedText(val text:String,val truncated:Boolean)
fun boundText(raw:String,maxBytes:Int=8192):BoundedText {
 require(maxBytes>=0)
 var offset=0;var bytes=0
 while(offset<raw.length){
  val point=raw.codePointAt(offset)
  val size=when{point<=0x7f->1;point<=0x7ff->2;point<=0xffff->3;else->4}
  if(bytes+size>maxBytes)break
  bytes+=size;offset+=Character.charCount(point)
 }
 return BoundedText(raw.substring(0,offset),offset<raw.length)
}
