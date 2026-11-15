package com.protraderai.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

private data class Candle(val open: Double, val high: Double, val low: Double, val close: Double)
private data class TfResult(
    val label: String, val trend: Int, val rsi: Double, val atr: Double,
    val last: Double, val support: Double, val resistance: Double, val count: Int
)

class MainActivity : Activity() {
    private val bg = Color.rgb(12, 18, 32)
    private val panel = Color.rgb(23, 33, 52)
    private val white = Color.rgb(236, 242, 250)
    private val muted = Color.rgb(153, 169, 190)
    private val accent = Color.rgb(51, 196, 157)
    private lateinit var symbol: Spinner
    private lateinit var apiKey: EditText
    private lateinit var priceText: TextView
    private lateinit var statusText: TextView
    private lateinit var refresh: Button
    private lateinit var analyze: Button
    private lateinit var analysisText: TextView

    private val frames = listOf(
        "1month" to "MN", "1week" to "W1", "1day" to "D1",
        "4h" to "H4", "1h" to "H1", "15min" to "M15",
        "5min" to "M5", "1min" to "M1"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 18)
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(label("PROTRADER AI", 24, accent, true))
        content.addView(label("دستیار تحلیل بازارهای مالی | نسخه 0.2", 14, muted))
        content.addView(space(18))

