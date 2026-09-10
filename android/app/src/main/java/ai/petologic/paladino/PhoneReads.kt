package ai.petologic.paladino

import android.app.AlarmManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.text.Normalizer
import java.util.concurrent.TimeUnit

internal enum class PhoneRead { CLOCK, ALARM, CALENDAR, WEATHER, EMAIL, CAPABILITIES }
internal fun phoneReadRequest(input:String):PhoneRead? {
 if(input.length>240||input.contains('\n'))return null
 val s=Normalizer.normalize(input.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").trim()
 if(Regex("^(summari[sz]e|resume|traduz|translate|explain|explica|how does|what does|what is a|como funciona|o que e)\\b").containsMatchIn(s))return null
 if(Regex("\\b(set|create|delete|cancel|cria|criar|apaga|apagar|cancela|cancelar|marca|marcar)\\b").containsMatchIn(s))return null
 return when{
  Regex("(what|which|quais|que).*(tools|skills|ferramentas|capacidades)").containsMatchIn(s)->PhoneRead.CAPABILITIES
  Regex("(que horas|what time is it|current time|hora atual|data de hoje|que dia e hoje|today.s date)").containsMatchIn(s)->PhoneRead.CLOCK
  Regex("(next alarm|proximo alarme|meus alarmes|my alarms)").containsMatchIn(s)->PhoneRead.ALARM
  Regex("(my calendar|my agenda|calendar today|meu calendario|minha agenda|agenda de hoje|compromissos)").containsMatchIn(s)->PhoneRead.CALENDAR
  Regex("\\b(weather|meteorologia|previsao do tempo)\\b|como esta o tempo").containsMatchIn(s)->PhoneRead.WEATHER
  Regex("(my emails|my email|meus emails|meu email|minha caixa|my inbox)").containsMatchIn(s)->PhoneRead.EMAIL
  else->null
 }
}
internal class PhoneReads(private val context:Context){
 private val prefs=context.getSharedPreferences("phone_reads",Context.MODE_PRIVATE)
 private val http=OkHttpClient.Builder().callTimeout(15,TimeUnit.SECONDS).build()
 suspend fun read(kind:PhoneRead,pt:Boolean,network:Boolean):String=withContext(Dispatchers.IO){
  fun t(en:String,portuguese:String)=if(pt)portuguese else en
  val now=ZonedDateTime.now()
  val format=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
  when(kind){
   PhoneRead.CLOCK->t("Phone clock: ","Relógio do telemóvel: ")+now.format(format)
   PhoneRead.ALARM->{
    val alarm=context.getSystemService(AlarmManager::class.java).nextAlarmClock
    if(alarm==null)t("Android reports no next alarm. Some clock apps do not expose their alarms here.","O Android não indica um próximo alarme. Algumas apps de relógio não publicam os seus alarmes aqui.")
    else t("Next alarm reported by Android: ","Próximo alarme indicado pelo Android: ")+Instant.ofEpochMilli(alarm.triggerTime).atZone(now.zone).format(format)
   }
   PhoneRead.CALENDAR->{
    if(context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED)return@withContext t("Calendar access is off. Open Settings → Phone access and allow calendar reading, then ask again.","O acesso ao calendário está desligado. Abre Definições → Acesso ao telemóvel, permite a leitura e volta a perguntar.")
    val start=now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
    val end=now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
    val uri=CalendarContract.Instances.CONTENT_URI.buildUpon().also{ContentUris.appendId(it,start);ContentUris.appendId(it,end)}.build()
    val rows=mutableListOf<String>()
    context.contentResolver.query(uri,arrayOf(CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN,CalendarContract.Instances.ALL_DAY),null,null,CalendarContract.Instances.BEGIN+" ASC")?.use{c->
     while(rows.size<20&&c.moveToNext())rows+= "• "+(if(c.getInt(2)==1)t("All day","Todo o dia")else Instant.ofEpochMilli(c.getLong(1)).atZone(now.zone).format(DateTimeFormatter.ofPattern("HH:mm")))+" — "+c.getString(0).orEmpty().take(200)
    }?:return@withContext t("Calendar provider unavailable.","Calendário indisponível neste aparelho.")
    t("Today · device calendar (up to 20 events):\n","Hoje · calendário do aparelho (até 20 eventos):\n")+if(rows.isEmpty())t("No events returned. Only calendars synchronized to Android are visible.","Sem eventos devolvidos. Só são visíveis calendários sincronizados com o Android.")else rows.joinToString("\n")
   }
   PhoneRead.WEATHER->{
    val city=prefs.getString("city","").orEmpty()
    if(!network||!prefs.getBoolean("weather",false)||city.isBlank())return@withContext t("Set your weather city and allow Open-Meteo in Settings → Phone access. This sends only the city/coordinates; no GPS or conversation.","Define a cidade e permite Open-Meteo em Definições → Acesso ao telemóvel. Só envia a cidade/coordenadas; não usa GPS nem envia a conversa.")
    fun fetch(url:String):JSONObject=http.newCall(Request.Builder().url(url).build()).execute().use{r->check(r.isSuccessful){t("Weather service unavailable; try again.","Meteorologia indisponível; tenta novamente.")};val source=checkNotNull(r.body).source();check(!source.request(65537)){"Weather response too large"};JSONObject(source.readUtf8())}
    val geo=fetch("https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder().addQueryParameter("name",city).addQueryParameter("count","1").build().toString()).optJSONArray("results")?.optJSONObject(0)?:return@withContext t("City not found. Include the country in Settings.","Cidade não encontrada. Indica também o país nas Definições.")
    val data=fetch("https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder().addQueryParameter("latitude",geo.getDouble("latitude").toString()).addQueryParameter("longitude",geo.getDouble("longitude").toString()).addQueryParameter("current","temperature_2m,precipitation,wind_speed_10m").addQueryParameter("timezone","auto").build().toString())
    val current=data.getJSONObject("current")
    "${geo.getString("name")}, ${geo.optString("country")} · ${current.getString("time")} ${data.optString("timezone")}\n"+t("Temperature","Temperatura")+": ${current.getDouble("temperature_2m")} °C\n"+t("Precipitation","Precipitação")+": ${current.getDouble("precipitation")} mm\n"+t("Wind","Vento")+": ${current.getDouble("wind_speed_10m")} km/h\n"+t("Source: Open-Meteo model estimate, not a phone sensor.","Fonte: estimativa do modelo Open-Meteo, não um sensor do telemóvel.")
   }
   PhoneRead.EMAIL->t("Email is not connected yet. Gmail/Outlook require an authorized account connection. I cannot read another app's private inbox. You can paste an email here for reading; it stays local in Tiny.","O email ainda não está ligado. Gmail/Outlook exigem uma ligação autorizada à conta. Não posso ler a caixa privada de outra app. Podes colar um email aqui para leitura local em Tiny.")
   PhoneRead.CAPABILITIES->t("Available: private note search/save (writes need approval), phone clock, next Android alarm, today's synchronized calendar (permission required), current weather for your configured city (Open-Meteo opt-in). Email account reading and alarm/calendar editing are not connected. Try: What time is it? / My calendar today / Next alarm / Weather.","Disponível: pesquisar/guardar notas privadas (escritas pedem aprovação), relógio, próximo alarme Android, calendário sincronizado de hoje (com permissão), meteorologia atual da cidade configurada (Open-Meteo opcional). Leitura da conta de email e edição de alarmes/calendário ainda não estão ligadas. Experimenta: Que horas são? / Minha agenda / Próximo alarme / Meteorologia.")
  }
 }
}
