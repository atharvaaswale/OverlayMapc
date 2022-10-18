package com.ornet.overlaymap

import android.Manifest
import android.app.Dialog
import android.app.DownloadManager
import android.app.ProgressDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.maps.android.SphericalUtil
import com.google.maps.android.data.geojson.GeoJsonLayer
import com.google.maps.android.data.geojson.GeoJsonPolygonStyle
import com.ornet.overlaymap.databinding.ActivityMainBinding
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.nio.charset.Charset
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() ,OnMapReadyCallback{

    private lateinit var mTiles: TileOverlay
    private var latlngList = ArrayList<LatLng>()
    private var markerList = ArrayList<Marker>()
    private var polygon: Polygon? = null
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMainBinding
    private var locationPermissionGranted = false
    private var PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    lateinit var client: FusedLocationProviderClient
    lateinit var currentLocation: Location
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val REQUEST_CODE = 101
    private val TAG = "log"
    val progress_bar_type = 0

    lateinit var pDialog:ProgressDialog
    val latitudeList = ArrayList<Double>()
    val longitudeList = ArrayList<Double>()
    var latlonlist: String = ""
    var isoutside = false


    // File url to download
    private val file_url = "http://192.168.1.5/treecensusapi/awdhan.geojson"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,R.layout.activity_main)
        supportActionBar!!.hide()

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        //todo download PDF file from server
        val executorService = Executors.newSingleThreadExecutor()
        executorService.execute {

            var FileURL = "http://192.168.1.5/treecensusapi/awdhan.geojson"
            var newstr = FileURL.substring(0, FileURL.length - 8)
            var segments = newstr.split("treecensusapi/")
            var document = segments[segments.size - 1]
            var passname="/storage/emulated/0/geojsonfiles/"+document+".geojson"

            try {
                var url = URL("https://gist.github.com/atharvaaswale/253279e30ee5ca29829b00872262cec7")
                var urlConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
                urlConnection.connect()
                var inputStream: InputStream = urlConnection.inputStream
                var fileOutputStream = FileOutputStream(File(passname))
                var totalSize: Int = urlConnection.contentLength
                var buffer = ByteArray(5048576)
                var bufferLength = 0
                while (inputStream.read(buffer).also { bufferLength = it } > 0) {
                    fileOutputStream.write(buffer, 0, bufferLength)
                }
                fileOutputStream.close()
                Log.d(TAG, "DOWNLOAD: Download DONE")

            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        getLocationPermission()
    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
    fetchLocation()

    val mapFragment = supportFragmentManager
        .findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)

    client = LocationServices.getFusedLocationProviderClient(applicationContext)
}



    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
            loadGeoJson()
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.uiSettings.setAllGesturesEnabled(true)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isScrollGesturesEnabled = true
        mMap.uiSettings.isZoomGesturesEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled
//    val location = LatLng(20.8478077896699, 74.76513818425006)
//        val location = LatLng(19.079681602708, 73.120355755091)
        val location = LatLng(19.0816627913009, 73.08868842042915)
        val tileProvider: TileProvider = object : UrlTileProvider(256, 256) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                val urlstring = "http://103.14.99.175/OVERLAY/Z$zoom/$y/$x.png"
                var url: URL? = null
                url = try {
                    URL(urlstring)
                } catch (e: MalformedURLException) {
                    throw AssertionError(e)
                }
                return url
            }
        }
        mTiles = mMap.addTileOverlay(TileOverlayOptions().tileProvider(tileProvider))!!
        mTiles.zIndex = -1f

        mMap.setOnMapClickListener {

            val mOption: MarkerOptions = MarkerOptions().position(it)
            val marker: Marker? = mMap.addMarker(mOption)
            marker!!.zIndex = 5f
            latlngList.add(it)
            markerList.add(marker!!)

            latitudeList.add(it.latitude)
            longitudeList.add(it.longitude)

        }



