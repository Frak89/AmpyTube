package com.ampy.ampytube

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.sin

/**
 * AmpyTube UI: screen-off playback is handled by PlaybackService.
 * Effects never control/stop the media session.
 */
class MainActivity : Activity() {
    private val dark=Color.rgb(7,16,28)
    private val panel=Color.rgb(18,34,53)
    private val aqua=Color.rgb(103,229,232)
    private val white=Color.rgb(239,247,252)
    private val grey=Color.rgb(155,178,195)
    private val purple=Color.rgb(190,153,246)
    private val ui=Handler(Looper.getMainLooper())
    private val executor=Executors.newSingleThreadExecutor()

    private lateinit var root:FrameLayout
    private lateinit var drawer:LinearLayout
    private lateinit var content:LinearLayout
    private lateinit var urlField:EditText
    private lateinit var status:TextView
    private lateinit var title:TextView
    private lateinit var mode:TextView
    private lateinit var mainPlay:TextView
    private lateinit var miniPlay:TextView
    private lateinit var miniTitle:TextView
    private lateinit var seek:SeekBar
    private lateinit var time:TextView
    private lateinit var videoHolder:FrameLayout
    private lateinit var playerShell:FrameLayout
    private lateinit var playerView:PlayerView
    private lateinit var orb:MorphOrbView
    private lateinit var mini:LinearLayout

    private var controllerFuture:ListenableFuture<MediaController>?=null
    private var player:MediaController?=null
    private var source:YouTubeResolver.Source?=null
    private var localUri:Uri?=null
    private var localName="File locale"
    private var onlyAudio=false
    private var minimized=false
    private var drawerOpen=false
    private var durationScale=1f
    private var motionIntensity=.65f
    private var motionEnabled=true
    private var seeking=false
    private var requestCount=0
    private var autoStart=false
    private var fullScreen:Dialog?=null

