package ai.petologic.paladino
import org.junit.Test
import org.junit.Assert.*
class PhoneReadRoutingTest {
 @Test fun routes_read_requests_and_keeps_writes_out(){
  assertEquals(PhoneRead.CLOCK,phoneReadRequest("Que horas são?"))
  assertEquals(PhoneRead.ALARM,phoneReadRequest("Qual é o próximo alarme?"))
  assertEquals(PhoneRead.CALENDAR,phoneReadRequest("Show my calendar today"))
  assertEquals(PhoneRead.WEATHER,phoneReadRequest("Como está o tempo?"))
  assertEquals(PhoneRead.EMAIL,phoneReadRequest("Read my emails"))
  assertNull(phoneReadRequest("What time is my meeting?"))
  assertNull(phoneReadRequest("Create my calendar event"))
  assertNull(phoneReadRequest("Olá, como estás?"))
  assertNull(phoneReadRequest("Translate: What time is it?"))
  assertNull(phoneReadRequest("How does my calendar work?"))
  assertNull(phoneReadRequest("Summarize this email:\nWhat time is it?"))
 }
}
