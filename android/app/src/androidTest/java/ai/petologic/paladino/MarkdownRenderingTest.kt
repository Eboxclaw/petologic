package ai.petologic.paladino
import android.content.res.Configuration
import android.graphics.Typeface
import io.noties.markwon.core.spans.StrongEmphasisSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.*
import org.junit.Assert.*
import java.util.Locale

class MarkdownRenderingTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun markdown_and_translations_render_without_remote_images(){
  val context=compose.activity
  val pt=context.createConfigurationContext(Configuration(context.resources.configuration).apply{setLocale(Locale.forLanguageTag("pt-PT"))})
  assertEquals("Definições",pt.uiText("Settings"))
  assertEquals("3 modelos verificados disponíveis",pt.uiText("3 verified models available"))
  assertEquals("Gerir chaves API de OpenAI",pt.uiText("Manage %1\$s API keys","OpenAI"))
  val renderer=markdownRenderer(context)
  val content="# Paladino\n\n**Negrito** e *itálico*; ~~riscado~~.\n\n- Primeiro\n- Segundo\n\n1. Abrir\n2. Confirmar\n\n> Uma nota importante.\n\n```json\n{\"tool\": \"notes_search\"}\n```\n\n| Modo | Local |\n| --- | --- |\n| Tiny | Sim |\n\n- [x] Guardado\n- [ ] Rever\n\n[Documentação](https://example.com)\n\n![Imagem sem download](https://example.invalid/never-fetch.png)"
  val rendered=renderer.toMarkdown(content)
  assertTrue(rendered.toString().contains("Negrito"));assertFalse(rendered.toString().contains("**Negrito**"))
  assertTrue(rendered.getSpans(0,rendered.length,StrongEmphasisSpan::class.java).isNotEmpty())
  assertTrue(rendered.toString().contains("Imagem sem download"))
  assertTrue(rendered.getSpans(0,rendered.length,Any::class.java).none{it.javaClass.name.contains("AsyncDrawable")})
  compose.runOnUiThread{context.setContent{MaterialTheme(colorScheme=PetColors){Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp)){MarkdownReply(content)}}}}
  compose.waitForIdle()
  UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(java.io.File(context.filesDir,"markdown-preview.png"))
 }
}
