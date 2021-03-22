package com.project.ti2358.data.manager

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.speech.tts.TextToSpeech
import com.project.ti2358.MainActivity
import com.project.ti2358.R
import com.project.ti2358.TheApplication
import com.project.ti2358.data.model.dto.Candle
import com.project.ti2358.service.log
import com.project.ti2358.service.toMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.abs


@KoinApiExtension
class StrategyRocket() : KoinComponent, TextToSpeech.OnInitListener {
    private val stockManager: StockManager by inject()
    var stocks: MutableList<Stock> = mutableListOf()
    var stocksSelected: MutableList<Stock> = mutableListOf()
    var rocketStocks: MutableList<RocketStock> = mutableListOf()
    var cometStocks: MutableList<RocketStock> = mutableListOf()

    private var started: Boolean = false
    private var textToSpeech: TextToSpeech? = null

    fun process(): MutableList<Stock> {
        val all = stockManager.getWhiteStocks()

        val min = SettingsManager.getCommonPriceMin()
        val max = SettingsManager.getCommonPriceMax()

        stocks.clear()
        stocks.addAll(all.filter { it.getPriceDouble() > min && it.getPriceDouble() < max })

        return stocks
    }

    override fun onInit(i: Int) {
        if (i == TextToSpeech.SUCCESS) {
            textToSpeech?.let {
                val locale = Locale.getDefault()
                val result = it.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                    Utils.showToastAlert("TTS не запущен 1")
                }
            }
        }
    }

    fun speak(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    fun startStrategy() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(TheApplication.application.applicationContext, this)
        }
        rocketStocks.clear()
        cometStocks.clear()
        started = true
    }

    fun stopStrategy() {
        started = false
    }

    @Synchronized
    fun processStrategy(stock: Stock) {
        if (!started) return

        val percentRocket = SettingsManager.getRocketChangePercent()
        var minutesRocket = SettingsManager.getRocketChangeMinutes()
        val volumeRocket = SettingsManager.getRocketChangeVolume()

        if (stock.minuteCandles.isNotEmpty()) {
            val firstCandle: Candle?
            val lastCandle = stock.minuteCandles.last()
            var fromIndex: Int = 0
            if (stock.minuteCandles.size >= minutesRocket) {
                fromIndex = stock.minuteCandles.size - minutesRocket
                firstCandle = stock.minuteCandles[fromIndex]
            } else {
                fromIndex = 0
                firstCandle = stock.minuteCandles.first()
            }

            var volume = 0
            for (i in fromIndex until stock.minuteCandles.size) {
                volume += stock.minuteCandles[i].volume
            }

            minutesRocket = stock.minuteCandles.size - fromIndex
            val changePercent = lastCandle.closingPrice / firstCandle.openingPrice * 100.0 - 100.0
            if (volume >= volumeRocket && abs(changePercent) >= percentRocket) {

                if ((rocketStocks.isNotEmpty() && rocketStocks.first().stock == stock) ||
                    (cometStocks.isNotEmpty() && cometStocks.first().stock == stock)) {
                    return
                }

                val rocketStock = RocketStock(stock, firstCandle.openingPrice, lastCandle.closingPrice, minutesRocket, volumeRocket, changePercent)
                rocketStock.process()
                if (changePercent > 0) {
                    rocketStocks.add(0, rocketStock)
                } else {
                    cometStocks.add(0, rocketStock)
                }
                createRocket(rocketStock)
            }
            log("ROCKET: ${stock.instrument.ticker}, $percentRocket > $changePercent")
        }
    }

    private fun createRocket(rocketStock: RocketStock) {
        val context: Context = TheApplication.application.applicationContext

        val ticker = rocketStock.stock.instrument.ticker
        val notificationChannelId = ticker

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Rocket notifications channel $ticker",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = notificationChannelId
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.enableLights(true)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(context, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(context, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, notificationChannelId) else Notification.Builder(context)

        var name = rocketStock.stock.instrument.name
        if (rocketStock.stock.alterName != "") {
            name = ticker
        }

        val changePercent: String
        if (rocketStock.changePercent > 0) {
            changePercent = "+%.2f%%".format(rocketStock.changePercent)
        } else {
            changePercent = "%.2f%%".format(rocketStock.changePercent)
        }

        if (SettingsManager.getRocketVoice()) {
            if (rocketStock.changePercent > 0) {
                speak("Ракета в $name! $changePercent за ${rocketStock.time} мин")
            } else {
                speak("Комета в $name! $changePercent за ${rocketStock.time} мин")
            }
        }

        val title = "${rocketStock.priceFrom.toMoney(rocketStock.stock)} -> ${rocketStock.priceTo.toMoney(rocketStock.stock)} = $changePercent за ${rocketStock.time} мин"

        val notification = builder
            .setSubText("$$ticker $changePercent")
            .setContentTitle(title)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .build()

        val manager = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        val uid = kotlin.random.Random.Default.nextInt(0, 100000)
        manager.notify(ticker, uid, notification)

        val alive: Long = SettingsManager.getRocketNotifyAlive().toLong()
        GlobalScope.launch(Dispatchers.Main) {
            delay(1000 * alive)
            manager.cancel(ticker, uid)
        }
    }
}