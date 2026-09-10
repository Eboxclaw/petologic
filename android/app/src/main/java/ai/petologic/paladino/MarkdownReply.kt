package ai.petologic.paladino

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.widget.TextView
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.*
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import org.commonmark.node.Image

/** Native text spans only: no WebView, HTML plugin, image loader or automatic network access. */
internal fun markdownRenderer(context:Context):Markwon=Markwon.builder(context)
 .usePlugin(StrikethroughPlugin.create())
 .usePlugin(TablePlugin.create(context))
 .usePlugin(TaskListPlugin.create(context))
 .usePlugin(object:AbstractMarkwonPlugin(){
  override fun configureTheme(builder:MarkwonTheme.Builder){
   builder.linkColor(PetPalette.gold).codeBackgroundColor(PetPalette.raised).blockQuoteColor(PetPalette.gold)
  }
  override fun configureConfiguration(builder:MarkwonConfiguration.Builder){
   builder.linkResolver{view,link->
    if(isWebLink(link))runCatching{view.context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
   }
  }
  override fun configureVisitor(builder:MarkwonVisitor.Builder){
   // Render image alt text as text. A model response cannot fetch remote pixels.
   builder.on(Image::class.java){visitor,node->visitor.visitChildren(node)}
  }
 }).build()
internal fun isWebLink(link:String):Boolean=runCatching{
 val uri=java.net.URI(link)
 uri.scheme?.lowercase() in setOf("https","http")&&!uri.host.isNullOrBlank()&&uri.rawUserInfo==null
}.getOrDefault(false)

@Composable internal fun MarkdownReply(content:String,modifier:Modifier=Modifier,maxLines:Int=Int.MAX_VALUE){
 val context=LocalContext.current
 val renderer=remember(context){markdownRenderer(context)}
 val spanned=remember(content,renderer){renderer.toMarkdown(content)}
 // Plain messages keep Compose semantics and selectable native chat text without extra views.
 if(spanned.getSpans(0,spanned.length,Any::class.java).isEmpty()){
  androidx.compose.foundation.text.selection.SelectionContainer{
   Text(spanned.toString(),modifier,fontSize=16.sp,lineHeight=24.sp,maxLines=maxLines,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
  }
 }else AndroidView(modifier=modifier,factory={TextView(it).apply{
  setTextColor(PetPalette.text);setTextSize(16f);setLineSpacing(0f,1.25f)
  setTextIsSelectable(true);setPadding(0,0,0,0)
 }},update={view->
  view.maxLines=maxLines;view.ellipsize=if(maxLines<Int.MAX_VALUE)TextUtils.TruncateAt.END else null
  renderer.setParsedMarkdown(view,spanned)
 })
}
