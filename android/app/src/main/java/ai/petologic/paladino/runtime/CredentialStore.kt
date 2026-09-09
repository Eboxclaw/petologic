package ai.petologic.paladino.runtime

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStore(context:Context,preferencesName:String="provider_credentials") {
 private val prefs=context.getSharedPreferences(preferencesName,Context.MODE_PRIVATE)
 private fun alias(provider:CloudProvider)="paladino.${provider.id}.v1"
 private fun slot(provider:CloudProvider)=if(provider==CloudProvider.OPENROUTER)"key" else "key.${provider.id}"
 private fun key(provider:CloudProvider):SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  (store.getKey(alias(provider),null) as? SecretKey)?.let{return it}
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias(provider),KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
 }
 fun save(value:String,provider:CloudProvider=CloudProvider.OPENROUTER){
  require(value.trim().length in 16..512 && !value.any{it.isWhitespace()}){"Enter a valid API key."}
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key(provider))}
  val encrypted=cipher.doFinal(value.toByteArray())
  check(prefs.edit().putString(slot(provider),Base64.encodeToString(cipher.iv+encrypted,Base64.NO_WRAP)).commit()){"Could not save provider connection."}
 }
 fun read(provider:CloudProvider=CloudProvider.OPENROUTER):String? {
  val text=prefs.getString(slot(provider),null)?:return null
  return try{val b=Base64.decode(text,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(provider),GCMParameterSpec(128,b.copyOfRange(0,12)))};String(c.doFinal(b.copyOfRange(12,b.size)))}catch(_:Exception){null}
 }
 fun disconnect(provider:CloudProvider=CloudProvider.OPENROUTER){check(prefs.edit().remove(slot(provider)).commit())}
}