    private val refreshTask=object:Runnable {
        override fun run() {
            refresh()
            ui.postDelayed(this,600)
        }
    }

    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.statusBarColor=dark
        window.navigationBarColor=dark
        volumeControlStream=AudioManager.STREAM_MUSIC
        buildUi()
        urlField.setText(getPreferences(MODE_PRIVATE).getString("last_url",""))
        if(intent?.action==Intent.ACTION_SEND) acceptShare(intent)
        connect()
    }

    private fun dp(x:Int)= (x*resources.displayMetrics.density+.5f).toInt()

    private fun rounded(fill:Int,radius:Int=16):GradientDrawable = GradientDrawable().apply {
        setColor(fill);cornerRadius=dp(radius).toFloat()
        setStroke(dp(1),Color.rgb(43,80,100))
    }

    private fun label(text:String,size:Float=14f,color:Int=white,bold:Boolean=false):TextView =
        TextView(this).apply {
            this.text=text
            textSize=size
            setTextColor(color)
            gravity=Gravity.CENTER_VERTICAL
            if(bold) typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
        }

    private fun button(text:String,filled:Boolean=false,press:()->Unit):TextView =
        label(text,13f,if(filled) dark else aqua,true).apply {
            gravity=Gravity.CENTER
            background=rounded(if(filled) aqua else panel,12)
            setPadding(dp(10),dp(12),dp(10),dp(12))
            isClickable=true;isFocusable=true
            setOnClickListener { press() }
        }

    private fun line(vararg views:View):LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.HORIZONTAL
        views.forEachIndexed { index,v ->
            addView(v,LinearLayout.LayoutParams(0,-2,1f).apply {
                if(index>0) marginStart=dp(6)
            })
        }
    }

    private fun gap(px:Int)=View(this).apply {
        layoutParams=LinearLayout.LayoutParams(1,dp(px))
    }

    private fun buildUi() {
        root=FrameLayout(this).apply { setBackgroundColor(dark) }
        setContentView(root)
        drawer=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(45),dp(13),0)
            background=rounded(panel)
        }
        root.addView(drawer,FrameLayout.LayoutParams(dp(275),-1,Gravity.START))
        drawer.addView(label("AMPY / TUBE",24f,aqua,true))
        drawer.addView(label("RESIDE  •  ORGANIC MOTION",11f,purple))
        drawer.addView(gap(28))
        drawer.addView(button("♫ SOLO AUDIO") { setAudio(true); reside(false) })
        drawer.addView(gap(12))
        drawer.addView(button("▶ VIDEO") { setAudio(false); reside(false) })
        drawer.addView(gap(12))
        drawer.addView(button("▣ FILE LOCALE") { reside(false); openFile() })
        drawer.addView(gap(12))
        drawer.addView(button("⟳ ROTAZIONE 3D") { reside(false); rotate() })
        drawer.addView(gap(12))
        drawer.addView(button("◇ FLIP 3D") { reside(false); flip() })
        drawer.addView(gap(12))
        drawer.addView(button("◌ LIQUID") { reside(false); liquid() })
        drawer.addView(gap(35))
        drawer.addView(label("Nessun effetto glitch",12f,grey))

        content=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(dark)
            pivotX=0f
        }
        root.addView(content,FrameLayout.LayoutParams(-1,-1))
        val scroll=ScrollView(this).apply { isFillViewport=true }
        content.addView(scroll)
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(15),dp(18),dp(15),dp(35))
        }
        scroll.addView(body)

        body.addView(line(button("☰  MENU") { reside() },label("AMPYTUBE  0.1",23f,white,true)))
        body.addView(gap(16))
        body.addView(label("YOUTUBE / LINK DIRETTO",11f,purple,true))
        body.addView(gap(7))
        urlField=EditText(this).apply {
            setSingleLine(true)
            textSize=13f
            setTextColor(white)
            setHintTextColor(grey)
            hint="https://www.youtube.com/watch?v=..."
            setPadding(dp(12),dp(11),dp(12),dp(11))
            background=rounded(panel)
        }
        body.addView(urlField,LinearLayout.LayoutParams(-1,dp(51)))
        body.addView(gap(10))
        body.addView(line(button("▶ CARICA",true) { load() },button("▣ FILE") { openFile() }))
        body.addView(gap(16))

        playerShell=FrameLayout(this).apply {
            background=rounded(Color.rgb(8,22,38),18)
            clipToOutline=true
        }
        body.addView(playerShell,LinearLayout.LayoutParams(-1,dp(224)))
        videoHolder=FrameLayout(this)
        playerShell.addView(videoHolder,FrameLayout.LayoutParams(-1,-1))
        playerView=LayoutInflater.from(this).inflate(R.layout.ampy_player,videoHolder,false) as PlayerView
        videoHolder.addView(playerView,FrameLayout.LayoutParams(-1,-1))
        orb=MorphOrbView(this)
        videoHolder.addView(orb,FrameLayout.LayoutParams(-1,-1))
        mini=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(10),0,dp(8),0)
            background=rounded(Color.rgb(24,51,65))
            visibility=View.GONE
        }
        playerShell.addView(mini,FrameLayout.LayoutParams(-1,dp(60),Gravity.CENTER_VERTICAL))
        miniTitle=label("♫ AMPYTUBE",12f,aqua,true)
        mini.addView(miniTitle,LinearLayout.LayoutParams(0,-1,1f))
        miniPlay=button("▶") { togglePlay() }
        mini.addView(miniPlay)
        mini.addView(button("⤢") { genie(false) })

        body.addView(gap(12))
        body.addView(line(button("✧ GENIE") { genie() },button("⟳ ROTATE") { rotate() },button("⛶ FULL") { fullscreen() }))
        body.addView(gap(7))
        body.addView(line(button("◇ FLIP") { flip() },button("◌ LIQUID") { liquid() },button("↗ ELASTIC") { elastic() }))
        body.addView(gap(7))
        mode=button("▣ VIDEO") { setAudio(!onlyAudio) }
        body.addView(line(mode,button("◉ BACKGROUND",true) { goBackground() }))
        body.addView(gap(18))
        title=label("Nessun video caricato",17f,white,true)
        body.addView(title)
        body.addView(gap(12))
        mainPlay=button("▶ PLAY",true) { togglePlay() }
        body.addView(line(button("−10 s") { skip(-10) },mainPlay,button("+10 s") { skip(10) }))
        seek=SeekBar(this).apply {
            max=1000
            progressTintList=ColorStateList.valueOf(aqua)
            thumbTintList=ColorStateList.valueOf(aqua)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(b:SeekBar?) { seeking=true }
                override fun onProgressChanged(b:SeekBar?,p:Int,user:Boolean) {}
                override fun onStopTrackingTouch(b:SeekBar?) {
                    player?.let { p ->
                        if(p.duration>0) p.seekTo(p.duration*(b?.progress ?: 0)/1000)
                    }
                    seeking=false
                }
            })
        }
        body.addView(seek)
        time=label("00:00 / 00:00",12f,grey).apply { gravity=Gravity.END }
        body.addView(time)
        body.addView(gap(8))
        body.addView(line(button("🔉") { volume(-1) },button("1× VELOCITÀ") { speed() },button("🔊") { volume(1) }))
        body.addView(gap(16))
        status=label("Pronto. MediaSessionService mantiene l'audio a schermo spento.",13f,aqua)
        status.setPadding(dp(12),dp(17),dp(12),dp(17))
        status.background=rounded(panel)
        body.addView(status)
        body.addView(gap(18))
        body.addView(label("INTENSITÀ MORPHING",11f,purple,true))
        body.addView(SeekBar(this).apply {
            max=100;progress=65
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(b:SeekBar?) {}
                override fun onStopTrackingTouch(b:SeekBar?) {}
                override fun onProgressChanged(b:SeekBar?,v:Int,user:Boolean) {
                    motionIntensity=v/100f;orb.intensity=motionIntensity
                }
            })
        })
        body.addView(label("DURATA ANIMAZIONI",11f,purple,true))
        body.addView(SeekBar(this).apply {
            max=100;progress=50
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(b:SeekBar?) {}
                override fun onStopTrackingTouch(b:SeekBar?) {}
                override fun onProgressChanged(b:SeekBar?,v:Int,user:Boolean) {
                    durationScale=.5f+1.1f*v/100f
                }
            })
        })
        body.addView(button("◉ ATTIVA / DISATTIVA ANIMAZIONI") {
            motionEnabled=!motionEnabled
            status.text=if(motionEnabled) "Animazioni attive" else "Animazioni disattivate, audio invariato"
        })
    }

    private fun connect() {
        if(controllerFuture!=null) return
        val token=SessionToken(this,android.content.ComponentName(this,PlaybackService::class.java))
        val future=MediaController.Builder(this,token).buildAsync()
        controllerFuture=future
        future.addListener({
            try {
                val c=future.get()
                player=c
                playerView.player=c
                c.addListener(object:Player.Listener {
                    override fun onIsPlayingChanged(isPlaying:Boolean) { refresh() }
                    override fun onPlayerError(error:PlaybackException) {
                        status.text="Errore stream: "+(error.message ?: error.errorCodeName)
                    }
                })
                if(autoStart) {
                    autoStart=false
                    playCurrent(0,true)
                }
                refresh()
            } catch(e:Exception) {
                controllerFuture=null
                status.text="Servizio media non disponibile: "+e.message
            }
        },java.util.concurrent.Executor { r -> runOnUiThread(r) })
    }

    private fun load() {
        val input=urlField.text.toString().trim()
        val id=YouTubeResolver.videoId(input)
        if(id==null) {
            if(input.startsWith("https://") && Regex("\\.(mp4|webm|mp3|m4a|ogg|m3u8)(\\?|$)",RegexOption.IGNORE_CASE).containsMatchIn(input)) {
                source=null;localUri=Uri.parse(input);localName="Flusso diretto"
                title.text=localName
                playCurrent(0,true)
            } else toast("Usa un URL YouTube oppure un URL HTTPS multimediale diretto")
            return
        }
        getPreferences(MODE_PRIVATE).edit().putString("last_url",input).apply()
        status.text="Analisi flussi YouTube..."
        val seq=++requestCount
        executor.execute {
            val result=runCatching { YouTubeResolver.resolve(id) }
            runOnUiThread {
                if(isFinishing || isDestroyed || seq!=requestCount) return@runOnUiThread
                result.onSuccess {
                    source=it;localUri=null
                    title.text=it.title
                    status.text=it.uploader+" • riproduzione in background disponibile"
                    playCurrent(0,true)
                }.onFailure {
                    status.text="Impossibile analizzare video: "+(it.message ?: "errore sconosciuto")
                }
            }
        }
    }

    private fun playCurrent(position:Long,autoplay:Boolean) {
        val c=player
        if(c==null) { autoStart=true;connect();return }
        val mediaUri:Uri
        val mediaTitle:String
        val artist:String
        val thumbnail:Uri?
        val s=source
        if(s!=null) {
            val selected=(if(onlyAudio) s.audio else s.video) ?: s.audio ?: s.video
            if(selected==null) { toast("Stream non disponibile");return }
            mediaUri=Uri.parse(selected)
            mediaTitle=s.title;artist=s.uploader
            thumbnail=s.thumbnail?.let(Uri::parse)
            orb.visibility=if(!onlyAudio && s.video!=null) View.GONE else View.VISIBLE
        } else {
            mediaUri=localUri ?: return
            mediaTitle=localName;artist="AmpyTube";thumbnail=null
            orb.visibility=View.GONE
        }
        val meta=MediaMetadata.Builder().setTitle(mediaTitle).setArtist(artist)
        thumbnail?.let { meta.setArtworkUri(it) }
        c.setMediaItem(MediaItem.Builder().setUri(mediaUri).setMediaMetadata(meta.build()).build())
        c.prepare()
        if(position>0) c.seekTo(position)
        if(autoplay) c.play()
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),250)
        }
        refresh()
    }

    private fun setAudio(value:Boolean) {
        if(value==onlyAudio) return
        onlyAudio=value
        mode.text=if(onlyAudio) "♫ SOLO AUDIO" else "▣ VIDEO"
        if(source!=null) playCurrent(player?.currentPosition ?: 0,player?.playWhenReady ?: true)
    }
    private fun togglePlay() {
        val c=player ?: return toast("Caricamento player...")
        if(c.mediaItemCount==0) {
            if(source==null && localUri==null) load() else playCurrent(0,true)
        } else if(c.isPlaying) c.pause() else c.play()
    }
    private fun skip(sec:Int) { player?.let { it.seekTo(max(0L,it.currentPosition+sec*1000L)) } }
    private fun volume(dir:Int) { (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC,dir,AudioManager.FLAG_SHOW_UI) }
    private fun speed() {
        val c=player ?:return
        val rates=listOf(1f,1.25f,1.5f,2f,.75f)
        val index=rates.indexOfFirst { kotlin.math.abs(c.playbackParameters.speed-it)<.01f }
        c.setPlaybackSpeed(rates[(index+1)%rates.size])
        toast("Velocità "+c.playbackParameters.speed+"×")
    }
    private fun goBackground() {
        if(player?.mediaItemCount==0) return toast("Avvia prima un video")
        toast("Riproduzione attiva in background. Controlli in notifiche e blocco.")
        moveTaskToBack(true)
    }
    private fun openFile() {
        val pick=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="*/*"
            putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("audio/*","video/*"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(pick,21)
    }
    @Deprecated("Activity result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=21 || resultCode!=RESULT_OK) return
        localUri=data?.data ?: return
        try { contentResolver.takePersistableUriPermission(localUri!!,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:SecurityException) {}
        localName=localUri!!.lastPathSegment?.substringAfterLast('/') ?: "File locale"
        source=null
        title.text=localName
        playCurrent(0,true)
    }
    private fun acceptShare(i:Intent) { i.getStringExtra(Intent.EXTRA_TEXT)?.let { urlField.setText(it) } }
    override fun onNewIntent(i:Intent) { super.onNewIntent(i);setIntent(i);if(i.action==Intent.ACTION_SEND)acceptShare(i) }

    private fun reside(show:Boolean = !drawerOpen) {
        drawerOpen=show
        content.animate().cancel()
        content.cameraDistance=resources.displayMetrics.density*9000f
        val animation=content.animate()
            .translationX(if(show)dp(246).toFloat() else 0f)
            .scaleX(if(show).82f else 1f)
            .scaleY(if(show).86f else 1f)
            .rotationY(if(show)-12f else 0f)
        if(motionEnabled) animation.setDuration((450*durationScale).toLong())
            .setInterpolator(DecelerateInterpolator(1.5f)).start()
        else animation.setDuration(0).start()
    }
    private fun rotate() {
        if(!motionEnabled) return
        videoHolder.cameraDistance=resources.displayMetrics.density*13000f
        videoHolder.animate().rotationYBy(360f).rotationBy(360f)
            .setDuration((970*durationScale).toLong())
            .setInterpolator(DecelerateInterpolator()).start()
    }
    private fun flip() {
        if(!motionEnabled)return
        val start=videoHolder.rotationX
        ObjectAnimator.ofFloat(videoHolder,View.ROTATION_X,start,start+180,start+360).apply {
            duration=(890*durationScale).toLong()
            interpolator=DecelerateInterpolator(1.5f)
        }.start()
    }
    private fun liquid() {
        if(!motionEnabled)return
        ValueAnimator.ofFloat(0f,1f).apply {
            duration=(1050*durationScale).toLong()
            addUpdateListener { a ->
                val t=a.animatedValue as Float
                val wave=sin(t*Math.PI*6).toFloat()*(1-t)*motionIntensity
                videoHolder.scaleX=1+wave*.11f
                videoHolder.scaleY=1-wave*.08f
                videoHolder.translationX=wave*dp(10)
            }
            addListener(object:AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation:Animator) {
                    videoHolder.scaleX=1f;videoHolder.scaleY=1f;videoHolder.translationX=0f
                }
            })
        }.start()
    }
    private fun elastic() {
        if(!motionEnabled)return
        videoHolder.scaleX=1f-.18f*motionIntensity
        videoHolder.scaleY=1f+.1f*motionIntensity
        videoHolder.animate().scaleX(1f).scaleY(1f)
            .setDuration((820*durationScale).toLong())
            .setInterpolator(OvershootInterpolator(2.3f)).start()
    }
    private fun genie(toMini:Boolean = !minimized) {
        if(toMini==minimized) return
        if(!motionEnabled) {
            minimized=toMini
            videoHolder.visibility=if(minimized)View.GONE else View.VISIBLE
            mini.visibility=if(minimized)View.VISIBLE else View.GONE
            playerShell.layoutParams=playerShell.layoutParams.apply { height=dp(if(minimized)77 else 224) }
            return
        }
        if(!toMini) {
            playerShell.layoutParams=playerShell.layoutParams.apply { height=dp(224) }
            playerShell.post { genieTransform(false) }
        } else genieTransform(true)
    }
    private fun genieTransform(toMini:Boolean) {
        if(playerShell.width<=0 || playerShell.height<=0)return
        val screenshot=runCatching {
            Bitmap.createBitmap(playerShell.width,playerShell.height,Bitmap.Config.ARGB_8888).also { videoHolder.draw(Canvas(it)) }
        }.getOrNull()
        val mesh=GenieMorphView(this,screenshot)
        playerShell.addView(mesh,FrameLayout.LayoutParams(-1,-1))
        videoHolder.visibility=View.INVISIBLE;mini.visibility=View.INVISIBLE
        ValueAnimator.ofFloat(0f,1f).apply {
            duration=(610*durationScale).toLong()
            interpolator=DecelerateInterpolator()
            addUpdateListener {
                val t=it.animatedValue as Float
                mesh.progress=if(toMini)t else 1-t
                mesh.invalidate()
            }
            addListener(object:AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation:Animator) {
                    playerShell.removeView(mesh);mesh.recycle()
                    minimized=toMini
                    videoHolder.visibility=if(minimized)View.GONE else View.VISIBLE
                    mini.visibility=if(minimized)View.VISIBLE else View.GONE
                    miniTitle.text="♫ "+(source?.title ?: localName).take(22)
                    playerShell.layoutParams=playerShell.layoutParams.apply { height=dp(if(minimized)77 else 224) }
                }
            })
        }.start()
    }
    private fun fullscreen() {
        val c=player ?: return toast("Nessun player disponibile")
        if(fullScreen!=null)return
        playerView.player=null
        val full=PlayerView(this).apply { player=c;useController=true;setBackgroundColor(Color.BLACK) }
        fullScreen=Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(full)
            setOnDismissListener { full.player=null;playerView.player=controllerFuture?.let { player };fullScreen=null }
            show()
        }
    }
    private fun refresh() {
        if(!::seek.isInitialized)return
        val c=player
        val length=c?.duration?.takeIf { it>0 } ?: 0L
        val pos=c?.currentPosition?.coerceAtLeast(0) ?: 0L
        if(!seeking)seek.progress=if(length>0)((pos*1000L/length).toInt().coerceIn(0,1000)) else 0
        time.text=clock(pos)+" / "+clock(length)
        mainPlay.text=if(c?.isPlaying==true)"❚❚ PAUSA" else "▶ PLAY"
        miniPlay.text=if(c?.isPlaying==true)"❚❚" else "▶"
    }
    private fun clock(ms:Long):String {
        val s=max(0L,ms)/1000
        return if(s>=3600) String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,(s/60)%60,s%60)
        else String.format(Locale.ROOT,"%02d:%02d",s/60,s%60)
    }
    private fun toast(message:String) { Toast.makeText(this,message,Toast.LENGTH_LONG).show() }
    override fun onStart() {
        super.onStart()
        ui.post(refreshTask)
        if(player==null)connect() else playerView.player=player
    }
    override fun onStop() {
        ui.removeCallbacks(refreshTask)
        if(fullScreen==null)playerView.player=null
        super.onStop()
    }
    override fun onDestroy() {
        ui.removeCallbacks(refreshTask)
        fullScreen?.dismiss()
        if(::playerView.isInitialized)playerView.player=null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture=null;player=null
        executor.shutdownNow()
        super.onDestroy()
    }
    @Deprecated("Navigation")
    override fun onBackPressed() {
        when {
            drawerOpen->reside(false)
            minimized->genie(false)
            else->super.onBackPressed()
        }
    }
}
