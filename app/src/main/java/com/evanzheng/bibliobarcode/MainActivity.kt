package com.evanzheng.bibliobarcode

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room.databaseBuilder
import com.evanzheng.bibliobarcode.BarcodeHelper.checkCode
import com.evanzheng.bibliobarcode.BarcodeHelper.imageToBitmap
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.common.util.concurrent.ListenableFuture
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

//Created by Evan Zheng
class MainActivity : AppCompatActivity(), CameraXConfig.Provider {
    private var sharedPref: SharedPreferences? = null

    //Initializing views
    private var viewfinder: PreviewView? = null
    private var loading: ProgressBar? = null
    private var takePhoto: FloatingActionButton? = null
    private var bibliographyButton: ImageButton? = null
    private var manualButton: ImageButton? = null
    private var blankButton: ImageButton? = null

    //Initializing our executor
    private val takePictureExecutor = Executor { obj: Runnable -> obj.run() }

    //Initializing camera and barcode objects
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var imageCapture: ImageCapture? = null
    private var detector: BarcodeDetector? = null
    private lateinit var context: Context

    //Initializing our image callback methods
    private val captureProcess: OnImageCapturedCallback = object : OnImageCapturedCallback() {
        override fun onCaptureSuccess(imageproxy: ImageProxy) {
            //PPL
//            @OptIn(markerClass = ExperimentalGetImage::class) @SuppressLint("UnsafeExperimentalUsageError")
            val image = imageproxy.image!!
            val bitmap = imageToBitmap(image)
            processImage(bitmap)
            super.onCaptureSuccess(imageproxy)
            image?.close()
        }

        override fun onError(exception: ImageCaptureException) {
            super.onError(exception)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Checking permissions, credit to Superpowered Effects Library for permission algorithm
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET
        )
        for (s in permissions) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                // Some permissions are not granted, ask the user.
                ActivityCompat.requestPermissions(this, permissions, 0)
                return
            }
        }
        initialize()
    }

    // Credit to Superpowered Effects Library
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) //PPL

        // Called when the user answers to the permission dialogs.
        if (requestCode != 0 || grantResults.size < 1 || grantResults.size != permissions.size) return
        var hasAllPermissions = true
        for (grantResult in grantResults) if (grantResult != PackageManager.PERMISSION_GRANTED) {
            hasAllPermissions = false
            Toast.makeText(
                applicationContext,
                "Please allow all permissions for the app.",
                Toast.LENGTH_LONG
            ).show()
        }
        if (hasAllPermissions) initialize()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initialize() {
        //Set up context
        context = applicationContext

        // Set up detector
        detector = BarcodeDetector.Builder(context)
            .setBarcodeFormats(0)
            .build()

        // Set up views
        viewfinder = findViewById(R.id.preview_view)
        takePhoto = findViewById(R.id.take_photo)
        loading = findViewById(R.id.loading)
        manualButton = findViewById(R.id.manualButton)
        bibliographyButton = findViewById(R.id.bibliographyButton)
        blankButton = findViewById(R.id.blankButton)

        // Set up database
        database = databaseBuilder(context, BookDatabase::class.java, "books")
            .allowMainThreadQueries()
            .build()

        // Set up camera provider, and bind our preview and take functions to it
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture!!.addListener({
            try {
                val cameraProvider = cameraProviderFuture!!.get()
                bindPreview(cameraProvider)
                bindTake(cameraProvider)
            } catch (e: ExecutionException) {
                // This should never be reached
                Log.wtf("Camera", "Camera error")
            } catch (e: InterruptedException) {
                Log.wtf("Camera", "Camera error")
            }
        }, ContextCompat.getMainExecutor(this))

        // Set up click listener
        takePhoto?.setOnClickListener(View.OnClickListener { v: View? -> Take() })
        viewfinder?.setOnTouchListener(object : OnSwipeTouchListener(this@MainActivity) {
            public override fun onSwipeTop() {
                goToBibliography()
            }

            public override fun onSwipeBottom() {
                goToISBNEntry()
            }
        })

        //Don't show that it's loading if it's done loading
        loading?.setVisibility(View.INVISIBLE)

        //Check if a tutorial needs to be run
        sharedPref = getPreferences(MODE_PRIVATE)
        val ranBefore = sharedPref?.getBoolean("camera", false)
        if (!ranBefore!!) {
            runTutorial()
        }
    }

    //Runs tutorial
    private fun runTutorial() {
        val tutorialConfig = ShowcaseConfig()
        tutorialConfig.delay = 500
        val tutorial = MaterialShowcaseSequence(this, "smthdif")
        tutorial.setConfig(tutorialConfig)
        tutorial.addSequenceItem(takePhoto, "Scan your barcodes by tapping this button", "OKAY")
        tutorial.addSequenceItem(
            blankButton,
            "You can add a book manually by tapping this button",
            "OKAY"
        )
        tutorial.addSequenceItem(
            manualButton,
            "You can add ISBNs manually by tapping this button, or swiping down",
            "OKAY"
        )
        tutorial.addSequenceItem(
            bibliographyButton,
            "You can view your bibliographies by tapping this button, or swiping up",
            "OKAY"
        )
        tutorial.start()

        //Tutorial won't be run again
        sharedPref!!.edit().putBoolean("camera", true).apply()
    }

    //links to ISBN button
    fun listenISBNPrompt(v: View?) {
        goToISBNEntry()
    }

    //links to bibliography button
    fun listenBibliographyPrompt(v: View?) {
        goToBibliography()
    }

    //Goes to ISBN entry dialog
    private fun goToISBNEntry() {
        val isbnEntry = Dialog(this@MainActivity, R.style.Theme_MaterialComponents_Light_Dialog)
        isbnEntry.setContentView(R.layout.isbn_dialog)
        isbnEntry.setTitle("Enter ISBN:")
        val entry = isbnEntry.findViewById<EditText>(R.id.enterISBN)
        val submitButton = isbnEntry.findViewById<Button>(R.id.submitISBN)
        submitButton.setOnClickListener { v: View? ->
            val code = entry.text.toString()
            //If the code was valid, send it to edit activity
            if (checkCode(code, context)) {
                goToEditActivity(code)
            }
        }
        val cancelButton = isbnEntry.findViewById<Button>(R.id.cancelISBN)
        cancelButton.setOnClickListener { v: View? -> isbnEntry.dismiss() }
        isbnEntry.show()
    }

    //Goes to edit activity
    private fun goToEditActivity(code: String) {
        val intent = Intent(this, BookActivity::class.java)
        intent.putExtra("barcode", code)
        startActivity(intent)
    }

    //Goes to bibliography
    private fun goToBibliography() {
        val intent = Intent(this, BibliographyActivity::class.java)
        startActivity(intent)
    }

    // Binding a imageCapture function to our camera
    private fun bindTake(cameraProvider: ProcessCameraProvider) {


        //Getting rotation was causing a crash on some devices, so a default rotation was created
        imageCapture = try {
            ImageCapture.Builder().setTargetRotation(viewfinder!!.display.rotation).build()
        } catch (e: NullPointerException) {
            ImageCapture.Builder().setTargetRotation(Surface.ROTATION_0).build()
        }
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
    }

    //When a photo is taken:
    private fun Take() {
        imageCapture!!.takePicture(takePictureExecutor, captureProcess)
        loading!!.visibility = View.VISIBLE
    }

    //Binding a preview function to our camera
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()

        //Linking preview to our previewview
        preview.setSurfaceProvider(viewfinder!!.surfaceProvider) //getPreviewSurfaceProvider() PPL
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        cameraProvider.bindToLifecycle(this, cameraSelector, preview)
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }

    //When an image is captured and converted:
    private fun processImage(image: Bitmap) {

        //convert bitmap to frame
        val frame = Frame.Builder().setBitmap(image).build()

        //detect barcodes from frame
        val barcodes = detector!!.detect(frame)
        val size = barcodes.size()

        //Check if barcodes exist
        if (size == 0) {
            Toast.makeText(context, "No barcode detected.", Toast.LENGTH_LONG).show()
        } else { // Iterate through barcodes
            for (i in 0 until size) {
                val targetCode = barcodes.valueAt(0)
                val code = targetCode.rawValue
                if (checkCode(code, context)) {
                    goToEditActivity(code)
                }
            }
        }
        loading!!.visibility = View.INVISIBLE
    }

    //Adds a blank book
    fun addBlankBook(v: View?) {

        //A permanent ID for the book
        val newID = sharedPref!!.getInt("manual", 0)

        //ID can never be used again
        sharedPref!!.edit().putInt("manual", newID + 1).apply()
        val intent = Intent(this, BookActivity::class.java)
        val newIDString = newID.toString()
        intent.putExtra("empty", newIDString)
        startActivity(intent)
    }

    companion object {
        //Initializing book database
        var database: BookDatabase? = null
    }
}