//    val kmllayer = KmlLayer(mMap, R.raw.awdhangj, this)
//    kmllayer.addLayerToMap()
//    loadGeoJson()


        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 17.5F))

        binding.undo.setOnClickListener {
            if (latlngList.isNotEmpty()){
                latlngList.removeAt(latlngList.size-1)
                markerList.removeAt(markerList.size-1)

                mMap.clear()
                mTiles = mMap.addTileOverlay(TileOverlayOptions().tileProvider(tileProvider))!!
                mTiles.zIndex = -1f
//                kmllayer.addLayerToMap()
                loadGeoJson()
                for(i in 0 until markerList.size){
                    mMap.addMarker(MarkerOptions().position(latlngList[i]))
                }
            }
        }

        binding.btnDraw.setOnClickListener {
            if (latlngList.isNotEmpty() || markerList.isNotEmpty()) {
                polygon?.remove()
                val polygonOptions: PolygonOptions = PolygonOptions().addAll(latlngList)
                    .clickable(true)
                polygon = mMap.addPolygon(polygonOptions)
                polygon!!.fillColor = Color.parseColor("#61A3A0FF")
                polygon!!.strokeColor = Color.parseColor("#9717138F")
                polygon!!.strokeWidth = 2.5f
                val area = String.format("%.2f", SphericalUtil.computeArea(latlngList))
                binding.tvArea.text = "Area: $area sqm"

                Log.d(TAG, "Latlongs: $latlngList")
                for (i in 0 until latlngList.size){
                    latlonlist += "[${latitudeList[i]}, " + "${longitudeList[i]}],"
                }
//                latlonlist = latlonlist.dropLast(1)
                Log.d(TAG, "LatLONSTRING: ${latlonlist.dropLast(1)}")
            }
        }

        binding.btnClear.setOnClickListener {
            polygon?.remove()
            for (mList in markerList) mList.remove()
            latlngList.clear()
            markerList.clear()
            binding.tvArea.text = ""
            mMap.clear()
            mTiles = mMap.addTileOverlay(TileOverlayOptions().tileProvider(tileProvider))!!
            mTiles.zIndex = -1f
//            kmllayer.addLayerToMap()
            loadGeoJson()
        }

}

    private fun getLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }

    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE
            )
            return
        }
        val task: Task<Location> = fusedLocationProviderClient.lastLocation
        task.addOnSuccessListener { location ->
            if (location != null) {
                currentLocation = location
                val supportMapFragment =
                    (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?)!!
                supportMapFragment.getMapAsync(this)
            }
        }
    }

    fun loadGeoJson(){
//        val json = readJsonFromUrl("http://192.168.1.5/treecensusapi/awdhan.geojson")
//        val json = readJsonFromUrl("http://192.168.1.5/treecensusapi/tree_data.geojson")
//        val geojsonlayer = GeoJsonLayer(mMap,json)
        val geojsonlayer = GeoJsonLayer(mMap,R.raw.taloja, this)

        //-------polygon----------//

        val polyStyle: GeoJsonPolygonStyle = geojsonlayer.defaultPolygonStyle
        polyStyle.fillColor = Color.parseColor("#3CF6DA24")
        polyStyle.strokeColor = Color.parseColor("#FFF6BA24")
        polyStyle.strokeWidth = 2.5f

        //--------point----------//

        val pointerStyle = geojsonlayer.defaultPointStyle
        pointerStyle.icon = bitmapDescriptorFromVector(this, R.drawable.ic_dot)
        pointerStyle.toMarkerOptions()

        geojsonlayer.addLayerToMap()
    }


    @Throws(IOException::class)
    private fun readAll(rd: Reader): String {
        val sb = StringBuilder()
        var cp: Int
        while (rd.read().also { cp = it } != -1) {
            sb.append(cp.toChar())
            Log.d("ATHARVA", "readAll: $sb")
        }
        return sb.toString()
    }

    @Throws(IOException::class, JSONException::class)
    fun readJsonFromUrl(url: String?): JSONObject? {
        val `is` = URL(url).openStream()
        return try {
            val rd = BufferedReader(
                InputStreamReader(
                    `is`,
                    Charset.forName("UTF-8")
                )
            )
            val jsonText = readAll(rd)
            JSONObject(jsonText)
        } finally {
            `is`.close()
        }
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    fun downloadFile(){
        var outputStream: OutputStream? = null
        var pdfpath: String? = null
        var fName = System.currentTimeMillis().toString()
        var file: File? = null
        pdfpath =
            Environment.getExternalStoragePublicDirectory(Environment.getExternalStoragePublicDirectory("/").absolutePath + "/ShopAndLicensePhoto").toString()
        file = File(pdfpath, "/$fName.pdf")
        outputStream = FileOutputStream(file)
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val uri: Uri = Uri.parse("http://192.168.1.5/treecensusapi/awdhan.geojson")
        val request = DownloadManager.Request(uri)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val reference: Long = manager.enqueue(request)
    }



}