        content.addView(card().apply {
            addView(label("نمای کلی بازار", 18, white, true))
            symbol = Spinner(this@MainActivity)
            val symbols = listOf("XAU/USD", "EUR/USD", "GBP/USD", "USD/JPY", "XAG/USD")
            symbol.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, symbols)
            addView(symbol)
            priceText = label("قیمت: —", 27, white, true)
            addView(priceText)
            statusText = label("وضعیت: کلید API را وارد کنید.", 12, muted)
            addView(statusText)
            refresh = Button(this@MainActivity).apply {
                text = "دریافت قیمت"
                setOnClickListener { fetchQuote() }
            }
            addView(refresh)
            analyze = Button(this@MainActivity).apply {
                text = "تحلیل مولتی‌تایم‌فریم"
                setOnClickListener { runAnalysis() }
            }
            addView(analyze)
        })

        content.addView(space(14))
        content.addView(card().apply {
            addView(label("اتصال داده بازار", 18, white, true))
            addView(label("ارائه‌دهنده: Twelve Data • برای تحلیل ۸ تایم‌فریم، سهمیه API کافی لازم است.", 12, muted))
            apiKey = EditText(this@MainActivity).apply {
                hint = "Twelve Data API Key"
                setHintTextColor(muted)
                setTextColor(white)
                inputType = 129
                setSingleLine(true)
            }
            addView(apiKey)
            addView(label("کلید روی دستگاه وارد می‌شود و در این نسخه در فضای ابری ذخیره نمی‌شود.", 11, muted))
        })

        content.addView(space(14))
        content.addView(card().apply {
            addView(label("گزارش تحلیل", 18, white, true))
            analysisText = label("پس از دریافت داده کافی، گزارش روند، RSI، ATR، حمایت و مقاومت هر تایم‌فریم در این قسمت نمایش داده می‌شود.", 13, white)
            addView(analysisText)
        })

        content.addView(space(14))
        content.addView(card().apply {
            addView(label("روش‌شناسی و محدودیت نسخه", 17, white, true))
            listOf(
                "ترتیب تحلیل: MN → W1 → D1 → H4 → H1 → M15 → M5 → M1",
                "نسخه فعلی: EMA20/EMA50، RSI14، ATR14 و محدوده نوسان اخیر",
                "امتیاز جهت‌گیری از هم‌سویی تایم‌فریم‌ها محاسبه می‌شود؛ سیگنال قطعی نیست.",
                "تشخیص دقیق OB/FVG، نقدینگی، خبر و اتصال MT5 هنوز در این نسخه فعال نیست."
            ).forEach { addView(label("•  $it", 12, white)) }
            addView(label("این ابزار فعلاً فقط تحلیل کمکی است؛ سفارش ثبت نمی‌کند و توصیه مالی شخصی ارائه نمی‌دهد.", 11, muted))
        })
        content.addView(space(12))
        content.addView(label("نسخه 0.2.0 • داده زنده وابسته به اعتبار و محدودیت حساب Twelve Data است.", 11, muted))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun fetchQuote() {
        val key = apiKey.text.toString().trim()
        if (key.isEmpty()) { statusText.text = "ابتدا کلید API را وارد کنید."; return }
        val pair = symbol.selectedItem.toString()
        refresh.isEnabled = false
        statusText.text = "در حال دریافت قیمت..."
        thread {
            try {
                val json = requestJson("https://api.twelvedata.com/price?symbol=${enc(pair)}&apikey=${enc(key)}")
                if (json.has("price")) {
                    val price = json.getString("price")
                    runOnUiThread {
                        priceText.text = "$pair  $price"
                        statusText.text = "دریافت قیمت موفق • ${now()}"
                        refresh.isEnabled = true
                    }
                } else throw Exception(json.optString("message", "پاسخ نامعتبر سرویس"))
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "خطا: ${e.message ?: "اتصال ناموفق"}"
                    refresh.isEnabled = true
                }
            }
        }
    }

    private fun runAnalysis() {
        val key = apiKey.text.toString().trim()
        if (key.isEmpty()) { statusText.text = "ابتدا کلید API را وارد کنید."; return }
        val pair = symbol.selectedItem.toString()
        refresh.isEnabled = false
        analyze.isEnabled = false
        statusText.text = "در حال دریافت ۸ مجموعه داده؛ ممکن است سهمیه API مصرف شود..."
        analysisText.text = "تحلیل در حال اجراست..."
        thread {
            try {
                val results = mutableListOf<TfResult>()
                for ((interval, label) in frames) {
                    val url = "https://api.twelvedata.com/time_series?symbol=${enc(pair)}&interval=${enc(interval)}&outputsize=100&apikey=${enc(key)}"
                    val json = requestJson(url)
                    val values = json.optJSONArray("values")
                        ?: throw Exception("$label: ${json.optString("message", "داده کندلی دریافت نشد")}")
                    val candles = mutableListOf<Candle>()
                    for (i in 0 until values.length()) {
                        val c = values.getJSONObject(i)
                        candles.add(Candle(c.getString("open").toDouble(), c.getString("high").toDouble(),
                            c.getString("low").toDouble(), c.getString("close").toDouble()))
                    }
                    candles.reverse()
                    if (candles.size < 55) throw Exception("$label: تعداد کندل کافی نیست (${candles.size})")
                    results.add(analyzeFrame(label, candles))
                }
                val report = buildReport(pair, results)
                runOnUiThread {
                    analysisText.text = report
                    statusText.text = "تحلیل داده‌ها کامل شد • ${now()}"
                    priceText.text = "$pair  ${fmt(results.last().last)}"
                    refresh.isEnabled = true
                    analyze.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "تحلیل کامل نشد: ${e.message ?: "خطای نامشخص"}"
                    analysisText.text = "برای جلوگیری از نتیجه‌سازی، تا دریافت داده معتبر گزارش سیگنال صادر نمی‌شود."
                    refresh.isEnabled = true
                    analyze.isEnabled = true
                }
            }
        }
    }

    private fun analyzeFrame(label: String, c: List<Candle>): TfResult {
        val closes = c.map { it.close }
        val ema20 = ema(closes, 20)
        val ema50 = ema(closes, 50)
        val last = closes.last()
        val trend = when {
            last > ema20 && ema20 > ema50 -> 1
            last < ema20 && ema20 < ema50 -> -1
            else -> 0
        }
        val rsi = rsi(closes, 14)
        val atr = atr(c, 14)
        val recent = c.takeLast(30)
        return TfResult(label, trend, rsi, atr, last,
            recent.minOf { it.low }, recent.maxOf { it.high }, c.size)
    }

    private fun buildReport(pair: String, r: List<TfResult>): String {
        val macro = r.take(4).sumOf { it.trend }
        val micro = r.drop(4).sumOf { it.trend }
        val total = r.sumOf { it.trend }
        val score = (abs(total) * 100 / r.size).coerceIn(0, 100)
        val direction = when {
            macro >= 3 && micro >= 2 -> "تمایل صعودی"
            macro <= -3 && micro <= -2 -> "تمایل نزولی"
            else -> "ناهم‌سویی / خنثی"
        }
        val decision = when {
            macro >= 3 && micro >= 2 && score >= 62 -> "بررسی سناریوی BUY؛ ورود هنوز نیازمند تأیید ساختار قیمت است."
            macro <= -3 && micro <= -2 && score >= 62 -> "بررسی سناریوی SELL؛ ورود هنوز نیازمند تأیید ساختار قیمت است."
            else -> "NO TRADE: هم‌سویی کافی بین روند اصلی و تایم‌فریم‌های ورود وجود ندارد."
        }
        val sb = StringBuilder()
        sb.append("نماد: $pair\nجهت کلی: $direction\nامتیاز هم‌سویی خام: $score/100\n")
        sb.append("روند تایم‌فریم‌های بالاتر (MN تا H4): ${bias(macro)}\n")
        sb.append("روند تایم‌فریم‌های ورود (H1 تا M1): ${bias(micro)}\n\n")
        r.forEach {
            val t = when(it.trend) { 1 -> "صعودی"; -1 -> "نزولی"; else -> "خنثی/مختلط" }
            sb.append("${it.label}: $t | RSI ${fmt(it.rsi)} | ATR ${fmt(it.atr)} | حمایت ${fmt(it.support)} | مقاومت ${fmt(it.resistance)}\n")
        }
        sb.append("\nنتیجه: $decision\n")
        sb.append("توجه: این امتیاز، احتمال موفقیت معامله نیست. OB/FVG، sweep، اخبار و حجم هنوز در امتیاز لحاظ نشده‌اند.")
        return sb.toString()
    }

    private fun bias(v: Int) = when { v >= 3 -> "صعودی"; v <= -3 -> "نزولی"; else -> "مختلط" }
    private fun ema(x: List<Double>, n: Int): Double {
        val k = 2.0 / (n + 1)
        var e = x.take(n).average()
        for (i in n until x.size) e = x[i] * k + e * (1 - k)
        return e
    }
    private fun rsi(x: List<Double>, n: Int): Double {
        var gains = 0.0; var losses = 0.0
        for (i in x.size - n until x.size) {
            val d = x[i] - x[i - 1]
            if (d > 0) gains += d else losses -= d
        }
        if (losses == 0.0) return 100.0
        val rs = (gains / n) / (losses / n)
        return 100.0 - 100.0 / (1.0 + rs)
    }
    private fun atr(c: List<Candle>, n: Int): Double {
        val trs = mutableListOf<Double>()
        for (i in c.size - n until c.size) {
            val prev = c[i - 1].close
            trs.add(max(c[i].high - c[i].low, max(abs(c[i].high - prev), abs(c[i].low - prev))))
        }
        return trs.average()
    }
    private fun requestJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 20000
        }
        try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
            if (json.has("status") && json.optString("status") == "error")
                throw Exception(json.optString("message", "API error"))
            return json
        } finally { conn.disconnect() }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun now() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.3f", v)

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 14); setBackgroundColor(panel)
    }
    private fun label(t: String, size: Int, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = size.toFloat(); setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 5, 0, 5)
        }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }
}
