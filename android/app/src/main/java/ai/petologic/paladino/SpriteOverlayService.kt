package ai.petologic.paladino

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs

/** User-started companion window; never reads other apps or starts autonomous inference. */
class SpriteOverlayService:Service(){
 companion object { val running=MutableStateFlow(false);const val STOP="stop_sprite" }
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val app get()=application as PaladinoApplication
 private val positionPrefs by lazy{getSharedPreferences("sprite_position",MODE_PRIVATE)}
 private lateinit var windows:WindowManager
 private var root:ViewGroup?=null
 private var layout:WindowManager.LayoutParams?=null
 private var animationLoad:Job?=null
 private var animationResource:Int?=null
 private var animation:android.graphics.drawable.AnimatedImageDrawable?=null
 private var appliedX=Int.MIN_VALUE
 private var appliedY=Int.MIN_VALUE
 private var expanded=false
 private var animator:SpriteAnimator?=null
 private var text:TextView?=null
 private var title:TextView?=null
 private var status:TextView?=null
 private var input:EditText?=null
 private var send:ImageButton?=null
 private var observedSession=""
 private var observation:Job?=null
 private val markdown by lazy{markdownRenderer(this)}
 private val drafts=mutableMapOf<String,String>()
 private val gold=PetPalette.gold
 private val ink=PetPalette.background
 private val textShadow=0xB3000000.toInt()
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun surface(color:Int,radius:Int=24,border:Boolean=false)=GradientDrawable().apply{
  setColor(color);cornerRadius=dp(radius).toFloat();if(border)setStroke(dp(1),PetPalette.outline)
 }
 /** Legibility on any wallpaper without a card behind the text. */
 private fun TextView.floatText(color:Int,size:Float){setTextColor(color);textSize=size;setShadowLayer(dp(3).toFloat(),0f,0f,textShadow)}
 private fun unlocked()=!getSystemService(KeyguardManager::class.java).isKeyguardLocked&&getSystemService(PowerManager::class.java).isInteractive
 private val screenEvents=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
  if(intent.action==Intent.ACTION_SCREEN_OFF){collapse();animator?.pause();root?.visibility=View.GONE}
  if(intent.action==Intent.ACTION_USER_PRESENT){render()}
 }}
 override fun onBind(intent:Intent?)=null
 override fun onCreate(){
  super.onCreate();windows=getSystemService(WindowManager::class.java)
  val notifications=getSystemService(NotificationManager::class.java)
  notifications.createNotificationChannel(NotificationChannel("sprite",uiText("Floating Paladino"),NotificationManager.IMPORTANCE_LOW))
  val stop=PendingIntent.getService(this,1,Intent(this,SpriteOverlayService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE)
  val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
  startForeground(41,Notification.Builder(this,"sprite").setSmallIcon(R.drawable.ic_paladino).setContentTitle(uiText("Paladino is floating")).setContentText(uiText("Tap to chat • drag to move")).setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,uiText("Stop Sprite"),stop).build()).build())
  val filter=IntentFilter().apply{addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_USER_PRESENT)}
  if(Build.VERSION.SDK_INT>=33)registerReceiver(screenEvents,filter,RECEIVER_NOT_EXPORTED)else registerReceiver(screenEvents,filter)
  scope.launch{app.tinyPets.state.map{it.sizeDp to it.animate}.distinctUntilChanged().drop(1).collect{if(root!=null)render()}}
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action==STOP||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
  if(root==null){running.value=true;render()}
  return START_NOT_STICKY
 }
 override fun onConfigurationChanged(newConfig:Configuration){super.onConfigurationChanged(newConfig);if(root!=null)render()}
 private fun workArea():Rect{
  val metrics=windows.currentWindowMetrics
  val inset=metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
  val keyboard=root?.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom?:0
  return Rect(inset.left+dp(8),inset.top+dp(8),metrics.bounds.width()-inset.right-dp(8),metrics.bounds.height()-maxOf(inset.bottom,keyboard)-dp(8))
 }
 private fun params():WindowManager.LayoutParams {
  val area=workArea();val width=if(expanded)minOf(dp(304),area.width())else dp(app.tinyPets.state.value.sizeDp+16)
  return WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
    if(expanded)WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
   PixelFormat.TRANSLUCENT).apply{
   gravity=Gravity.TOP or Gravity.LEFT
   x=OverlayPosition.coordinate(positionPrefs.getFloat("x",1f),area.left,area.right,width)
   y=OverlayPosition.coordinate(positionPrefs.getFloat("y",.5f),area.top,area.bottom,if(expanded)dp(290)else width)
   softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
  }
 }
 private fun clampWindow(){
  val view=root?:return;val p=layout?:return;val area=workArea()
  p.x=p.x.coerceIn(area.left,maxOf(area.left,area.right-view.width))
  p.y=p.y.coerceIn(area.top,maxOf(area.top,area.bottom-view.height))
  if(p.x!=appliedX||p.y!=appliedY){runCatching{windows.updateViewLayout(view,p)};appliedX=p.x;appliedY=p.y}
 }
 private fun savePosition(){
  val view=root?:return;val p=layout?:return;val area=workArea()
  positionPrefs.edit().putFloat("x",OverlayPosition.fraction(p.x,area.left,area.right,view.width)).putFloat("y",OverlayPosition.fraction(p.y,area.top,area.bottom,view.height)).apply()
 }
 private fun draggable(view:View){
  var downX=0f;var downY=0f;var startX=0;var startY=0;var dragging=false
  val slop=ViewConfiguration.get(this).scaledTouchSlop
  view.setOnTouchListener{target,event->
   when(event.actionMasked){
    MotionEvent.ACTION_DOWN->{downX=event.rawX;downY=event.rawY;startX=layout?.x?:0;startY=layout?.y?:0;dragging=false;true}
    MotionEvent.ACTION_MOVE->{
     val dx=event.rawX-downX;val dy=event.rawY-downY
     if(abs(dx)>slop||abs(dy)>slop)dragging=true
     if(dragging){layout?.let{it.x=startX+dx.toInt();it.y=startY+dy.toInt()};clampWindow()};true
    }
    MotionEvent.ACTION_UP->{
     if(dragging){
      if(!expanded){val area=workArea();layout?.let{it.x=if(it.x+(root?.width?:0)/2<area.centerX())area.left else area.right-(root?.width?:0)}}
      clampWindow();savePosition()
     }else target.performClick()
     true
    }
    MotionEvent.ACTION_CANCEL->{if(dragging){clampWindow();savePosition()};true}
    else->false
   }
  }
 }
 private fun saveDraft(){input?.let{drafts[observedSession]=it.text.toString()}}
 private fun collapse(){saveDraft();expanded=false;render()}
 private fun icon(id:Int,label:String,filled:Boolean=false,action:()->Unit)=ImageButton(this).apply{
  setImageResource(id);contentDescription=uiText(label);imageTintList=android.content.res.ColorStateList.valueOf(if(filled)ink else gold)
  background=if(filled)surface(gold,20)else null
  setPadding(dp(12),dp(12),dp(12),dp(12));filterTouchesWhenObscured=true
  setOnClickListener{action()};layoutParams=LinearLayout.LayoutParams(dp(48),dp(48))
 }
 private fun action(id:Int,label:String,run:()->Unit)=TextView(this).apply{
  floatText(gold,12f);gravity=Gravity.CENTER;minHeight=dp(48);setPadding(dp(10),dp(8),dp(10),dp(8))
  setCompoundDrawablesRelativeWithIntrinsicBounds(id,0,0,0);compoundDrawablePadding=dp(6)
  isClickable=true;isFocusable=true;filterTouchesWhenObscured=true;setOnClickListener{run()}
 }
 /** Fresh player per window build; idle chains both clips for a continuous loop. */
 private fun startAnimation(sprite:ImageView){
  animator?.release()
  val player=SpriteAnimator(resources,scope).also{animator=it}
  player.gate={app.tinyPets.state.value.animate&&android.animation.ValueAnimator.areAnimatorsEnabled()&&unlocked()}
  player.attach(sprite)
  showReaction(app.sessionHub.active.value.ui.value.petReaction())
 }
 private fun showReaction(reaction:PetReaction){
  val player=animator?:return
  if(reaction==PetReaction.RUNNING)player.playLooping(reaction.animationResource())else player.playIdle(idlePair.first,idlePair.second)
 }
 private fun render(){
  saveDraft();animator?.release();animator=null;observation?.cancel();root?.let{runCatching{windows.removeView(it)}}
  root=null;text=null;title=null;status=null;input=null;send=null
  if(!Settings.canDrawOverlays(this)){stopSelf();return}
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(if(expanded)16 else 8),dp(8),dp(if(expanded)16 else 8),dp(8));isFocusableInTouchMode=true}
  if(!expanded){
   val sprite=ImageView(this).apply{setImageResource(R.drawable.paladino_static);contentDescription=uiText("Floating Paladino. Tap to chat");adjustViewBounds=true;filterTouchesWhenObscured=true;setOnClickListener{FeedbackPlayer.combined(this@SpriteOverlayService,it,ai.petologic.paladino.skills.FeedbackEvent.SPRITE_TAP);expanded=true;render()}}
   panel.addView(sprite,LinearLayout.LayoutParams(-1,dp(app.tinyPets.state.value.sizeDp)));draggable(sprite);startAnimation(sprite)
   val badge=TextView(this).apply{floatText(gold,10f);gravity=Gravity.CENTER;setPadding(dp(4),dp(2),dp(4),dp(2));setOnClickListener{expanded=true;render()}}
   panel.addView(badge)
   observation=scope.launch{app.sessionHub.active.collectLatest{controller->controller.ui.collect{state->
    val reaction=state.petReaction();showReaction(reaction);badge.text=uiText(reaction.label);badge.visibility=if(reaction==PetReaction.IDLE)View.GONE else View.VISIBLE
    sprite.contentDescription=uiText("Floating Paladino. Tap to chat")+". "+uiText(reaction.label);panel.post{clampWindow()}
   }}}

  }else{
   val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
   val headerSprite=ImageView(this).apply{setImageResource(R.drawable.paladino_static);contentDescription="Paladino"}
   header.addView(headerSprite,LinearLayout.LayoutParams(dp(40),dp(44)));startAnimation(headerSprite)
   title=TextView(this).apply{text="Paladino";floatText(Color.WHITE,16f);setPadding(dp(10),0,dp(6),0);maxLines=2;contentDescription=uiText("Drag Paladino bubble")}
   header.addView(title,LinearLayout.LayoutParams(0,dp(52),1f));draggable(title!!)
   header.addView(icon(R.drawable.ic_pet_collapse,"Collapse"){collapse()});panel.addView(header)
   status=TextView(this).apply{floatText(gold,11f);setPadding(0,dp(6),0,dp(10));maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};panel.addView(status)
   text=TextView(this).apply{floatText(PetPalette.text,14f);maxLines=5;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(0,0,0,dp(12))};panel.addView(text)
   val composer=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
   input=EditText(this).apply{hint=uiText("Message Paladino");setHintTextColor(PetPalette.muted);setTextColor(Color.WHITE);textSize=14f;maxLines=2;minHeight=dp(48);background=null;setShadowLayer(dp(2).toFloat(),0f,0f,textShadow);setPadding(dp(12),dp(8),dp(12),dp(8));inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;filterTouchesWhenObscured=true}
   composer.addView(input,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(8)})
   send=icon(R.drawable.ic_pet_send,"Send",true){
    val controller=app.sessionHub.active.value
    if(controller.ui.value.busy)controller.cancel()else{val draft=input!!.text.toString();if(draft.isNotBlank()){FeedbackPlayer.combined(this@SpriteOverlayService,send!!,ai.petologic.paladino.skills.FeedbackEvent.MESSAGE_SENT);controller.send(draft);input!!.setText("");drafts[controller.sessionId]=""}}
   };composer.addView(send);panel.addView(composer)
   panel.addView(View(this).apply{setBackgroundColor(0x40F0D64B)},LinearLayout.LayoutParams(-1,dp(1)).apply{topMargin=dp(2)})
   val largeText=resources.configuration.fontScale>1.3f
   val actions=LinearLayout(this).apply{orientation=if(largeText)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL;setPadding(0,dp(10),0,dp(4))}
   val newChat=action(R.drawable.ic_pet_new,"New chat"){saveDraft();app.sessionHub.create()}
   actions.addView(newChat,if(largeText)LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)} else LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(8)})
   val fullChat=action(R.drawable.ic_pet_open,"Full chat"){collapse();startActivity(Intent(this,MainActivity::class.java).putExtra("open_chat",true).putExtra("widget_session",app.sessionHub.active.value.sessionId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))}
   actions.addView(fullChat,if(largeText)LinearLayout.LayoutParams(-1,-2) else LinearLayout.LayoutParams(0,-2,1f));panel.addView(actions)
   panel.setOnTouchListener{_,event->if(event.action==MotionEvent.ACTION_OUTSIDE){collapse();true}else false}
   panel.setOnKeyListener{_,key,event->if(key==KeyEvent.KEYCODE_BACK&&event.action==KeyEvent.ACTION_UP){collapse();true}else false}
   observation=scope.launch{app.sessionHub.active.collectLatest{controller->
    if(observedSession!=controller.sessionId){saveDraft();observedSession=controller.sessionId}
    input?.setText(drafts[observedSession]?:"")
     combine(controller.ui,controller.messages,controller.sessionInfo){state,messages,session->Triple(state,messages,session)}.collect{(state,messages,session)->
      showReaction(state.petReaction())
     title?.text="Paladino";status?.text="${state.mode} · ${uiText(state.petReaction().label)} · ${sessionText(session.title)}"
     text?.let{markdown.setMarkdown(it,when{state.pendingModelMessage!=null->uiText("Install a model to continue.");state.error!=null->uiText(state.error);state.action!=null||state.cloud!=null->uiText("Open the app to review this request.");state.busy->state.streaming.ifBlank{uiText(state.status)};else->messages.lastOrNull{it.speaker=="assistant"}?.text?:uiText("A little help, wherever you are.")})}
     val reviewing=state.action!=null||state.cloud!=null
     fullChat.text=uiText(if(state.pendingModelMessage!=null)"Set up model" else if(reviewing)"Open to approve" else "Full chat")
     newChat.isEnabled=!state.busy&&!reviewing;newChat.alpha=if(newChat.isEnabled)1f else .4f
     send?.setImageResource(if(state.busy)R.drawable.ic_pet_stop else R.drawable.ic_pet_send);send?.contentDescription=uiText(if(state.busy)"Stop" else "Send")
     send?.isEnabled=state.pendingModelMessage==null&&(state.busy||(state.action==null&&state.cloud==null));send?.alpha=if(send?.isEnabled==true)1f else .4f;input?.isEnabled=!state.busy&&state.pendingModelMessage==null
     panel.post{clampWindow()}
    }
   }}
  }
  val windowRoot:ViewGroup=if(expanded)object:ScrollView(this){
   override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
    super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(workArea().height().coerceAtLeast(dp(48)),View.MeasureSpec.AT_MOST))
   }
  }.apply{isFillViewport=false;addView(panel);setOnTouchListener{_,event->if(event.action==MotionEvent.ACTION_OUTSIDE){collapse();true}else false}}else panel
  root=windowRoot;layout=params();if(!unlocked())windowRoot.visibility=View.GONE
  panel.setOnApplyWindowInsetsListener{_,insets->panel.post{clampWindow()};insets}
  try{windows.addView(windowRoot,layout);appliedX=layout!!.x;appliedY=layout!!.y;panel.post{clampWindow()}}catch(_:RuntimeException){root=null;stopSelf()}
 }
 override fun onDestroy(){
  running.value=false;animator?.release();animator=null;scope.cancel();runCatching{unregisterReceiver(screenEvents)};root?.let{runCatching{windows.removeView(it)}};root=null
  app.sessionHub.active.value.cancel();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()
 }
}
