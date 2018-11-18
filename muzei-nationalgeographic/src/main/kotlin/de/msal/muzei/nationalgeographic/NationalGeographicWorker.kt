package de.msal.muzei.nationalgeographic

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.*
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderContract
import java.io.IOException
import java.util.*

class NationalGeographicWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

   companion object {
      var isRandom = false

      internal fun enqueueLoad(random: Boolean) {
         this.isRandom = random
         WorkManager
               .getInstance()
               .enqueue(OneTimeWorkRequestBuilder<NationalGeographicWorker>()
                     .setConstraints(Constraints.Builder()
                           .setRequiredNetworkType(NetworkType.CONNECTED)
                           .build())
                     .build())
      }
   }

   override fun doWork(): Result {
      // fetch photo
      val photo = try {
         if (isRandom) {
            // get a random photo of a month between January 2011 and now
            val cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"))
            val randYear = getRand(2011, cal.get(Calendar.YEAR))
            val randMonth = if (randYear == cal.get(Calendar.YEAR)) getRand(1, cal.get(Calendar.MONTH) + 1) else getRand(1, 12)
            NationalGeographicService.getPhotosOfTheDay(randYear, randMonth)?.random()
         } else {
            // get most recent photo
            NationalGeographicService.getPhotosOfTheDay()?.first()
         }
      } catch (e: IOException) {
         Log.w(javaClass.simpleName, "Error reading API", e)
         return Result.RETRY
      }

      // check if successful
      if (photo == null) {
         Log.w(javaClass.simpleName, "No photo returned from API.")
         return Result.FAILURE
      } else if (photo.imageUrl == null) {
         Log.w(javaClass.simpleName, "Photo url is null (${photo.publishDate}).")
         return Result.FAILURE
      }

      // success -> set Artwork
      val artwork = Artwork().apply {
         title = photo.title
         byline = photo.publishDate
         attribution = photo.photographer
         persistentUri = photo.sizes?.get2048()?.toUri() ?: photo.imageUrl?.toUri()
         token = photo.description
         webUri = photo.pageUrlPhotoOfTheDay?.toUri()
      }
      if (isRandom) {
         ProviderContract.Artwork.addArtwork(applicationContext, NationalGeographicArtProvider::class.java, artwork)
      } else {
         ProviderContract.Artwork.setArtwork(applicationContext, NationalGeographicArtProvider::class.java, artwork)
      }
      return Result.SUCCESS
   }

   /**
    * @param min inclusive
    * @param max inclusive
    * @return the random number
    */
   private fun getRand(min: Int, max: Int): Int {
      val rand = Random()
      return rand.nextInt(max - min + 1) + min
   }

}
