package ai.petologic.paladino

import ai.petologic.paladino.skills.FeedbackEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

fun interface FeedbackHook{fun on(event:FeedbackEvent)}

@Composable internal fun rememberFeedback():FeedbackHook{
 val view=LocalView.current
 val context=LocalContext.current
 return remember{FeedbackHook{event->FeedbackPlayer.combined(context,view,event)}}
}
