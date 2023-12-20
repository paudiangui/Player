package com.paudiangui.player

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import com.paudiangui.player.databinding.ActivityMainBinding
import com.paudiangui.service.DownloadFileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var currentViewContent: View? = null
    private var oldViewContent: View? = null
    private var currentRelativeLayout: RelativeLayout? = null
    private var oldRelativeLayout: RelativeLayout? = null
    private var currentCardView: CardView? = null
    private lateinit var views: List<View>

    private var mSecPlayed = 0
    private var mIntents = 0
    private var positionMedia = 0

    private var mSecondsSent = 0

    private val urlsContent = listOf(
        "https://www.w3schools.com/w3css/img_lights.jpg",
        "https://plus.unsplash.com/premium_photo-1669324357471-e33e71e3f3d8?q=80&w=1470&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
        "https://images.unsplash.com/photo-1617854818583-09e7f077a156?q=80&w=1470&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initVariables()
        initUI()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event!!.action == KeyEvent.ACTION_DOWN) {
            Log.w(TAG, "onKeyDown: action: ${event.action} keyCode:$keyCode")
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> playNextItem()
                KeyEvent.KEYCODE_DPAD_RIGHT -> playPreviousItem()
                else -> Log.w(TAG, "Se ha pulsado keyCode: $keyCode")
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun initVariables() {
        views = listOf(binding.viewImage1, binding.viewImage2)
    }

    private fun initUI() {
        startPlayingQueue()
    }

    private fun startPlayingQueue() {
        showProgressbar()
        downloadContent()
    }

    private fun downloadContent() {
        lifecycleScope.launch(Dispatchers.IO) {
            val localUrlsContent = mutableListOf<Uri>()
            val deferredAllLocalUris = urlsContent.map { url ->
                async {
                    isLocalUriExist(url, this@MainActivity) ?: downloadMedia(
                        this@MainActivity, url
                    )
                }
            }

            val resultLocalUris = deferredAllLocalUris.awaitAll()
            localUrlsContent.addAll(resultLocalUris.filterNotNull())

            withContext(Dispatchers.Main) {
                //en el codigo original puede ser un video o una imagen por eso paso una lista
                val listOfUri = listOf<Uri>(localUrlsContent[positionMedia])
                if (localUrlsContent.isNotEmpty()) {
                    loadAndDisplayContent(
                        listOfUri
                    )
                } else {
                    Log.e(TAG, "Error downloading media: $urlsContent")
                    // TODO: Show an error message to the user
                }
            }
        }

    }

    private fun loadAndDisplayContent(localUris: List<Uri>) {
        getCurrentViews()
        mSecondsSent = 0
        mSecPlayed = 0
        launchImage(localUris)
    }

    private fun launchImage(localUris: List<Uri>) {
        Log.i(TAG, "Image Content: ${localUris.first()}")
        launchImageView(localUris)
    }

    private fun launchVideoThumbnail(localUris: List<Uri>) {
        Log.i(TAG, "Video Thumbnail: ${localUris.first()}")
        launchImageView(localUris, true)
        //launchVideo(localUris)
    }

    private fun launchImageView(localUris: List<Uri>, isThumbnail: Boolean = false) {
        lifecycleScope.launch {
            try {
                val currentUri = checkUriImageView(localUris)
                currentCardView?.apply {
                    setCardBackgroundColor(Color.TRANSPARENT)
                    cardElevation = 0F
                }

                val file = File(currentUri.path.toString())
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(file.absolutePath)
                }

                initImageViewListener(isThumbnail) {
                    //lauch video method
                }

                //On my code I use this method to scale the image to not 16:9
//                val resultDeferredBitMap = withContext(Dispatchers.Default) {
//                    playerScaleBitmap(bitmap, is16by9, this@PlayerActivity)
//                }
//                (currentViewContent as ImageView).setImageBitmap(resultDeferredBitMap)


                (currentViewContent as ImageView).setImageBitmap(bitmap)


            } catch (e: Exception) {
                Log.e(TAG, "Error loading image: ${e.message}")
            }
        }
    }

    private fun checkUriImageView(localUris: List<Uri>): Uri {
        val uriWithImageExtension = localUris.firstOrNull { uri ->
            val lowerCaseUri = uri.toString().lowercase()
            lowerCaseUri.contains(".jpg") || lowerCaseUri.contains(".jpeg") || lowerCaseUri.contains(
                ".png"
            )
        }
        return uriWithImageExtension ?: localUris.last()
    }

    private fun initImageViewListener(isVideoThumbnail: Boolean, onLaunchVideo: () -> Unit) {
        val imageView = (currentViewContent as ImageView)
        Log.d(TAG, "imageView: $imageView")
        Log.d(TAG, "imageView.viewTreeObserver: ${imageView.viewTreeObserver.isAlive}")
        imageView.viewTreeObserver.removeOnPreDrawListener(null)
        Log.d(TAG, "imageView.viewTreeObserver: ${imageView.viewTreeObserver.isAlive}")

        imageView.doOnPreDraw {
            Log.d(TAG, "onPreDraw")
            currentCardView?.elevation = cardElevation
            this@MainActivity.crossFade(TAG,
                currentRelativeLayout!!,
                oldRelativeLayout,
                onCrossFadeFinish = { crossFadeFinish(isVideoThumbnail, onLaunchVideo) })

            //Call an endpoint from my API
            //startPlaying()
        }
    }

    private fun crossFadeFinish(isVideoThumbnail: Boolean, onLaunchVideo: () -> Unit) {
        if (isVideoThumbnail) {
            onLaunchVideo.invoke()
        } else {
            binding.progressBar.visibility = View.INVISIBLE
        }
    }

    private fun getCurrentViews() {
        oldViewContent = getOldView(views, currentViewContent)
        oldRelativeLayout = oldViewContent?.parent?.parent as? RelativeLayout
        currentViewContent = checkCurrentImageView()
        currentCardView = currentViewContent!!.parent as? CardView
        currentRelativeLayout = currentViewContent!!.parent.parent as? RelativeLayout
    }

    private fun checkCurrentImageView(): View {
        return binding.takeIf { it.viewImage1 != currentViewContent }?.viewImage1
            ?: binding.viewImage2
    }

    private fun getOldView(list: List<View>, currentView: View?): View? {
        if (list.contains(currentView)) {
            return currentView
        }
        return null
    }

    //Function of han extension class
    fun Context.crossFade(
        TAG: String, viewToFadeIn: View, viewToFadeOut: View?, onCrossFadeFinish: () -> Unit
    ) {
        Log.d(TAG, "CrossFade START")
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                Log.d(TAG, "FadeIn START")
                viewToFadeIn.visibility = View.VISIBLE

            }

            override fun onAnimationEnd(animation: Animation?) {
                Log.d(TAG, "FadeIn END")
                onCrossFadeFinish.invoke()
            }

            override fun onAnimationRepeat(animation: Animation?) {}

        })

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                Log.d(TAG, "FadeOut START")
                viewToFadeOut?.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animation?) {
                Log.d(TAG, "FadeOut END")
                viewToFadeOut?.visibility = View.INVISIBLE
            }

            override fun onAnimationRepeat(animation: Animation?) {}

        })
        viewToFadeIn.startAnimation(fadeIn)
        viewToFadeOut?.startAnimation(fadeOut)
    }

    private suspend fun downloadMedia(context: Context, url: String): Uri? {
        val result = DownloadFileService().downloadFile(context, url)
        return if (result != null) {
            Log.i(TAG, "Url downloaded: $result")
            Uri.parse(result)
        } else {
            if (mIntents <= 5) {
                mIntents++
                downloadMedia(context, url)
            } else null
        }
    }

    suspend fun isLocalUriExist(mediaUrl: String, context: Context): Uri? =
        withContext(Dispatchers.IO) {
            return@withContext if (isVideoAvailable(mediaUrl, context)) {
                getLocalUri(mediaUrl, context)
            } else null
        }

    private fun getLocalUri(externalUrl: String?, context: Context): Uri? {
        return if (externalUrl == null) {
            null
        } else Uri.fromFile(
            File(
                context.filesDir.toString() + "/" + DownloadFileService.MEDIA_FOLDER + "/" + externalUrl.substring(
                    externalUrl.lastIndexOf("/") + 1
                )
            )
        )
    }

    private fun isVideoAvailable(uri: String?, context: Context): Boolean {
        if (uri == null) {
            return false
        }
        val localUri =
            context.filesDir.toString() + "/" + DownloadFileService.MEDIA_FOLDER + "/" + uri.substring(
                uri.lastIndexOf("/") + 1
            )
        val outputFile = File(localUri)
        val ret = outputFile.exists()
        if (ret) {
            Log.w(
                TAG,
                "isVideoAvailable: " + ret + " len:" + outputFile.length() + " path:" + localUri
            )
        } else {
            Log.w(
                TAG, "isVideoAvailable: NO EXISTE :$uri"
            )
        }
        return ret
    }

    private fun showProgressbar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    fun playPreviousItem() {
        if (positionMedia == urlsContent.size - 1) {
            positionMedia = 0
        } else {
            positionMedia++
        }
        showProgressbar()
        downloadContent()
    }

    fun playNextItem() {
        if (positionMedia == 0) {
            positionMedia = urlsContent.size - 1
        } else {
            positionMedia--
        }
        showProgressbar()
        downloadContent()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val cardElevation = 16F
    }


}