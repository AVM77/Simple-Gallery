package com.simplemobiletools.gallery.fragments

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.gallery.R
import com.simplemobiletools.gallery.activities.ViewPagerActivity
import com.simplemobiletools.gallery.extensions.config
import com.simplemobiletools.gallery.extensions.getRealPathFromURI
import com.simplemobiletools.gallery.helpers.GlideRotateTransformation
import com.simplemobiletools.gallery.helpers.MEDIUM
import com.simplemobiletools.gallery.models.Medium
import it.sephiroth.android.library.exif2.ExifInterface
import kotlinx.android.synthetic.main.pager_photo_item.view.*
import uk.co.senab.photoview.PhotoViewAttacher
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PhotoFragment : ViewPagerFragment() {
    lateinit var medium: Medium
    lateinit var view: ViewGroup
    private var isFragmentVisible = false
    private var wasInit = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        view = inflater.inflate(R.layout.pager_photo_item, container, false) as ViewGroup

        medium = arguments.getSerializable(MEDIUM) as Medium

        if (medium.path.startsWith("content://")) {
            val originalPath = medium.path
            medium.path = context.getRealPathFromURI(Uri.parse(medium.path)) ?: ""

            if (medium.path.isEmpty()) {
                var out: FileOutputStream? = null
                try {
                    var inputStream = context.contentResolver.openInputStream(Uri.parse(originalPath))
                    val exif = ExifInterface()
                    exif.readExif(inputStream, ExifInterface.Options.OPTION_ALL)
                    val tag = exif.getTag(ExifInterface.TAG_ORIENTATION)
                    val orientation = tag?.getValueAsInt(-1) ?: -1

                    inputStream = context.contentResolver.openInputStream(Uri.parse(originalPath))
                    val original = BitmapFactory.decodeStream(inputStream)
                    val rotated = rotateViaMatrix(original, orientation)
                    exif.setTagValue(ExifInterface.TAG_ORIENTATION, 1)
                    exif.removeCompressedThumbnail()

                    val file = File(context.externalCacheDir, Uri.parse(originalPath).lastPathSegment)
                    out = FileOutputStream(file)
                    rotated.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    medium.path = file.absolutePath
                } catch (e: Exception) {
                    context.toast(R.string.unknown_error_occurred)
                    return view
                } finally {
                    try {
                        out?.close()
                    } catch (e: IOException) {
                    }
                }
            }
        }

        view.subsampling_view.apply { setOnClickListener({ photoClicked() }) }
        view.photo_view.apply {
            maximumScale = 8f
            mediumScale = 3f
            setOnPhotoTapListener(object : PhotoViewAttacher.OnPhotoTapListener {
                override fun onPhotoTap(view: View?, x: Float, y: Float) {
                    photoClicked()
                }

                override fun onOutsidePhotoTap() {
                    photoClicked()
                }
            })
        }
        loadImage()

        activity.window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            listener?.systemUiVisibilityChanged(visibility)
        }
        wasInit = true

        return view
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        isFragmentVisible = menuVisible
        if (wasInit) {
            if (menuVisible) {
                addZoomableView()
            } else {
                view.subsampling_view.recycle()
                view.subsampling_view.beGone()
                view.subsampling_view.background = ColorDrawable(Color.TRANSPARENT)
            }
        }
    }

    private fun degreesForRotation(orientation: Int): Int {
        return when (orientation) {
            8 -> 270
            3 -> 180
            6 -> 90
            else -> 0
        }
    }

    private fun rotateViaMatrix(original: Bitmap, orientation: Int): Bitmap {
        val degrees = degreesForRotation(orientation).toFloat()
        return if (degrees == 0f) {
            original
        } else {
            val matrix = Matrix()
            matrix.setRotate(degrees)
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        }
    }

    private fun loadImage() {
        if (medium.isGif()) {
            Glide.with(this)
                    .load(medium.path)
                    .asGif()
                    .crossFade()
                    .priority(if (isFragmentVisible) Priority.IMMEDIATE else Priority.LOW)
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .into(view.photo_view)
        } else {
            loadBitmap()
        }
    }

    private fun loadBitmap(degrees: Float = 0f) {
        if (degrees == 0f) {
            val targetWidth = if (ViewPagerActivity.screenWidth == 0) Target.SIZE_ORIGINAL else ViewPagerActivity.screenWidth
            val targetHeight = if (ViewPagerActivity.screenHeight == 0) Target.SIZE_ORIGINAL else ViewPagerActivity.screenHeight
            Glide.with(this)
                    .load(medium.path)
                    .asBitmap()
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .override(targetWidth, targetHeight)
                    .listener(object : RequestListener<String, Bitmap> {
                        override fun onException(e: java.lang.Exception?, model: String?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                            return false
                        }

                        override fun onResourceReady(resource: Bitmap?, model: String?, target: Target<Bitmap>?, isFromMemoryCache: Boolean, isFirstResource: Boolean): Boolean {
                            if (isMenuVisible)
                                addZoomableView()
                            return false
                        }
                    }).into(view.photo_view)
        } else {
            Glide.with(this)
                    .load(medium.path)
                    .asBitmap()
                    .transform(GlideRotateTransformation(context, degrees))
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .thumbnail(0.2f)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(view.photo_view)
        }
    }

    private fun addZoomableView() {
        if ((medium.isImage()) && isMenuVisible && view.subsampling_view.visibility == View.GONE) {
            view.subsampling_view.apply {
                maxScale = 10f
                beVisible()
                setDoubleTapZoomScale(2f)
                setImage(ImageSource.uri(medium.path))
                orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
                setOnImageEventListener(object : SubsamplingScaleImageView.OnImageEventListener {
                    override fun onImageLoaded() {
                    }

                    override fun onReady() {
                        background = ColorDrawable(context.config.backgroundColor)
                    }

                    override fun onTileLoadError(p0: java.lang.Exception?) {
                    }

                    override fun onPreviewReleased() {
                    }

                    override fun onImageLoadError(p0: java.lang.Exception?) {
                    }

                    override fun onPreviewLoadError(p0: java.lang.Exception?) {
                    }
                })
            }
        }
    }

    fun rotateImageViewBy(degrees: Float) {
        view.subsampling_view.visibility = View.GONE
        loadBitmap(degrees)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Glide.clear(view.photo_view)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        loadImage()
    }

    private fun photoClicked() {
        listener?.fragmentClicked()
    }
}
