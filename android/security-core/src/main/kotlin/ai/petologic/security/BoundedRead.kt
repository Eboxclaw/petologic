package ai.petologic.security

/** Available on Android 12; does not depend on newer InputStream.readNBytes APIs. */
fun java.io.InputStream.readAtMost(max:Int):ByteArray {
 require(max>=0)
 val output=java.io.ByteArrayOutputStream(minOf(max,32768));val buffer=ByteArray(32768)
 var remaining=max
 while(remaining>0){val count=read(buffer,0,minOf(buffer.size,remaining));if(count<0)break;if(count==0)continue;output.write(buffer,0,count);remaining-=count}
 return output.toByteArray()
}
